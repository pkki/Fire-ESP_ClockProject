package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.max

/**
 * 音楽ファイル自動音量ノーマライザー (AudioVolumeNormalizer)
 * - 楽曲ごとの音量のバラつき（録音レベルが低い曲など）を自動検知
 * - 最大音量（0 dBFS / 100% 音量）まで自動で引き上げ（Normalizing & Auto Gain Boost）
 * - ハードウェア LoudnessEnhancer と連携し、音割れ（クリッピング）のない迫力ある最大音量を実現
 */
object AudioVolumeNormalizer {
    private const val TAG = "AudioVolumeNormalizer"
    private const val PREFS_NAME = "desk_clock_audio_normalizer_cache"

    // filePath -> Peak amplitude fraction (0.0 .. 1.0)
    private val peakCache = ConcurrentHashMap<String, Float>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadCachedPeaks()
    }

    private fun loadCachedPeaks() {
        try {
            val ctx = appContext ?: return
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.all.forEach { (path, value) ->
                if (value is Float) {
                    peakCache[path] = value
                } else if (value is Number) {
                    peakCache[path] = value.toFloat()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached audio peaks: ${e.message}")
        }
    }

    private fun saveCachedPeak(filePath: String, peak: Float) {
        peakCache[filePath] = peak
        try {
            val ctx = appContext ?: return
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(filePath, peak)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * 指定された楽曲のピーク音量を基に、最大音量まで引き上げるためのゲイン倍率 (1.0f .. 3.5f) を取得
     */
    fun getTrackGainMultiplier(filePath: String): Float {
        val peak = peakCache[filePath] ?: 0.65f // 未解析時の安全なデフォルト推定値
        if (peak <= 0.05f) return 3.0f
        if (peak >= 0.95f) return 1.0f // 既に最大音量近くの曲はそのまま
        // 目標ピーク 0.96 (音割れ寸前の最大音量)
        val target = 0.96f
        val multiplier = target / peak
        return multiplier.coerceIn(1.0f, 3.5f)
    }

    /**
     * ハードウェア LoudnessEnhancer に設定する目標ブースト量 (mB: milliBels, 0 .. 1200) を算出
     * 例: peak 0.35 (-9.1 dBFS) -> +600mB (+6.0 dB) 〜 +900mB
     */
    fun calculateLoudnessBoostMb(filePath: String, baseBoostMb: Int = 400): Int {
        val peak = peakCache[filePath] ?: 0.65f
        if (peak >= 0.95f) return (baseBoostMb / 2).coerceIn(0, 300) // 既に音量が大きい曲は控えめに
        val deficitRatio = (0.95f - peak) / 0.95f // 0.0 (大きい) .. 1.0 (極小)
        val autoBoost = (deficitRatio * 800).toInt() // 最大 +800mB (+8.0dB) 追加
        return (baseBoostMb + autoBoost).coerceIn(100, 1200)
    }

    /**
     * バックグラウンドで楽曲のピーク音量を高速サンプリング解析
     */
    suspend fun analyzeTrackPeakAsync(filePath: String): Float = withContext(Dispatchers.IO) {
        val cached = peakCache[filePath]
        if (cached != null) return@withContext cached

        val file = File(filePath)
        if (!file.exists() || file.length() < 1024) {
            return@withContext 0.7f
        }

        try {
            val peak = sampleAudioPeak(filePath)
            saveCachedPeak(filePath, peak)
            Log.d(TAG, "Analyzed peak for ${file.name}: peak=$peak (gain boost=${getTrackGainMultiplier(filePath)}x, loudnessBoost=${calculateLoudnessBoostMb(filePath)}mB)")
            peak
        } catch (e: Throwable) {
            Log.w(TAG, "Audio peak sampling fallback for $filePath: ${e.message}")
            val fallback = 0.65f
            peakCache[filePath] = fallback
            fallback
        }
    }

    /**
     * MediaExtractor と MediaCodec を用いて曲全体から代表箇所（開始・中間・サビ等）をデコードし、最大PCMピーク振幅を検出
     */
    private fun sampleAudioPeak(filePath: String): Float {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(filePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return 0.7f
            }

            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 60_000_000L
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var maxSampleVal = 0
            val bufferInfo = MediaCodec.BufferInfo()

            // 楽曲の 10%, 30%, 50%, 70%, 85% の5地点からサンプリングして最大ピークを探索
            val samplePositionsUs = listOf(
                (durationUs * 0.10).toLong(),
                (durationUs * 0.30).toLong(),
                (durationUs * 0.50).toLong(),
                (durationUs * 0.70).toLong(),
                (durationUs * 0.85).toLong()
            )

            for (seekPos in samplePositionsUs) {
                try {
                    extractor.seekTo(seekPos, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    var framesRead = 0
                    var isEOS = false

                    while (framesRead < 15 && !isEOS) {
                        val inIndex = codec.dequeueInputBuffer(5000)
                        if (inIndex >= 0) {
                            val inBuffer = codec.getInputBuffer(inIndex)
                            if (inBuffer != null) {
                                val sampleSize = extractor.readSampleData(inBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    isEOS = true
                                } else {
                                    codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }

                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
                        if (outIndex >= 0) {
                            val outBuffer = codec.getOutputBuffer(outIndex)
                            if (outBuffer != null && bufferInfo.size > 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val pcm16 = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                while (pcm16.hasRemaining()) {
                                    val s = kotlin.math.abs(pcm16.get().toInt())
                                    if (s > maxSampleVal) {
                                        maxSampleVal = s
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            framesRead++
                        } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            framesRead++
                        }
                    }
                } catch (_: Throwable) {}
            }

            val peakFraction = (maxSampleVal.toFloat() / 32767.0f).coerceIn(0.05f, 1.0f)
            return peakFraction
        } catch (e: Throwable) {
            Log.w(TAG, "Error in sampleAudioPeak: ${e.message}")
            return 0.7f
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }
}

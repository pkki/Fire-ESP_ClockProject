package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Silent Audio Keep-Alive Manager (常時無音再生 / イヤホンジャックノイズ防止)
 *
 * 【開発背景・目的】
 * タブレットの3.5mmイヤホンジャックやライン出力をアンプ・外部スピーカーに接続した際、
 * AndroidのオーディオDACや内蔵アンプがスタンバイ（スリープ/ハイインピーダンス）状態に入ることで、
 * バックライト・CPU・Wi-Fi等の高周波ノイズが漏れて「ジー」「サー」という不快なホワイトノイズや
 * ハムノイズが発生します。また、時報やアラームが鳴る瞬間に「プチッ」「ボツッ」というポップノイズが発生します。
 *
 * 本クラスは、バックグラウンドで人間に聴こえない超微小信号（-90dBFS / 1-LSBディザまたは純無音）を
 * ループ再生し続けることで、DACおよびアンプの給電状態とバイアスを常時維持（Keep-Alive）します。
 *
 * 【特長】
 * 1. AudioTrack.MODE_STATIC + ループ再生により、CPU負荷は実質ほぼ0.0%（ハードウェアDMA転送）。
 * 2. 1-LSB（振幅 1/32768）のサブ可聴ディザ波形を採用し、MediaTekやRockchip等の過剰な
 *    ゼロ検出によるスリープ（Zero-Detect Power Gating）を確実に回避。
 * 3. アプリ内の他の音声（時報チャイム、BGM音楽、アラーム、音声アシスタント）とAudioFlingerで
 *    自動ミキシングされるため、他の音の邪魔を一切せず、再生開始時の音切れ・ポップ音も解消。
 */
object SilentAudioKeepAliveManager {
    private const val TAG = "SilentAudioKeepAlive"
    private const val SAMPLE_RATE = 44100
    private const val BUFFER_DURATION_SEC = 0.5
    private val DEFAULT_SAMPLE_COUNT = (SAMPLE_RATE * BUFFER_DURATION_SEC).toInt()

    private val lock = Any()
    private var audioTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * 無音キープアライブ再生を開始
     */
    fun start() {
        synchronized(lock) {
            if (audioTrack != null && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                _isPlaying.value = true
                return
            }

            try {
                stopInternal()

                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val sampleCount = maxOf(
                    DEFAULT_SAMPLE_COUNT,
                    if (minBuf > 0) (minBuf + 1) / 2 else DEFAULT_SAMPLE_COUNT
                )

                // 1-LSBディザ信号（人間の耳には完全な無音、DSPのゼロ検出スリープ防止）
                val samples = ShortArray(sampleCount) { idx ->
                    when (idx % 4) {
                        0 -> 1.toShort()
                        2 -> (-1).toShort()
                        else -> 0.toShort()
                    }
                }
                val byteCount = samples.size * 2

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                // loopCount = -1 で無限ループ再生
                track.setLoopPoints(0, samples.size, -1)
                track.setVolume(1.0f)
                track.play()

                audioTrack = track
                _isPlaying.value = true
                Log.i(TAG, "Silent keep-alive AudioTrack started successfully (session: ${track.audioSessionId})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start silent audio keep-alive track", e)
                stopInternal()
                _isPlaying.value = false
            }
        }
    }

    /**
     * 無音キープアライブ再生を停止
     */
    fun stop() {
        synchronized(lock) {
            stopInternal()
            _isPlaying.value = false
            Log.i(TAG, "Silent keep-alive AudioTrack stopped")
        }
    }

    /**
     * 現在の状態に応じてトグル
     */
    fun toggle(): Boolean {
        synchronized(lock) {
            return if (_isPlaying.value) {
                stop()
                false
            } else {
                start()
                true
            }
        }
    }

    private fun stopInternal() {
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping/releasing silent audio track", e)
        } finally {
            audioTrack = null
        }
    }
}

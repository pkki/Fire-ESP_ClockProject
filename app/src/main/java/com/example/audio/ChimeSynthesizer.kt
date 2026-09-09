package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class ChimeSound(val displayName: String, val description: String) {
    WESTMINSTER("ウエストミンスター", "学校や時計台の伝統的な4音メロディ"),
    ZEN_BELL("和の響き (水琴鈴)", "澄み渡る余韻と倍音の深い鐘の音"),
    CRYSTAL_BELL("クリスタルベル", "透明感のある澄んだ2音チャイム"),
    DIGITAL_SIGNAL("デジタル時報 (NHK式)", "伝統のピッピッピッ・ポーン"),
    SOFT_MARIMBA("ソフトマリンバ", "心地よい木の温もりを感じる和音")
}

object ChimeSynthesizer {
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null

    fun playChime(sound: ChimeSound, volume: Float = 0.8f, onComplete: (() -> Unit)? = null) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val samples = when (sound) {
                    ChimeSound.WESTMINSTER -> generateWestminsterSamples()
                    ChimeSound.ZEN_BELL -> generateZenBellSamples()
                    ChimeSound.CRYSTAL_BELL -> generateCrystalBellSamples()
                    ChimeSound.DIGITAL_SIGNAL -> generateDigitalSignalSamples()
                    ChimeSound.SOFT_MARIMBA -> generateMarimbaSamples()
                }
                playPcmSamples(samples, volume)
                onComplete?.invoke()
            } catch (_: Exception) {
                // Ignore playback cancellation or interruption
            }
        }
    }

    fun playSinglePing(volume: Float = 0.8f) {
        scope.launch {
            try {
                val samples = generateSinglePingSamples()
                playPcmSamples(samples, volume)
            } catch (_: Exception) {}
        }
    }

    private fun playPcmSamples(samples: ShortArray, volume: Float) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, samples.size * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            audioTrack.setVolume(volume.coerceIn(0f, 1f))
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            val durationMs = (samples.size * 1000L) / SAMPLE_RATE
            Thread.sleep(durationMs + 100)
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    // Westminster 4-tone chime: G#4 (415Hz) -> F#4 (370Hz) -> E4 (330Hz) -> B3 (247Hz)
    private fun generateWestminsterSamples(): ShortArray {
        val notes = listOf(
            415.30 to 0.45, // G#4
            369.99 to 0.45, // F#4
            329.63 to 0.50, // E4
            246.94 to 0.90  // B3
        )
        val totalDuration = notes.sumOf { it.second } + 0.5
        val totalSamples = (totalDuration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        var currentSample = 0
        for ((freq, duration) in notes) {
            val noteSamples = (duration * SAMPLE_RATE).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = exp(-3.0 * t / duration)
                // Fundamental + harmonics for rich tubular bell tone
                val wave = 0.65 * sin(2 * PI * freq * t) +
                        0.25 * sin(2 * PI * freq * 2 * t) +
                        0.10 * sin(2 * PI * freq * 3 * t)
                val sampleValue = (wave * envelope * 24000).toInt().coerceIn(-32768, 32767)
                val targetIndex = currentSample + i
                if (targetIndex < totalSamples) {
                    result[targetIndex] = (result[targetIndex] + sampleValue).coerceIn(-32768, 32767).toShort()
                }
            }
            currentSample += (0.42 * SAMPLE_RATE).toInt()
        }
        return result
    }

    // Zen Singing Bowl: resonant deep chime with lingering harmonic decay
    private fun generateZenBellSamples(): ShortArray {
        val duration = 3.2
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        val fundamental = 392.0 // G4
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env0 = exp(-1.2 * t)
            val env1 = exp(-2.0 * t)
            val env2 = exp(-3.5 * t)
            val wave = 0.55 * sin(2 * PI * fundamental * t) * env0 +
                    0.30 * sin(2 * PI * (fundamental * 2.02) * t) * env1 +
                    0.15 * sin(2 * PI * (fundamental * 3.98) * t) * env2
            result[i] = (wave * 26000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    // Crystal Bell: high crystal 2-tone ping
    private fun generateCrystalBellSamples(): ShortArray {
        val notes = listOf(
            659.25 to 0.5,  // E5
            987.77 to 1.4   // B5
        )
        val totalSamples = (2.0 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        var currentSample = 0
        for ((freq, duration) in notes) {
            val noteSamples = (duration * SAMPLE_RATE).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = exp(-3.2 * t / duration)
                val wave = 0.8 * sin(2 * PI * freq * t) +
                        0.2 * sin(2 * PI * freq * 2.76 * t)
                val sampleValue = (wave * envelope * 22000).toInt().coerceIn(-32768, 32767)
                val targetIndex = currentSample + i
                if (targetIndex < totalSamples) {
                    result[targetIndex] = (result[targetIndex] + sampleValue).coerceIn(-32768, 32767).toShort()
                }
            }
            currentSample += (0.35 * SAMPLE_RATE).toInt()
        }
        return result
    }

    // Digital Time Signal: Pip-pip-pip-peeeep!
    private fun generateDigitalSignalSamples(): ShortArray {
        val totalSamples = (2.2 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        // 3 short pips at 440Hz, 1 long at 880Hz
        val pips = listOf(
            0.0 to (440.0 to 0.08),
            0.4 to (440.0 to 0.08),
            0.8 to (440.0 to 0.08),
            1.2 to (880.0 to 0.55)
        )

        for ((startTime, note) in pips) {
            val (freq, dur) = note
            val startIdx = (startTime * SAMPLE_RATE).toInt()
            val durIdx = (dur * SAMPLE_RATE).toInt()
            for (i in 0 until durIdx) {
                val t = i.toDouble() / SAMPLE_RATE
                // Soft attack and release to avoid audio click
                val attack = minOf(1.0, t / 0.008)
                val release = minOf(1.0, (dur - t) / 0.015)
                val env = attack * release
                val wave = sin(2 * PI * freq * t)
                val idx = startIdx + i
                if (idx < totalSamples) {
                    result[idx] = (wave * env * 22000).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return result
    }

    // Soft Marimba
    private fun generateMarimbaSamples(): ShortArray {
        val chords = listOf(
            523.25 to 0.5, // C5
            659.25 to 0.6, // E5
            783.99 to 1.1  // G5
        )
        val totalSamples = (1.8 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        for ((freq, duration) in chords) {
            val noteSamples = (duration * SAMPLE_RATE).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = exp(-5.0 * t / duration)
                val wave = 0.85 * sin(2 * PI * freq * t) +
                        0.15 * sin(2 * PI * freq * 3.0 * t)
                val sampleValue = (wave * envelope * 12000).toInt().coerceIn(-32768, 32767)
                if (i < totalSamples) {
                    result[i] = (result[i] + sampleValue).coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return result
    }

    private fun generateSinglePingSamples(): ShortArray {
        val duration = 1.0
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq = 880.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-4.0 * t)
            val wave = sin(2 * PI * freq * t)
            result[i] = (wave * envelope * 20000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }
}

package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class ChimeSound(val displayName: String, val description: String) {
    WESTMINSTER("ウエストミンスター", "学校や時計台の伝統的な4音メロディ"),
    TUBULAR_BELLS("チューブラーベル", "重厚で荘厳な教会の鐘"),
    CRYSTAL_BELL("クリスタルベル", "透明感のある澄んだ2音チャイム"),
    BIRD_CHIRP("小鳥のさえずり", "爽やかな朝のさえずりハーモニー"),
    SOFT_MARIMBA("ソフトマリンバ", "心地よい木の温もりを感じる和音"),
    ZEN_BELL("静寂の和鐘", "澄み渡る余韻と倍音の深い鐘の音"),
    GRANDFATHER("アンティーク柱時計", "ボンボンと響くクラシックな古時計"),
    DIGITAL_SIGNAL("デジタル時報", "伝統のピッピッピッ・ポーン")
}

object ChimeSynthesizer {
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    private var countdownJob: Job? = null
    private var alarmJob: Job? = null
    private var fireSirenJob: Job? = null

    fun playFireSirenLoop(volume: Float = 1.0f) {
        fireSirenJob?.cancel()
        fireSirenJob = scope.launch {
            try {
                while (isActive) {
                    val samples = generateFireAlarmSirenSamples()
                    playPcmSamples(samples, volume)
                    delay(100)
                }
            } catch (_: Exception) {}
        }
    }

    fun stopFireSiren() {
        fireSirenJob?.cancel()
        fireSirenJob = null
    }

    fun isFireSirenPlaying(): Boolean = fireSirenJob?.isActive == true

    fun playAlarmLoop(soundType: String, volume: Float = 0.85f) {
        alarmJob?.cancel()
        alarmJob = scope.launch {
            try {
                while (isActive) {
                    val samples = when (soundType) {
                        "TWIN_BELL" -> generateTwinBellAlarmSamples()
                        "MELODY" -> generateMelodyAlarmSamples()
                        "JAPANESE" -> generateZenBellSamples()
                        "CHIME" -> generateWestminsterSamples()
                        else -> generateDigitalAlarmSamples()
                    }
                    playPcmSamples(samples, volume)
                    delay(300)
                }
            } catch (_: Exception) {}
        }
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
        playbackJob?.cancel()
        playbackJob = null
    }

    fun isAlarmPlaying(): Boolean = alarmJob?.isActive == true

    fun playButtonClickFeedback(volume: Float = 0.5f) {
        scope.launch {
            try {
                val samples = generateClickSamples()
                playPcmSamples(samples, volume)
            } catch (_: Exception) {}
        }
    }

    fun playChime(sound: ChimeSound, volume: Float = 0.8f, onComplete: (() -> Unit)? = null) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val samples = when (sound) {
                    ChimeSound.WESTMINSTER -> generateWestminsterSamples()
                    ChimeSound.TUBULAR_BELLS -> generateTubularBellsSamples()
                    ChimeSound.CRYSTAL_BELL -> generateCrystalBellSamples()
                    ChimeSound.BIRD_CHIRP -> generateBirdChirpSamples()
                    ChimeSound.SOFT_MARIMBA -> generateMarimbaSamples()
                    ChimeSound.ZEN_BELL -> generateZenBellSamples()
                    ChimeSound.GRANDFATHER -> generateGrandfatherSamples()
                    ChimeSound.DIGITAL_SIGNAL -> generateDigitalSignalSamples()
                }
                playPcmSamples(samples, volume)
                onComplete?.invoke()
            } catch (_: Exception) {
                // Ignore playback cancellation or interruption
            }
        }
    }

    fun playSinglePing(volume: Float = 0.8f, onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                val samples = generateSinglePingSamples()
                playPcmSamples(samples, volume)
                onComplete?.invoke()
            } catch (_: Exception) {
                onComplete?.invoke()
            }
        }
    }

    fun playEewAlarm(volume: Float = 1.0f, onComplete: (() -> Unit)? = null) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val samples = generateEewAlarmSamples()
                playPcmSamples(samples, volume)
                onComplete?.invoke()
            } catch (_: Exception) {}
        }
    }

    fun playCountdownBeep(count: Int, volume: Float = 0.85f) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            try {
                val samples = if (count <= 3) {
                    generateUrgentDoubleBeepSamples()
                } else {
                    generateStandardBeepSamples()
                }
                playPcmSamples(samples, volume)
            } catch (_: Exception) {}
        }
    }

    fun playArrivalAlert(volume: Float = 0.85f) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            try {
                val samples = generateArrivalAlertSamples()
                playPcmSamples(samples, volume)
            } catch (_: Exception) {}
        }
    }

    fun playCancelTone(volume: Float = 0.8f) {
        playbackJob?.cancel()
        countdownJob?.cancel()
        playbackJob = scope.launch {
            try {
                val samples = generateCancelSamples()
                playPcmSamples(samples, volume)
            } catch (_: Exception) {}
        }
    }

    private suspend fun playPcmSamples(samples: ShortArray, volume: Float) {
        if (samples.isEmpty()) return
        val validVolume = volume.coerceIn(0.1f, 1.0f)
        val byteCount = samples.size * 2

        // For short buffers (beeps, alerts, pips <= 64KB), use MODE_STATIC.
        // MODE_STATIC is 100% reliable on low-end hardware and Fire OS with zero buffer underrun!
        if (byteCount <= 65536) {
            var staticTrack: AudioTrack? = null
            try {
                staticTrack = AudioTrack.Builder()
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

                staticTrack.write(samples, 0, samples.size)
                staticTrack.setVolume(validVolume)
                staticTrack.play()

                val durationMs = (samples.size.toLong() * 1000L / SAMPLE_RATE) + 60L
                delay(durationMs)
            } catch (_: Exception) {
            } finally {
                try {
                    staticTrack?.stop()
                    staticTrack?.release()
                } catch (_: Exception) {}
            }
            return
        }

        // Streaming mode for longer audio like clock chimes
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 2, 8192)

        val audioTrack = AudioTrack.Builder()
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            audioTrack.setVolume(validVolume)
            audioTrack.play()

            var offset = 0
            val chunkSize = 2048
            while (offset < samples.size && coroutineContext.isActive) {
                val count = minOf(chunkSize, samples.size - offset)
                val written = audioTrack.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
                offset += written
            }

            // Drain audio buffer
            val tailWaitMs = (bufferSize.toLong() * 1000L / (SAMPLE_RATE * 2L)) + 100L
            delay(tailWaitMs)
        } catch (_: Exception) {
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    // Westminster Melody: E4, G#4, F#4, B3
    private fun generateWestminsterSamples(): ShortArray {
        val notes = listOf(
            659.25 to 0.75, // E5
            523.25 to 0.75, // C5
            587.33 to 0.75, // D5
            392.00 to 1.30  // G4
        )
        val totalSamples = (3.8 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        var currentSample = 0
        for ((freq, duration) in notes) {
            val noteSamples = (duration * SAMPLE_RATE).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = exp(-2.8 * t / duration)
                val wave = 0.70 * sin(2 * PI * freq * t) +
                        0.20 * sin(2 * PI * freq * 2.0 * t) +
                        0.10 * sin(2 * PI * freq * 3.0 * t)
                val sampleValue = (wave * envelope * 22000).toInt().coerceIn(-32768, 32767)
                val targetIndex = currentSample + i
                if (targetIndex < totalSamples) {
                    result[targetIndex] = (result[targetIndex] + sampleValue).coerceIn(-32768, 32767).toShort()
                }
            }
            currentSample += (0.65 * SAMPLE_RATE).toInt()
        }
        return result
    }

    // Tubular Bells (Church bell chime)
    private fun generateTubularBellsSamples(): ShortArray {
        val notes = listOf(
            440.0 to 1.2,  // A4
            554.37 to 1.2, // C#5
            659.25 to 1.8  // E5
        )
        val totalSamples = (3.5 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        var currentSample = 0
        for ((freq, duration) in notes) {
            val noteSamples = (duration * SAMPLE_RATE).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = exp(-2.2 * t / duration)
                val wave = 0.55 * sin(2 * PI * freq * t) +
                        0.25 * sin(2 * PI * freq * 2.756 * t) +
                        0.15 * sin(2 * PI * freq * 5.404 * t) +
                        0.05 * sin(2 * PI * freq * 8.932 * t)
                val sampleValue = (wave * envelope * 22000).toInt().coerceIn(-32768, 32767)
                val targetIndex = currentSample + i
                if (targetIndex < totalSamples) {
                    result[targetIndex] = (result[targetIndex] + sampleValue).coerceIn(-32768, 32767).toShort()
                }
            }
            currentSample += (0.7 * SAMPLE_RATE).toInt()
        }
        return result
    }

    // Zen Suikinkutsu / Temple Bell
    private fun generateZenBellSamples(): ShortArray {
        val totalSamples = (3.2 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val fundamental = 330.0 // E4

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-1.4 * t)
            val wave = 0.50 * sin(2 * PI * fundamental * t) +
                    0.25 * sin(2 * PI * fundamental * 1.52 * t) +
                    0.15 * sin(2 * PI * fundamental * 2.18 * t) +
                    0.10 * sin(2 * PI * fundamental * 3.45 * t)
            result[i] = (wave * envelope * 24000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    // Crystal Bell: high pure chimes
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

    // Bird Chirp Synthesis
    private fun generateBirdChirpSamples(): ShortArray {
        val totalSamples = (2.2 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val chirps = listOf(
            0.0 to 0.35,
            0.4 to 0.30,
            0.8 to 0.45,
            1.3 to 0.35
        )

        for ((startTime, duration) in chirps) {
            val startIdx = (startTime * SAMPLE_RATE).toInt()
            val durIdx = (duration * SAMPLE_RATE).toInt()
            for (i in 0 until durIdx) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = t / duration
                val freq = 2200.0 + 1200.0 * sin(PI * progress) + 300.0 * sin(4 * PI * progress)
                val env = sin(PI * progress) * exp(-1.5 * progress)
                val wave = sin(2 * PI * freq * t)
                val idx = startIdx + i
                if (idx < totalSamples) {
                    result[idx] = (result[idx] + (wave * env * 18000).toInt()).coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return result
    }

    // Antique Grandfather Clock (Deep Gong)
    private fun generateGrandfatherSamples(): ShortArray {
        val totalSamples = (3.6 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val gongs = listOf(0.0, 1.2) // 2 deep strikes

        for (startTime in gongs) {
            val startIdx = (startTime * SAMPLE_RATE).toInt()
            val strikeDur = (2.2 * SAMPLE_RATE).toInt()
            for (i in 0 until strikeDur) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-2.0 * t)
                val freq = 130.81 // C3 deep gong
                val wave = 0.60 * sin(2 * PI * freq * t) +
                        0.25 * sin(2 * PI * freq * 2.01 * t) +
                        0.15 * sin(2 * PI * freq * 3.12 * t)
                val idx = startIdx + i
                if (idx < totalSamples) {
                    result[idx] = (result[idx] + (wave * env * 24000).toInt()).coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return result
    }

    // Digital Time Signal: Pip-pip-pip-peeeep!
    private fun generateDigitalSignalSamples(): ShortArray {
        val totalSamples = (2.2 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

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

    private fun generateEewAlarmSamples(): ShortArray {
        // High-contrast rising EEW alert tones (C5, E5, G5, C6) repeated
        val notes = listOf(
            523.25 to 0.12, // C5
            659.25 to 0.12, // E5
            783.99 to 0.12, // G5
            1046.50 to 0.28 // C6
        )
        val repeatCount = 3
        val singlePatternDuration = notes.sumOf { it.second } + 0.15
        val totalDuration = singlePatternDuration * repeatCount
        val totalSamples = (totalDuration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)

        var currentSampleOffset = 0
        for (rep in 0 until repeatCount) {
            for ((freq, duration) in notes) {
                val noteSamples = (duration * SAMPLE_RATE).toInt()
                for (i in 0 until noteSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = (1.0 - exp(-30.0 * t)) * exp(-1.5 * t / duration)
                    val wave = 0.7 * sin(2 * PI * freq * t) +
                            0.25 * sin(2 * PI * (freq * 2) * t) +
                            0.05 * sin(2 * PI * (freq * 3) * t)
                    val sample = (wave * env * 24000).toInt().coerceIn(-32768, 32767).toShort()
                    val idx = currentSampleOffset + i
                    if (idx < totalSamples) {
                        result[idx] = sample
                    }
                }
                currentSampleOffset += noteSamples
            }
            currentSampleOffset += (0.15 * SAMPLE_RATE).toInt()
        }
        return result
    }

    private fun generateStandardBeepSamples(): ShortArray {
        val duration = 0.055 // 55ms
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq = 1046.50 // C6 clear electronic blip
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = minOf(1.0, t / 0.005)
            val release = minOf(1.0, (duration - t) / 0.010)
            val env = attack * release
            val wave = sin(2 * PI * freq * t)
            result[i] = (wave * env * 22000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    private fun generateUrgentDoubleBeepSamples(): ShortArray {
        val duration = 0.160 // 160ms total: 45ms beep + 40ms silence + 45ms beep + 30ms tail
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq = 1480.0 // Urgent high warning frequency
        val beepDuration = 0.045
        val beepSamples = (beepDuration * SAMPLE_RATE).toInt()
        val gapSamples = (0.035 * SAMPLE_RATE).toInt()

        // First beep
        for (i in 0 until beepSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = minOf(1.0, t / 0.004) * minOf(1.0, (beepDuration - t) / 0.008)
            val wave = sin(2 * PI * freq * t)
            result[i] = (wave * env * 25000).toInt().coerceIn(-32768, 32767).toShort()
        }

        // Second beep
        val offset2 = beepSamples + gapSamples
        for (i in 0 until beepSamples) {
            val idx = offset2 + i
            if (idx < totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = minOf(1.0, t / 0.004) * minOf(1.0, (beepDuration - t) / 0.008)
                val wave = sin(2 * PI * freq * t)
                result[idx] = (wave * env * 25000).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return result
    }

    private fun generateArrivalAlertSamples(): ShortArray {
        // Deep warning burst when S-wave reaches
        val duration = 0.35 // 350ms
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq1 = 587.33 // D5
        val freq2 = 880.0  // A5
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-6.0 * t / duration)
            val wave = 0.6 * sin(2 * PI * freq1 * t) + 0.4 * sin(2 * PI * freq2 * t)
            result[i] = (wave * env * 26000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    private fun generateCancelSamples(): ShortArray {
        // Gentle descending tone indicating cancel
        val totalSamples = (0.6 * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val tones = listOf(
            0.0 to (783.99 to 0.22), // G5
            0.22 to (523.25 to 0.35) // C5
        )
        for ((startTime, note) in tones) {
            val (freq, dur) = note
            val startIdx = (startTime * SAMPLE_RATE).toInt()
            val durIdx = (dur * SAMPLE_RATE).toInt()
            for (i in 0 until durIdx) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-3.5 * t / dur)
                val wave = sin(2 * PI * freq * t)
                val idx = startIdx + i
                if (idx < totalSamples) {
                    result[idx] = (wave * env * 20000).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return result
    }

    private fun generateDigitalAlarmSamples(): ShortArray {
        // Classic digital alarm "Pi-Pi-Pi-Pi" (4 rapid pulses, 1000Hz)
        val duration = 0.8
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq = 2048.0 // High pitch electronic beeper
        val pulseCount = 4
        val pulseDur = 0.08
        val gapDur = 0.05

        for (p in 0 until pulseCount) {
            val startT = p * (pulseDur + gapDur)
            val startIdx = (startT * SAMPLE_RATE).toInt()
            val pulseSamples = (pulseDur * SAMPLE_RATE).toInt()
            for (i in 0 until pulseSamples) {
                val idx = startIdx + i
                if (idx < totalSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = minOf(1.0, t / 0.003) * minOf(1.0, (pulseDur - t) / 0.003)
                    val wave = sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 2 * t)
                    result[idx] = (wave * env * 24000).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return result
    }

    private fun generateFireAlarmSirenSamples(): ShortArray {
        // Japanese Residential Fire Alarm Siren (770Hz - 960Hz frequency sweep modulated siren)
        val sweepDuration = 0.5 // 0.5s per sweep (2 sweeps per second)
        val sweepSamples = (sweepDuration * SAMPLE_RATE).toInt()
        val totalSamples = sweepSamples * 2
        val result = ShortArray(totalSamples)

        for (sweep in 0..1) {
            val offset = sweep * sweepSamples
            for (i in 0 until sweepSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val normT = t / sweepDuration
                // Frequency rises exponentially/linearly from 770Hz to 960Hz
                val instantFreq = 770.0 + (960.0 - 770.0) * normT
                val phase = 2 * PI * (770.0 * t + 0.5 * (960.0 - 770.0) * normT * t)
                // Add rich third harmonic for piercing emergency alert quality
                val wave = 0.8 * sin(phase) + 0.3 * sin(phase * 3.0)
                val env = minOf(1.0, t / 0.02) * minOf(1.0, (sweepDuration - t) / 0.02)
                result[offset + i] = (wave * env * 29000).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return result
    }

    private fun generateTwinBellAlarmSamples(): ShortArray {
        // Mechanical twin-bell ringing hammer vibration
        val duration = 0.7
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq1 = 2800.0
        val freq2 = 3200.0
        val modFreq = 24.0 // 24Hz hammer strike

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val am = 0.5 * (1.0 + sin(2 * PI * modFreq * t))
            val wave = 0.6 * sin(2 * PI * freq1 * t) + 0.4 * sin(2 * PI * freq2 * t)
            val env = minOf(1.0, t / 0.01) * minOf(1.0, (duration - t) / 0.05)
            result[i] = (wave * am * env * 26000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    private fun generateMelodyAlarmSamples(): ShortArray {
        // Cheerful morning waking arpeggio
        val notes = listOf(
            523.25 to 0.12, // C5
            659.25 to 0.12, // E5
            783.99 to 0.12, // G5
            1046.50 to 0.28 // C6
        )
        val duration = 0.75
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        var curStart = 0.0

        for ((freq, noteDur) in notes) {
            val startIdx = (curStart * SAMPLE_RATE).toInt()
            val noteSamples = (noteDur * SAMPLE_RATE).toInt()
            for (i in 0 until noteSamples) {
                val idx = startIdx + i
                if (idx < totalSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = exp(-3.0 * t / noteDur)
                    val wave = sin(2 * PI * freq * t) + 0.25 * sin(2 * PI * freq * 2 * t)
                    result[idx] = (wave * env * 24000).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            curStart += noteDur + 0.02
        }
        return result
    }

    private fun generateClickSamples(): ShortArray {
        // High crisp UI click / mechanical tactile sound
        val duration = 0.03
        val totalSamples = (duration * SAMPLE_RATE).toInt()
        val result = ShortArray(totalSamples)
        val freq = 1600.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-120.0 * t)
            val wave = sin(2 * PI * freq * t) + 0.5 * sin(2 * PI * 3200.0 * t)
            result[i] = (wave * env * 22000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }
}

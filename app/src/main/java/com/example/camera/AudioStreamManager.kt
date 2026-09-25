package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioStreamManager(
    private val context: Context,
    private val server: IpCameraServer
) {
    companion object {
        private const val TAG = "AudioStreamManager"
        const val SAMPLE_RATE = 16000 // 16 kHz
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    private val _audioLevelPercent = MutableStateFlow(0)
    val audioLevelPercent: StateFlow<Int> = _audioLevelPercent.asStateFlow()

    private var gainMultiplier = 1.0f

    fun setGain(gain: Float) {
        gainMultiplier = gain.coerceIn(0.1f, 5.0f)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(gain: Float = 1.0f, onError: ((String) -> Unit)? = null) {
        if (isRecording.get()) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val msg = "マイク録音の権限が許可されていません"
            Log.w(TAG, msg)
            onError?.invoke(msg)
            return
        }

        gainMultiplier = gain

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onError?.invoke("音声録音バッファの初期化に失敗しました")
                return
            }

            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                onError?.invoke("マイクの初期化に失敗しました")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingThread = Thread({
                val audioBuffer = ShortArray(1024)
                val byteBuffer = ByteArray(audioBuffer.size * 2)

                while (isRecording.get()) {
                    val record = audioRecord ?: break
                    val readCount = record.read(audioBuffer, 0, audioBuffer.size)

                    if (readCount > 0) {
                        var sumOfSquares = 0.0
                        var byteIdx = 0

                        val applyGain = gainMultiplier != 1.0f

                        for (i in 0 until readCount) {
                            var sample = audioBuffer[i].toInt()
                            if (applyGain) {
                                sample = (sample * gainMultiplier).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            }
                            sumOfSquares += sample * sample

                            // Little-endian PCM 16-bit
                            byteBuffer[byteIdx++] = (sample and 0xFF).toByte()
                            byteBuffer[byteIdx++] = ((sample shr 8) and 0xFF).toByte()
                        }

                        // Calculate RMS level (0 - 100%)
                        val rms = sqrt(sumOfSquares / readCount)
                        val level = ((rms / 32768.0) * 100.0 * 2.5).toInt().coerceIn(0, 100)
                        _audioLevelPercent.value = level

                        // Send to server
                        server.pushAudioChunk(byteBuffer.copyOf(byteIdx), level)
                    } else if (readCount < 0) {
                        Log.e(TAG, "AudioRecord read error: $readCount")
                        Thread.sleep(20)
                    }
                }
            }, "AudioStreamRecordingThread").apply { start() }

            Log.i(TAG, "Audio recording started (Sample rate: $SAMPLE_RATE Hz, 16-bit Mono)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            onError?.invoke("マイク起動エラー: ${e.message}")
            stopRecording()
        }
    }

    fun stopRecording() {
        if (!isRecording.getAndSet(false)) return

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            recordingThread?.interrupt()
            recordingThread = null
        } catch (_: Exception) {}

        _audioLevelPercent.value = 0
        Log.i(TAG, "Audio recording stopped")
    }

    fun isRecording(): Boolean = isRecording.get()
}

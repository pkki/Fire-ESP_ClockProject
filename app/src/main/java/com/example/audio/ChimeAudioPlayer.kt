package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.model.ChimeAudioSourceType
import com.example.model.ScheduledChime
import java.io.File

object ChimeAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingCustom = false

    fun playScheduledChime(
        context: Context,
        chime: ScheduledChime,
        onComplete: (() -> Unit)? = null
    ) {
        stop()
        if (chime.sourceType == ChimeAudioSourceType.NONE) {
            // No audio configured (e.g. IR remote routine only, or silent video)
            onComplete?.invoke()
            return
        }
        if (chime.isVideoOnlyAudio) {
            // Video plays its own audio track; no separate synthetic/audio chime needed
            onComplete?.invoke()
            return
        }
        if (chime.sourceType == ChimeAudioSourceType.CUSTOM_FILE && !chime.customAudioPath.isNullOrEmpty()) {
            playCustomFile(chime.customAudioPath, chime.volume, onComplete)
        } else {
            ChimeSynthesizer.playChime(chime.builtInSound, chime.volume, onComplete)
        }
    }

    fun playCustomFile(
        filePath: String,
        volume: Float = 0.85f,
        onComplete: (() -> Unit)? = null
    ) {
        stop()
        val file = File(filePath)
        if (!file.exists()) {
            android.util.Log.e("ChimeAudioPlayer", "Audio file does not exist: $filePath")
            onComplete?.invoke()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                java.io.FileInputStream(file).use { fis ->
                    setDataSource(fis.fd)
                }
                setVolume(volume, volume)
                setOnCompletionListener {
                    val sessionId = try { it.audioSessionId } catch (_: Exception) { 0 }
                    if (sessionId > 0) {
                        AudioEqualizerManager.unregisterAudioSession(sessionId)
                    }
                    it.release()
                    mediaPlayer = null
                    isPlayingCustom = false
                    onComplete?.invoke()
                }
                setOnErrorListener { mp, what, extra ->
                    android.util.Log.e("ChimeAudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    val sessionId = try { mp.audioSessionId } catch (_: Exception) { 0 }
                    if (sessionId > 0) {
                        AudioEqualizerManager.unregisterAudioSession(sessionId)
                    }
                    mp.release()
                    mediaPlayer = null
                    isPlayingCustom = false
                    onComplete?.invoke()
                    true
                }
                setOnPreparedListener { mp ->
                    val sessionId = mp.audioSessionId
                    if (sessionId > 0) {
                        AudioEqualizerManager.registerAudioSession(sessionId)
                    }
                    mp.start()
                    isPlayingCustom = true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            android.util.Log.e("ChimeAudioPlayer", "Exception in playCustomFile: $filePath", e)
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayingCustom = false
            onComplete?.invoke()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let { mp ->
                val sessionId = try { mp.audioSessionId } catch (_: Exception) { 0 }
                if (sessionId > 0) {
                    AudioEqualizerManager.unregisterAudioSession(sessionId)
                }
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
        isPlayingCustom = false
    }

    fun isPlaying(): Boolean = isPlayingCustom
}

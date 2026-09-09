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
        if (!file.exists() || !file.canRead()) {
            onComplete?.invoke()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(filePath)
                setVolume(volume, volume)
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    isPlayingCustom = false
                    onComplete?.invoke()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    isPlayingCustom = false
                    onComplete?.invoke()
                    true
                }
                prepare()
                start()
            }
            isPlayingCustom = true
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayingCustom = false
            onComplete?.invoke()
        }
    }

    fun stop() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
        } catch (_: Exception) {}
        isPlayingCustom = false
    }

    fun isPlaying(): Boolean = isPlayingCustom
}

package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.R
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class EewVoiceAnnouncer(private val context: Context) : TextToSpeech.OnInitListener {
    companion object {
        private const val TAG = "EewVoiceAnnouncer"
    }

    private val appContext = context.applicationContext

    // Volume fraction (0.0f .. 1.0f) from user's chime volume preferences
    var volumeFraction: Float = 0.8f
    var soundMode: String = "SYNTH_BEEP" // "SYNTH_BEEP", "VOICE", "MUTE"

    // AudioAttributes for media playback (synchronized with user's music volume)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // SoundPool for zero-latency countdown ticks, numbers, and voice clips
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(audioAttributes)
        .build()

    private val soundMap = ConcurrentHashMap<Int, Int>()
    private val loadedSounds = ConcurrentHashMap<Int, Boolean>()

    // ToneGenerator: built-in native hardware telephony/prompt engine that NEVER fails on Android
    private var toneGenerator: ToneGenerator? = null

    private var activeMediaPlayer: MediaPlayer? = null
    private var isPlayingChime: Boolean = false
    private var pendingFollowup: (() -> Unit)? = null

    // Optional TTS engine
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init error", e)
        }
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds[sampleId] = true
            }
        }
        preloadSounds()
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.w(TAG, "TTS initialization skipped/failed: ${e.message}")
        }
    }

    private fun preloadSounds() {
        val soundResIds = listOf(
            R.raw.eew_chime,
            R.raw.countdown_beep,
            R.raw.countdown_urgent,
            R.raw.count_1,
            R.raw.count_2,
            R.raw.count_3,
            R.raw.count_4,
            R.raw.count_5,
            R.raw.count_6,
            R.raw.count_7,
            R.raw.count_8,
            R.raw.count_9,
            R.raw.count_10,
            R.raw.voice_arrival,
            R.raw.voice_cancel,
            R.raw.voice_eew_alert,
            R.raw.voice_after_15s,
            R.raw.voice_after_20s,
            R.raw.voice_after_30s
        )
        for (resId in soundResIds) {
            try {
                val soundId = soundPool.load(appContext, resId, 1)
                soundMap[resId] = soundId
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading sound resource $resId", e)
            }
        }
    }

    private fun ensureAudibleVolume() {
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            // If device media volume is muted (0), bring it up to an audible level based on chimeVolume (NOT MAX)
            if (currentVol == 0 && maxVol > 0) {
                val targetVol = (maxVol * volumeFraction).toInt().coerceIn(1, (maxVol * 0.7f).toInt())
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                Log.d(TAG, "Muted device adjusted to STREAM_MUSIC volume $targetVol/$maxVol")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check/adjust stream volume", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val result = tts?.setLanguage(Locale.JAPANESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = false
                    Log.d(TAG, "Japanese TTS not supported on device; using built-in high fidelity voice assets")
                } else {
                    tts?.setSpeechRate(1.15f)
                    tts?.setPitch(1.0f)
                    isTtsReady = true
                }
            } catch (e: Exception) {
                isTtsReady = false
            }
        } else {
            isTtsReady = false
        }
    }

    fun playEewChime(volume: Float = volumeFraction) {
        if (soundMode == "MUTE") return
        ensureAudibleVolume()
        volumeFraction = volume
        stopActivePlayer()

        val vol = volume.coerceIn(0.2f, 1.0f)

        // 1. Try playing preloaded eew_chime.wav via SoundPool (instant, lowest latency)
        val chimeSoundId = soundMap[R.raw.eew_chime]
        var played = false
        if (chimeSoundId != null && (loadedSounds[chimeSoundId] == true || chimeSoundId > 0)) {
            val streamId = soundPool.play(chimeSoundId, vol, vol, 3, 0, 1.0f)
            if (streamId > 0) {
                played = true
            }
        }

        // 2. If SoundPool didn't play, try MediaPlayer
        if (!played) {
            isPlayingChime = true
            try {
                val mp = MediaPlayer.create(appContext, R.raw.eew_chime)
                if (mp != null) {
                    activeMediaPlayer = mp
                    mp.setVolume(vol, vol)
                    mp.setOnCompletionListener {
                        isPlayingChime = false
                        mp.release()
                        if (activeMediaPlayer == mp) {
                            activeMediaPlayer = null
                        }
                        val next = pendingFollowup
                        pendingFollowup = null
                        next?.invoke()
                    }
                    mp.start()
                    played = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play eew_chime MediaPlayer", e)
            }
        }

        // 3. Synthesizer & Hardware Tone fallback
        if (!played) {
            isPlayingChime = false
            ChimeSynthesizer.playEewAlarm(volume = vol) {
                val next = pendingFollowup
                pendingFollowup = null
                next?.invoke()
            }
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 800)
            } catch (_: Exception) {}
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (soundMode == "MUTE") return
        if (text.isBlank()) return
        val trimmed = text.trim()
        ensureAudibleVolume()

        // 1. Countdown numbers "1" through "10"
        val number = trimmed.toIntOrNull()
        if (number != null && number in 1..10) {
            playCountdownPip(number)
            return
        }

        // 2. Cancellation
        if (trimmed.contains("取り消") || trimmed.contains("取消")) {
            playCancelAlert()
            return
        }

        // 3. Arrival
        if (trimmed.contains("到達") || trimmed.contains("主要動")) {
            playArrivalAlert()
            return
        }

        // 4. Initial alert ("〇〇で地震。到達まで、あと〇秒。")
        if (trimmed.contains("地震") || trimmed.contains("速報") || trimmed.contains("警戒")) {
            if (soundMode == "VOICE") {
                if (isPlayingChime) {
                    pendingFollowup = { playRawVoice(R.raw.voice_eew_alert) }
                } else {
                    playRawVoice(R.raw.voice_eew_alert)
                }
            } else {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
                } catch (_: Exception) {}
            }
            return
        }

        // 5. Milestone times ("15秒", "20秒", "30秒")
        if (trimmed.contains("15秒") || trimmed.contains("20秒") || trimmed.contains("30秒")) {
            if (soundMode == "VOICE") {
                val res = when {
                    trimmed.contains("15秒") -> R.raw.voice_after_15s
                    trimmed.contains("20秒") -> R.raw.voice_after_20s
                    else -> R.raw.voice_after_30s
                }
                playRawVoice(res)
            } else {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                } catch (_: Exception) {}
            }
            return
        }

        // 6. Fallback to TTS if supported on device (in VOICE mode)
        if (soundMode == "VOICE" && isTtsReady) {
            try {
                val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(trimmed, queueMode, null, "eew_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak failed", e)
            }
        }
    }

    fun playCountdownPip(count: Int) {
        ensureAudibleVolume()
        val vol = volumeFraction.coerceIn(0.2f, 1.0f)

        // 1. Instant hardware ToneGenerator: zero latency, guaranteed output on all Android/Fire devices
        try {
            val tone = if (count <= 3) ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE else ToneGenerator.TONE_PROP_BEEP
            val duration = if (count <= 3) 140 else 80
            toneGenerator?.startTone(tone, duration)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator countdown beep error", e)
        }

        // 2. Play high-quality beep from SoundPool
        val beepRes = if (count <= 3) R.raw.countdown_urgent else R.raw.countdown_beep
        val beepId = soundMap[beepRes]
        if (beepId != null && (loadedSounds[beepId] == true || beepId > 0)) {
            soundPool.play(beepId, vol, vol, 2, 0, 1.0f)
        } else {
            ChimeSynthesizer.playCountdownBeep(count, vol)
        }

        // 3. If in VOICE mode, also play spoken Japanese countdown number ("じゅう", "きゅう" ... "いち")
        if (soundMode == "VOICE") {
            val countRes = when (count) {
                1 -> R.raw.count_1
                2 -> R.raw.count_2
                3 -> R.raw.count_3
                4 -> R.raw.count_4
                5 -> R.raw.count_5
                6 -> R.raw.count_6
                7 -> R.raw.count_7
                8 -> R.raw.count_8
                9 -> R.raw.count_9
                10 -> R.raw.count_10
                else -> null
            }
            if (countRes != null) {
                val numSoundId = soundMap[countRes]
                if (numSoundId != null && (loadedSounds[numSoundId] == true || numSoundId > 0)) {
                    soundPool.play(numSoundId, vol, vol, 1, 0, 1.0f)
                }
            }
        }
    }

    fun playArrivalAlert() {
        ensureAudibleVolume()
        val vol = volumeFraction.coerceIn(0.2f, 1.0f)
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 350)
        } catch (_: Exception) {}
        ChimeSynthesizer.playArrivalAlert(vol)
        if (soundMode == "VOICE") {
            playRawVoice(R.raw.voice_arrival)
        }
    }

    fun playCancelAlert() {
        ensureAudibleVolume()
        val vol = volumeFraction.coerceIn(0.2f, 1.0f)
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 250)
        } catch (_: Exception) {}
        ChimeSynthesizer.playCancelTone(vol)
        if (soundMode == "VOICE") {
            playRawVoice(R.raw.voice_cancel)
        }
    }

    private fun playRawVoice(resId: Int) {
        // First attempt using pre-cached SoundPool for zero lag
        val soundId = soundMap[resId]
        val vol = volumeFraction.coerceIn(0.2f, 1.0f)
        if (soundId != null && (loadedSounds[soundId] == true || soundId > 0)) {
            val streamId = soundPool.play(soundId, vol, vol, 1, 0, 1.0f)
            if (streamId > 0) return
        }

        // Fallback to MediaPlayer
        stopActivePlayer()
        try {
            val mp = MediaPlayer.create(appContext, resId)
            if (mp != null) {
                activeMediaPlayer = mp
                mp.setVolume(vol, vol)
                mp.setOnCompletionListener {
                    mp.release()
                    if (activeMediaPlayer == mp) {
                        activeMediaPlayer = null
                    }
                }
                mp.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing raw voice $resId", e)
        }
    }

    private fun stopActivePlayer() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        activeMediaPlayer = null
        isPlayingChime = false
    }

    fun stop() {
        pendingFollowup = null
        stopActivePlayer()
        try {
            soundPool.autoPause()
        } catch (_: Exception) {}
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun destroy() {
        stop()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
        try {
            soundPool.release()
        } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (_: Exception) {}
    }
}


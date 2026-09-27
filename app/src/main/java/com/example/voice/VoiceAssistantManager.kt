package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.VoiceAssistantState
import com.example.model.WakeWordOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Ultra-lightweight Voice Assistant & Wake Word Manager
 * Uses Android native SpeechRecognizer and TextToSpeech.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val commandCallbacks: VoiceCommandCallbacks
) {
    companion object {
        private const val TAG = "VoiceAssistantManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val _assistantState = MutableStateFlow(VoiceAssistantState())
    val assistantState: StateFlow<VoiceAssistantState> = _assistantState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private var isWakeWordMode = false
    private var isListening = false
    private var dismissHudJob: Job? = null

    // Preferences cache
    var isEnabled: Boolean = true
    var isWakeWordListeningEnabled: Boolean = true
    var wakeWordType: String = "OK_CLOCK"
    var customWakeWord: String = "クロック"
    var isTtsVoiceEnabled: Boolean = true
    var ttsPitch: Float = 1.0f
    var ttsSpeechRate: Float = 1.05f

    init {
        initTts()
    }

    private fun initTts() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.JAPANESE)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech?.language = Locale.US
                    }
                    isTtsReady = true
                    textToSpeech?.setPitch(ttsPitch)
                    textToSpeech?.setSpeechRate(ttsSpeechRate)
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _assistantState.value = _assistantState.value.copy(isSpeaking = true)
                        }

                        override fun onDone(utteranceId: String?) {
                            _assistantState.value = _assistantState.value.copy(isSpeaking = false)
                            // If wake word listening is enabled and not in manual dialog, resume wake word
                            if (isWakeWordListeningEnabled && isEnabled && !_assistantState.value.isActivelyListening) {
                                startWakeWordListeningDelayed(600)
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            _assistantState.value = _assistantState.value.copy(isSpeaking = false)
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS: ${e.message}")
        }
    }

    /**
     * Start continuous background wake word listening
     */
    fun startWakeWordListening() {
        if (!isEnabled || !isWakeWordListeningEnabled) return
        mainHandler.post {
            ensureSpeechRecognizer()
            if (isListening) return@post

            isWakeWordMode = true
            isListening = true
            _assistantState.value = _assistantState.value.copy(
                isWakeWordListening = true,
                isActivelyListening = false
            )

            try {
                val intent = createRecognizerIntent(partialResults = true)
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start wake word listening: ${e.message}")
                isListening = false
                _assistantState.value = _assistantState.value.copy(isWakeWordListening = false)
            }
        }
    }

    /**
     * Manually trigger active listening (e.g. user tapped Mic button)
     */
    fun startActiveListeningPrompt() {
        dismissHudJob?.cancel()
        mainHandler.post {
            stopListeningInternal()
            ensureSpeechRecognizer()

            isWakeWordMode = false
            isListening = true
            _assistantState.value = _assistantState.value.copy(
                isActivelyListening = true,
                isWakeWordListening = false,
                recognizedText = "",
                assistantResponseText = "👂 聞いています... (例: 「音楽かけて」「何時？」「今日の天気」)",
                sessionTimestamp = System.currentTimeMillis()
            )

            try {
                val intent = createRecognizerIntent(partialResults = true)
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start active listening: ${e.message}")
                isListening = false
                _assistantState.value = _assistantState.value.copy(isActivelyListening = false)
            }
        }
    }

    /**
     * Manually stop and dismiss assistant
     */
    fun stopAssistant() {
        dismissHudJob?.cancel()
        mainHandler.post {
            stopListeningInternal()
            stopTts()
            _assistantState.value = _assistantState.value.copy(
                isActivelyListening = false,
                isWakeWordListening = isWakeWordListeningEnabled && isEnabled
            )
            if (isWakeWordListeningEnabled && isEnabled) {
                startWakeWordListeningDelayed(1000)
            }
        }
    }

    /**
     * Process text command directly (e.g. from Web Dashboard or shortcut chips)
     */
    fun processTextCommand(text: String) {
        dismissHudJob?.cancel()
        _assistantState.value = _assistantState.value.copy(
            isActivelyListening = true,
            recognizedText = text,
            sessionTimestamp = System.currentTimeMillis()
        )

        val result = VoiceCommandProcessor.processCommand(text, commandCallbacks)
        _assistantState.value = _assistantState.value.copy(
            assistantResponseText = result.displayMessage,
            lastCommandAction = result.actionName
        )

        if (isTtsVoiceEnabled && isTtsReady && result.spokenResponse.isNotEmpty()) {
            speakTts(result.spokenResponse)
        }

        scheduleDismissHud(if (result.isHandled) 4500 else 6000)
    }

    private fun ensureSpeechRecognizer() {
        if (speechRecognizer == null) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(AssistantRecognitionListener())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun createRecognizerIntent(partialResults: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    private fun stopListeningInternal() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
    }

    private fun speakTts(text: String) {
        try {
            if (isTtsReady && textToSpeech != null) {
                textToSpeech?.setPitch(ttsPitch)
                textToSpeech?.setSpeechRate(ttsSpeechRate)
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ASSISTANT_REPLY_${System.currentTimeMillis()}")
                }
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ASSISTANT_REPLY")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error: ${e.message}")
        }
    }

    private fun stopTts() {
        try {
            textToSpeech?.stop()
            _assistantState.value = _assistantState.value.copy(isSpeaking = false)
        } catch (_: Exception) {}
    }

    private fun startWakeWordListeningDelayed(delayMs: Long) {
        coroutineScope.launch {
            delay(delayMs)
            if (!_assistantState.value.isActivelyListening && isEnabled && isWakeWordListeningEnabled) {
                startWakeWordListening()
            }
        }
    }

    private fun scheduleDismissHud(delayMs: Long) {
        dismissHudJob?.cancel()
        dismissHudJob = coroutineScope.launch {
            delay(delayMs)
            _assistantState.value = _assistantState.value.copy(
                isActivelyListening = false,
                isWakeWordListening = isWakeWordListeningEnabled && isEnabled
            )
            if (isWakeWordListeningEnabled && isEnabled) {
                startWakeWordListening()
            }
        }
    }

    /**
     * Checks if given phrase contains any wake words
     */
    private fun containsWakeWord(spokenText: String): Pair<Boolean, String> {
        val clean = spokenText.lowercase(Locale.JAPANESE)
            .replace(" ", "")
            .replace("　", "")

        val selectedOption = try {
            WakeWordOption.valueOf(wakeWordType)
        } catch (_: Exception) {
            WakeWordOption.OK_CLOCK
        }

        val phrasesToCheck = when (selectedOption) {
            WakeWordOption.CUSTOM -> listOf(customWakeWord.lowercase(Locale.JAPANESE).replace(" ", ""))
            else -> selectedOption.wakePhrases.map { it.lowercase(Locale.JAPANESE).replace(" ", "") }
        }

        // Also check universal wake words for robustness
        val allPhrases = phrasesToCheck + listOf("okクロック", "オッケークロック", "ねえクロック", "okgoogle", "オッケーグーグル")

        for (phrase in allPhrases) {
            if (phrase.isNotEmpty() && clean.contains(phrase)) {
                val index = clean.indexOf(phrase)
                val remainingCommand = clean.substring(index + phrase.length)
                return Pair(true, remainingCommand)
            }
        }
        return Pair(false, "")
    }

    private inner class AssistantRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize -2dB..10dB to 0f..1f for visualizer
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _assistantState.value = _assistantState.value.copy(soundLevelRms = normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            _assistantState.value = _assistantState.value.copy(soundLevelRms = 0f)

            if (isWakeWordMode) {
                // In background wake-word mode, restart smoothly after brief delay
                if (isEnabled && isWakeWordListeningEnabled && !_assistantState.value.isActivelyListening) {
                    startWakeWordListeningDelayed(1500)
                }
            } else {
                // In active listening mode, if error is no match or timeout, prompt gently
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    _assistantState.value = _assistantState.value.copy(
                        assistantResponseText = "聞き取れませんでした。「何時？」「音楽かけて」などと話しかけてください。"
                    )
                    scheduleDismissHud(3500)
                } else {
                    scheduleDismissHud(2000)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            _assistantState.value = _assistantState.value.copy(soundLevelRms = 0f)

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognized = matches?.firstOrNull() ?: ""

            if (isWakeWordMode) {
                val (wakeWordDetected, followUpCommand) = containsWakeWord(recognized)
                if (wakeWordDetected) {
                    // Wake word triggered!
                    Log.d(TAG, "Wake word triggered: recognized='$recognized', command='$followUpCommand'")
                    if (followUpCommand.trim().isNotEmpty()) {
                        // User said: "OKクロック 音楽かけて" in one breath
                        processTextCommand(followUpCommand)
                    } else {
                        // User only said "OK クロック" -> Open active listening prompt with chime
                        startActiveListeningPrompt()
                    }
                } else {
                    // Resume wake word listening
                    if (isEnabled && isWakeWordListeningEnabled) {
                        startWakeWordListeningDelayed(800)
                    }
                }
            } else {
                // Active listening results
                if (recognized.isNotBlank()) {
                    processTextCommand(recognized)
                } else {
                    _assistantState.value = _assistantState.value.copy(
                        assistantResponseText = "声が聞き取れませんでした。"
                    )
                    scheduleDismissHud(3000)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull() ?: ""

            if (isWakeWordMode) {
                val (wakeWordDetected, followUp) = containsWakeWord(partial)
                if (wakeWordDetected) {
                    stopListeningInternal()
                    if (followUp.trim().isNotEmpty()) {
                        processTextCommand(followUp)
                    } else {
                        startActiveListeningPrompt()
                    }
                }
            } else {
                if (partial.isNotBlank()) {
                    _assistantState.value = _assistantState.value.copy(recognizedText = partial)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun destroy() {
        try {
            stopListeningInternal()
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }
}

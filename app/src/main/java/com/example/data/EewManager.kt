package com.example.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.example.audio.ChimeSynthesizer
import com.example.audio.EewVoiceAnnouncer
import com.example.model.ClockPreferencesState
import com.example.model.EewLiveState
import com.example.model.EewTestScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EewManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "EewManager"
        private const val WS_URL = "wss://api.p2pquake.net/v2/ws"
    }

    private val _liveState = MutableStateFlow(EewLiveState())
    val liveState: StateFlow<EewLiveState> = _liveState.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _pendingScenario = MutableStateFlow<String?>(null)
    val pendingScenario: StateFlow<String?> = _pendingScenario.asStateFlow()

    private val _pendingJsonPayload = MutableStateFlow<String?>(null)
    val pendingJsonPayload: StateFlow<String?> = _pendingJsonPayload.asStateFlow()

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var preferences: ClockPreferencesState = ClockPreferencesState()
    private val voiceAnnouncer = EewVoiceAnnouncer(context)

    init {
        initOkHttpClient()
    }

    fun updatePreferences(prefs: ClockPreferencesState) {
        this.preferences = prefs
        voiceAnnouncer.volumeFraction = prefs.chimeVolume
        voiceAnnouncer.soundMode = if (prefs.eewSoundEnabled) prefs.eewSoundMode else "MUTE"
        if (prefs.eewEnabled) {
            connectWebSocket()
        } else {
            disconnectWebSocket()
        }
    }

    private fun initOkHttpClient() {
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun start() {
        if (preferences.eewEnabled) {
            connectWebSocket()
        }
    }

    fun stop() {
        disconnectWebSocket()
    }

    private fun connectWebSocket() {
        if (webSocket != null) return
        reconnectJob?.cancel()

        try {
            val request = Request.Builder().url(WS_URL).build()
            webSocket = client?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "P2PQuake WebSocket connected")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "P2PQuake WebSocket failure: ${t.message}")
                    this@EewManager.webSocket = null
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "P2PQuake WebSocket closed ($code: $reason)")
                    this@EewManager.webSocket = null
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect WebSocket", e)
            scheduleReconnect()
        }
    }

    private fun disconnectWebSocket() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
    }

    private fun scheduleReconnect() {
        if (!preferences.eewEnabled) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            if (isActive && preferences.eewEnabled && webSocket == null) {
                connectWebSocket()
            }
        }
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val code = root.optInt("code", 0)

            if (code == 554) {
                // EEW Announcement Detected
                if (preferences.eewEnabled) {
                    _pendingJsonPayload.value = jsonStr
                    _isOverlayVisible.value = true
                }
            } else if (code == 556) {
                // EEW Forecast / Warning
                val cancelled = root.optBoolean("cancelled", false)
                val areas = root.optJSONArray("areas")
                var maxScale = -1
                if (areas != null) {
                    for (i in 0 until areas.length()) {
                        val a = areas.optJSONObject(i)
                        val st = a?.optInt("scaleTo", -1) ?: -1
                        if (st > maxScale) maxScale = st
                    }
                }

                if (preferences.eewEnabled && (cancelled || maxScale >= preferences.eewMinScale)) {
                    _pendingJsonPayload.value = jsonStr
                    _isOverlayVisible.value = true
                }
            } else if (code == 551 || code == 552) {
                // Observed intensity report
                if (preferences.eewEnabled) {
                    _pendingJsonPayload.value = jsonStr
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling EEW message", e)
        }
    }

    fun triggerTestScenario(scenario: EewTestScenario) {
        _liveState.value = EewLiveState(
            isActive = true,
            summaryText = scenario.title,
            lastUpdatedEpochMs = System.currentTimeMillis(),
            isTestMode = true
        )
        _pendingScenario.value = scenario.id
        _isOverlayVisible.value = true
    }

    fun dismissOverlay() {
        _isOverlayVisible.value = false
        _pendingScenario.value = null
        _pendingJsonPayload.value = null
        _liveState.value = _liveState.value.copy(isActive = false, isTestMode = false)
        voiceAnnouncer.stop()
    }

    fun onJsStatusChanged(isActive: Boolean, summary: String) {
        _liveState.value = _liveState.value.copy(
            isActive = isActive,
            summaryText = summary,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    fun onJsAlarmTriggered(soundType: String) {
        playAlarm(isWarning = soundType == "warning")
    }

    fun onJsVoiceAnnounce(text: String, flush: Boolean) {
        if (preferences.eewSoundEnabled && preferences.eewSoundMode != "MUTE") {
            voiceAnnouncer.volumeFraction = preferences.chimeVolume
            voiceAnnouncer.soundMode = preferences.eewSoundMode
            voiceAnnouncer.speak(text, flush)
        }
    }

    private fun playAlarm(isWarning: Boolean) {
        if (preferences.eewSoundEnabled && preferences.eewSoundMode != "MUTE") {
            val vol = preferences.chimeVolume.coerceIn(0.1f, 1.0f)
            voiceAnnouncer.volumeFraction = vol
            voiceAnnouncer.soundMode = preferences.eewSoundMode
            if (isWarning) {
                voiceAnnouncer.playEewChime(volume = vol)
            } else {
                voiceAnnouncer.speak("緊急地震速報は取り消されました。", flush = true)
            }
        }
        if (preferences.eewVibrationEnabled) {
            triggerVibration()
        }
    }

    private fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 400, 200, 400, 200, 800)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 400, 200, 400, 200, 800), -1)
                }
            }
        } catch (_: Exception) {}
    }
}

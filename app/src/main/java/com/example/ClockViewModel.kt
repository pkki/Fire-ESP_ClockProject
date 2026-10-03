package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChimeAudioPlayer
import com.example.audio.ChimeSound
import com.example.audio.ChimeSynthesizer
import com.example.audio.CustomAudioFileManager
import com.example.audio.CustomVideoFileManager
import com.example.audio.MusicPlayerManager
import com.example.audio.MusicPlayerState
import com.example.audio.MusicRepeatMode
import com.example.audio.VideoPlayerManager
import com.example.audio.VideoPlayerState
import com.example.audio.VideoAspectRatio
import com.example.camera.AudioStreamManager
import com.example.camera.CameraStreamManager
import com.example.camera.IpCameraConfig
import com.example.camera.IpCameraServer
import com.example.camera.IpCameraStatus
import com.example.data.ClockPreferencesManager
import com.example.data.DeviceLocationHelper
import com.example.data.EewManager
import com.example.data.EspSensorManager
import com.example.data.JapanMunicipalities
import com.example.data.MunicipalityItem
import com.example.data.WeatherRepository
import com.example.data.IrRemoteManager
import com.example.model.IrDeviceCategory
import com.example.model.IrLearnState
import com.example.model.IrRemoteButton
import com.example.model.ActiveBackgroundVideo
import com.example.model.ChimeAudioSourceType
import com.example.model.ChimeVideoSourceType
import com.example.model.VideoDisplayLayer
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.model.EewLiveState
import com.example.model.EewTestScenario
import com.example.model.EspSensorData
import com.example.model.BleDeviceInfo
import com.example.model.FireAlertInfo
import com.example.model.PhysicalButtonEvent
import com.example.model.ScheduledChime
import com.example.model.WeatherState
import com.example.model.VoiceAssistantState
import com.example.model.WakeWordOption
import com.example.model.EqualizerPreset
import com.example.model.BassCutMode
import com.example.model.EqualizerState
import com.example.audio.AudioEqualizerManager
import com.example.audio.SilentAudioKeepAliveManager
import com.example.voice.VoiceAssistantManager
import com.example.voice.VoiceCommandCallbacks
import com.example.voice.VoiceCommandProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CurrentTimeState(
    val hour24: Int = 0,
    val hour12: Int = 0,
    val isPm: Boolean = false,
    val minute: Int = 0,
    val second: Int = 0,
    val millisecond: Int = 0,
    val year: Int = 2026,
    val month: Int = 9,
    val day: Int = 9,
    val dayOfWeekEn: String = "WEDNESDAY",
    val dayOfWeekJa: String = "水曜日",
    val formattedDateFullEn: String = "Wednesday, September 9, 2026",
    val formattedDateFullJa: String = "2026年9月9日 (水)",
    val formattedDateShortJa: String = "9月9日 (水)",
    val timezoneDisplayName: String = "Japan Standard Time (JST)",
    val burnInShiftX: Float = 0f,
    val burnInShiftY: Float = 0f,
    val batteryPercent: Int = 92
)

data class StopwatchLap(
    val lapIndex: Int,
    val lapTimeMillis: Long,
    val overallTimeMillis: Long,
    val isBest: Boolean = false,
    val isWorst: Boolean = false
) {
    val formattedLapTime: String get() = formatStopwatchTime(lapTimeMillis)
    val formattedOverallTime: String get() = formatStopwatchTime(overallTimeMillis)
}

data class DeskStopwatchState(
    val elapsedMillis: Long = 0L,
    val isRunning: Boolean = false,
    val laps: List<StopwatchLap> = emptyList()
) {
    val formattedTime: String get() = formatStopwatchTime(elapsedMillis)
    val minutes: Int get() = ((elapsedMillis / 1000) / 60).toInt()
    val seconds: Int get() = ((elapsedMillis / 1000) % 60).toInt()
    val hundredths: Int get() = ((elapsedMillis % 1000) / 10).toInt()
    val hours: Int get() = ((elapsedMillis / 1000) / 3600).toInt()
}

fun formatStopwatchTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val hundredths = (millis % 1000) / 10
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}

data class DeskTimerState(
    val remainingSeconds: Int = 0,
    val initialSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val label: String = "タイマー",
    val chimeSound: ChimeSound = ChimeSound.CRYSTAL_BELL,
    val autoRepeat: Boolean = false
) {
    val progressFraction: Float
        get() = if (initialSeconds > 0) {
            (remainingSeconds.toFloat() / initialSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val hours: Int get() = remainingSeconds / 3600
    val minutes: Int get() = (remainingSeconds % 3600) / 60
    val seconds: Int get() = remainingSeconds % 60

    val formattedTime: String
        get() {
            return if (hours > 0) {
                String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
}

class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsManager = ClockPreferencesManager(application)
    val preferences: StateFlow<ClockPreferencesState> = prefsManager.state

    private val weatherRepo = WeatherRepository()
    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val _isWeatherRefreshing = MutableStateFlow(false)
    val isWeatherRefreshing: StateFlow<Boolean> = _isWeatherRefreshing.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MunicipalityItem>>(emptyList())
    val searchResults: StateFlow<List<MunicipalityItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isDetectingLocation = MutableStateFlow(false)
    val isDetectingLocation: StateFlow<Boolean> = _isDetectingLocation.asStateFlow()

    private val _timeState = MutableStateFlow(CurrentTimeState())
    val timeState: StateFlow<CurrentTimeState> = _timeState.asStateFlow()

    private val _timerState = MutableStateFlow(DeskTimerState())
    val timerState: StateFlow<DeskTimerState> = _timerState.asStateFlow()

    // --- Desk Stopwatch (ストップウォッチ) State ---
    private val _stopwatchState = MutableStateFlow(DeskStopwatchState())
    val stopwatchState: StateFlow<DeskStopwatchState> = _stopwatchState.asStateFlow()

    private var stopwatchJob: Job? = null
    private var stopwatchBaseTime: Long = 0L
    private var stopwatchAccumulatedTime: Long = 0L

    // --- Alarm Clock (目覚まし時計) State ---
    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

    // --- Hourly & Scheduled Chime Ringing State (時報チャイム鳴動状態) ---
    private val _isChimeRinging = MutableStateFlow(false)
    val isChimeRinging: StateFlow<Boolean> = _isChimeRinging.asStateFlow()

    private val _chimeRingingInfo = MutableStateFlow<String?>(null)
    val chimeRingingInfo: StateFlow<String?> = _chimeRingingInfo.asStateFlow()

    private val _isAlarmSnoozed = MutableStateFlow(false)
    val isAlarmSnoozed: StateFlow<Boolean> = _isAlarmSnoozed.asStateFlow()

    private val _alarmSnoozeRemainingSec = MutableStateFlow(0)
    val alarmSnoozeRemainingSec: StateFlow<Int> = _alarmSnoozeRemainingSec.asStateFlow()

    // --- MQ-2 Fire & Smoke Emergency Detection (火災・煙検知アラート) ---
    private val _isFireAlertRinging = MutableStateFlow(false)
    val isFireAlertRinging: StateFlow<Boolean> = _isFireAlertRinging.asStateFlow()

    private val _fireAlertDetails = MutableStateFlow<FireAlertInfo?>(null)
    val fireAlertDetails: StateFlow<FireAlertInfo?> = _fireAlertDetails.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // --- Physical Button Event HUD / Feedback State ---
    private val _lastPhysicalButtonEvent = MutableStateFlow<PhysicalButtonEvent?>(null)
    val lastPhysicalButtonEvent: StateFlow<PhysicalButtonEvent?> = _lastPhysicalButtonEvent.asStateFlow()
    private var buttonClearJob: Job? = null

    private var lastAlarmTriggerMinute = -1
    private var lastAlarmTriggerHour = -1

    val scheduledChimes: StateFlow<List<ScheduledChime>> = prefsManager.scheduledChimes
    val customAudioList: StateFlow<List<CustomAudioItem>> = prefsManager.customAudioList
    val customVideoList: StateFlow<List<CustomVideoItem>> = prefsManager.customVideoList

    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath: StateFlow<String?> = _playingAudioPath.asStateFlow()

    // --- Dedicated Music Player State ---
    val musicPlayerState: StateFlow<MusicPlayerState> = MusicPlayerManager.playerState

    // --- Dedicated Video Player State ---
    val videoPlayerState: StateFlow<VideoPlayerState> = VideoPlayerManager.playerState

    // --- Master Equalizer & Bass Protection State ---
    val equalizerState: StateFlow<EqualizerState> = AudioEqualizerManager.equalizerState

    // --- Earphone Jack Anti-Noise Keep-Alive Silence State ---
    val isSilenceKeepAlivePlaying: StateFlow<Boolean> = SilentAudioKeepAliveManager.isPlaying

    private val audioManager by lazy {
        getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    }

    private val _activeBackgroundVideo = MutableStateFlow<ActiveBackgroundVideo?>(null)
    val activeBackgroundVideo: StateFlow<ActiveBackgroundVideo?> = _activeBackgroundVideo.asStateFlow()

    private var videoDismissJob: Job? = null

    // Flag to ensure chime only triggers once per target minute
    private var lastChimeTriggerMinute = -1
    private var lastChimeTriggerHour = -1
    private val triggeredScheduledChimesSet = mutableSetOf<String>()

    // --- IP Camera Engine ---

    val ipCameraConfig: StateFlow<IpCameraConfig> = prefsManager.ipCameraConfig

    private val _ipCameraStatus = MutableStateFlow(IpCameraStatus())
    val ipCameraStatus: StateFlow<IpCameraStatus> = _ipCameraStatus.asStateFlow()

    private var ipCameraServer: IpCameraServer? = null
    private var cameraStreamManager: CameraStreamManager? = null
    private var audioStreamManager: AudioStreamManager? = null

    private val _ipCameraPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val ipCameraPreviewBitmap: StateFlow<Bitmap?> = _ipCameraPreviewBitmap.asStateFlow()

    private val _ipCameraFps = MutableStateFlow(0f)
    val ipCameraFps: StateFlow<Float> = _ipCameraFps.asStateFlow()

    private val _ipCameraAudioLevel = MutableStateFlow(0)
    val ipCameraAudioLevel: StateFlow<Int> = _ipCameraAudioLevel.asStateFlow()

    private var cameraPreviewJob: Job? = null
    private var cameraFpsJob: Job? = null
    private var audioLevelJob: Job? = null

    // --- EEW (緊急地震速報) Engine ---
    private val eewManager = EewManager(application, viewModelScope)
    val isEewOverlayVisible: StateFlow<Boolean> = eewManager.isOverlayVisible
    val eewLiveState: StateFlow<EewLiveState> = eewManager.liveState
    val eewPendingScenario: StateFlow<String?> = eewManager.pendingScenario
    val eewPendingJson: StateFlow<String?> = eewManager.pendingJsonPayload

    // --- ESP8266 / ESP32-C3 AHT20+BMP280 Sensor Engine ---
    private val espSensorManager = EspSensorManager(application)
    val espSensorData: StateFlow<EspSensorData> = espSensorManager.sensorData
    val bleBondedDevices: StateFlow<List<BleDeviceInfo>> = espSensorManager.bleSensorManager.bondedDevices
    val bleScannedDevices: StateFlow<List<BleDeviceInfo>> = espSensorManager.bleSensorManager.scannedDevices

    // --- ESP32-C3 Smart IR Remote (学習・送受信・家電制御) ---
    private val irRemoteManager = IrRemoteManager(
        application,
        espSensorManager.bleSensorManager,
        espSensorManager.usbSensorManager
    )
    val irButtons: StateFlow<List<IrRemoteButton>> = irRemoteManager.buttons
    val irLearnState: StateFlow<IrLearnState> = irRemoteManager.learnState

    // --- Ultra-lightweight Voice Assistant & Wake Word Engine ---
    val voiceAssistantManager by lazy {
        VoiceAssistantManager(
            context = application,
            commandCallbacks = object : VoiceCommandCallbacks {
                override fun getCurrentTimeText(): String {
                    val s = timeState.value
                    val ampm = if (s.hour24 < 12) "午前" else "午後"
                    val h12 = if (s.hour24 % 12 == 0) 12 else s.hour24 % 12
                    return "$ampm ${h12}時${s.minute}分"
                }

                override fun getCurrentDateText(): String {
                    val s = timeState.value
                    return "${s.year}年${s.month}月${s.day}日 ${s.dayOfWeekJa}"
                }

                override fun getWeatherSummary(): String {
                    val w = weatherState.value
                    val p = preferences.value.selectedPrefecture
                    return "${p}の天気は${w.conditionText}、現在の気温は${w.temperatureCelsius}度、予想最高気温は${w.highTemp}度、最低気温は${w.lowTemp}度です。"
                }

                override fun playMusic() {
                    val currentList = customAudioList.value
                    if (currentList.isNotEmpty()) {
                        MusicPlayerManager.playTrack(currentList.first())
                    } else {
                        testChimeSound(ChimeSound.WESTMINSTER, preferences.value.chimeVolume)
                    }
                }

                override fun pauseMusic() {
                    MusicPlayerManager.togglePlayPause()
                }

                override fun nextMusic() {
                    MusicPlayerManager.next()
                }

                override fun prevMusic() {
                    MusicPlayerManager.previous()
                }

                override fun setVolume(volume: Float) {
                    setChimeVolume(volume)
                    MusicPlayerManager.setVolume(volume)
                }

                override fun adjustVolume(delta: Float) {
                    val newVol = (preferences.value.chimeVolume + delta).coerceIn(0f, 1f)
                    setChimeVolume(newVol)
                    MusicPlayerManager.setVolume(newVol)
                }

                override fun playVideo(
                    videoType: ChimeVideoSourceType,
                    customPath: String?,
                    customName: String?,
                    layer: VideoDisplayLayer
                ) {
                    val path = customPath ?: customVideoList.value.firstOrNull()?.filePath
                    val name = customName ?: customVideoList.value.firstOrNull()?.name
                    previewBackgroundVideo(
                        videoSourceType = videoType,
                        customVideoPath = path,
                        customVideoName = name,
                        playVideoAudio = true,
                        durationSeconds = ScheduledChime.DURATION_MANUAL_STOP,
                        displayLayer = layer
                    )
                }

                override fun stopVideo() {
                    dismissBackgroundVideo()
                }

                override fun toggleVideoLayer() {
                    toggleActiveVideoDisplayLayer()
                }

                override fun startTimer(seconds: Int) {
                    setTimerSeconds(seconds)
                }

                override fun stopAlarm() {
                    this@ClockViewModel.stopAlarm()
                }

                override fun toggleNightMode() {
                    this@ClockViewModel.toggleNightMode()
                }

                override fun setNightMode(enabled: Boolean) {
                    if (preferences.value.isNightMode != enabled) {
                        this@ClockViewModel.toggleNightMode()
                    }
                }

                override fun cycleColorTheme() {
                    val palettes = ColorPalette.entries
                    val currentIndex = palettes.indexOf(preferences.value.colorPalette)
                    val nextIndex = (currentIndex + 1) % palettes.size
                    selectColorPalette(palettes[nextIndex])
                }

                override fun triggerIrButton(buttonNameOrId: String): Boolean {
                    val btn = irButtons.value.find {
                        it.id == buttonNameOrId || it.name.equals(buttonNameOrId, ignoreCase = true) || buttonNameOrId.contains(it.name, ignoreCase = true)
                    } ?: return false
                    return sendIrButton(btn)
                }

                override fun getRegisteredIrButtons(): List<String> {
                    return irButtons.value.map { it.name }
                }

                override fun triggerEewTest() {
                    triggerTestEewScenario(EewTestScenario.HYUGANADA_M71)
                }

                override fun setEqualizerPreset(preset: EqualizerPreset) {
                    this@ClockViewModel.setEqualizerPreset(preset)
                }

                override fun setBassCutMode(mode: BassCutMode) {
                    this@ClockViewModel.setEqualizerBassCutMode(mode)
                }

                override fun resetEqualizer() {
                    this@ClockViewModel.resetEqualizerToFlat()
                }

                override fun setAntiNoiseSilence(enabled: Boolean) {
                    this@ClockViewModel.setAntiNoiseSilenceEnabled(enabled)
                }

                override fun startStopwatch() {
                    this@ClockViewModel.startStopwatch()
                }

                override fun pauseStopwatch() {
                    this@ClockViewModel.pauseStopwatch()
                }

                override fun resetStopwatch() {
                    this@ClockViewModel.resetStopwatch()
                }

                override fun recordStopwatchLap() {
                    this@ClockViewModel.recordStopwatchLap()
                }
            }
        ).apply {
            val pref = preferences.value
            isEnabled = pref.voiceAssistantEnabled
            isWakeWordListeningEnabled = pref.wakeWordListeningEnabled
            wakeWordType = pref.wakeWordType
            customWakeWord = pref.customWakeWord
            isTtsVoiceEnabled = pref.voiceTtsResponseEnabled
            ttsPitch = pref.voiceTtsPitch
            ttsSpeechRate = pref.voiceTtsSpeechRate
        }
    }
    val voiceAssistantState: StateFlow<VoiceAssistantState> get() = voiceAssistantManager.assistantState

    init {
        startTimeTicker()
        startTimerTicker()
        startWeatherTicker()

        // Sync EEW preferences and start listener
        eewManager.updatePreferences(preferences.value)
        eewManager.start()

        // Sync ESP sensor preferences and start connection
        val espPref = preferences.value
        espSensorManager.onBleAddressConnected = { address ->
            prefsManager.saveLastConnectedBleAddress(address)
        }
        espSensorManager.updateConfig(
            enabled = espPref.espSensorEnabled,
            mode = espPref.espConnectionMode,
            targetBleName = espPref.espBleDeviceName,
            targetBleAddress = espPref.espBleDeviceAddress,
            baud = espPref.espBaudRate,
            newHost = espPref.espSensorHost,
            newPort = espPref.espSensorPort,
            newInterval = espPref.espSensorIntervalSeconds,
            newTempOffset = espPref.espTempOffset,
            newHumOffset = espPref.espHumOffset,
            newPressOffset = espPref.espPressOffset
        )
        espSensorManager.onPhysicalButtonEvent = { btnId, btnName ->
            handlePhysicalButton(btnId, btnName)
        }
        espSensorManager.onFireAlertEvent = { detected, rawVal, message ->
            if (detected && preferences.value.fireAlertEnabled) {
                triggerFireAlert(rawVal, message ?: "火災・煙を検知しました")
            }
        }
        espSensorManager.onMq2TelemetryEvent = { rawVal, voltage, smoke ->
            val pref = preferences.value
            if (pref.fireAlertEnabled && rawVal != null) {
                if (rawVal >= pref.mq2SensitivityThreshold || smoke) {
                    if (!_isFireAlertRinging.value) {
                        triggerFireAlert(rawVal, "火事です！火災または煙を検知しました (測定値: $rawVal / 閾値: ${pref.mq2SensitivityThreshold})")
                    }
                }
            }
        }
        espSensorManager.start()

        // Initialize TTS for emergency voice announcements
        try {
            tts = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.JAPANESE)
                    isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        } catch (_: Exception) {}

        viewModelScope.launch {
            preferences.collect { pref ->
                espSensorManager.updateConfig(
                    enabled = pref.espSensorEnabled,
                    mode = pref.espConnectionMode,
                    targetBleName = pref.espBleDeviceName,
                    baud = pref.espBaudRate,
                    newHost = pref.espSensorHost,
                    newPort = pref.espSensorPort,
                    newInterval = pref.espSensorIntervalSeconds,
                    newTempOffset = pref.espTempOffset,
                    newHumOffset = pref.espHumOffset,
                    newPressOffset = pref.espPressOffset
                )
            }
        }

        // Auto-start IP camera if enabled in preferences
        viewModelScope.launch {
            if (ipCameraConfig.value.isEnabled) {
                startIpCameraService(ipCameraConfig.value)
            }
        }

        // ESP32接続時に最新の火災検知感度閾値およびセンサー更新間隔を自動同期
        viewModelScope.launch {
            espSensorData.collect { data ->
                if (data.isConnected) {
                    espSensorManager.sendFireThreshold(preferences.value.mq2SensitivityThreshold)
                    espSensorManager.sendSensorInterval(preferences.value.espSensorIntervalSeconds)
                }
            }
        }

        // Initialize Dedicated Music Player configuration from preferences
        val initialRepeat = try {
            MusicRepeatMode.valueOf(preferences.value.musicPlayerRepeatMode)
        } catch (_: Exception) {
            MusicRepeatMode.ALL
        }
        MusicPlayerManager.setVolume(preferences.value.musicPlayerVolume)
        MusicPlayerManager.setRepeatMode(initialRepeat)
        MusicPlayerManager.onPlaybackStateChanged = { isPlaying, track ->
            if (isPlaying && track != null) {
                _playingAudioPath.value = track.filePath
            } else if (!isPlaying && _playingAudioPath.value == track?.filePath) {
                _playingAudioPath.value = null
            }
        }

        // Real-time synchronization of Music Player state with Web Dashboard via WebSocket
        viewModelScope.launch {
            var lastTrackId: String? = null
            var lastIsPlaying = false
            var lastIsPaused = false
            var lastRepeatMode: MusicRepeatMode? = null
            var lastVol = -1f
            var lastBroadcastPosMs = 0L

            MusicPlayerManager.playerState.collect { state ->
                val trackChanged = state.currentTrack?.id != lastTrackId
                val playChanged = state.isPlaying != lastIsPlaying
                val pauseChanged = state.isPaused != lastIsPaused
                val repeatChanged = state.repeatMode != lastRepeatMode
                val volChanged = state.volume != lastVol
                val now = System.currentTimeMillis()
                val posAdvanced = (now - lastBroadcastPosMs) >= 1000L

                if (trackChanged || playChanged || pauseChanged || repeatChanged || volChanged || posAdvanced) {
                    lastTrackId = state.currentTrack?.id
                    lastIsPlaying = state.isPlaying
                    lastIsPaused = state.isPaused
                    lastRepeatMode = state.repeatMode
                    lastVol = state.volume
                    lastBroadcastPosMs = now
                    ipCameraServer?.broadcastMusicPlayerUpdate()
                }
            }
        }

        // Initialize Master Equalizer from saved preferences
        AudioEqualizerManager.initFromPreferences(
            enabled = preferences.value.equalizerEnabled,
            presetName = preferences.value.equalizerPreset,
            bands = listOf(
                preferences.value.equalizerBand0,
                preferences.value.equalizerBand1,
                preferences.value.equalizerBand2,
                preferences.value.equalizerBand3,
                preferences.value.equalizerBand4
            ),
            bassBoostStrength = preferences.value.equalizerBassBoostStrength,
            virtualizerStrength = preferences.value.equalizerVirtualizerStrength,
            bassCutName = preferences.value.equalizerBassCutMode,
            autoVolumeNormalization = preferences.value.autoVolumeNormalizationEnabled,
            loudnessBoostGainMb = preferences.value.loudnessBoostGainMb
        )
        AudioEqualizerManager.onStateChanged = { eqState ->
            prefsManager.updateEqualizerPreset(
                preset = eqState.currentPreset.id,
                bands = eqState.bandGainsDb,
                bassCutMode = eqState.bassCutMode.id,
                bassBoost = eqState.bassBoostStrength,
                virtualizer = eqState.virtualizerStrength
            )
            prefsManager.updateAutoVolumeNormalization(eqState.autoVolumeNormalization)
            prefsManager.updateLoudnessBoostGainMb(eqState.loudnessBoostGainMb)
        }

        // Initialize Earphone Jack Anti-Noise Keep-Alive Silence Track
        if (preferences.value.antiNoiseSilenceEnabled) {
            SilentAudioKeepAliveManager.start()
        }

        // Sync Voice Assistant settings & start wake-word listening if enabled
        viewModelScope.launch {
            preferences.collect { pref ->
                voiceAssistantManager.apply {
                    isEnabled = pref.voiceAssistantEnabled
                    isWakeWordListeningEnabled = pref.wakeWordListeningEnabled
                    wakeWordType = pref.wakeWordType
                    customWakeWord = pref.customWakeWord
                    isTtsVoiceEnabled = pref.voiceTtsResponseEnabled
                    ttsPitch = pref.voiceTtsPitch
                    ttsSpeechRate = pref.voiceTtsSpeechRate
                    if (pref.voiceAssistantEnabled && pref.wakeWordListeningEnabled) {
                        startWakeWordListening()
                    } else {
                        stopAssistant()
                    }
                }
            }
        }
    }

    // --- Master Equalizer & Bass Protection Controls ---
    fun setEqualizerEnabled(enabled: Boolean) {
        AudioEqualizerManager.setEqualizerEnabled(enabled)
        prefsManager.updateEqualizerEnabled(enabled)
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        AudioEqualizerManager.setPreset(preset)
        prefsManager.updateEqualizerPreset(
            preset = preset.id,
            bands = preset.bandGainsDb,
            bassCutMode = preset.bassCutMode.id,
            bassBoost = preset.bassBoostStrength,
            virtualizer = preset.virtualizerStrength
        )
    }

    fun setEqualizerBassBoost(strength: Int) {
        AudioEqualizerManager.setBassBoostStrength(strength)
        prefsManager.updateEqualizerBassBoost(strength)
    }

    fun setEqualizerVirtualizer(strength: Int) {
        AudioEqualizerManager.setVirtualizerStrength(strength)
        prefsManager.updateEqualizerVirtualizer(strength)
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Int) {
        AudioEqualizerManager.setBandGain(bandIndex, gainDb)
        prefsManager.updateEqualizerBand(bandIndex, gainDb)
    }

    fun setEqualizerBassCutMode(mode: BassCutMode) {
        AudioEqualizerManager.setBassCutMode(mode)
        prefsManager.updateEqualizerBassCutMode(mode.id)
    }

    fun setAutoVolumeNormalization(enabled: Boolean) {
        AudioEqualizerManager.setAutoVolumeNormalization(enabled)
        prefsManager.updateAutoVolumeNormalization(enabled)
    }

    fun setLoudnessBoostGainMb(gainMb: Int) {
        AudioEqualizerManager.setLoudnessBoostGainMb(gainMb)
        prefsManager.updateLoudnessBoostGainMb(gainMb)
    }

    fun resetEqualizerToFlat() {
        setEqualizerPreset(EqualizerPreset.FLAT)
    }

    // --- Earphone Jack Anti-Noise Keep-Alive Silence Controls ---
    fun setAntiNoiseSilenceEnabled(enabled: Boolean) {
        prefsManager.updateAntiNoiseSilenceEnabled(enabled)
        if (enabled) {
            SilentAudioKeepAliveManager.start()
        } else {
            SilentAudioKeepAliveManager.stop()
        }
    }

    fun toggleAntiNoiseSilence() {
        setAntiNoiseSilenceEnabled(!preferences.value.antiNoiseSilenceEnabled)
    }

    // Voice Assistant controls
    fun startVoiceAssistantActiveListening() {
        voiceAssistantManager.startActiveListeningPrompt()
    }

    fun stopVoiceAssistant() {
        voiceAssistantManager.stopAssistant()
    }

    fun processVoiceCommandText(text: String) {
        voiceAssistantManager.processTextCommand(text)
    }

    fun refreshEspSensor() {
        espSensorManager.refreshNow()
    }

    fun retryEspSensorUsb() {
        espSensorManager.retryConnection()
    }

    fun retryEspSensorConnection() {
        espSensorManager.retryConnection()
    }

    fun connectBleDevice(device: BleDeviceInfo) {
        prefsManager.saveLastConnectedBleAddress(device.address)
        espSensorManager.bleSensorManager.savedDeviceAddress = device.address
        espSensorManager.bleSensorManager.connectToAddress(device.address)
        updateEspSensorPreferences(
            enabled = true,
            mode = "BLE",
            bleDeviceName = device.name,
            bleDeviceAddress = device.address
        )
    }

    fun refreshBleDevices() {
        espSensorManager.bleSensorManager.refreshBondedDevices()
        espSensorManager.bleSensorManager.retryConnection()
    }

    fun getArduinoSketchCode(): String {
        return espSensorManager.loadArduinoSketchCode()
    }

    fun exportIrButtonsJson(): String {
        return irRemoteManager.exportButtonsJson()
    }

    fun importIrButtonsFromJson(json: String): Boolean {
        return irRemoteManager.importButtonsFromJson(json)
    }

    /**
     * WebダッシュボードからアップロードされたAPKファイルをインストール (Android 7〜最新Android対応)
     */
    fun installUploadedApk(fileName: String, apkBytes: ByteArray): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            val apkDir = File(app.cacheDir, "apk_updates")
            if (!apkDir.exists()) apkDir.mkdirs()
            apkDir.setReadable(true, false)
            apkDir.setExecutable(true, false)

            val safeName = fileName.ifBlank { "DeskClock_update.apk" }
            val apkFile = File(apkDir, safeName)
            apkFile.writeBytes(apkBytes)
            installApkFile(apkFile, safeName)
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Failed to write APK file", e)
            Pair(false, "APK保存失敗: ${e.localizedMessage}")
        }
    }

    /**
     * APKファイルを端末パッケージインストーラーで起動 (Android 7 Nougat 完全対応)
     */
    fun installApkFile(apkFile: File, fileName: String): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            val apkDir = apkFile.parentFile ?: File(app.cacheDir, "apk_updates")
            if (!apkDir.exists()) apkDir.mkdirs()
            apkDir.setReadable(true, false)
            apkDir.setExecutable(true, false)
            apkFile.setReadable(true, false)

            val pm = app.packageManager
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val infoStr = if (archiveInfo != null) {
                "バージョン: ${archiveInfo.versionName ?: "最新"} (コード: ${archiveInfo.versionCode})"
            } else {
                "APKサイズ: ${String.format("%.1f", apkFile.length() / (1024.0 * 1024.0))} MB"
            }

            // Android 7 (API 24/25): Check Unknown Sources permission
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                try {
                    @Suppress("DEPRECATION")
                    val isNonMarketAllowed = android.provider.Settings.Secure.getInt(
                        app.contentResolver,
                        android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS,
                        0
                    ) == 1
                    if (!isNonMarketAllowed) {
                        try {
                            val secIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            app.startActivity(secIntent)
                            return Pair(
                                false,
                                "Androidの「セキュリティ」設定で「提供元不明のアプリのインストール」をONにしてください (設定画面を開きました)。再度更新をお試しください。"
                            )
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            } else {
                // Android 8.0+ Unknown sources check
                if (!pm.canRequestPackageInstalls()) {
                    try {
                        val permIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        app.startActivity(permIntent)
                    } catch (_: Exception) {}
                }
            }

            val apkUri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                apkFile
            )

            // Primary Intent for Android 7+ (FileProvider content URI)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, app.packageName)
            }

            // Explicitly grant URI read permissions to system Package Installers on Android 7
            val knownInstallers = listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.android.defcontainer",
                "com.google.android.apps.packageinstaller"
            )
            for (pkg in knownInstallers) {
                try {
                    app.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            // Grant to all resolved activities matching the intent without MATCH_DEFAULT_ONLY filter
            try {
                val resInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
                } else {
                    pm.queryIntentActivities(intent, 0)
                }
                for (resolveInfo in resInfoList) {
                    val pkg = resolveInfo.activityInfo?.packageName
                    if (!pkg.isNullOrBlank()) {
                        try {
                            app.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            try {
                app.startActivity(intent)
            } catch (e: Exception) {
                Log.w("ClockViewModel", "ACTION_VIEW install failed, trying ACTION_INSTALL_PACKAGE fallback", e)
                @Suppress("DEPRECATION")
                val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                app.startActivity(fallbackIntent)
            }

            Pair(true, "APKの準備完了 ($infoStr)！卓上時計の画面にインストーラーが表示されました。「更新」または「インストール」をタップしてください。")
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Failed to launch APK installer", e)
            Pair(false, "インストーラー起動失敗: ${e.localizedMessage}")
        }
    }

    /**
     * 外部URLからAPKをダウンロードして端末にインストール
     */
    suspend fun downloadAndInstallApk(urlStr: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = java.net.URL(urlStr)
            val fileName = url.path.substringAfterLast("/").ifBlank { "DeskClock_remote.apk" }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder().url(urlStr).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Pair(false, "ダウンロード失敗: HTTP ${response.code} ${response.message}")
            }
            val bytes = response.body?.bytes() ?: return@withContext Pair(false, "APKデータが空です")
            installUploadedApk(fileName, bytes)
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Failed to download and install APK", e)
            Pair(false, "ダウンロードエラー: ${e.localizedMessage}")
        }
    }

    /**
     * 端末内ストレージのURIからAPKを読み込んでインストール (省メモリ高速ストリーム対応)
     */
    fun installApkFromUri(uri: Uri): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            val apkDir = File(app.cacheDir, "apk_updates")
            if (!apkDir.exists()) apkDir.mkdirs()
            val fileName = uri.lastPathSegment?.substringAfterLast("/")?.ifBlank { "DeskClock_local.apk" } ?: "DeskClock_local.apk"
            val safeName = if (fileName.endsWith(".apk", ignoreCase = true)) fileName else "$fileName.apk"
            val apkFile = File(apkDir, safeName)

            val inputStream = app.contentResolver.openInputStream(uri)
                ?: return Pair(false, "ファイルを開けませんでした (権限またはファイルが存在しません)")

            inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16384)
                }
            }

            installApkFile(apkFile, safeName)
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Failed to install APK from URI", e)
            Pair(false, "APKインストール失敗: ${e.localizedMessage}")
        }
    }

    /**
     * 端末内ストレージのURIからESPファームウェア(.bin)を読み込んでOTA書き込み (BLE OTAまたはWi-Fi OTA)
     */
    suspend fun flashEspFirmwareFromUri(
        uri: Uri,
        host: String? = null,
        port: Int? = null,
        onProgress: ((percent: Int, writtenBytes: Int, totalBytes: Int, speedKbps: Float, statusMsg: String) -> Unit)? = null
    ): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Pair(false, "ファームウェアファイルを開けませんでした")
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "firmware.bin"
            val errMsg = flashEspFirmware(fileName, bytes, host, port, onProgress)
            if (errMsg.isEmpty()) {
                Pair(true, "ESPへのOTAファームウェア書き込みが完了しました！ESPが再起動します。")
            } else {
                Pair(false, errMsg)
            }
        } catch (e: Exception) {
            Log.e("ClockViewModel", "Failed to flash ESP firmware from URI", e)
            Pair(false, "OTA転送エラー: ${e.localizedMessage}")
        }
    }

    /**
     * WebダッシュボードまたはアプリからESPファームウェア(.bin)をOTA書き込み
     */
    suspend fun flashEspFirmware(
        fileName: String,
        binBytes: ByteArray,
        host: String? = null,
        port: Int? = null,
        onProgress: ((percent: Int, writtenBytes: Int, totalBytes: Int, speedKbps: Float, statusMsg: String) -> Unit)? = null
    ): String {
        return espSensorManager.flashEspFirmware(fileName, binBytes, host, port, onProgress)
    }

    fun updateEspSensorPreferences(
        enabled: Boolean = preferences.value.espSensorEnabled,
        mode: String = preferences.value.espConnectionMode,
        bleDeviceName: String = preferences.value.espBleDeviceName,
        bleDeviceAddress: String? = preferences.value.espBleDeviceAddress,
        baud: Int = preferences.value.espBaudRate,
        host: String = preferences.value.espSensorHost,
        port: Int = preferences.value.espSensorPort,
        intervalSeconds: Int = preferences.value.espSensorIntervalSeconds,
        tempOffset: Float = preferences.value.espTempOffset,
        humOffset: Float = preferences.value.espHumOffset,
        pressOffset: Float = preferences.value.espPressOffset,
        showOnClock: Boolean = preferences.value.showEspSensorOnClock
    ) {
        prefsManager.updateEspSensorSettings(
            enabled = enabled,
            mode = mode,
            bleDeviceName = bleDeviceName,
            bleDeviceAddress = bleDeviceAddress,
            baud = baud,
            host = host,
            port = port,
            intervalSeconds = intervalSeconds,
            tempOffset = tempOffset,
            humOffset = humOffset,
            pressOffset = pressOffset,
            showOnClock = showOnClock
        )
        espSensorManager.updateConfig(
            enabled = enabled,
            mode = mode,
            targetBleName = bleDeviceName,
            targetBleAddress = bleDeviceAddress,
            baud = baud,
            newHost = host,
            newPort = port,
            newInterval = intervalSeconds,
            newTempOffset = tempOffset,
            newHumOffset = humOffset,
            newPressOffset = pressOffset
        )
        espSensorManager.sendSensorInterval(intervalSeconds)
    }

    fun triggerTestEewScenario(scenario: EewTestScenario) {
        eewManager.triggerTestScenario(scenario)
    }

    fun dismissEewOverlay() {
        eewManager.dismissOverlay()
    }

    fun onEewJsStatusChanged(isActive: Boolean, summary: String) {
        eewManager.onJsStatusChanged(isActive, summary)
    }

    fun onEewJsAlarm(soundType: String) {
        eewManager.onJsAlarmTriggered(soundType)
    }

    fun onEewJsVoiceAnnounce(text: String, flush: Boolean) {
        eewManager.onJsVoiceAnnounce(text, flush)
    }

    fun updateEewPreferences(
        enabled: Boolean = preferences.value.eewEnabled,
        minScale: Int = preferences.value.eewMinScale,
        soundEnabled: Boolean = preferences.value.eewSoundEnabled,
        vibrationEnabled: Boolean = preferences.value.eewVibrationEnabled,
        soundMode: String = preferences.value.eewSoundMode,
        lightweightMap: Boolean = preferences.value.eewLightweightMap
    ) {
        prefsManager.updateEewSettings(
            enabled = enabled,
            minScale = minScale,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            soundMode = soundMode,
            lightweightMap = lightweightMap
        )
        val updated = preferences.value.copy(
            eewEnabled = enabled,
            eewMinScale = minScale,
            eewSoundEnabled = soundEnabled,
            eewVibrationEnabled = vibrationEnabled,
            eewSoundMode = soundMode,
            eewLightweightMap = lightweightMap
        )
        eewManager.updatePreferences(updated)
    }

    fun toggleEewLightweightMap() {
        val current = preferences.value.eewLightweightMap
        updateEewPreferences(lightweightMap = !current)
    }

    fun cycleEewSoundMode() {
        val nextMode = when (preferences.value.eewSoundMode) {
            "SYNTH_BEEP" -> "VOICE"
            "VOICE" -> "MUTE"
            else -> "SYNTH_BEEP"
        }
        updateEewPreferences(soundMode = nextMode)
    }

    fun enableIpCamera(enable: Boolean) {
        val current = ipCameraConfig.value
        val newConfig = current.copy(isEnabled = enable)
        prefsManager.updateIpCameraConfig(newConfig)
        if (enable) {
            startIpCameraService(newConfig)
        } else {
            stopIpCameraService()
        }
    }

    fun toggleIpCamera() {
        val current = ipCameraConfig.value
        val isCurrentlyRunning = _ipCameraStatus.value.isRunning
        val shouldEnable = if (!isCurrentlyRunning) true else !current.isEnabled
        val newConfig = current.copy(isEnabled = shouldEnable)
        prefsManager.updateIpCameraConfig(newConfig)
        if (shouldEnable) {
            startIpCameraService(newConfig)
        } else {
            stopIpCameraService()
        }
    }

    private fun attachCameraStreamFlows(streamManager: CameraStreamManager) {
        cameraPreviewJob?.cancel()
        cameraPreviewJob = viewModelScope.launch {
            streamManager.latestPreviewBitmap.collect { bitmap ->
                _ipCameraPreviewBitmap.value = bitmap
            }
        }
        cameraFpsJob?.cancel()
        cameraFpsJob = viewModelScope.launch {
            streamManager.currentFps.collect { fps ->
                _ipCameraFps.value = fps
            }
        }
    }

    private fun attachAudioStreamFlows(aStream: AudioStreamManager) {
        audioLevelJob?.cancel()
        audioLevelJob = viewModelScope.launch {
            aStream.audioLevelPercent.collect { lvl ->
                _ipCameraAudioLevel.value = lvl
            }
        }
    }

    fun setIpCameraConfig(newConfig: IpCameraConfig) {
        val wasRunning = ipCameraStatus.value.isRunning
        prefsManager.updateIpCameraConfig(newConfig)
        if (newConfig.isEnabled) {
            val currentServer = ipCameraServer
            if (wasRunning && currentServer != null && currentServer.isRunning() && currentServer.getPort() == newConfig.port && cameraStreamManager != null) {
                // Keep HTTP server running; only reconfigure camera stream to prevent port collision and stream drop
                cameraStreamManager?.let { streamMgr ->
                    attachCameraStreamFlows(streamMgr)
                    streamMgr.startCamera(
                        useFront = newConfig.useFrontCamera,
                        fps = newConfig.targetFps,
                        width = newConfig.resolutionWidth,
                        height = newConfig.resolutionHeight,
                        quality = newConfig.jpegQuality,
                        onError = { err ->
                            _ipCameraStatus.value = _ipCameraStatus.value.copy(errorMessage = err)
                        }
                    )
                }
                _ipCameraStatus.value = _ipCameraStatus.value.copy(
                    useFrontCamera = newConfig.useFrontCamera,
                    isAudioEnabled = newConfig.enableAudio,
                    errorMessage = null
                )

                // Update audio stream without server restart
                if (newConfig.enableAudio) {
                    var aStream = audioStreamManager
                    if (aStream == null) {
                        aStream = AudioStreamManager(getApplication(), currentServer)
                        audioStreamManager = aStream
                    }
                    aStream.setGain(newConfig.audioGain)
                    attachAudioStreamFlows(aStream)
                    if (!aStream.isRecording()) {
                        aStream.startRecording(
                            onError = { err ->
                                Log.w("ClockViewModel", "Audio streamer error: $err")
                            }
                        )
                    }
                } else {
                    audioLevelJob?.cancel()
                    audioLevelJob = null
                    _ipCameraAudioLevel.value = 0
                    audioStreamManager?.stopRecording()
                }
            } else {
                startIpCameraService(newConfig)
            }
        } else if (wasRunning) {
            stopIpCameraService()
        }
    }

    fun switchIpCameraLens() {
        val current = ipCameraConfig.value
        val newConfig = current.copy(useFrontCamera = !current.useFrontCamera)
        prefsManager.updateIpCameraConfig(newConfig)
        if (_ipCameraStatus.value.isRunning && cameraStreamManager != null) {
            // Hot-swap camera lens without restarting HTTP server
            cameraStreamManager?.let { streamMgr ->
                attachCameraStreamFlows(streamMgr)
                streamMgr.startCamera(
                    useFront = newConfig.useFrontCamera,
                    fps = newConfig.targetFps,
                    width = newConfig.resolutionWidth,
                    height = newConfig.resolutionHeight,
                    quality = newConfig.jpegQuality,
                    onError = { err ->
                        _ipCameraStatus.value = _ipCameraStatus.value.copy(errorMessage = err)
                    }
                )
            }
            _ipCameraStatus.value = _ipCameraStatus.value.copy(useFrontCamera = newConfig.useFrontCamera)
        } else {
            startIpCameraService(newConfig)
        }
    }

    fun restartIpCamera() {
        viewModelScope.launch(Dispatchers.IO) {
            stopIpCameraService()
            delay(400)
            startIpCameraService(ipCameraConfig.value)
        }
    }

    fun resumeIpCamera() {
        if (ipCameraConfig.value.isEnabled) {
            if (cameraStreamManager != null) {
                cameraStreamManager?.resumeCamera { err ->
                    _ipCameraStatus.value = _ipCameraStatus.value.copy(errorMessage = err)
                }
            } else {
                startIpCameraService(ipCameraConfig.value)
            }
        }
    }

    fun pauseIpCamera() {
        cameraStreamManager?.pauseCamera()
    }

    fun startIpCameraService(config: IpCameraConfig = ipCameraConfig.value) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localIp = IpCameraServer.getLocalIpAddress()

                // Check if existing server can be reused
                var server = ipCameraServer
                if (server == null || !server.isRunning() || server.getPort() != config.port) {
                    // Stop previous server if any
                    server?.stop()
                    server = IpCameraServer(
                        port = config.port,
                        context = getApplication(),
                        stateProvider = { preferences.value },
                        weatherProvider = { weatherState.value },
                        scheduledChimesProvider = { scheduledChimes.value },
                        customAudioProvider = { customAudioList.value },
                        customVideoProvider = { customVideoList.value },
                        cameraConfigProvider = { ipCameraConfig.value },
                        irButtonsProvider = { irButtons.value },
                        irLearnStateProvider = { irLearnState.value },
                        espSensorDataProvider = { espSensorData.value },
                        onUpdatePreferences = { newPrefs ->
                            prefsManager.updateClockFace(newPrefs.clockFace)
                            prefsManager.updateColorPalette(newPrefs.colorPalette)
                            prefsManager.update24Hour(newPrefs.is24Hour)
                            prefsManager.updateShowSeconds(newPrefs.showSeconds)
                            prefsManager.updateShowWeather(newPrefs.showWeather)
                            prefsManager.updateNightMode(newPrefs.isNightMode)
                            prefsManager.updateKioskLock(newPrefs.isKioskLocked)
                            prefsManager.updateBurnInProtection(newPrefs.burnInProtection)
                            prefsManager.updateShowWarnings(newPrefs.showWarnings)
                            prefsManager.updateEewSettings(
                                enabled = newPrefs.eewEnabled,
                                minScale = preferences.value.eewMinScale,
                                soundEnabled = preferences.value.eewSoundEnabled,
                                vibrationEnabled = preferences.value.eewVibrationEnabled,
                                soundMode = newPrefs.eewSoundMode
                            )
                            prefsManager.updateHourlyChime(newPrefs.hourlyChimeEnabled)
                            prefsManager.updateHalfHourlyChime(newPrefs.halfHourlyChimeEnabled)
                            prefsManager.updateHourlyChimeSoundDetailed(
                                sourceType = newPrefs.hourlyChimeSourceType,
                                builtInSound = newPrefs.chimeSound,
                                customId = newPrefs.hourlyCustomAudioId,
                                customName = newPrefs.hourlyCustomAudioName,
                                customPath = newPrefs.hourlyCustomAudioPath
                            )
                            prefsManager.updateChimeHours(newPrefs.chimeStartHour, newPrefs.chimeEndHour)
                            setChimeVolume(newPrefs.chimeVolume)
                            if (newPrefs.selectedPrefecture != preferences.value.selectedPrefecture) {
                                selectPrefecture(newPrefs.selectedPrefecture)
                            }
                        },
                        onAddOrUpdateChime = { chime -> saveScheduledChime(chime) },
                        onDeleteChime = { id -> deleteScheduledChime(id) },
                        onToggleChime = { id -> toggleScheduledChime(id) },
                        onTestChime = { chime -> testPlayScheduledChime(chime) },
                        onTestSound = { sound, vol -> testChimeSound(sound, vol) },
                        onTestCustomAudio = { path, vol -> testPlayCustomAudio(path, vol) },
                        onPreviewVideo = { type, path, name, dur, layer ->
                            previewBackgroundVideo(
                                videoSourceType = type,
                                customVideoPath = path,
                                customVideoName = name,
                                playVideoAudio = true,
                                durationSeconds = dur,
                                displayLayer = layer
                            )
                        },
                        onToggleVideoDisplayLayer = { toggleActiveVideoDisplayLayer() },
                        activeVideoProvider = { activeBackgroundVideo.value },
                        onUploadAudio = { name, bytes ->
                            viewModelScope.launch {
                                val item = CustomAudioFileManager.saveAudioBytes(getApplication(), name, bytes)
                                if (item != null) {
                                    prefsManager.addCustomAudioItem(item)
                                }
                            }
                        },
                        onUploadVideo = { name, bytes ->
                            viewModelScope.launch {
                                val item = CustomVideoFileManager.saveVideoBytes(getApplication(), name, bytes)
                                if (item != null) {
                                    prefsManager.addCustomVideoItem(item)
                                }
                            }
                        },
                        onDeleteAudio = { id ->
                            val item = customAudioList.value.find { it.id == id }
                            if (item != null) deleteCustomAudio(item)
                        },
                        onDeleteVideo = { id ->
                            val item = customVideoList.value.find { it.id == id }
                            if (item != null) deleteCustomVideo(item)
                        },
                        onRenameAudio = { id, newName -> renameCustomAudio(id, newName) },
                        onRenameVideo = { id, newName -> renameCustomVideo(id, newName) },
                        onStopAudio = { stopAudioPlayback() },
                        onDismissVideo = { dismissBackgroundVideo() },
                        onSetDeviceVolume = { vol -> setChimeVolume(vol) },
                        onToggleCameraLens = { switchIpCameraLens() },
                        onUpdateCameraConfig = { newCamConfig -> setIpCameraConfig(newCamConfig) },
                        onUpdateAudioGain = { gain ->
                            val current = ipCameraConfig.value
                            val updated = current.copy(audioGain = gain)
                            prefsManager.updateIpCameraConfig(updated)
                            audioStreamManager?.setGain(gain)
                        },
                        onClientCountChanged = { clientCount ->
                            _ipCameraStatus.value = _ipCameraStatus.value.copy(clientCount = clientCount)
                        },
                        onAudioClientCountChanged = { audioClients ->
                            _ipCameraStatus.value = _ipCameraStatus.value.copy(audioClientCount = audioClients)
                        },
                        onSendIrButton = { btn -> irRemoteManager.sendButton(btn) },
                        onStartIrLearning = { irRemoteManager.startLearning() },
                        onStopIrLearning = { irRemoteManager.stopLearning() },
                        onClearLearnedSignal = { irRemoteManager.clearLearnedSignal() },
                        onSaveIrButton = { btn -> irRemoteManager.saveButton(btn) },
                        onDeleteIrButton = { id -> irRemoteManager.deleteButton(id) },
                        onUpdateEspConfig = { enabled, mode, ble, baud, host, port, interval, tOff, hOff, pOff, showClock ->
                            updateEspSensorPreferences(
                                enabled = enabled,
                                mode = mode,
                                bleDeviceName = ble,
                                baud = baud,
                                host = host,
                                port = port,
                                intervalSeconds = interval,
                                tempOffset = tOff,
                                humOffset = hOff,
                                pressOffset = pOff,
                                showOnClock = showClock
                            )
                        },
                        onRefreshEspSensor = { refreshEspSensor() },
                        onRetryEspConnection = { retryEspSensorConnection() },
                        onGetArduinoSketch = { getArduinoSketchCode() },
                        onExportIrButtonsJson = { exportIrButtonsJson() },
                        onImportIrButtonsJson = { json -> importIrButtonsFromJson(json) },
                        onUpdateApk = { name, bytes -> installUploadedApk(name, bytes) },
                        onDownloadAndInstallApk = { url -> downloadAndInstallApk(url) },
                        onEspOtaUpdate = { name, bytes, h, p -> flashEspFirmware(name, bytes, h, p) },
                        musicPlayerStateProvider = { musicPlayerState.value },
                        onPlayMusic = { track -> playMusic(track) },
                        onToggleMusic = { toggleMusicPlayPause() },
                        onNextMusic = { nextMusicTrack() },
                        onPrevMusic = { previousMusicTrack() },
                        onStopMusic = { stopMusic() },
                        onSetMusicVolume = { vol -> setMusicPlayerVolume(vol) },
                        onSeekMusic = { pos -> seekMusicTo(pos) },
                        onSeekMusicRelative = { offset -> seekMusicRelative(offset) },
                        onSetMusicSpeed = { speed -> setMusicPlaybackSpeed(speed) },
                        onSetMusicRepeatMode = { mode -> setMusicRepeatMode(mode) },
                        onVoiceCommand = { voiceAssistantManager.processTextCommand(it) },
                        equalizerStateProvider = { equalizerState.value },
                        onSetEqualizerEnabled = { setEqualizerEnabled(it) },
                        onSetEqualizerPreset = { setEqualizerPreset(it) },
                        onSetEqualizerBassCutMode = { setEqualizerBassCutMode(it) },
                        onSetEqualizerBandGain = { idx, gain -> setEqualizerBandGain(idx, gain) },
                        onResetEqualizer = { resetEqualizerToFlat() },
                        onSetAntiNoiseSilence = { setAntiNoiseSilenceEnabled(it) }
                    )

                    val started = server.start()
                    if (!started) {
                        _ipCameraStatus.value = IpCameraStatus(
                            isRunning = false,
                            errorMessage = "ポート ${config.port} のバインドに失敗しました (再試行してください)"
                        )
                        return@launch
                    }
                    ipCameraServer = server
                }

                // Initialize or restart camera streamer
                var streamManager = cameraStreamManager
                if (streamManager == null) {
                    streamManager = CameraStreamManager(getApplication(), server)
                    cameraStreamManager = streamManager
                }

                attachCameraStreamFlows(streamManager)

                streamManager.startCamera(
                    useFront = config.useFrontCamera,
                    fps = config.targetFps,
                    width = config.resolutionWidth,
                    height = config.resolutionHeight,
                    quality = config.jpegQuality,
                    onError = { err ->
                        _ipCameraStatus.value = _ipCameraStatus.value.copy(errorMessage = err)
                    }
                )

                // Initialize or restart audio streamer if enabled
                if (config.enableAudio) {
                    var aStream = audioStreamManager
                    if (aStream == null) {
                        aStream = AudioStreamManager(getApplication(), server)
                        audioStreamManager = aStream
                    }
                    aStream.setGain(config.audioGain)
                    attachAudioStreamFlows(aStream)
                    aStream.startRecording(
                        onError = { err ->
                            Log.w("ClockViewModel", "Audio streamer error: $err")
                        }
                    )
                } else {
                    audioLevelJob?.cancel()
                    audioLevelJob = null
                    _ipCameraAudioLevel.value = 0
                    audioStreamManager?.stopRecording()
                }

                _ipCameraStatus.value = IpCameraStatus(
                    isRunning = true,
                    serverUrl = "http://$localIp:${config.port}",
                    localIpAddress = localIp,
                    port = config.port,
                    useFrontCamera = config.useFrontCamera,
                    clientCount = 0,
                    audioClientCount = 0,
                    isAudioEnabled = config.enableAudio,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _ipCameraStatus.value = IpCameraStatus(
                    isRunning = false,
                    errorMessage = "起動エラー: ${e.localizedMessage}"
                )
            }
        }
    }

    fun setIpCameraAudioEnabled(enable: Boolean) {
        val current = ipCameraConfig.value
        val updated = current.copy(enableAudio = enable)
        setIpCameraConfig(updated)
    }

    fun setIpCameraAudioGain(gain: Float) {
        val current = ipCameraConfig.value
        val updated = current.copy(audioGain = gain.coerceIn(0.1f, 5.0f))
        prefsManager.updateIpCameraConfig(updated)
        audioStreamManager?.setGain(updated.audioGain)
    }

    fun stopIpCameraService() {
        try {
            audioLevelJob?.cancel()
            audioLevelJob = null
            _ipCameraAudioLevel.value = 0

            cameraPreviewJob?.cancel()
            cameraPreviewJob = null
            _ipCameraPreviewBitmap.value = null

            cameraFpsJob?.cancel()
            cameraFpsJob = null
            _ipCameraFps.value = 0f

            audioStreamManager?.stopRecording()
            audioStreamManager = null

            cameraStreamManager?.stopCamera()
            cameraStreamManager?.stopBackgroundThread()
            cameraStreamManager = null

            ipCameraServer?.stop()
            ipCameraServer = null

            _ipCameraStatus.value = IpCameraStatus(
                isRunning = false,
                errorMessage = null
            )
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            SilentAudioKeepAliveManager.stop()
        } catch (_: Exception) {}
        espSensorManager.stop()
        stopIpCameraService()
        eewManager.stop()
        ChimeAudioPlayer.stop()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }

    private fun startWeatherTicker() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val p = preferences.value
                    val weather = weatherRepo.fetchWeather(
                        prefecture = p.selectedPrefecture,
                        cityName = p.selectedCityName,
                        customLat = p.customLatitude,
                        customLon = p.customLongitude,
                        demoWarnings = p.demoWarningsPreview
                    )
                    _weatherState.value = weather
                } catch (_: Exception) {}
                // Refresh weather every 15 minutes
                delay(15 * 60 * 1000L)
            }
        }
    }

    private fun startTimeTicker() {
        viewModelScope.launch {
            val fullDateFormatEn = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
            val fullDateFormatJa = SimpleDateFormat("yyyy年M月d日 (E)", Locale.JAPANESE)
            val shortDateFormatJa = SimpleDateFormat("M月d日 (E)", Locale.JAPANESE)
            val dayOfWeekFormatEn = SimpleDateFormat("EEEE", Locale.ENGLISH)
            val dayOfWeekFormatJa = SimpleDateFormat("E曜日", Locale.JAPANESE)

            var cachedDay = -1
            var cachedDayOfWeekEn = ""
            var cachedDayOfWeekJa = ""
            var cachedFormattedDateFullEn = ""
            var cachedFormattedDateFullJa = ""
            var cachedFormattedDateShortJa = ""

            var cachedIntervalSlot = -1
            var cachedShiftX = 0f
            var cachedShiftY = 0f
            var cachedBatteryPercent = queryBatteryLevel()
            var lastBatteryCheckTime = 0L

            val cal = Calendar.getInstance()

            while (isActive) {
                val now = System.currentTimeMillis()
                cal.timeInMillis = now

                val hour24 = cal.get(Calendar.HOUR_OF_DAY)
                val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                val isPm = cal.get(Calendar.AM_PM) == Calendar.PM
                val minute = cal.get(Calendar.MINUTE)
                val second = cal.get(Calendar.SECOND)
                val millisecond = cal.get(Calendar.MILLISECOND)
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)

                // Date formatting is cached and only recomputed when day changes (once a day)
                if (day != cachedDay) {
                    cachedDay = day
                    cachedDayOfWeekEn = dayOfWeekFormatEn.format(cal.time).uppercase()
                    cachedDayOfWeekJa = dayOfWeekFormatJa.format(cal.time)
                    cachedFormattedDateFullEn = fullDateFormatEn.format(cal.time)
                    cachedFormattedDateFullJa = fullDateFormatJa.format(cal.time)
                    cachedFormattedDateShortJa = shortDateFormatJa.format(cal.time)
                }

                // --- Alarm Clock (目覚まし時計) Check & Snooze Handling ---
                val calDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isoDayOfWeek = if (calDayOfWeek == Calendar.SUNDAY) 7 else calDayOfWeek - 1
                val currentPrefs = preferences.value

                // Check Alarm trigger at second == 0
                if (second == 0 && currentPrefs.alarmEnabled && !_isAlarmRinging.value) {
                    if (currentPrefs.alarmHour == hour24 && currentPrefs.alarmMinute == minute) {
                        if (currentPrefs.alarmDays.isEmpty() || currentPrefs.alarmDays.contains(isoDayOfWeek)) {
                            if (hour24 != lastAlarmTriggerHour || minute != lastAlarmTriggerMinute) {
                                lastAlarmTriggerHour = hour24
                                lastAlarmTriggerMinute = minute
                                triggerAlarmRinging()
                            }
                        }
                    }
                }

                // Handle Snooze countdown
                if (_isAlarmSnoozed.value) {
                    val remaining = _alarmSnoozeRemainingSec.value
                    if (remaining > 1) {
                        _alarmSnoozeRemainingSec.value = remaining - 1
                    } else if (remaining == 1) {
                        _alarmSnoozeRemainingSec.value = 0
                        _isAlarmSnoozed.value = false
                        triggerAlarmRinging()
                    }
                }

                // Check and trigger chimes & IR scheduled automation at second == 0
                if (second == 0) {
                    // Smart IR Remote Scheduled Automation Check
                    irRemoteManager.checkScheduledTriggers(hour24, minute, isoDayOfWeek)

                    if (!currentPrefs.isNightMode) {
                        val currentChimes = scheduledChimes.value

                        var scheduledChimeTriggered = false

                        // 1. Check user-configured scheduled chimes first (PRIORITY)
                        for (chime in currentChimes) {
                            if (chime.isEnabled && chime.hour == hour24 && chime.minute == minute) {
                                if (chime.daysOfWeek.isEmpty() || chime.daysOfWeek.contains(isoDayOfWeek)) {
                                    val chimeKey = "${chime.id}_${hour24}_${minute}"
                                    if (!triggeredScheduledChimesSet.contains(chimeKey)) {
                                        triggeredScheduledChimesSet.add(chimeKey)
                                        scheduledChimeTriggered = true
                                        if (chime.irSendEnabled && !chime.irButtonId.isNullOrEmpty()) {
                                            irRemoteManager.sendButtonById(chime.irButtonId)
                                        } else {
                                            irRemoteManager.onAlarmTriggered()
                                        }
                                        _isChimeRinging.value = true
                                        _chimeRingingInfo.value = chime.label.ifBlank { "スケジュールチャイム" }
                                        val chimeVol = if (chime.volume > 0f) chime.volume else currentPrefs.chimeVolume
                                        if (chime.isVideoOnlyAudio && chime.videoSourceType == ChimeVideoSourceType.NONE) {
                                            playWithTemporaryVolume(chimeVol) { onDone ->
                                                ChimeSynthesizer.playChime(chime.builtInSound, 1.0f, onComplete = {
                                                    _isChimeRinging.value = false
                                                    _chimeRingingInfo.value = null
                                                    onDone()
                                                })
                                            }
                                        } else {
                                            playWithTemporaryVolume(chimeVol) { onDone ->
                                                ChimeAudioPlayer.playScheduledChime(getApplication(), chime, onComplete = {
                                                    _isChimeRinging.value = false
                                                    _chimeRingingInfo.value = null
                                                    onDone()
                                                })
                                            }
                                            if (chime.videoSourceType != ChimeVideoSourceType.NONE) {
                                                triggerBackgroundVideo(chime)
                                            }
                                        }
                                    } else {
                                        scheduledChimeTriggered = true
                                    }
                                }
                            }
                        }

                    // 2. If NO scheduled chime is active for this exact minute, check hourly / half-hourly chimes
                    if (!scheduledChimeTriggered) {
                        if (minute == 0 && (hour24 != lastChimeTriggerHour || minute != lastChimeTriggerMinute)) {
                            lastChimeTriggerHour = hour24
                            lastChimeTriggerMinute = minute
                            if (currentPrefs.hourlyChimeEnabled) {
                                val inWindow = if (currentPrefs.chimeStartHour <= currentPrefs.chimeEndHour) {
                                    hour24 in currentPrefs.chimeStartHour..currentPrefs.chimeEndHour
                                } else {
                                    hour24 >= currentPrefs.chimeStartHour || hour24 <= currentPrefs.chimeEndHour
                                }
                                if (inWindow) {
                                    _isChimeRinging.value = true
                                    _chimeRingingInfo.value = "時報チャイム (%02d:00)".format(hour24)
                                    if (currentPrefs.hourlyChimeSourceType == com.example.model.ChimeAudioSourceType.CUSTOM_FILE &&
                                        !currentPrefs.hourlyCustomAudioPath.isNullOrEmpty()
                                    ) {
                                        _playingAudioPath.value = currentPrefs.hourlyCustomAudioPath
                                        playWithTemporaryVolume(currentPrefs.chimeVolume) { onDone ->
                                            com.example.audio.ChimeAudioPlayer.playCustomFile(
                                                currentPrefs.hourlyCustomAudioPath,
                                                1.0f,
                                                onComplete = {
                                                    if (_playingAudioPath.value == currentPrefs.hourlyCustomAudioPath) {
                                                        _playingAudioPath.value = null
                                                    }
                                                    _isChimeRinging.value = false
                                                    _chimeRingingInfo.value = null
                                                    onDone()
                                                }
                                            )
                                        }
                                    } else {
                                        playWithTemporaryVolume(currentPrefs.chimeVolume) { onDone ->
                                            ChimeSynthesizer.playChime(currentPrefs.chimeSound, 1.0f, onComplete = {
                                                _isChimeRinging.value = false
                                                _chimeRingingInfo.value = null
                                                onDone()
                                            })
                                        }
                                    }
                                }
                            }
                        } else if (minute == 30 && minute != lastChimeTriggerMinute) {
                            lastChimeTriggerMinute = minute
                            if (currentPrefs.halfHourlyChimeEnabled) {
                                val inWindow = if (currentPrefs.chimeStartHour <= currentPrefs.chimeEndHour) {
                                    hour24 in currentPrefs.chimeStartHour..currentPrefs.chimeEndHour
                                } else {
                                    hour24 >= currentPrefs.chimeStartHour || hour24 <= currentPrefs.chimeEndHour
                                }
                                if (inWindow) {
                                    _isChimeRinging.value = true
                                    _chimeRingingInfo.value = "時報チャイム (%02d:30)".format(hour24)
                                    val halfVol = (currentPrefs.chimeVolume * 0.7f).coerceIn(0.05f, 1.0f)
                                    playWithTemporaryVolume(halfVol) { onDone ->
                                        ChimeSynthesizer.playSinglePing(1.0f, onComplete = {
                                            _isChimeRinging.value = false
                                            _chimeRingingInfo.value = null
                                            onDone()
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reset minute latch when second moves on
                if (second > 2) {
                    lastChimeTriggerMinute = -1
                    triggeredScheduledChimesSet.clear()
                }

                // Burn-in prevention drift: subtle micro shifts every 3 minutes (cached)
                if (currentPrefs.burnInProtection) {
                    val intervalSlot = (minute / 3) % 4
                    if (intervalSlot != cachedIntervalSlot) {
                        cachedIntervalSlot = intervalSlot
                        cachedShiftX = when (intervalSlot) {
                            0 -> 0f
                            1 -> 3f
                            2 -> -2f
                            else -> 1.5f
                        }
                        cachedShiftY = when (intervalSlot) {
                            0 -> 0f
                            1 -> -2f
                            2 -> 3f
                            else -> -1.5f
                        }
                    }
                } else {
                    cachedShiftX = 0f
                    cachedShiftY = 0f
                }

                val tz = TimeZone.getDefault()
                val tzName = if (currentPrefs.customLocationName.isNotBlank()) {
                    currentPrefs.customLocationName
                } else {
                    "${tz.displayName} (${tz.id})"
                }

                _timeState.value = CurrentTimeState(
                    hour24 = hour24,
                    hour12 = hour12,
                    isPm = isPm,
                    minute = minute,
                    second = second,
                    millisecond = millisecond,
                    year = year,
                    month = month,
                    day = day,
                    dayOfWeekEn = cachedDayOfWeekEn,
                    dayOfWeekJa = cachedDayOfWeekJa,
                    formattedDateFullEn = cachedFormattedDateFullEn,
                    formattedDateFullJa = cachedFormattedDateFullJa,
                    formattedDateShortJa = cachedFormattedDateShortJa,
                    timezoneDisplayName = tzName,
                    burnInShiftX = cachedShiftX,
                    burnInShiftY = cachedShiftY,
                    batteryPercent = cachedBatteryPercent
                )

                if (now - lastBatteryCheckTime > 30_000L) {
                    lastBatteryCheckTime = now
                    cachedBatteryPercent = queryBatteryLevel()
                }

                // Sync precisely to the next whole second (1Hz ultra-low power update)
                val elapsedInSec = System.currentTimeMillis() % 1000L
                val sleepTime = (1000L - elapsedInSec).coerceIn(50L, 1000L)
                delay(sleepTime)
            }
        }
    }

    private fun startTimerTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val current = _timerState.value
                if (current.isRunning && current.remainingSeconds > 0) {
                    val next = current.remainingSeconds - 1
                    if (next == 0) {
                        // Play configured chime sound for timer finished
                        ChimeSynthesizer.playChime(current.chimeSound, preferences.value.chimeVolume)
                        if (current.autoRepeat && current.initialSeconds > 0) {
                            _timerState.value = current.copy(
                                remainingSeconds = current.initialSeconds,
                                isRunning = true,
                                isFinished = false
                            )
                        } else {
                            _timerState.value = current.copy(
                                remainingSeconds = 0,
                                isRunning = false,
                                isFinished = true
                            )
                        }
                    } else {
                        _timerState.value = current.copy(remainingSeconds = next)
                    }
                }
            }
        }
    }

    // Detailed timer controls (User Requested: タイマーをもっと詳細に設定できるように)
    fun setTimerDetails(
        hours: Int,
        minutes: Int,
        seconds: Int,
        label: String = "タイマー",
        chimeSound: ChimeSound = ChimeSound.CRYSTAL_BELL,
        autoRepeat: Boolean = false,
        startImmediately: Boolean = true
    ) {
        val totalSec = (hours * 3600) + (minutes * 60) + seconds
        if (totalSec <= 0) return
        _timerState.value = DeskTimerState(
            remainingSeconds = totalSec,
            initialSeconds = totalSec,
            isRunning = startImmediately,
            isFinished = false,
            label = label.ifBlank { "タイマー" },
            chimeSound = chimeSound,
            autoRepeat = autoRepeat
        )
    }

    fun updateTimerSettings(
        label: String? = null,
        chimeSound: ChimeSound? = null,
        autoRepeat: Boolean? = null
    ) {
        val cur = _timerState.value
        _timerState.value = cur.copy(
            label = label ?: cur.label,
            chimeSound = chimeSound ?: cur.chimeSound,
            autoRepeat = autoRepeat ?: cur.autoRepeat
        )
    }

    fun setTimerSeconds(seconds: Int) {
        _timerState.value = DeskTimerState(
            remainingSeconds = seconds,
            initialSeconds = seconds,
            isRunning = true,
            isFinished = false
        )
    }

    fun addTimerMinutes(minutes: Int) {
        val current = _timerState.value
        val added = minutes * 60
        val newRem = current.remainingSeconds + added
        val newInit = maxOf(current.initialSeconds, newRem)
        _timerState.value = current.copy(
            remainingSeconds = newRem,
            initialSeconds = newInit,
            isRunning = true,
            isFinished = false
        )
    }

    fun addTimerSeconds(seconds: Int) {
        val current = _timerState.value
        val newRem = (current.remainingSeconds + seconds).coerceAtLeast(0)
        val newInit = maxOf(current.initialSeconds, newRem)
        _timerState.value = current.copy(
            remainingSeconds = newRem,
            initialSeconds = newInit,
            isFinished = false
        )
    }

    fun toggleTimerPause() {
        val current = _timerState.value
        if (current.remainingSeconds > 0) {
            _timerState.value = current.copy(isRunning = !current.isRunning)
        }
    }

    fun resetTimer() {
        _timerState.value = DeskTimerState()
    }

    fun dismissTimerFinished() {
        _timerState.value = _timerState.value.copy(isFinished = false)
    }

    // --- Desk Stopwatch Controls (ストップウォッチ操作) ---
    fun startStopwatch() {
        if (_stopwatchState.value.isRunning) return
        stopwatchBaseTime = SystemClock.elapsedRealtime()
        _stopwatchState.value = _stopwatchState.value.copy(isRunning = true)

        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _stopwatchState.value.isRunning) {
                val now = SystemClock.elapsedRealtime()
                val currentElapsed = stopwatchAccumulatedTime + (now - stopwatchBaseTime)
                _stopwatchState.value = _stopwatchState.value.copy(elapsedMillis = currentElapsed)
                delay(16) // 高精度 1/100秒表示 (約60fps更新)
            }
        }
    }

    fun pauseStopwatch() {
        if (!_stopwatchState.value.isRunning) return
        val now = SystemClock.elapsedRealtime()
        stopwatchAccumulatedTime += (now - stopwatchBaseTime)
        stopwatchJob?.cancel()
        stopwatchJob = null
        _stopwatchState.value = _stopwatchState.value.copy(
            elapsedMillis = stopwatchAccumulatedTime,
            isRunning = false
        )
    }

    fun toggleStopwatch() {
        if (_stopwatchState.value.isRunning) {
            pauseStopwatch()
        } else {
            startStopwatch()
        }
    }

    fun recordStopwatchLap() {
        val current = _stopwatchState.value
        val totalTime = current.elapsedMillis
        if (totalTime <= 0L) return

        val lastOverall = current.laps.firstOrNull()?.overallTimeMillis ?: 0L
        val lapTime = (totalTime - lastOverall).coerceAtLeast(0L)
        val nextIndex = current.laps.size + 1

        val newLap = StopwatchLap(
            lapIndex = nextIndex,
            lapTimeMillis = lapTime,
            overallTimeMillis = totalTime
        )

        val updatedLaps = listOf(newLap) + current.laps

        val bestLapTime = updatedLaps.minOfOrNull { it.lapTimeMillis }
        val worstLapTime = if (updatedLaps.size >= 2) updatedLaps.maxOfOrNull { it.lapTimeMillis } else null

        val finalLaps = updatedLaps.map { lap ->
            lap.copy(
                isBest = updatedLaps.size >= 2 && lap.lapTimeMillis == bestLapTime,
                isWorst = updatedLaps.size >= 2 && lap.lapTimeMillis == worstLapTime && bestLapTime != worstLapTime
            )
        }

        _stopwatchState.value = current.copy(laps = finalLaps)
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = null
        stopwatchAccumulatedTime = 0L
        stopwatchBaseTime = 0L
        _stopwatchState.value = DeskStopwatchState()
    }

    // Preference mutations
    fun selectClockFace(face: ClockFace) = prefsManager.updateClockFace(face)
    fun selectColorPalette(palette: ColorPalette) = prefsManager.updateColorPalette(palette)
    fun updateVoiceAssistantEnabled(enabled: Boolean) = prefsManager.updateVoiceAssistantEnabled(enabled)
    fun updateWakeWordListeningEnabled(enabled: Boolean) = prefsManager.updateWakeWordListeningEnabled(enabled)
    fun updateWakeWordType(type: String) = prefsManager.updateWakeWordType(type)
    fun updateCustomWakeWord(word: String) = prefsManager.updateCustomWakeWord(word)
    fun updateVoiceTtsResponseEnabled(enabled: Boolean) = prefsManager.updateVoiceTtsResponseEnabled(enabled)
    fun updateVoiceTtsPitch(pitch: Float) = prefsManager.updateVoiceTtsPitch(pitch)
    fun updateVoiceTtsSpeechRate(rate: Float) = prefsManager.updateVoiceTtsSpeechRate(rate)
    fun toggle24Hour() = prefsManager.toggle24Hour()
    fun toggleShowSeconds() = prefsManager.toggleShowSeconds()
    fun toggleShowWeather() = prefsManager.toggleShowWeather()
    fun toggleShowWarnings() = prefsManager.toggleShowWarnings()
    fun toggleDemoWarnings() {
        prefsManager.toggleDemoWarnings()
        refreshWeather()
    }

    fun selectPrefecture(prefecture: String) {
        prefsManager.updatePrefecture(prefecture)
        refreshWeather()
    }

    fun selectMunicipality(item: MunicipalityItem) {
        prefsManager.updateMunicipality(
            cityName = item.name,
            prefecture = item.prefecture,
            lat = item.latitude,
            lon = item.longitude,
            isAuto = false
        )
        refreshWeather()
    }

    fun searchMunicipalities(query: String) {
        viewModelScope.launch {
            if (query.trim().isEmpty()) {
                _searchResults.value = emptyList()
                return@launch
            }
            _isSearching.value = true
            try {
                val results = JapanMunicipalities.searchMunicipalities(query)
                _searchResults.value = results
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun detectAndSetCurrentLocation(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isDetectingLocation.value = true
            try {
                val location = DeviceLocationHelper.getCurrentLocation(getApplication())
                if (location != null) {
                    prefsManager.updateMunicipality(
                        cityName = location.cityName,
                        prefecture = location.prefecture,
                        lat = location.latitude,
                        lon = location.longitude,
                        isAuto = true
                    )
                    refreshWeather()
                    onComplete(true, "${location.cityName} を検出しました")
                } else {
                    onComplete(false, "位置情報を取得できませんでした。位置情報サービスをご確認ください。")
                }
            } catch (e: Exception) {
                onComplete(false, "位置情報取得エラー: ${e.message}")
            } finally {
                _isDetectingLocation.value = false
            }
        }
    }

    fun setHourlyChime(enabled: Boolean) = prefsManager.updateHourlyChime(enabled)
    fun setHalfHourlyChime(enabled: Boolean) = prefsManager.updateHalfHourlyChime(enabled)
    fun selectChimeSound(sound: ChimeSound) = prefsManager.updateChimeSound(sound)
    fun selectHourlyCustomAudio(audio: CustomAudioItem) = prefsManager.updateHourlyCustomAudio(audio)
    fun setChimeHours(start: Int, end: Int) = prefsManager.updateChimeHours(start, end)

    fun setChimeVolume(volume: Float) {
        val clamped = volume.coerceIn(0.05f, 1.0f)
        prefsManager.updateChimeVolume(clamped)
        // Note: Do NOT change device media volume here!
        // The volume is temporarily applied only when the chime sounds (hourly chime, scheduled chime, test playback)
        // and automatically restores to the user's current device volume once playback finishes.
    }

    private var preChimeStreamVolume: Int? = null
    private var activeChimePlaybackCount = 0
    private val volumeLock = Any()
    private var volumeRestoreJob: Job? = null

    /**
     * Temporarily applies desiredFraction to STREAM_MUSIC during chime playback,
     * then cleanly restores the original stream volume when complete.
     */
    fun playWithTemporaryVolume(
        desiredFraction: Float = preferences.value.chimeVolume,
        timeoutMs: Long = 45_000L,
        block: (onComplete: () -> Unit) -> Unit
    ) {
        val am = audioManager
        if (am == null) {
            block {}
            return
        }

        synchronized(volumeLock) {
            val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (activeChimePlaybackCount == 0) {
                preChimeStreamVolume = currentVol
            }
            activeChimePlaybackCount++

            val clampedFraction = desiredFraction.coerceIn(0.05f, 1.0f)
            val targetVol = kotlin.math.round((clampedFraction * maxVol)).toInt().coerceIn(1, maxVol)
            try {
                // Apply silently without popping up system UI volume bar
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
            } catch (_: Exception) {}
        }

        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val finishCallback: () -> Unit = {
            if (completed.compareAndSet(false, true)) {
                synchronized(volumeLock) {
                    activeChimePlaybackCount = (activeChimePlaybackCount - 1).coerceAtLeast(0)
                    if (activeChimePlaybackCount == 0) {
                        preChimeStreamVolume?.let { orig ->
                            try {
                                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, orig, 0)
                            } catch (_: Exception) {}
                        }
                        preChimeStreamVolume = null
                    }
                }
            }
        }

        // Failsafe timeout to always restore original volume even if playback listener is dropped
        volumeRestoreJob?.cancel()
        volumeRestoreJob = viewModelScope.launch {
            delay(timeoutMs)
            finishCallback()
        }

        try {
            block {
                finishCallback()
            }
        } catch (e: Exception) {
            finishCallback()
            throw e
        }
    }

    fun applyTemporaryPlaybackVolume(desiredFraction: Float) {
        val am = audioManager ?: return
        synchronized(volumeLock) {
            val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (activeChimePlaybackCount == 0) {
                preChimeStreamVolume = currentVol
            }
            activeChimePlaybackCount++

            val clampedFraction = desiredFraction.coerceIn(0.05f, 1.0f)
            val targetVol = kotlin.math.round((clampedFraction * maxVol)).toInt().coerceIn(1, maxVol)
            try {
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
            } catch (_: Exception) {}
        }
    }

    private fun forceRestoreDeviceVolume() {
        val am = audioManager ?: return
        synchronized(volumeLock) {
            activeChimePlaybackCount = 0
            preChimeStreamVolume?.let { orig ->
                try {
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, orig, 0)
                } catch (_: Exception) {}
            }
            preChimeStreamVolume = null
        }
    }

    fun getDeviceStreamVolumePercent(): Int {
        return try {
            audioManager?.let { am ->
                val current = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                if (maxVol > 0) ((current.toFloat() / maxVol.toFloat()) * 100).toInt() else 75
            } ?: 75
        } catch (_: Exception) {
            75
        }
    }

    fun toggleKioskLock() = prefsManager.toggleKioskLock()
    fun toggleNightMode() {
        val willBeNight = !preferences.value.isNightMode
        prefsManager.toggleNightMode()
        if (willBeNight) {
            irRemoteManager.onNightModeEntered()
        } else {
            irRemoteManager.onNightModeExited()
        }
    }
    fun setLocationName(name: String) = prefsManager.updateLocationName(name)

    // --- Smart IR Remote Controls ---
    fun sendIrButton(button: IrRemoteButton): Boolean {
        return irRemoteManager.sendButton(button)
    }

    fun sendIrButtonById(id: String): Boolean {
        return irRemoteManager.sendButtonById(id)
    }

    fun startIrLearning() {
        irRemoteManager.startLearning()
    }

    fun stopIrLearning() {
        irRemoteManager.stopLearning()
    }

    fun clearIrLearnedSignal() {
        irRemoteManager.clearLearnedSignal()
    }

    fun saveIrButton(button: IrRemoteButton) {
        irRemoteManager.saveButton(button)
    }

    fun deleteIrButton(id: String) {
        irRemoteManager.deleteButton(id)
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _isWeatherRefreshing.value = true
            try {
                val p = preferences.value
                val weather = weatherRepo.fetchWeather(
                    prefecture = p.selectedPrefecture,
                    cityName = p.selectedCityName,
                    customLat = p.customLatitude,
                    customLon = p.customLongitude,
                    demoWarnings = p.demoWarningsPreview
                )
                _weatherState.value = weather
            } catch (_: Exception) {
            } finally {
                _isWeatherRefreshing.value = false
            }
        }
    }

    // Test chime sound immediately
    fun testCurrentChime() {
        val p = preferences.value
        playWithTemporaryVolume(p.chimeVolume) { onDone ->
            if (p.hourlyChimeSourceType == com.example.model.ChimeAudioSourceType.CUSTOM_FILE && !p.hourlyCustomAudioPath.isNullOrEmpty()) {
                _playingAudioPath.value = p.hourlyCustomAudioPath
                com.example.audio.ChimeAudioPlayer.playCustomFile(p.hourlyCustomAudioPath, 1.0f) {
                    _playingAudioPath.value = null
                    onDone()
                }
            } else {
                ChimeSynthesizer.playChime(p.chimeSound, 1.0f) {
                    onDone()
                }
            }
        }
    }

    fun testChimeSound(sound: ChimeSound, volume: Float = preferences.value.chimeVolume) {
        playWithTemporaryVolume(volume) { onDone ->
            ChimeSynthesizer.playChime(sound, 1.0f) {
                onDone()
            }
        }
    }

    fun testCustomAudio(filePath: String, volume: Float = preferences.value.chimeVolume) {
        playWithTemporaryVolume(volume) { onDone ->
            _playingAudioPath.value = filePath
            com.example.audio.ChimeAudioPlayer.playCustomFile(filePath, 1.0f) {
                if (_playingAudioPath.value == filePath) {
                    _playingAudioPath.value = null
                }
                onDone()
            }
        }
    }

    // --- Scheduled Chimes (Alarm-style) Operations ---

    fun saveScheduledChime(chime: ScheduledChime) {
        prefsManager.saveOrUpdateChime(chime)
    }

    fun deleteScheduledChime(chimeId: String) {
        prefsManager.deleteChime(chimeId)
    }

    fun toggleScheduledChime(chimeId: String) {
        prefsManager.toggleChimeEnabled(chimeId)
    }

    fun importCustomAudio(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val item = CustomAudioFileManager.importAudioFile(getApplication(), uri)
                if (item != null) {
                    prefsManager.addCustomAudioItem(item)
                    onResult(true, "音声ファイル「${item.name}」を追加しました")
                } else {
                    onResult(false, "音声ファイルの取り込みに失敗しました")
                }
            } catch (e: Exception) {
                onResult(false, "エラーが発生しました: ${e.message}")
            }
        }
    }

    fun renameCustomAudio(id: String, newName: String) {
        prefsManager.renameCustomAudioItem(id, newName)
    }

    fun deleteCustomAudio(item: CustomAudioItem) {
        viewModelScope.launch {
            CustomAudioFileManager.deleteAudioFile(item.filePath)
            prefsManager.deleteCustomAudioItem(item.id)
            if (_playingAudioPath.value == item.filePath) {
                stopAudioPlayback()
            }
        }
    }

    fun testPlayScheduledChime(chime: ScheduledChime) {
        if (chime.irSendEnabled && !chime.irButtonId.isNullOrEmpty()) {
            irRemoteManager.sendButtonById(chime.irButtonId)
        }
        val effectiveVol = if (chime.volume > 0f) chime.volume else preferences.value.chimeVolume
        playWithTemporaryVolume(effectiveVol) { onDone ->
            if (chime.isVideoOnlyAudio && chime.videoSourceType == ChimeVideoSourceType.NONE) {
                ChimeSynthesizer.playChime(chime.builtInSound, 1.0f, onComplete = onDone)
            } else {
                ChimeAudioPlayer.playScheduledChime(getApplication(), chime, onComplete = onDone)
                if (chime.videoSourceType != ChimeVideoSourceType.NONE) {
                    triggerBackgroundVideo(chime)
                }
            }
        }
    }

    fun testPlayCustomAudio(filePath: String, volume: Float = preferences.value.chimeVolume) {
        playWithTemporaryVolume(volume) { onDone ->
            _playingAudioPath.value = filePath
            ChimeAudioPlayer.playCustomFile(filePath, 1.0f) {
                if (_playingAudioPath.value == filePath) {
                    _playingAudioPath.value = null
                }
                onDone()
            }
        }
    }

    fun stopAudioPlayback() {
        _playingAudioPath.value = null
        ChimeAudioPlayer.stop()
        MusicPlayerManager.stop()
        forceRestoreDeviceVolume()
    }

    // --- Dedicated Music Player Control & Volume Management ---

    fun playMusic(track: CustomAudioItem, playlist: List<CustomAudioItem> = customAudioList.value) {
        VideoPlayerManager.stop()
        dismissBackgroundVideo()
        val volume = preferences.value.musicPlayerVolume
        val repeatMode = try {
            MusicRepeatMode.valueOf(preferences.value.musicPlayerRepeatMode)
        } catch (_: Exception) {
            MusicRepeatMode.ALL
        }
        MusicPlayerManager.playTrack(track, playlist, volume, repeatMode)
    }

    fun toggleMusicPlayPause() {
        if (MusicPlayerManager.playerState.value.isPlaying) {
            MusicPlayerManager.pause()
        } else if (MusicPlayerManager.playerState.value.isPaused) {
            MusicPlayerManager.resume()
        } else {
            val track = MusicPlayerManager.playerState.value.currentTrack
                ?: customAudioList.value.firstOrNull()
            if (track != null) {
                playMusic(track, customAudioList.value)
            } else {
                MusicPlayerManager.togglePlayPause()
            }
        }
    }

    fun pauseMusic() {
        MusicPlayerManager.pause()
    }

    fun resumeMusic() {
        MusicPlayerManager.resume()
    }

    fun stopMusic() {
        MusicPlayerManager.stop()
        if (_playingAudioPath.value != null && _playingAudioPath.value == MusicPlayerManager.playerState.value.currentTrack?.filePath) {
            _playingAudioPath.value = null
        }
    }

    fun nextMusicTrack() {
        MusicPlayerManager.next()
    }

    fun previousMusicTrack() {
        MusicPlayerManager.previous()
    }

    fun seekMusicTo(positionMs: Long) {
        MusicPlayerManager.seekTo(positionMs)
    }

    fun seekMusicRelative(offsetMs: Long) {
        MusicPlayerManager.seekRelative(offsetMs)
    }

    fun setMusicPlaybackSpeed(speed: Float) {
        MusicPlayerManager.setPlaybackSpeed(speed)
    }

    fun setMusicPlayerVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        MusicPlayerManager.setVolume(clamped)
        prefsManager.updatePreferences(preferences.value.copy(musicPlayerVolume = clamped))
    }

    fun setMusicRepeatMode(mode: MusicRepeatMode) {
        MusicPlayerManager.setRepeatMode(mode)
        prefsManager.updatePreferences(preferences.value.copy(musicPlayerRepeatMode = mode.name))
    }

    fun cycleMusicRepeatMode(): MusicRepeatMode {
        val nextMode = MusicPlayerManager.cycleRepeatMode()
        prefsManager.updatePreferences(preferences.value.copy(musicPlayerRepeatMode = nextMode.name))
        return nextMode
    }

    // --- Video Player Controls ---

    fun playVideoInMusicPlayer(
        video: CustomVideoItem,
        playAudio: Boolean = true,
        volume: Float = preferences.value.musicPlayerVolume,
        isLooping: Boolean = true
    ) {
        MusicPlayerManager.stop()
        dismissBackgroundVideo()
        VideoPlayerManager.playVideo(video, VideoDisplayLayer.BACKGROUND, playAudio, volume, isLooping)
    }

    fun playCustomVideoDirect(
        video: CustomVideoItem,
        displayLayer: VideoDisplayLayer = VideoDisplayLayer.BACKGROUND,
        playAudio: Boolean = true,
        volume: Float = preferences.value.musicPlayerVolume,
        isLooping: Boolean = true
    ) {
        videoDismissJob?.cancel()
        MusicPlayerManager.stop()
        VideoPlayerManager.playVideo(video, displayLayer, playAudio, volume, isLooping)
        _activeBackgroundVideo.value = ActiveBackgroundVideo(
            chimeId = null,
            chimeLabel = if (displayLayer == VideoDisplayLayer.FOREGROUND) "前面動画再生" else "背景動画再生",
            videoSourceType = ChimeVideoSourceType.CUSTOM_FILE,
            customVideoPath = video.filePath,
            customVideoName = video.name,
            playVideoAudio = playAudio,
            volume = volume,
            startTimeMs = System.currentTimeMillis(),
            durationSeconds = if (isLooping) 0 else ScheduledChime.DURATION_VIDEO_LENGTH,
            displayLayer = displayLayer
        )
    }

    fun toggleVideoPlayPause() {
        VideoPlayerManager.togglePlayPause()
    }

    fun pauseVideo() {
        VideoPlayerManager.pause()
    }

    fun resumeVideo() {
        VideoPlayerManager.resume()
    }

    fun stopVideo() {
        VideoPlayerManager.stop()
        dismissBackgroundVideo()
    }

    fun seekVideoTo(positionMs: Long) {
        VideoPlayerManager.seekTo(positionMs)
    }

    fun seekVideoRelative(offsetMs: Long) {
        VideoPlayerManager.seekRelative(offsetMs)
    }

    fun setVideoVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        VideoPlayerManager.setVolume(clamped)
        prefsManager.updatePreferences(preferences.value.copy(musicPlayerVolume = clamped))
    }

    fun toggleVideoMute() {
        VideoPlayerManager.toggleMute()
    }

    fun setVideoMuted(muted: Boolean) {
        VideoPlayerManager.setMuted(muted)
    }

    fun setVideoLooping(loop: Boolean) {
        VideoPlayerManager.setLooping(loop)
    }

    fun setVideoPlaybackSpeed(speed: Float) {
        VideoPlayerManager.setPlaybackSpeed(speed)
    }

    fun setVideoAspectRatio(ratio: VideoAspectRatio) {
        VideoPlayerManager.setAspectRatio(ratio)
    }

    fun cycleVideoAspectRatio(): VideoAspectRatio {
        return VideoPlayerManager.cycleAspectRatio()
    }

    fun getVideoDurationSeconds(filePath: String?): Int {
        if (filePath.isNullOrBlank()) return 60
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return 60
            val retriever = android.media.MediaMetadataRetriever()
            java.io.FileInputStream(file).use { fis ->
                retriever.setDataSource(fis.fd)
            }
            val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val timeMs = timeStr?.toLongOrNull() ?: 60000L
            kotlin.math.max(1, (timeMs / 1000).toInt())
        } catch (_: Exception) {
            60
        }
    }

    // --- Video Playback & Background/Foreground Layer Management ---

    fun triggerBackgroundVideo(chime: ScheduledChime) {
        videoDismissJob?.cancel()
        val shouldPlayAudio = chime.playVideoAudio || chime.sourceType == ChimeAudioSourceType.VIDEO_SOUND
        if (shouldPlayAudio) {
            applyTemporaryPlaybackVolume(chime.volume)
        }
        _activeBackgroundVideo.value = ActiveBackgroundVideo(
            chimeId = chime.id,
            chimeLabel = chime.label,
            videoSourceType = chime.videoSourceType,
            customVideoPath = chime.customVideoPath,
            customVideoName = chime.customVideoName,
            playVideoAudio = shouldPlayAudio,
            volume = chime.volume,
            startTimeMs = System.currentTimeMillis(),
            durationSeconds = chime.videoDurationSeconds,
            displayLayer = chime.videoDisplayLayer
        )

        // Auto dismiss:
        // -1 (DURATION_VIDEO_LENGTH): Dismissed when video ends (with safety timeout fallback)
        // 0 (DURATION_MANUAL_STOP): Manual dismiss by user tap
        // > 0: Specific seconds
        if (chime.videoDurationSeconds == ScheduledChime.DURATION_VIDEO_LENGTH) {
            if (chime.videoSourceType == ChimeVideoSourceType.CUSTOM_FILE && !chime.customVideoPath.isNullOrEmpty()) {
                val durSec = getVideoDurationSeconds(chime.customVideoPath)
                videoDismissJob = viewModelScope.launch {
                    delay((durSec + 2) * 1000L)
                    _activeBackgroundVideo.value = null
                    if (shouldPlayAudio) forceRestoreDeviceVolume()
                }
            } else {
                videoDismissJob = viewModelScope.launch {
                    delay(60 * 1000L)
                    _activeBackgroundVideo.value = null
                    if (shouldPlayAudio) forceRestoreDeviceVolume()
                }
            }
        } else if (chime.videoDurationSeconds > 0) {
            videoDismissJob = viewModelScope.launch {
                delay(chime.videoDurationSeconds * 1000L)
                _activeBackgroundVideo.value = null
                if (shouldPlayAudio) forceRestoreDeviceVolume()
            }
        }
    }

    fun previewBackgroundVideo(
        videoSourceType: ChimeVideoSourceType,
        customVideoPath: String? = null,
        customVideoName: String? = null,
        playVideoAudio: Boolean = true,
        volume: Float = preferences.value.musicPlayerVolume,
        durationSeconds: Int = ScheduledChime.DURATION_VIDEO_LENGTH,
        displayLayer: VideoDisplayLayer = try {
            VideoDisplayLayer.valueOf(preferences.value.defaultVideoDisplayLayer)
        } catch (_: Exception) {
            VideoDisplayLayer.BACKGROUND
        }
    ) {
        videoDismissJob?.cancel()
        MusicPlayerManager.stop()

        if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE && !customVideoPath.isNullOrEmpty()) {
            val curVid = VideoPlayerManager.playerState.value
            val isSameVideo = curVid.currentVideo?.filePath == customVideoPath
            // Preserve the exact playing volume to prevent volume drops when toggling full-screen
            val effectiveVolume = if (isSameVideo && (curVid.isPlaying || curVid.isPaused)) curVid.volume else volume
            val isAlreadyPlaying = isSameVideo && curVid.isPlaying

            if (!isAlreadyPlaying) {
                val videoItem = customVideoList.value.find { it.filePath == customVideoPath }
                    ?: CustomVideoItem(
                        id = customVideoPath.hashCode().toString(),
                        name = customVideoName ?: File(customVideoPath).name,
                        filePath = customVideoPath
                    )
                VideoPlayerManager.playVideo(
                    video = videoItem,
                    displayLayer = displayLayer,
                    playAudio = playVideoAudio,
                    volume = effectiveVolume,
                    isLooping = (durationSeconds != -1 && durationSeconds != 0) || durationSeconds == 0
                )
            } else {
                VideoPlayerManager.setDisplayLayer(displayLayer)
                if (playVideoAudio) {
                    VideoPlayerManager.setVolume(effectiveVolume)
                }
            }
        } else {
            VideoPlayerManager.stop()
        }

        val curVidForActive = VideoPlayerManager.playerState.value
        val actualVol = if (curVidForActive.currentVideo?.filePath == customVideoPath) curVidForActive.volume else volume

        _activeBackgroundVideo.value = ActiveBackgroundVideo(
            chimeId = null,
            chimeLabel = if (displayLayer == VideoDisplayLayer.FOREGROUND) "前面動画再生" else "背景動画再生",
            videoSourceType = videoSourceType,
            customVideoPath = customVideoPath,
            customVideoName = customVideoName,
            playVideoAudio = playVideoAudio,
            volume = actualVol,
            startTimeMs = System.currentTimeMillis(),
            durationSeconds = durationSeconds,
            displayLayer = displayLayer
        )

        val isScheduledChime = durationSeconds > 0 && durationSeconds != -2
        if (durationSeconds == ScheduledChime.DURATION_VIDEO_LENGTH) {
            if (!customVideoPath.isNullOrEmpty()) {
                val durSec = getVideoDurationSeconds(customVideoPath)
                videoDismissJob = viewModelScope.launch {
                    delay((durSec + 2) * 1000L)
                    _activeBackgroundVideo.value = null
                }
            } else {
                videoDismissJob = viewModelScope.launch {
                    delay(60 * 1000L)
                    _activeBackgroundVideo.value = null
                }
            }
        } else if (durationSeconds > 0 && isScheduledChime) {
            videoDismissJob = viewModelScope.launch {
                delay(durationSeconds * 1000L)
                _activeBackgroundVideo.value = null
            }
        }
    }

    fun testPlayCustomVideo(
        video: CustomVideoItem,
        withAudio: Boolean = true,
        displayLayer: VideoDisplayLayer = try {
            VideoDisplayLayer.valueOf(preferences.value.defaultVideoDisplayLayer)
        } catch (_: Exception) {
            VideoDisplayLayer.BACKGROUND
        }
    ) {
        previewBackgroundVideo(
            videoSourceType = ChimeVideoSourceType.CUSTOM_FILE,
            customVideoPath = video.filePath,
            customVideoName = video.name,
            playVideoAudio = withAudio,
            volume = preferences.value.chimeVolume,
            durationSeconds = ScheduledChime.DURATION_VIDEO_LENGTH,
            displayLayer = displayLayer
        )
    }

    fun toggleActiveVideoDisplayLayer() {
        val current = _activeBackgroundVideo.value ?: return
        val newLayer = if (current.displayLayer == VideoDisplayLayer.BACKGROUND) {
            VideoDisplayLayer.FOREGROUND
        } else {
            VideoDisplayLayer.BACKGROUND
        }
        _activeBackgroundVideo.value = current.copy(displayLayer = newLayer)
    }

    fun setActiveVideoDisplayLayer(layer: VideoDisplayLayer) {
        val current = _activeBackgroundVideo.value ?: return
        _activeBackgroundVideo.value = current.copy(displayLayer = layer)
    }

    fun setDefaultVideoDisplayLayer(layer: VideoDisplayLayer) {
        prefsManager.updatePreferences(preferences.value.copy(defaultVideoDisplayLayer = layer.name))
    }

    fun dismissBackgroundVideo() {
        videoDismissJob?.cancel()
        _activeBackgroundVideo.value = null
        forceRestoreDeviceVolume()
    }

    fun importCustomVideo(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val item = CustomVideoFileManager.importVideoFile(getApplication(), uri)
                if (item != null) {
                    prefsManager.addCustomVideoItem(item)
                    onResult(true, "動画「${item.name}」を追加しました")
                } else {
                    onResult(false, "動画ファイルの取り込みに失敗しました")
                }
            } catch (e: Exception) {
                onResult(false, "エラーが発生しました: ${e.message}")
            }
        }
    }

    fun renameCustomVideo(id: String, newName: String) {
        prefsManager.renameCustomVideoItem(id, newName)
    }

    fun deleteCustomVideo(item: CustomVideoItem) {
        viewModelScope.launch {
            CustomVideoFileManager.deleteVideoFile(item.filePath)
            prefsManager.deleteCustomVideoItem(item.id)
            // If the deleted video is currently playing, dismiss it
            if (_activeBackgroundVideo.value?.customVideoPath == item.filePath) {
                dismissBackgroundVideo()
            }
        }
    }

    private fun queryBatteryLevel(): Int {
        return try {
            val bm = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (cap in 0..100) {
                cap
            } else {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val status = getApplication<Application>().registerReceiver(null, filter)
                val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    ((level / scale.toFloat()) * 100).toInt()
                } else {
                    92
                }
            }
        } catch (_: Exception) {
            92
        }
    }

    // --- Alarm Clock (目覚まし時計) Operations ---

    fun triggerAlarmRinging() {
        val pref = preferences.value
        _isAlarmRinging.value = true
        _isAlarmSnoozed.value = false
        _alarmSnoozeRemainingSec.value = 0

        // Play alarm synthesized audio loop
        playWithTemporaryVolume(pref.alarmVolume) {
            ChimeSynthesizer.playAlarmLoop(pref.alarmSoundType, 1.0f)
        }

        // Trigger vibration if enabled
        if (pref.alarmVibration) {
            vibratePattern(longArrayOf(0, 500, 250, 500, 250, 500))
        }

        // Trigger IR lighting if configured
        if (pref.alarmTriggerIr) {
            irRemoteManager.onAlarmTriggered()
        }
    }

    fun stopChime() {
        _isChimeRinging.value = false
        _chimeRingingInfo.value = null
        if (_playingAudioPath.value != null &&
            _playingAudioPath.value != MusicPlayerManager.playerState.value.currentTrack?.filePath
        ) {
            _playingAudioPath.value = null
        }
        ChimeSynthesizer.stopChime()
        ChimeAudioPlayer.stop()
        forceRestoreDeviceVolume()
    }

    fun stopAlarm() {
        _isAlarmRinging.value = false
        _isAlarmSnoozed.value = false
        _alarmSnoozeRemainingSec.value = 0
        _isChimeRinging.value = false
        _chimeRingingInfo.value = null
        if (_playingAudioPath.value != null &&
            _playingAudioPath.value != MusicPlayerManager.playerState.value.currentTrack?.filePath
        ) {
            _playingAudioPath.value = null
        }
        ChimeSynthesizer.stopAlarm()
        ChimeSynthesizer.stopChime()
        ChimeAudioPlayer.stop()
        forceRestoreDeviceVolume()
        cancelVibration()
    }

    fun snoozeAlarm() {
        val pref = preferences.value
        _isAlarmRinging.value = false
        _isAlarmSnoozed.value = true
        _alarmSnoozeRemainingSec.value = pref.alarmSnoozeMinutes * 60
        ChimeSynthesizer.stopAlarm()
        forceRestoreDeviceVolume()
        cancelVibration()
    }

    fun toggleAlarmEnabled() {
        val current = preferences.value.alarmEnabled
        updateAlarmPreferences(enabled = !current)
    }

    fun updateAlarmPreferences(
        enabled: Boolean = preferences.value.alarmEnabled,
        hour: Int = preferences.value.alarmHour,
        minute: Int = preferences.value.alarmMinute,
        days: Set<Int> = preferences.value.alarmDays,
        soundType: String = preferences.value.alarmSoundType,
        volume: Float = preferences.value.alarmVolume,
        snoozeMinutes: Int = preferences.value.alarmSnoozeMinutes,
        vibration: Boolean = preferences.value.alarmVibration,
        triggerIr: Boolean = preferences.value.alarmTriggerIr
    ) {
        val updated = preferences.value.copy(
            alarmEnabled = enabled,
            alarmHour = hour.coerceIn(0, 23),
            alarmMinute = minute.coerceIn(0, 59),
            alarmDays = days,
            alarmSoundType = soundType,
            alarmVolume = volume.coerceIn(0f, 1f),
            alarmSnoozeMinutes = snoozeMinutes.coerceIn(1, 30),
            alarmVibration = vibration,
            alarmTriggerIr = triggerIr
        )
        prefsManager.updatePreferences(updated)
    }

    fun testAlarmSound(soundType: String, volume: Float) {
        ChimeSynthesizer.stopAlarm()
        playWithTemporaryVolume(volume) {
            ChimeSynthesizer.playAlarmLoop(soundType, 1.0f)
        }
        viewModelScope.launch {
            delay(3000)
            if (!_isAlarmRinging.value) {
                ChimeSynthesizer.stopAlarm()
                forceRestoreDeviceVolume()
            }
        }
    }

    // --- MQ-2 Fire & Smoke Emergency Detection (火災・煙検知アラート) ---

    fun triggerFireAlert(rawVal: Int? = null, msg: String = "火事です！火災または煙を検知しました") {
        val pref = preferences.value
        if (!pref.fireAlertEnabled) return

        _isFireAlertRinging.value = true
        _fireAlertDetails.value = FireAlertInfo(
            detected = true,
            mq2RawValue = rawVal,
            message = msg,
            timestampMs = System.currentTimeMillis()
        )

        // Temporary boost device volume to maximum/high for fire emergency
        if (pref.fireAlertSoundEnabled) {
            playWithTemporaryVolume(1.0f) {
                ChimeSynthesizer.playFireSirenLoop(1.0f)
            }
        }

        // Fire alarm vibration
        if (pref.fireAlertVibration) {
            vibratePattern(longArrayOf(0, 800, 200, 800, 200, 800, 200, 800))
        }

        // Japanese Fire Emergency Voice Speech announcement: 「火事です！火事です！」
        if (pref.fireAlertVoiceTts && isTtsReady) {
            try {
                tts?.speak(
                    "火事です、火事です。火災または煙を検知しました。安全を確認してください。",
                    TextToSpeech.QUEUE_ADD,
                    null,
                    "FIRE_EMERGENCY_TTS"
                )
            } catch (_: Exception) {}
        }
    }

    fun dismissFireAlert() {
        _isFireAlertRinging.value = false
        _fireAlertDetails.value = null
        ChimeSynthesizer.stopFireSiren()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        cancelVibration()
        forceRestoreDeviceVolume()
    }

    fun testFireAlert() {
        triggerFireAlert(rawVal = 1450, msg = "🔥 [火災検知テスト] 火事です！火災警報が発報しました")
    }

    fun updateFireAlertPreferences(
        enabled: Boolean = preferences.value.fireAlertEnabled,
        soundEnabled: Boolean = preferences.value.fireAlertSoundEnabled,
        vibration: Boolean = preferences.value.fireAlertVibration,
        voiceTts: Boolean = preferences.value.fireAlertVoiceTts,
        sensitivityThreshold: Int = preferences.value.mq2SensitivityThreshold
    ) {
        prefsManager.updateFireAlertPreferences(
            enabled = enabled,
            soundEnabled = soundEnabled,
            vibration = vibration,
            voiceTts = voiceTts,
            sensitivityThreshold = sensitivityThreshold
        )
        // ESP32ハードウェアへ即座に検知閾値を送信・反映
        espSensorManager.sendFireThreshold(sensitivityThreshold)
    }

    // --- PCF8574P Physical Button Event Handler (Ultra-Low-Latency, 6-Button Setup) ---

    fun handlePhysicalButton(btnId: Int, btnName: String) {
        // High-precision sound feedback for physical press
        ChimeSynthesizer.playButtonClickFeedback(0.6f)

        // 6-Button Configuration: P0 (Alarm/Fire Stop) + 5 Standard Functions (P1-P5)
        val (actionLabel, desc) = when (btnId) {
            0 -> "🚨 アラーム・火災警報停止 / スヌーズ" to "目覚まし・タイマー・火災警報を即座に停止 (大ボタン推奨)"
            1 -> "🌙 夜間モード切替" to "常夜灯・暗色モードのON/OFF"
            2 -> "🎨 文字盤デザイン切替" to "次のクロックデザインへ循環変更"
            3 -> "⏱️ タイマー 開始/停止" to "5分デスク作業タイマーの開始・リセット"
            4 -> "💡 照明リモコン送信" to "学習済み赤外線リモコンで照明ON/OFF"
            5 -> "🔔 時報・チャイム再生" to "現在の時刻チャイム・時報を手動再生"
            else -> "ボタン P$btnId" to "PCF8574P 物理キー押下"
        }

        // Update HUD popup state
        val event = PhysicalButtonEvent(
            buttonId = btnId,
            buttonName = btnName,
            actionLabel = actionLabel,
            description = desc
        )
        _lastPhysicalButtonEvent.value = event
        buttonClearJob?.cancel()
        buttonClearJob = viewModelScope.launch {
            delay(3500)
            _lastPhysicalButtonEvent.value = null
        }

        // Execute corresponding action with zero latency
        when (btnId) {
            0 -> {
                // P0: ALARM_STOP / FIRE_ALERT_STOP / CHIME_STOP
                if (_isFireAlertRinging.value) {
                    dismissFireAlert()
                } else if (_isAlarmRinging.value) {
                    stopAlarm()
                } else if (_isChimeRinging.value || ChimeSynthesizer.isChimePlaying() || ChimeAudioPlayer.isPlaying()) {
                    stopAlarm()
                } else if (_timerState.value.isFinished || _timerState.value.isRunning) {
                    resetTimer()
                } else if (_activeBackgroundVideo.value != null) {
                    dismissBackgroundVideo()
                } else if (ChimeSynthesizer.isAlarmPlaying()) {
                    ChimeSynthesizer.stopAlarm()
                } else {
                    // Normal idle press -> gentle chime ping feedback
                    ChimeSynthesizer.playSinglePing(0.6f)
                }
            }
            1 -> {
                // P1: NIGHT_MODE
                toggleNightMode()
            }
            2 -> {
                // P2: NEXT_FACE
                cycleClockFace()
            }
            3 -> {
                // P3: TIMER_START_STOP
                if (_timerState.value.isRunning) {
                    toggleTimerPause()
                } else if (_timerState.value.isFinished) {
                    resetTimer()
                } else {
                    setTimerSeconds(300) // 5-minute standard desk focus timer
                }
            }
            4 -> {
                // P4: LIGHT_TOGGLE
                irRemoteManager.triggerLightToggle()
            }
            5 -> {
                // P5: TIME_CHIME
                triggerManualChime()
            }
        }
    }

    private fun cycleClockFace() {
        val faces = com.example.model.ClockFace.values()
        val curIndex = faces.indexOf(preferences.value.clockFace)
        val nextFace = faces[(curIndex + 1) % faces.size]
        prefsManager.updateClockFace(nextFace)
    }

    private fun triggerManualChime() {
        val pref = preferences.value
        _isChimeRinging.value = true
        _chimeRingingInfo.value = "時報チャイム"
        playWithTemporaryVolume(pref.chimeVolume) { onDone ->
            ChimeSynthesizer.playChime(pref.chimeSound, 1.0f, onComplete = {
                _isChimeRinging.value = false
                _chimeRingingInfo.value = null
                onDone()
            })
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun cancelVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}

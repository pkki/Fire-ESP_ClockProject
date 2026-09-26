package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
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
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.model.EewLiveState
import com.example.model.EewTestScenario
import com.example.model.EspSensorData
import com.example.model.FireAlertInfo
import com.example.model.PhysicalButtonEvent
import com.example.model.ScheduledChime
import com.example.model.WeatherState
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

data class DeskTimerState(
    val remainingSeconds: Int = 0,
    val initialSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false
)

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

    // --- Alarm Clock (目覚まし時計) State ---
    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

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

    // --- ESP32-C3 Smart IR Remote (学習・送受信・家電制御) ---
    private val irRemoteManager = IrRemoteManager(
        application,
        espSensorManager.bleSensorManager,
        espSensorManager.usbSensorManager
    )
    val irButtons: StateFlow<List<IrRemoteButton>> = irRemoteManager.buttons
    val irLearnState: StateFlow<IrLearnState> = irRemoteManager.learnState

    init {
        startTimeTicker()
        startTimerTicker()
        startWeatherTicker()

        // Sync EEW preferences and start listener
        eewManager.updatePreferences(preferences.value)
        eewManager.start()

        // Sync ESP sensor preferences and start connection
        val espPref = preferences.value
        espSensorManager.updateConfig(
            enabled = espPref.espSensorEnabled,
            mode = espPref.espConnectionMode,
            targetBleName = espPref.espBleDeviceName,
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
     * WebダッシュボードからアップロードされたAPKファイルをインストール
     */
    fun installUploadedApk(fileName: String, apkBytes: ByteArray): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            val apkDir = File(app.cacheDir, "apk_updates")
            if (!apkDir.exists()) apkDir.mkdirs()
            val safeName = fileName.ifBlank { "DeskClock_update.apk" }
            val apkFile = File(apkDir, safeName)
            apkFile.writeBytes(apkBytes)

            val pm = app.packageManager
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val infoStr = if (archiveInfo != null) {
                "バージョン: ${archiveInfo.versionName ?: "最新"} (コード: ${archiveInfo.versionCode})"
            } else {
                "APK解析完了"
            }

            val apkUri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            Pair(true, "APKを端末へ転送しました ($infoStr)。端末画面でインストール確認ダイアログを開きました。")
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
     * 端末内ストレージのURIからAPKを読み込んでインストール
     */
    fun installApkFromUri(uri: Uri): Pair<Boolean, String> {
        return try {
            val app = getApplication<Application>()
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Pair(false, "ファイルを開けませんでした")
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "DeskClock_local.apk"
            installUploadedApk(fileName, bytes)
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
                        onPreviewVideo = { type, path, name, dur ->
                            previewBackgroundVideo(
                                videoSourceType = type,
                                customVideoPath = path,
                                customVideoName = name,
                                playVideoAudio = true,
                                durationSeconds = dur
                            )
                        },
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
                        onSetMusicVolume = { vol -> setMusicPlayerVolume(vol) }
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
                                        val chimeVol = if (chime.volume > 0f) chime.volume else currentPrefs.chimeVolume
                                        if (chime.isVideoOnlyAudio && chime.videoSourceType == ChimeVideoSourceType.NONE) {
                                            playWithTemporaryVolume(chimeVol) { onDone ->
                                                ChimeSynthesizer.playChime(chime.builtInSound, 1.0f, onComplete = onDone)
                                            }
                                        } else {
                                            playWithTemporaryVolume(chimeVol) { onDone ->
                                                ChimeAudioPlayer.playScheduledChime(getApplication(), chime, onComplete = onDone)
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
                                    if (currentPrefs.hourlyChimeSourceType == com.example.model.ChimeAudioSourceType.CUSTOM_FILE &&
                                        !currentPrefs.hourlyCustomAudioPath.isNullOrEmpty()
                                    ) {
                                        playWithTemporaryVolume(currentPrefs.chimeVolume) { onDone ->
                                            com.example.audio.ChimeAudioPlayer.playCustomFile(
                                                currentPrefs.hourlyCustomAudioPath,
                                                1.0f,
                                                onComplete = onDone
                                            )
                                        }
                                    } else {
                                        playWithTemporaryVolume(currentPrefs.chimeVolume) { onDone ->
                                            ChimeSynthesizer.playChime(currentPrefs.chimeSound, 1.0f, onComplete = onDone)
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
                                    val halfVol = (currentPrefs.chimeVolume * 0.7f).coerceIn(0.05f, 1.0f)
                                    playWithTemporaryVolume(halfVol) { onDone ->
                                        ChimeSynthesizer.playSinglePing(1.0f, onComplete = onDone)
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
                        _timerState.value = current.copy(
                            remainingSeconds = 0,
                            isRunning = false,
                            isFinished = true
                        )
                        // Play alert sound for timer finished
                        ChimeSynthesizer.playChime(ChimeSound.CRYSTAL_BELL, preferences.value.chimeVolume)
                    } else {
                        _timerState.value = current.copy(remainingSeconds = next)
                    }
                }
            }
        }
    }

    // Timer controls
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

    // Preference mutations
    fun selectClockFace(face: ClockFace) = prefsManager.updateClockFace(face)
    fun selectColorPalette(palette: ColorPalette) = prefsManager.updateColorPalette(palette)
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
        val volume = preferences.value.musicPlayerVolume
        val repeatMode = try {
            MusicRepeatMode.valueOf(preferences.value.musicPlayerRepeatMode)
        } catch (_: Exception) {
            MusicRepeatMode.ALL
        }
        MusicPlayerManager.playTrack(track, playlist, volume, repeatMode)
    }

    fun toggleMusicPlayPause() {
        MusicPlayerManager.togglePlayPause()
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

    // --- Background Video Control & Management ---

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
            durationSeconds = chime.videoDurationSeconds
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
        volume: Float = preferences.value.chimeVolume,
        durationSeconds: Int = ScheduledChime.DURATION_VIDEO_LENGTH
    ) {
        videoDismissJob?.cancel()
        if (playVideoAudio) {
            applyTemporaryPlaybackVolume(volume)
        }
        _activeBackgroundVideo.value = ActiveBackgroundVideo(
            chimeId = null,
            chimeLabel = "プレビュー",
            videoSourceType = videoSourceType,
            customVideoPath = customVideoPath,
            customVideoName = customVideoName,
            playVideoAudio = playVideoAudio,
            volume = volume,
            startTimeMs = System.currentTimeMillis(),
            durationSeconds = durationSeconds
        )

        if (durationSeconds == ScheduledChime.DURATION_VIDEO_LENGTH) {
            if (!customVideoPath.isNullOrEmpty()) {
                val durSec = getVideoDurationSeconds(customVideoPath)
                videoDismissJob = viewModelScope.launch {
                    delay((durSec + 2) * 1000L)
                    _activeBackgroundVideo.value = null
                    if (playVideoAudio) forceRestoreDeviceVolume()
                }
            } else {
                videoDismissJob = viewModelScope.launch {
                    delay(30 * 1000L)
                    _activeBackgroundVideo.value = null
                    if (playVideoAudio) forceRestoreDeviceVolume()
                }
            }
        } else if (durationSeconds > 0) {
            videoDismissJob = viewModelScope.launch {
                delay(durationSeconds * 1000L)
                _activeBackgroundVideo.value = null
                if (playVideoAudio) forceRestoreDeviceVolume()
            }
        }
    }

    fun testPlayCustomVideo(video: CustomVideoItem, withAudio: Boolean = true) {
        previewBackgroundVideo(
            videoSourceType = ChimeVideoSourceType.CUSTOM_FILE,
            customVideoPath = video.filePath,
            customVideoName = video.name,
            playVideoAudio = withAudio,
            volume = preferences.value.chimeVolume,
            durationSeconds = ScheduledChime.DURATION_VIDEO_LENGTH
        )
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

    fun stopAlarm() {
        _isAlarmRinging.value = false
        _isAlarmSnoozed.value = false
        _alarmSnoozeRemainingSec.value = 0
        ChimeSynthesizer.stopAlarm()
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
                // P0: ALARM_STOP / FIRE_ALERT_STOP
                if (_isFireAlertRinging.value) {
                    dismissFireAlert()
                } else if (_isAlarmRinging.value) {
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
        playWithTemporaryVolume(pref.chimeVolume) { onDone ->
            ChimeSynthesizer.playChime(pref.chimeSound, 1.0f, onComplete = onDone)
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

package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChimeAudioPlayer
import com.example.audio.ChimeSound
import com.example.audio.ChimeSynthesizer
import com.example.audio.CustomAudioFileManager
import com.example.audio.CustomVideoFileManager
import com.example.data.ClockPreferencesManager
import com.example.data.DeviceLocationHelper
import com.example.data.JapanMunicipalities
import com.example.data.MunicipalityItem
import com.example.data.WeatherRepository
import com.example.model.ActiveBackgroundVideo
import com.example.model.ChimeVideoSourceType
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.model.ScheduledChime
import com.example.model.WeatherState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val timezoneDisplayName: String = "Japan Standard Time (JST)",
    val burnInShiftX: Float = 0f,
    val burnInShiftY: Float = 0f
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

    val scheduledChimes: StateFlow<List<ScheduledChime>> = prefsManager.scheduledChimes
    val customAudioList: StateFlow<List<CustomAudioItem>> = prefsManager.customAudioList
    val customVideoList: StateFlow<List<CustomVideoItem>> = prefsManager.customVideoList

    private val _activeBackgroundVideo = MutableStateFlow<ActiveBackgroundVideo?>(null)
    val activeBackgroundVideo: StateFlow<ActiveBackgroundVideo?> = _activeBackgroundVideo.asStateFlow()

    private var videoDismissJob: Job? = null

    // Flag to ensure chime only triggers once per target minute
    private var lastChimeTriggerMinute = -1
    private var lastChimeTriggerHour = -1
    private val triggeredScheduledChimesSet = mutableSetOf<String>()

    init {
        startTimeTicker()
        startTimerTicker()
        startWeatherTicker()
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
            val dayOfWeekFormatEn = SimpleDateFormat("EEEE", Locale.ENGLISH)
            val dayOfWeekFormatJa = SimpleDateFormat("E曜日", Locale.JAPANESE)

            var driftCycle = 0

            while (isActive) {
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                val hour24 = cal.get(Calendar.HOUR_OF_DAY)
                val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                val isPm = cal.get(Calendar.AM_PM) == Calendar.PM
                val minute = cal.get(Calendar.MINUTE)
                val second = cal.get(Calendar.SECOND)
                val millisecond = cal.get(Calendar.MILLISECOND)
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)

                // Check hourly chime
                val currentPrefs = preferences.value
                if (second == 0 && minute == 0 && (hour24 != lastChimeTriggerHour || minute != lastChimeTriggerMinute)) {
                    lastChimeTriggerHour = hour24
                    lastChimeTriggerMinute = minute
                    if (currentPrefs.hourlyChimeEnabled && !currentPrefs.isNightMode) {
                        val inWindow = if (currentPrefs.chimeStartHour <= currentPrefs.chimeEndHour) {
                            hour24 in currentPrefs.chimeStartHour..currentPrefs.chimeEndHour
                        } else {
                            hour24 >= currentPrefs.chimeStartHour || hour24 <= currentPrefs.chimeEndHour
                        }
                        if (inWindow) {
                            ChimeSynthesizer.playChime(currentPrefs.chimeSound, currentPrefs.chimeVolume)
                        }
                    }
                } else if (second == 0 && minute == 30 && minute != lastChimeTriggerMinute) {
                    lastChimeTriggerMinute = minute
                    if (currentPrefs.halfHourlyChimeEnabled && !currentPrefs.isNightMode) {
                        val inWindow = if (currentPrefs.chimeStartHour <= currentPrefs.chimeEndHour) {
                            hour24 in currentPrefs.chimeStartHour..currentPrefs.chimeEndHour
                        } else {
                            hour24 >= currentPrefs.chimeStartHour || hour24 <= currentPrefs.chimeEndHour
                        }
                        if (inWindow) {
                            ChimeSynthesizer.playSinglePing(currentPrefs.chimeVolume * 0.7f)
                        }
                    }
                }

                // Check user-configured scheduled chimes (alarms/chimes)
                if (second == 0 && !currentPrefs.isNightMode) {
                    val calDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    // ISO 1..7 (Monday=1 .. Sunday=7)
                    val isoDayOfWeek = if (calDayOfWeek == Calendar.SUNDAY) 7 else calDayOfWeek - 1
                    val currentChimes = scheduledChimes.value
                    for (chime in currentChimes) {
                        if (chime.isEnabled && chime.hour == hour24 && chime.minute == minute) {
                            if (chime.daysOfWeek.isEmpty() || chime.daysOfWeek.contains(isoDayOfWeek)) {
                                val chimeKey = "${chime.id}_${hour24}_${minute}"
                                if (!triggeredScheduledChimesSet.contains(chimeKey)) {
                                    triggeredScheduledChimesSet.add(chimeKey)
                                    ChimeAudioPlayer.playScheduledChime(getApplication(), chime)
                                    if (chime.videoSourceType != ChimeVideoSourceType.NONE) {
                                        triggerBackgroundVideo(chime)
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

                // Burn-in prevention drift: subtle micro shifts every 3 minutes
                val shiftX: Float
                val shiftY: Float
                if (currentPrefs.burnInProtection) {
                    val intervalSlot = (minute / 3) % 4
                    shiftX = when (intervalSlot) {
                        0 -> 0f
                        1 -> 3f
                        2 -> -2f
                        else -> 1.5f
                    }
                    shiftY = when (intervalSlot) {
                        0 -> 0f
                        1 -> -2f
                        2 -> 3f
                        else -> -1.5f
                    }
                } else {
                    shiftX = 0f
                    shiftY = 0f
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
                    dayOfWeekEn = dayOfWeekFormatEn.format(cal.time).uppercase(),
                    dayOfWeekJa = dayOfWeekFormatJa.format(cal.time),
                    formattedDateFullEn = fullDateFormatEn.format(cal.time),
                    formattedDateFullJa = fullDateFormatJa.format(cal.time),
                    timezoneDisplayName = tzName,
                    burnInShiftX = shiftX,
                    burnInShiftY = shiftY
                )

                // Sleep until next frame (~50ms for smooth second hand or 200ms)
                delay(50)
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
    fun setChimeHours(start: Int, end: Int) = prefsManager.updateChimeHours(start, end)
    fun setChimeVolume(volume: Float) = prefsManager.updateChimeVolume(volume)
    fun toggleKioskLock() = prefsManager.toggleKioskLock()
    fun toggleNightMode() = prefsManager.toggleNightMode()
    fun setLocationName(name: String) = prefsManager.updateLocationName(name)

    fun refreshWeather() {
        viewModelScope.launch {
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
        }
    }

    // Test chime sound immediately
    fun testCurrentChime() {
        ChimeSynthesizer.playChime(preferences.value.chimeSound, preferences.value.chimeVolume)
    }

    fun testChimeSound(sound: ChimeSound) {
        ChimeSynthesizer.playChime(sound, preferences.value.chimeVolume)
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

    fun deleteCustomAudio(item: CustomAudioItem) {
        viewModelScope.launch {
            CustomAudioFileManager.deleteAudioFile(item.filePath)
            prefsManager.deleteCustomAudioItem(item.id)
        }
    }

    fun testPlayScheduledChime(chime: ScheduledChime) {
        ChimeAudioPlayer.playScheduledChime(getApplication(), chime)
        if (chime.videoSourceType != ChimeVideoSourceType.NONE) {
            triggerBackgroundVideo(chime)
        }
    }

    fun testPlayCustomAudio(filePath: String, volume: Float = 0.85f) {
        ChimeAudioPlayer.playCustomFile(filePath, volume)
    }

    fun stopAudioPlayback() {
        ChimeAudioPlayer.stop()
    }

    // --- Background Video Control & Management ---

    fun triggerBackgroundVideo(chime: ScheduledChime) {
        videoDismissJob?.cancel()
        _activeBackgroundVideo.value = ActiveBackgroundVideo(
            chimeId = chime.id,
            chimeLabel = chime.label,
            videoSourceType = chime.videoSourceType,
            customVideoPath = chime.customVideoPath,
            customVideoName = chime.customVideoName,
            playVideoAudio = chime.playVideoAudio,
            startTimeMs = System.currentTimeMillis(),
            durationSeconds = chime.videoDurationSeconds
        )

        // Auto dismiss after specified seconds if duration > 0
        if (chime.videoDurationSeconds > 0) {
            videoDismissJob = viewModelScope.launch {
                delay(chime.videoDurationSeconds * 1000L)
                _activeBackgroundVideo.value = null
            }
        }
    }

    fun previewBackgroundVideo(
        videoSourceType: ChimeVideoSourceType,
        customVideoPath: String? = null,
        customVideoName: String? = null,
        playVideoAudio: Boolean = false,
        durationSeconds: Int = 30
    ) {
        videoDismissJob?.cancel()
        _activeBackgroundVideo.value = ActiveBackgroundVideo(
            chimeId = null,
            chimeLabel = "プレビュー",
            videoSourceType = videoSourceType,
            customVideoPath = customVideoPath,
            customVideoName = customVideoName,
            playVideoAudio = playVideoAudio,
            startTimeMs = System.currentTimeMillis(),
            durationSeconds = durationSeconds
        )

        if (durationSeconds > 0) {
            videoDismissJob = viewModelScope.launch {
                delay(durationSeconds * 1000L)
                _activeBackgroundVideo.value = null
            }
        }
    }

    fun dismissBackgroundVideo() {
        videoDismissJob?.cancel()
        _activeBackgroundVideo.value = null
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
}

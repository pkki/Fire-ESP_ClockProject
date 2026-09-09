package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChimeSound
import com.example.audio.ChimeSynthesizer
import com.example.data.ClockPreferencesManager
import com.example.data.WeatherRepository
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.WeatherState
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

    private val _timeState = MutableStateFlow(CurrentTimeState())
    val timeState: StateFlow<CurrentTimeState> = _timeState.asStateFlow()

    private val _timerState = MutableStateFlow(DeskTimerState())
    val timerState: StateFlow<DeskTimerState> = _timerState.asStateFlow()

    // Flag to ensure chime only triggers once per target minute
    private var lastChimeTriggerMinute = -1
    private var lastChimeTriggerHour = -1

    init {
        startTimeTicker()
        startTimerTicker()
        startWeatherTicker()
    }

    private fun startWeatherTicker() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val weather = weatherRepo.fetchWeather("Tokyo")
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

                // Reset minute latch when second moves on
                if (second > 2) {
                    lastChimeTriggerMinute = -1
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
                val weather = weatherRepo.fetchWeather("Tokyo")
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
}

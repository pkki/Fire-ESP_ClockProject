package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.audio.ChimeSound
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClockPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadPreferences())
    val state: StateFlow<ClockPreferencesState> = _state.asStateFlow()

    private fun loadPreferences(): ClockPreferencesState {
        val faceName = prefs.getString("clock_face", ClockFace.SEVEN_SEGMENT.name)
        val paletteName = prefs.getString("color_palette", ColorPalette.ICE_WHITE.name)
        val soundName = prefs.getString("chime_sound", ChimeSound.WESTMINSTER.name)

        return ClockPreferencesState(
            clockFace = try { ClockFace.valueOf(faceName ?: "") } catch (_: Exception) { ClockFace.SEVEN_SEGMENT },
            colorPalette = try { ColorPalette.valueOf(paletteName ?: "") } catch (_: Exception) { ColorPalette.ICE_WHITE },
            is24Hour = prefs.getBoolean("is_24_hour", true),
            showSeconds = prefs.getBoolean("show_seconds", true),
            showWeather = prefs.getBoolean("show_weather", true),
            hourlyChimeEnabled = prefs.getBoolean("hourly_chime", true),
            halfHourlyChimeEnabled = prefs.getBoolean("half_hourly_chime", false),
            chimeSound = try { ChimeSound.valueOf(soundName ?: "") } catch (_: Exception) { ChimeSound.WESTMINSTER },
            chimeStartHour = prefs.getInt("chime_start_hour", 8),
            chimeEndHour = prefs.getInt("chime_end_hour", 22),
            chimeVolume = prefs.getFloat("chime_volume", 0.75f),
            isKioskLocked = prefs.getBoolean("kiosk_locked", true),
            isNightMode = prefs.getBoolean("night_mode", false),
            burnInProtection = prefs.getBoolean("burn_in_protection", true),
            customLocationName = prefs.getString("location_name", "Japan Standard Time (JST)") ?: "Japan Standard Time (JST)"
        )
    }

    fun updateClockFace(face: ClockFace) {
        prefs.edit().putString("clock_face", face.name).apply()
        _state.value = _state.value.copy(clockFace = face)
    }

    fun updateColorPalette(palette: ColorPalette) {
        prefs.edit().putString("color_palette", palette.name).apply()
        _state.value = _state.value.copy(colorPalette = palette)
    }

    fun toggle24Hour() {
        val newValue = !_state.value.is24Hour
        prefs.edit().putBoolean("is_24_hour", newValue).apply()
        _state.value = _state.value.copy(is24Hour = newValue)
    }

    fun toggleShowSeconds() {
        val newValue = !_state.value.showSeconds
        prefs.edit().putBoolean("show_seconds", newValue).apply()
        _state.value = _state.value.copy(showSeconds = newValue)
    }

    fun toggleShowWeather() {
        val newValue = !_state.value.showWeather
        prefs.edit().putBoolean("show_weather", newValue).apply()
        _state.value = _state.value.copy(showWeather = newValue)
    }

    fun updateHourlyChime(enabled: Boolean) {
        prefs.edit().putBoolean("hourly_chime", enabled).apply()
        _state.value = _state.value.copy(hourlyChimeEnabled = enabled)
    }

    fun updateHalfHourlyChime(enabled: Boolean) {
        prefs.edit().putBoolean("half_hourly_chime", enabled).apply()
        _state.value = _state.value.copy(halfHourlyChimeEnabled = enabled)
    }

    fun updateChimeSound(sound: ChimeSound) {
        prefs.edit().putString("chime_sound", sound.name).apply()
        _state.value = _state.value.copy(chimeSound = sound)
    }

    fun updateChimeHours(start: Int, end: Int) {
        prefs.edit().putInt("chime_start_hour", start).putInt("chime_end_hour", end).apply()
        _state.value = _state.value.copy(chimeStartHour = start, chimeEndHour = end)
    }

    fun updateChimeVolume(volume: Float) {
        prefs.edit().putFloat("chime_volume", volume).apply()
        _state.value = _state.value.copy(chimeVolume = volume)
    }

    fun toggleKioskLock() {
        val newValue = !_state.value.isKioskLocked
        prefs.edit().putBoolean("kiosk_locked", newValue).apply()
        _state.value = _state.value.copy(isKioskLocked = newValue)
    }

    fun toggleNightMode() {
        val newValue = !_state.value.isNightMode
        prefs.edit().putBoolean("night_mode", newValue).apply()
        _state.value = _state.value.copy(isNightMode = newValue)
    }

    fun updateLocationName(name: String) {
        prefs.edit().putString("location_name", name).apply()
        _state.value = _state.value.copy(customLocationName = name)
    }
}

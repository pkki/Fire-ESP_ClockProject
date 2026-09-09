package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.audio.ChimeSound
import com.example.model.ChimeAudioSourceType
import com.example.model.ChimeVideoSourceType
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.model.ScheduledChime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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
            showWarnings = prefs.getBoolean("show_warnings", true),
            selectedPrefecture = prefs.getString("selected_prefecture", "東京都") ?: "東京都",
            selectedCityName = prefs.getString("selected_city_name", "東京都") ?: "東京都",
            customLatitude = if (prefs.contains("custom_lat")) prefs.getFloat("custom_lat", 0f).toDouble() else null,
            customLongitude = if (prefs.contains("custom_lon")) prefs.getFloat("custom_lon", 0f).toDouble() else null,
            isAutoLocationEnabled = prefs.getBoolean("auto_location", false),
            demoWarningsPreview = prefs.getBoolean("demo_warnings_preview", false),
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

    fun toggleShowWarnings() {
        val newValue = !_state.value.showWarnings
        prefs.edit().putBoolean("show_warnings", newValue).apply()
        _state.value = _state.value.copy(showWarnings = newValue)
    }

    fun updatePrefecture(prefecture: String) {
        prefs.edit().putString("selected_prefecture", prefecture)
            .putString("selected_city_name", prefecture)
            .remove("custom_lat")
            .remove("custom_lon")
            .putBoolean("auto_location", false)
            .apply()
        _state.value = _state.value.copy(
            selectedPrefecture = prefecture,
            selectedCityName = prefecture,
            customLatitude = null,
            customLongitude = null,
            isAutoLocationEnabled = false
        )
    }

    fun updateMunicipality(cityName: String, prefecture: String, lat: Double, lon: Double, isAuto: Boolean = false) {
        prefs.edit().putString("selected_prefecture", prefecture)
            .putString("selected_city_name", cityName)
            .putFloat("custom_lat", lat.toFloat())
            .putFloat("custom_lon", lon.toFloat())
            .putBoolean("auto_location", isAuto)
            .apply()
        _state.value = _state.value.copy(
            selectedPrefecture = prefecture,
            selectedCityName = cityName,
            customLatitude = lat,
            customLongitude = lon,
            isAutoLocationEnabled = isAuto
        )
    }

    fun toggleDemoWarnings() {
        val newValue = !_state.value.demoWarningsPreview
        prefs.edit().putBoolean("demo_warnings_preview", newValue).apply()
        _state.value = _state.value.copy(demoWarningsPreview = newValue)
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

    // --- Scheduled Chimes & Custom Audio Persistence ---

    private val _scheduledChimes = MutableStateFlow(loadScheduledChimes())
    val scheduledChimes: StateFlow<List<ScheduledChime>> = _scheduledChimes.asStateFlow()

    private val _customAudioList = MutableStateFlow(loadCustomAudioList())
    val customAudioList: StateFlow<List<CustomAudioItem>> = _customAudioList.asStateFlow()

    private fun loadScheduledChimes(): List<ScheduledChime> {
        val jsonStr = prefs.getString("scheduled_chimes_json", null)
        if (jsonStr.isNullOrEmpty()) {
            // Provide sensible defaults
            val defaults = listOf(
                ScheduledChime(
                    hour = 8,
                    minute = 30,
                    label = "朝のチャイム",
                    daysOfWeek = setOf(1, 2, 3, 4, 5),
                    sourceType = ChimeAudioSourceType.BUILT_IN,
                    builtInSound = ChimeSound.WESTMINSTER
                ),
                ScheduledChime(
                    hour = 12,
                    minute = 0,
                    label = "お昼休み",
                    daysOfWeek = setOf(1, 2, 3, 4, 5),
                    sourceType = ChimeAudioSourceType.BUILT_IN,
                    builtInSound = ChimeSound.SOFT_MARIMBA
                ),
                ScheduledChime(
                    hour = 17,
                    minute = 0,
                    label = "終業・夕方のチャイム",
                    daysOfWeek = setOf(1, 2, 3, 4, 5),
                    sourceType = ChimeAudioSourceType.BUILT_IN,
                    builtInSound = ChimeSound.ZEN_BELL
                )
            )
            saveScheduledChimesToPrefs(defaults)
            return defaults
        }

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ScheduledChime>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val daysArray = obj.optJSONArray("daysOfWeek")
                val daysSet = mutableSetOf<Int>()
                if (daysArray != null) {
                    for (d in 0 until daysArray.length()) {
                        daysSet.add(daysArray.getInt(d))
                    }
                } else {
                    daysSet.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
                }

                val sourceTypeStr = obj.optString("sourceType", ChimeAudioSourceType.BUILT_IN.name)
                val soundName = obj.optString("builtInSound", ChimeSound.WESTMINSTER.name)
                val videoTypeStr = obj.optString("videoSourceType", ChimeVideoSourceType.NONE.name)

                list.add(
                    ScheduledChime(
                        id = obj.optString("id"),
                        hour = obj.optInt("hour", 8),
                        minute = obj.optInt("minute", 0),
                        label = obj.optString("label", "チャイム"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        daysOfWeek = daysSet,
                        sourceType = try { ChimeAudioSourceType.valueOf(sourceTypeStr) } catch (_: Exception) { ChimeAudioSourceType.BUILT_IN },
                        builtInSound = try { ChimeSound.valueOf(soundName) } catch (_: Exception) { ChimeSound.WESTMINSTER },
                        customAudioId = obj.optString("customAudioId").ifEmpty { null },
                        customAudioName = obj.optString("customAudioName").ifEmpty { null },
                        customAudioPath = obj.optString("customAudioPath").ifEmpty { null },
                        volume = obj.optDouble("volume", 0.85).toFloat(),
                        videoSourceType = try { ChimeVideoSourceType.valueOf(videoTypeStr) } catch (_: Exception) { ChimeVideoSourceType.NONE },
                        customVideoId = obj.optString("customVideoId").ifEmpty { null },
                        customVideoName = obj.optString("customVideoName").ifEmpty { null },
                        customVideoPath = obj.optString("customVideoPath").ifEmpty { null },
                        videoDurationSeconds = obj.optInt("videoDurationSeconds", 60),
                        playVideoAudio = obj.optBoolean("playVideoAudio", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveScheduledChimesToPrefs(list: List<ScheduledChime>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("hour", item.hour)
                put("minute", item.minute)
                put("label", item.label)
                put("isEnabled", item.isEnabled)
                put("sourceType", item.sourceType.name)
                put("builtInSound", item.builtInSound.name)
                put("customAudioId", item.customAudioId ?: "")
                put("customAudioName", item.customAudioName ?: "")
                put("customAudioPath", item.customAudioPath ?: "")
                put("volume", item.volume.toDouble())
                put("videoSourceType", item.videoSourceType.name)
                put("customVideoId", item.customVideoId ?: "")
                put("customVideoName", item.customVideoName ?: "")
                put("customVideoPath", item.customVideoPath ?: "")
                put("videoDurationSeconds", item.videoDurationSeconds)
                put("playVideoAudio", item.playVideoAudio)

                val daysArray = JSONArray()
                item.daysOfWeek.forEach { daysArray.put(it) }
                put("daysOfWeek", daysArray)
            }
            array.put(obj)
        }
        prefs.edit().putString("scheduled_chimes_json", array.toString()).apply()
    }

    fun saveOrUpdateChime(chime: ScheduledChime) {
        val current = _scheduledChimes.value.toMutableList()
        val index = current.indexOfFirst { it.id == chime.id }
        if (index != -1) {
            current[index] = chime
        } else {
            current.add(chime)
        }
        current.sortBy { it.hour * 60 + it.minute }
        saveScheduledChimesToPrefs(current)
        _scheduledChimes.value = current
    }

    fun deleteChime(chimeId: String) {
        val current = _scheduledChimes.value.filter { it.id != chimeId }
        saveScheduledChimesToPrefs(current)
        _scheduledChimes.value = current
    }

    fun toggleChimeEnabled(chimeId: String) {
        val current = _scheduledChimes.value.map {
            if (it.id == chimeId) it.copy(isEnabled = !it.isEnabled) else it
        }
        saveScheduledChimesToPrefs(current)
        _scheduledChimes.value = current
    }

    private fun loadCustomAudioList(): List<CustomAudioItem> {
        val jsonStr = prefs.getString("custom_audio_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CustomAudioItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomAudioItem(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        filePath = obj.optString("filePath"),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomAudioListToPrefs(list: List<CustomAudioItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("filePath", item.filePath)
                put("dateAdded", item.dateAdded)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_audio_json", array.toString()).apply()
    }

    fun addCustomAudioItem(item: CustomAudioItem) {
        val current = _customAudioList.value.filter { it.id != item.id }.toMutableList()
        current.add(0, item)
        saveCustomAudioListToPrefs(current)
        _customAudioList.value = current
    }

    fun deleteCustomAudioItem(id: String) {
        val current = _customAudioList.value.filter { it.id != id }
        saveCustomAudioListToPrefs(current)
        _customAudioList.value = current
    }

    // --- Custom Video Management ---

    private val _customVideoList = MutableStateFlow(loadCustomVideoList())
    val customVideoList: StateFlow<List<CustomVideoItem>> = _customVideoList.asStateFlow()

    private fun loadCustomVideoList(): List<CustomVideoItem> {
        val jsonStr = prefs.getString("custom_video_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CustomVideoItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomVideoItem(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        filePath = obj.optString("filePath"),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomVideoListToPrefs(list: List<CustomVideoItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("filePath", item.filePath)
                put("dateAdded", item.dateAdded)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_video_json", array.toString()).apply()
    }

    fun addCustomVideoItem(item: CustomVideoItem) {
        val current = _customVideoList.value.filter { it.id != item.id }.toMutableList()
        current.add(0, item)
        saveCustomVideoListToPrefs(current)
        _customVideoList.value = current
    }

    fun deleteCustomVideoItem(id: String) {
        val current = _customVideoList.value.filter { it.id != id }
        saveCustomVideoListToPrefs(current)
        _customVideoList.value = current
    }
}

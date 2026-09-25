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
        val hourlySourceTypeStr = prefs.getString("hourly_chime_source_type", ChimeAudioSourceType.BUILT_IN.name)

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
            hourlyChimeSourceType = try { ChimeAudioSourceType.valueOf(hourlySourceTypeStr ?: "") } catch (_: Exception) { ChimeAudioSourceType.BUILT_IN },
            hourlyCustomAudioId = prefs.getString("hourly_custom_audio_id", null),
            hourlyCustomAudioName = prefs.getString("hourly_custom_audio_name", null),
            hourlyCustomAudioPath = prefs.getString("hourly_custom_audio_path", null),
            chimeStartHour = prefs.getInt("chime_start_hour", 8),
            chimeEndHour = prefs.getInt("chime_end_hour", 22),
            chimeVolume = prefs.getFloat("chime_volume", 0.75f),
            isKioskLocked = prefs.getBoolean("kiosk_locked", true),
            isNightMode = prefs.getBoolean("night_mode", false),
            burnInProtection = prefs.getBoolean("burn_in_protection", true),
            customLocationName = prefs.getString("location_name", "Japan Standard Time (JST)") ?: "Japan Standard Time (JST)",
            eewEnabled = prefs.getBoolean("eew_enabled", true),
            eewMinScale = prefs.getInt("eew_min_scale", 45),
            eewSoundEnabled = prefs.getBoolean("eew_sound_enabled", true),
            eewVibrationEnabled = prefs.getBoolean("eew_vibration_enabled", true),
            eewSoundMode = prefs.getString("eew_sound_mode", "SYNTH_BEEP") ?: "SYNTH_BEEP",
            eewLightweightMap = prefs.getBoolean("eew_lightweight_map", true),
            espSensorEnabled = prefs.getBoolean("esp_sensor_enabled", true),
            espConnectionMode = prefs.getString("esp_connection_mode", "BLE") ?: "BLE",
            espBleDeviceName = prefs.getString("esp_ble_device_name", "ESP32C3-Sensor") ?: "ESP32C3-Sensor",
            espBaudRate = prefs.getInt("esp_baud_rate", 115200),
            espSensorHost = prefs.getString("esp_sensor_host", "192.168.1.100") ?: "192.168.1.100",
            espSensorPort = prefs.getInt("esp_sensor_port", 80),
            espSensorIntervalSeconds = prefs.getInt("esp_sensor_interval", 5),
            espTempOffset = prefs.getFloat("esp_temp_offset", 0.0f),
            espHumOffset = prefs.getFloat("esp_hum_offset", 0.0f),
            espPressOffset = prefs.getFloat("esp_press_offset", 0.0f),
            showEspSensorOnClock = prefs.getBoolean("show_esp_sensor_on_clock", true),
            alarmEnabled = prefs.getBoolean("alarm_enabled", false),
            alarmHour = prefs.getInt("alarm_hour", 7),
            alarmMinute = prefs.getInt("alarm_minute", 0),
            alarmDays = prefs.getStringSet("alarm_days", setOf("1", "2", "3", "4", "5", "6", "7"))?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7),
            alarmSoundType = prefs.getString("alarm_sound_type", "DIGITAL_BEEP") ?: "DIGITAL_BEEP",
            alarmVolume = prefs.getFloat("alarm_volume", 0.85f),
            alarmSnoozeMinutes = prefs.getInt("alarm_snooze_minutes", 5),
            alarmVibration = prefs.getBoolean("alarm_vibration", true),
            alarmTriggerIr = prefs.getBoolean("alarm_trigger_ir", false),
            fireAlertEnabled = prefs.getBoolean("fire_alert_enabled", true),
            fireAlertSoundEnabled = prefs.getBoolean("fire_alert_sound_enabled", true),
            fireAlertVibration = prefs.getBoolean("fire_alert_vibration", true),
            fireAlertVoiceTts = prefs.getBoolean("fire_alert_voice_tts", true),
            mq2SensitivityThreshold = prefs.getInt("mq2_sensitivity_threshold", 900)
        )
    }

    fun updatePreferences(state: ClockPreferencesState) {
        prefs.edit()
            .putBoolean("eew_enabled", state.eewEnabled)
            .putInt("eew_min_scale", state.eewMinScale)
            .putBoolean("eew_sound_enabled", state.eewSoundEnabled)
            .putBoolean("eew_vibration_enabled", state.eewVibrationEnabled)
            .putString("eew_sound_mode", state.eewSoundMode)
            .putBoolean("eew_lightweight_map", state.eewLightweightMap)
            .putBoolean("esp_sensor_enabled", state.espSensorEnabled)
            .putString("esp_connection_mode", state.espConnectionMode)
            .putString("esp_ble_device_name", state.espBleDeviceName)
            .putInt("esp_baud_rate", state.espBaudRate)
            .putString("esp_sensor_host", state.espSensorHost)
            .putInt("esp_sensor_port", state.espSensorPort)
            .putInt("esp_sensor_interval", state.espSensorIntervalSeconds)
            .putFloat("esp_temp_offset", state.espTempOffset)
            .putFloat("esp_hum_offset", state.espHumOffset)
            .putFloat("esp_press_offset", state.espPressOffset)
            .putBoolean("show_esp_sensor_on_clock", state.showEspSensorOnClock)
            .putBoolean("alarm_enabled", state.alarmEnabled)
            .putInt("alarm_hour", state.alarmHour)
            .putInt("alarm_minute", state.alarmMinute)
            .putStringSet("alarm_days", state.alarmDays.map { it.toString() }.toSet())
            .putString("alarm_sound_type", state.alarmSoundType)
            .putFloat("alarm_volume", state.alarmVolume)
            .putInt("alarm_snooze_minutes", state.alarmSnoozeMinutes)
            .putBoolean("alarm_vibration", state.alarmVibration)
            .putBoolean("alarm_trigger_ir", state.alarmTriggerIr)
            .putBoolean("fire_alert_enabled", state.fireAlertEnabled)
            .putBoolean("fire_alert_sound_enabled", state.fireAlertSoundEnabled)
            .putBoolean("fire_alert_vibration", state.fireAlertVibration)
            .putBoolean("fire_alert_voice_tts", state.fireAlertVoiceTts)
            .putInt("mq2_sensitivity_threshold", state.mq2SensitivityThreshold)
            .apply()
        _state.value = state
    }

    fun updateEspSensorSettings(
        enabled: Boolean,
        mode: String,
        bleDeviceName: String = _state.value.espBleDeviceName,
        baud: Int,
        host: String,
        port: Int,
        intervalSeconds: Int,
        tempOffset: Float,
        humOffset: Float,
        pressOffset: Float,
        showOnClock: Boolean
    ) {
        prefs.edit()
            .putBoolean("esp_sensor_enabled", enabled)
            .putString("esp_connection_mode", mode)
            .putString("esp_ble_device_name", bleDeviceName)
            .putInt("esp_baud_rate", baud)
            .putString("esp_sensor_host", host)
            .putInt("esp_sensor_port", port)
            .putInt("esp_sensor_interval", intervalSeconds)
            .putFloat("esp_temp_offset", tempOffset)
            .putFloat("esp_hum_offset", humOffset)
            .putFloat("esp_press_offset", pressOffset)
            .putBoolean("show_esp_sensor_on_clock", showOnClock)
            .apply()
        _state.value = _state.value.copy(
            espSensorEnabled = enabled,
            espConnectionMode = mode,
            espBleDeviceName = bleDeviceName,
            espBaudRate = baud,
            espSensorHost = host,
            espSensorPort = port,
            espSensorIntervalSeconds = intervalSeconds,
            espTempOffset = tempOffset,
            espHumOffset = humOffset,
            espPressOffset = pressOffset,
            showEspSensorOnClock = showOnClock
        )
    }

    fun updateEewSettings(
        enabled: Boolean,
        minScale: Int,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        soundMode: String = _state.value.eewSoundMode,
        lightweightMap: Boolean = _state.value.eewLightweightMap
    ) {
        prefs.edit()
            .putBoolean("eew_enabled", enabled)
            .putInt("eew_min_scale", minScale)
            .putBoolean("eew_sound_enabled", soundEnabled)
            .putBoolean("eew_vibration_enabled", vibrationEnabled)
            .putString("eew_sound_mode", soundMode)
            .putBoolean("eew_lightweight_map", lightweightMap)
            .apply()
        _state.value = _state.value.copy(
            eewEnabled = enabled,
            eewMinScale = minScale,
            eewSoundEnabled = soundEnabled,
            eewVibrationEnabled = vibrationEnabled,
            eewSoundMode = soundMode,
            eewLightweightMap = lightweightMap
        )
    }

    fun updateEewSoundMode(soundMode: String) {
        prefs.edit().putString("eew_sound_mode", soundMode).apply()
        _state.value = _state.value.copy(eewSoundMode = soundMode)
    }

    fun updateEewLightweightMap(enabled: Boolean) {
        prefs.edit().putBoolean("eew_lightweight_map", enabled).apply()
        _state.value = _state.value.copy(eewLightweightMap = enabled)
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
        prefs.edit()
            .putString("chime_sound", sound.name)
            .putString("hourly_chime_source_type", ChimeAudioSourceType.BUILT_IN.name)
            .apply()
        _state.value = _state.value.copy(
            chimeSound = sound,
            hourlyChimeSourceType = ChimeAudioSourceType.BUILT_IN
        )
    }

    fun updateHourlyCustomAudio(audio: CustomAudioItem) {
        prefs.edit()
            .putString("hourly_chime_source_type", ChimeAudioSourceType.CUSTOM_FILE.name)
            .putString("hourly_custom_audio_id", audio.id)
            .putString("hourly_custom_audio_name", audio.name)
            .putString("hourly_custom_audio_path", audio.filePath)
            .apply()
        _state.value = _state.value.copy(
            hourlyChimeSourceType = ChimeAudioSourceType.CUSTOM_FILE,
            hourlyCustomAudioId = audio.id,
            hourlyCustomAudioName = audio.name,
            hourlyCustomAudioPath = audio.filePath
        )
    }

    fun updateHourlyChimeSoundDetailed(
        sourceType: ChimeAudioSourceType,
        builtInSound: ChimeSound = ChimeSound.WESTMINSTER,
        customId: String? = null,
        customName: String? = null,
        customPath: String? = null
    ) {
        val editor = prefs.edit()
            .putString("hourly_chime_source_type", sourceType.name)
            .putString("chime_sound", builtInSound.name)
        if (customId != null) editor.putString("hourly_custom_audio_id", customId) else editor.remove("hourly_custom_audio_id")
        if (customName != null) editor.putString("hourly_custom_audio_name", customName) else editor.remove("hourly_custom_audio_name")
        if (customPath != null) editor.putString("hourly_custom_audio_path", customPath) else editor.remove("hourly_custom_audio_path")
        editor.apply()

        _state.value = _state.value.copy(
            hourlyChimeSourceType = sourceType,
            chimeSound = builtInSound,
            hourlyCustomAudioId = customId,
            hourlyCustomAudioName = customName,
            hourlyCustomAudioPath = customPath
        )
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

    fun updateNightMode(enabled: Boolean) {
        prefs.edit().putBoolean("night_mode", enabled).apply()
        _state.value = _state.value.copy(isNightMode = enabled)
    }

    fun updateKioskLock(locked: Boolean) {
        prefs.edit().putBoolean("kiosk_locked", locked).apply()
        _state.value = _state.value.copy(isKioskLocked = locked)
    }

    fun updateBurnInProtection(enabled: Boolean) {
        prefs.edit().putBoolean("burn_in_protection", enabled).apply()
        _state.value = _state.value.copy(burnInProtection = enabled)
    }

    fun updateShowWarnings(enabled: Boolean) {
        prefs.edit().putBoolean("show_warnings", enabled).apply()
        _state.value = _state.value.copy(showWarnings = enabled)
    }

    fun updateShowWeather(enabled: Boolean) {
        prefs.edit().putBoolean("show_weather", enabled).apply()
        _state.value = _state.value.copy(showWeather = enabled)
    }

    fun update24Hour(is24: Boolean) {
        prefs.edit().putBoolean("is_24_hour", is24).apply()
        _state.value = _state.value.copy(is24Hour = is24)
    }

    fun updateShowSeconds(show: Boolean) {
        prefs.edit().putBoolean("show_seconds", show).apply()
        _state.value = _state.value.copy(showSeconds = show)
    }

    fun updateLocationName(name: String) {
        prefs.edit().putString("location_name", name).apply()
        _state.value = _state.value.copy(customLocationName = name)
    }

    fun updateAlarmPreferences(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        days: Set<Int>,
        soundType: String,
        volume: Float,
        snoozeMinutes: Int,
        vibration: Boolean,
        triggerIr: Boolean
    ) {
        prefs.edit()
            .putBoolean("alarm_enabled", enabled)
            .putInt("alarm_hour", hour)
            .putInt("alarm_minute", minute)
            .putStringSet("alarm_days", days.map { it.toString() }.toSet())
            .putString("alarm_sound_type", soundType)
            .putFloat("alarm_volume", volume)
            .putInt("alarm_snooze_minutes", snoozeMinutes)
            .putBoolean("alarm_vibration", vibration)
            .putBoolean("alarm_trigger_ir", triggerIr)
            .apply()
        _state.value = _state.value.copy(
            alarmEnabled = enabled,
            alarmHour = hour,
            alarmMinute = minute,
            alarmDays = days,
            alarmSoundType = soundType,
            alarmVolume = volume,
            alarmSnoozeMinutes = snoozeMinutes,
            alarmVibration = vibration,
            alarmTriggerIr = triggerIr
        )
    }

    fun updateFireAlertPreferences(
        enabled: Boolean,
        soundEnabled: Boolean,
        vibration: Boolean,
        voiceTts: Boolean,
        sensitivityThreshold: Int
    ) {
        prefs.edit()
            .putBoolean("fire_alert_enabled", enabled)
            .putBoolean("fire_alert_sound_enabled", soundEnabled)
            .putBoolean("fire_alert_vibration", vibration)
            .putBoolean("fire_alert_voice_tts", voiceTts)
            .putInt("mq2_sensitivity_threshold", sensitivityThreshold)
            .apply()
        _state.value = _state.value.copy(
            fireAlertEnabled = enabled,
            fireAlertSoundEnabled = soundEnabled,
            fireAlertVibration = vibration,
            fireAlertVoiceTts = voiceTts,
            mq2SensitivityThreshold = sensitivityThreshold
        )
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
                        playVideoAudio = obj.optBoolean("playVideoAudio", false),
                        irSendEnabled = obj.optBoolean("irSendEnabled", false),
                        irButtonId = obj.optString("irButtonId").ifEmpty { null },
                        irButtonName = obj.optString("irButtonName").ifEmpty { null }
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
                put("irSendEnabled", item.irSendEnabled)
                put("irButtonId", item.irButtonId ?: "")
                put("irButtonName", item.irButtonName ?: "")

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

    fun renameCustomAudioItem(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = _customAudioList.value.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        saveCustomAudioListToPrefs(current)
        _customAudioList.value = current

        // Also update any scheduled chime references
        val updatedChimes = _scheduledChimes.value.map { chime ->
            if (chime.customAudioId == id) chime.copy(customAudioName = trimmed) else chime
        }
        if (updatedChimes != _scheduledChimes.value) {
            saveScheduledChimesToPrefs(updatedChimes)
            _scheduledChimes.value = updatedChimes
        }

        // Also update hourly chime if matched
        if (_state.value.hourlyCustomAudioId == id) {
            prefs.edit().putString("hourly_custom_audio_name", trimmed).apply()
            _state.value = _state.value.copy(hourlyCustomAudioName = trimmed)
        }
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

    fun renameCustomVideoItem(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = _customVideoList.value.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        saveCustomVideoListToPrefs(current)
        _customVideoList.value = current

        // Also update any scheduled chime references
        val updatedChimes = _scheduledChimes.value.map { chime ->
            if (chime.customVideoId == id) chime.copy(customVideoName = trimmed) else chime
        }
        if (updatedChimes != _scheduledChimes.value) {
            saveScheduledChimesToPrefs(updatedChimes)
            _scheduledChimes.value = updatedChimes
        }
    }

    fun deleteCustomVideoItem(id: String) {
        val current = _customVideoList.value.filter { it.id != id }
        saveCustomVideoListToPrefs(current)
        _customVideoList.value = current
    }

    // --- IP Camera Configuration ---

    private val _ipCameraConfig = MutableStateFlow(loadIpCameraConfig())
    val ipCameraConfig: StateFlow<com.example.camera.IpCameraConfig> = _ipCameraConfig.asStateFlow()

    private fun loadIpCameraConfig(): com.example.camera.IpCameraConfig {
        return com.example.camera.IpCameraConfig(
            isEnabled = prefs.getBoolean("ipcam_enabled", false),
            port = prefs.getInt("ipcam_port", 8080),
            useFrontCamera = prefs.getBoolean("ipcam_front", true),
            targetFps = prefs.getInt("ipcam_fps", 10),
            resolutionWidth = prefs.getInt("ipcam_width", 640),
            resolutionHeight = prefs.getInt("ipcam_height", 480),
            jpegQuality = prefs.getInt("ipcam_quality", 75),
            showMiniPreviewOnClock = prefs.getBoolean("ipcam_show_mini", false)
        )
    }

    fun updateIpCameraConfig(config: com.example.camera.IpCameraConfig) {
        prefs.edit()
            .putBoolean("ipcam_enabled", config.isEnabled)
            .putInt("ipcam_port", config.port)
            .putBoolean("ipcam_front", config.useFrontCamera)
            .putInt("ipcam_fps", config.targetFps)
            .putInt("ipcam_width", config.resolutionWidth)
            .putInt("ipcam_height", config.resolutionHeight)
            .putInt("ipcam_quality", config.jpegQuality)
            .putBoolean("ipcam_show_mini", config.showMiniPreviewOnClock)
            .apply()
        _ipCameraConfig.value = config
    }
}

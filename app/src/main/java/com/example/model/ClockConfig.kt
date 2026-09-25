package com.example.model

import androidx.compose.ui.graphics.Color

enum class ClockFace(val title: String, val subtitle: String) {
    SEVEN_SEGMENT("7-Segment LED", "レトロな琥珀色LEDセグメント (実機再現)"),
    ANALOG_SWISS("Swiss Minimal", "洗練された大型スイス調アナログ針"),
    TYPOGRAPHIC("Typographic", "圧倒的コントラストの巨大幾何学フォント"),
    FLIP_CLOCK("Split-Flap", "駅や空港のレトロモダンな反転フラップ"),
    MATRIX_DOTS("Luminous Matrix", "微細な点光源が浮かぶドットマトリクス"),
    NEON_CYBERPUNK("Cyber Glow Neon", "鮮やかな多層発光ネオン管チューブ"),
    NIXIE_TUBE("Vintage Nixie", "温かみあるオレンジフィラメント真空管"),
    MINIMAL_BOLD("Studio Bold", "画面いっぱいに広がる超極太モダン数字"),
    ANALOG_STATION("Station Railroad", "視認性に優れたクラシック鉄道駅舎時計"),
    DIGITAL_STATION_BLUE("Station 7-Seg (Blue)", "年月日・曜日マトリクス・大型7セグ・バッテリーバー"),
    DIGITAL_STATION_MATRIX("Station Matrix (Cyan)", "LEDドットマトリクス時刻・年月日・バッテリーインジケータ"),
    RETRO_LCD_GOLD("Retro LCD Gold", "クラシック液晶ゴールド・ゴーストセグメント表示")
}

enum class ColorPalette(
    val title: String,
    val primary: Color,
    val glow: Color,
    val inactiveSegment: Color,
    val background: Color,
    val surface: Color
) {
    AMBER_GLOW(
        title = "Amber Glow (標準・琥珀)",
        primary = Color(0xFFFFB300),
        glow = Color(0xFFFFC107).copy(alpha = 0.45f),
        inactiveSegment = Color(0xFF1E1705),
        background = Color(0xFF020202),
        surface = Color(0xFF121008)
    ),
    CYBER_CYAN(
        title = "Cyber Neon (シアン)",
        primary = Color(0xFF00E5FF),
        glow = Color(0xFF18FFFF).copy(alpha = 0.40f),
        inactiveSegment = Color(0xFF021B20),
        background = Color(0xFF020406),
        surface = Color(0xFF0A1218)
    ),
    MINT_VFD(
        title = "VFD Mint (蛍光緑)",
        primary = Color(0xFF00E676),
        glow = Color(0xFF69F0AE).copy(alpha = 0.35f),
        inactiveSegment = Color(0xFF031E0D),
        background = Color(0xFF010603),
        surface = Color(0xFF08150C)
    ),
    ICE_WHITE(
        title = "Studio White (純白)",
        primary = Color(0xFFF5F5F7),
        glow = Color(0xFFFFFFFF).copy(alpha = 0.25f),
        inactiveSegment = Color(0xFF1C1C1E),
        background = Color(0xFF000000),
        surface = Color(0xFF161618)
    ),
    CRIMSON_NIGHT(
        title = "Crimson (暗視レッド)",
        primary = Color(0xFFFF1744),
        glow = Color(0xFFFF5252).copy(alpha = 0.40f),
        inactiveSegment = Color(0xFF240307),
        background = Color(0xFF040001),
        surface = Color(0xFF140205)
    )
}

data class ClockPreferencesState(
    val clockFace: ClockFace = ClockFace.SEVEN_SEGMENT,
    val colorPalette: ColorPalette = ColorPalette.ICE_WHITE,
    val is24Hour: Boolean = true,
    val showSeconds: Boolean = true,
    val showWeather: Boolean = true,
    val showWarnings: Boolean = true,
    val selectedPrefecture: String = "東京都",
    val selectedCityName: String = "東京都",
    val customLatitude: Double? = null,
    val customLongitude: Double? = null,
    val isAutoLocationEnabled: Boolean = false,
    val demoWarningsPreview: Boolean = false,
    val hourlyChimeEnabled: Boolean = true,
    val halfHourlyChimeEnabled: Boolean = false,
    val chimeSound: com.example.audio.ChimeSound = com.example.audio.ChimeSound.WESTMINSTER,
    val hourlyChimeSourceType: com.example.model.ChimeAudioSourceType = com.example.model.ChimeAudioSourceType.BUILT_IN,
    val hourlyCustomAudioId: String? = null,
    val hourlyCustomAudioName: String? = null,
    val hourlyCustomAudioPath: String? = null,
    val chimeStartHour: Int = 8,
    val chimeEndHour: Int = 22,
    val chimeVolume: Float = 0.75f,
    val isKioskLocked: Boolean = true,
    val isNightMode: Boolean = false,
    val burnInProtection: Boolean = true,
    val customLocationName: String = "Japan Standard Time (JST)",
    val eewEnabled: Boolean = true,
    val eewMinScale: Int = 45,
    val eewSoundEnabled: Boolean = true,
    val eewVibrationEnabled: Boolean = true,
    val eewSoundMode: String = "SYNTH_BEEP", // "SYNTH_BEEP", "VOICE", "MUTE"
    val eewLightweightMap: Boolean = true,
    // ESP32-C3 / ESP8266 AHT20+BMP280 Sensor Settings
    val espSensorEnabled: Boolean = true,
    val espConnectionMode: String = "BLE", // "BLE" (ESP32-C3 auto-connect), "USB", "WIFI"
    val espBleDeviceName: String = "ESP32C3-Sensor",
    val espBaudRate: Int = 115200,
    val espSensorHost: String = "192.168.1.100",
    val espSensorPort: Int = 80,
    val espSensorIntervalSeconds: Int = 5,
    val espTempOffset: Float = 0.0f,
    val espHumOffset: Float = 0.0f,
    val espPressOffset: Float = 0.0f,
    val showEspSensorOnClock: Boolean = true,
    // Alarm Clock (目覚まし時計) Settings
    val alarmEnabled: Boolean = false,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val alarmDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=月..7=日
    val alarmSoundType: String = "DIGITAL_BEEP", // "DIGITAL_BEEP", "TWIN_BELL", "MELODY", "JAPANESE", "CHIME"
    val alarmVolume: Float = 0.85f,
    val alarmSnoozeMinutes: Int = 5,
    val alarmVibration: Boolean = true,
    val alarmTriggerIr: Boolean = false,
    // MQ-2 Fire & Smoke Detection (火災・煙検知)
    val fireAlertEnabled: Boolean = true,
    val fireAlertSoundEnabled: Boolean = true,
    val fireAlertVibration: Boolean = true,
    val fireAlertVoiceTts: Boolean = true,
    val mq2SensitivityThreshold: Int = 900
)

data class PhysicalButtonEvent(
    val buttonId: Int,
    val buttonName: String,
    val actionLabel: String,
    val description: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class FireAlertInfo(
    val detected: Boolean = true,
    val mq2RawValue: Int? = null,
    val message: String = "🔥 火事です！ 火災・煙を検知しました",
    val timestampMs: Long = System.currentTimeMillis()
)

enum class WarningSeverity {
    ADVISORY,       // 注意報 (黄色)
    WARNING,        // 警報 (赤色)
    SPECIAL_WARNING // 特別警報 (紫/深紅)
}

data class WeatherWarning(
    val title: String,
    val severity: WarningSeverity = WarningSeverity.ADVISORY
)

enum class WeatherCondition(val label: String) {
    CLEAR("快晴"),
    PARTLY_CLOUDY("晴れ時々曇"),
    CLOUDY("曇り"),
    RAIN("雨"),
    THUNDERSTORM("雷雨"),
    SNOW("雪")
}

enum class UmbrellaStatus(val label: String, val iconText: String, val advice: String) {
    NONE("傘不要", "☀️", "傘は不要です。安心してお出かけください。"),
    FOLDING("折りたたみ傘", "🌂", "にわか雨や小雨の可能性があります。折りたたみ傘があると安心です。"),
    UMBRELLA("傘が必要", "☂️", "雨が降る見込みです。傘を持ってお出かけください。"),
    HEAVY_RAIN("大雨・長傘", "☔", "しっかりとした雨が予想されます。大きめの傘やレイン具が必要です。")
}

data class HourlyForecast(
    val time: String, // "12:00"
    val temperatureCelsius: Int,
    val condition: WeatherCondition,
    val conditionText: String,
    val precipitationProbability: Int, // %
    val precipitationMm: Double = 0.0 // mm/h
) {
    val umbrellaStatus: UmbrellaStatus
        get() = when {
            precipitationMm >= 3.0 || precipitationProbability >= 80 -> UmbrellaStatus.HEAVY_RAIN
            precipitationMm >= 0.8 || precipitationProbability >= 50 -> UmbrellaStatus.UMBRELLA
            precipitationMm > 0.0 || precipitationProbability >= 20 -> UmbrellaStatus.FOLDING
            else -> UmbrellaStatus.NONE
        }
}

data class DailyForecast(
    val date: String, // "9/12 (金)"
    val condition: WeatherCondition,
    val conditionText: String,
    val highTemp: Int,
    val lowTemp: Int,
    val precipitationProbability: Int = 0, // %
    val precipitationSumMm: Double = 0.0, // 合計降水量 mm
    val sunrise: String = "", // "05:21"
    val sunset: String = "", // "17:54"
    val uvIndexMax: Double = 0.0
) {
    val umbrellaStatus: UmbrellaStatus
        get() = when {
            precipitationSumMm >= 10.0 || precipitationProbability >= 80 -> UmbrellaStatus.HEAVY_RAIN
            precipitationSumMm >= 2.0 || precipitationProbability >= 50 -> UmbrellaStatus.UMBRELLA
            precipitationSumMm > 0.0 || precipitationProbability >= 20 -> UmbrellaStatus.FOLDING
            else -> UmbrellaStatus.NONE
        }
}

data class WeatherState(
    val cityName: String = "Tokyo",
    val regionName: String = "東京都",
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val temperatureCelsius: Int = 22,
    val conditionText: String = "晴れ",
    val highTemp: Int = 25,
    val lowTemp: Int = 17,
    val humidityPercent: Int = 45,
    val windSpeedKmH: Int = 8,
    val windDirectionText: String = "北東",
    val apparentTempCelsius: Int = 22,
    val pressureHpa: Int = 1013,
    val precipitationMm: Double = 0.0, // 現在の降水量 mm/h
    val uvIndexMax: Double = 5.0,
    val sunriseTime: String = "05:22",
    val sunsetTime: String = "17:52",
    val lastUpdatedTime: String = "",
    val hourlyForecasts: List<HourlyForecast> = emptyList(),
    val dailyForecasts: List<DailyForecast> = emptyList(),
    val warnings: List<WeatherWarning> = listOf(
        WeatherWarning("レベル2 大雨注意報", WarningSeverity.ADVISORY),
        WeatherWarning("レベル2 土砂災害注意報", WarningSeverity.ADVISORY),
        WeatherWarning("雷注意報", WarningSeverity.ADVISORY)
    )
) {
    val currentUmbrellaStatus: UmbrellaStatus
        get() {
            // 直近の予報または現在の降水量から総合判断
            val maxNextHoursProb = hourlyForecasts.take(6).maxOfOrNull { it.precipitationProbability } ?: 0
            val maxNextHoursMm = hourlyForecasts.take(6).maxOfOrNull { it.precipitationMm } ?: precipitationMm

            return when {
                maxNextHoursMm >= 3.0 || maxNextHoursProb >= 80 || precipitationMm >= 3.0 -> UmbrellaStatus.HEAVY_RAIN
                maxNextHoursMm >= 0.8 || maxNextHoursProb >= 50 || precipitationMm >= 0.5 -> UmbrellaStatus.UMBRELLA
                maxNextHoursMm > 0.0 || maxNextHoursProb >= 20 || precipitationMm > 0.0 -> UmbrellaStatus.FOLDING
                else -> UmbrellaStatus.NONE
            }
        }
}

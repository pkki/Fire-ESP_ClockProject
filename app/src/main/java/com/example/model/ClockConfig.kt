package com.example.model

import androidx.compose.ui.graphics.Color

enum class ClockFace(val title: String, val subtitle: String) {
    SEVEN_SEGMENT("7-Segment LED", "レトロな琥珀色LEDセグメント (実機再現)"),
    ANALOG_SWISS("Swiss Minimal", "バウハウス調の洗練されたアナログ針"),
    TYPOGRAPHIC("Typographic", "圧倒的コントラストの巨大幾何学フォント"),
    FLIP_CLOCK("Split-Flap", "駅や空港のレトロモダンな反転フラップ"),
    MATRIX_DOTS("Luminous Matrix", "微細な点光源が浮かぶドットマトリクス")
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
    val hourlyChimeEnabled: Boolean = true,
    val halfHourlyChimeEnabled: Boolean = false,
    val chimeSound: com.example.audio.ChimeSound = com.example.audio.ChimeSound.WESTMINSTER,
    val chimeStartHour: Int = 8,
    val chimeEndHour: Int = 22,
    val chimeVolume: Float = 0.75f,
    val isKioskLocked: Boolean = true,
    val isNightMode: Boolean = false,
    val burnInProtection: Boolean = true,
    val customLocationName: String = "Japan Standard Time (JST)"
)

enum class WeatherCondition(val label: String) {
    CLEAR("快晴"),
    PARTLY_CLOUDY("晴れ時々曇"),
    CLOUDY("曇り"),
    RAIN("雨"),
    THUNDERSTORM("雷雨"),
    SNOW("雪")
}

data class WeatherState(
    val cityName: String = "Tokyo",
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val temperatureCelsius: Int = 22,
    val conditionText: String = "晴れ",
    val highTemp: Int = 25,
    val lowTemp: Int = 17,
    val humidityPercent: Int = 45,
    val windSpeedKmH: Int = 8
)

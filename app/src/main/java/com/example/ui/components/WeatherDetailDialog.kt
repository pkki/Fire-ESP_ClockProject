package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.model.UmbrellaStatus
import java.util.Locale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DailyForecast
import com.example.model.HourlyForecast
import com.example.model.WarningSeverity
import com.example.model.WeatherCondition
import com.example.model.WeatherState
import com.example.model.WeatherWarning

@Composable
fun WeatherDetailDialog(
    weather: WeatherState,
    isRefreshing: Boolean,
    isAutoLocation: Boolean,
    accentColor: Color,
    onRefresh: () -> Unit,
    onChangeLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .widthIn(max = 840.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                    .testTag("weather_detail_dialog"),
                color = Color(0xFF10131A),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // --- Top Header ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isAutoLocation) Icons.Default.MyLocation else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${weather.regionName} ${weather.cityName}",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isAutoLocation) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x3300E5FF))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "GPS現在地",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            if (weather.lastUpdatedTime.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = weather.lastUpdatedTime,
                                    color = Color(0xFF8E929E),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Change location button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable(onClick = onChangeLocation)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "地域変更",
                                        tint = Color(0xFFB0BEC5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "地域変更",
                                        color = Color(0xFFE0E0E0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Refresh button
                            IconButton(
                                onClick = onRefresh,
                                enabled = !isRefreshing,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                                    .size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "更新",
                                    tint = if (isRefreshing) accentColor else Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .then(if (isRefreshing) Modifier.rotate(rotation) else Modifier)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Close button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                                    .size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "閉じる",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Scrollable Weather Content ---
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Hero Weather Card
                        item {
                            HeroWeatherCard(weather = weather, accentColor = accentColor)
                        }

                        // 2. Umbrella Advisory Card (tenki.jp style)
                        item {
                            UmbrellaAdvisorySection(weather = weather)
                        }

                        // 3. JMA Warnings & Advisories Section
                        item {
                            WarningsSection(warnings = weather.warnings)
                        }

                        // 4. Hourly Forecast (24 Hours - tenki.jp style with mm and umbrella mark)
                        if (weather.hourlyForecasts.isNotEmpty()) {
                            item {
                                HourlyForecastSection(hourlyList = weather.hourlyForecasts)
                            }
                        }

                        // 5. Daily Forecast (7 Days with precipitation mm and umbrella mark)
                        if (weather.dailyForecasts.isNotEmpty()) {
                            item {
                                DailyForecastSection(dailyList = weather.dailyForecasts)
                            }
                        }

                        // 6. Weather Metrics Grid (Humidity, Wind, UV, Pressure, Sunrise/Sunset)
                        item {
                            WeatherMetricsSection(weather = weather)
                        }

                        // Footer note
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "気象データ提供: Open-Meteo & 気象庁 (JMA 防災情報)",
                                    color = Color(0xFF616161),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroWeatherCard(
    weather: WeatherState,
    accentColor: Color
) {
    val (icon, iconColor) = getWeatherIconAndColor(weather.condition)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E2433), Color(0xFF141923))
                )
            )
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${weather.temperatureCelsius}°",
                        color = Color.White,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = weather.conditionText,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "体感 ${weather.apparentTempCelsius}°C",
                            color = Color(0xFFB0BEC5),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FF5722))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "最高 ${weather.highTemp}°C",
                            color = Color(0xFFFF7043),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x332196F3))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "最低 ${weather.lowTemp}°C",
                            color = Color(0xFF42A5F5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "湿度 ${weather.humidityPercent}% • 風速 ${weather.windSpeedKmH}km/h (${weather.windDirectionText})",
                        color = Color(0xFF90A4AE),
                        fontSize = 13.sp
                    )
                }
            }

            // Big Weather Icon
            Icon(
                imageVector = icon,
                contentDescription = weather.conditionText,
                tint = iconColor,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

@Composable
private fun UmbrellaAdvisorySection(weather: WeatherState) {
    val umbrella = weather.currentUmbrellaStatus
    val (bgGradient, borderColor, badgeColor) = when (umbrella) {
        UmbrellaStatus.NONE -> Triple(
            listOf(Color(0xFF1B2B24), Color(0xFF101C17)),
            Color(0x334CAF50),
            Color(0xFF81C784)
        )
        UmbrellaStatus.FOLDING -> Triple(
            listOf(Color(0xFF26241C), Color(0xFF1B1910)),
            Color(0x4DFFD54F),
            Color(0xFFFFE082)
        )
        UmbrellaStatus.UMBRELLA -> Triple(
            listOf(Color(0xFF1A2636), Color(0xFF111A26)),
            Color(0x4D42A5F5),
            Color(0xFF90CAF9)
        )
        UmbrellaStatus.HEAVY_RAIN -> Triple(
            listOf(Color(0xFF2B1824), Color(0xFF1D0E17)),
            Color(0x4DFF5252),
            Color(0xFFFF8A80)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = bgGradient))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Big Emoji/Icon for Umbrella Status
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(borderColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = umbrella.iconText,
                    fontSize = 26.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "傘指数・お出かけ目安",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(borderColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = umbrella.label,
                            color = badgeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = umbrella.advice,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun WarningsSection(warnings: List<WeatherWarning>) {
    if (warnings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x1A4CAF50))
                .border(1.dp, Color(0x334CAF50), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "現在、発表されている警報・注意報はありません（平常）",
                    color = Color(0xFF81C784),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B181E))
                .border(1.dp, Color(0x40FF9800), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "気象警報・注意報 (${warnings.size}件)",
                    color = Color(0xFFFFB300),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                warnings.forEach { warning ->
                    val (badgeBg, badgeText) = when (warning.severity) {
                        WarningSeverity.ADVISORY -> Pair(Color(0x4DFFD600), Color(0xFFFFEA00))
                        WarningSeverity.WARNING -> Pair(Color(0x4DFF1744), Color(0xFFFF5252))
                        WarningSeverity.SPECIAL_WARNING -> Pair(Color(0x66D500F9), Color(0xFFE040FB))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = warning.title,
                            color = badgeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastSection(hourlyList: List<HourlyForecast>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF161922))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "24時間天気予報 (1時間ごと)",
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "降水量(mm) • 降水確率 • 傘マーク",
                color = Color(0xFF78909C),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(hourlyList) { hour ->
                val (hIcon, hColor) = getWeatherIconAndColor(hour.condition)
                val precipMm = hour.precipitationMm
                val prob = hour.precipitationProbability
                val umbrella = hour.umbrellaStatus

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (precipMm >= 1.0 || prob >= 60) Color(0x2E1976D2)
                            else if (precipMm > 0.0 || prob >= 30) Color(0x1F0288D1)
                            else Color(0x1AFFFFFF)
                        )
                        .border(
                            1.dp,
                            if (precipMm >= 1.0 || prob >= 60) Color(0x4064B5F6) else Color(0x0FFFFFFF),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                ) {
                    // Time Label
                    Text(
                        text = hour.time,
                        color = if (hour.time == "今") Color(0xFFFFD54F) else Color(0xFFCFD8DC),
                        fontSize = 13.sp,
                        fontWeight = if (hour.time == "今") FontWeight.Bold else FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Weather Icon
                    Icon(
                        imageVector = hIcon,
                        contentDescription = hour.conditionText,
                        tint = hColor,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Temperature
                    Text(
                        text = "${hour.temperatureCelsius}°",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Umbrella Icon / Status
                    Text(
                        text = umbrella.iconText,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Precipitation mm (降水量)
                    val mmText = if (precipMm > 0.0) String.format(Locale.JAPAN, "%.1fmm", precipMm) else "0.0mm"
                    val mmColor = when {
                        precipMm >= 3.0 -> Color(0xFFFF5252)
                        precipMm >= 1.0 -> Color(0xFF40C4FF)
                        precipMm > 0.0 -> Color(0xFF80D8FF)
                        else -> Color(0xFF78909C)
                    }
                    Text(
                        text = mmText,
                        color = mmColor,
                        fontSize = 11.sp,
                        fontWeight = if (precipMm > 0.0) FontWeight.Bold else FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Rain Probability (降水確率 %)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = if (prob >= 30) Color(0xFF4FC3F7) else Color(0xFF78909C),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        Text(
                            text = "$prob%",
                            color = if (prob >= 30) Color(0xFF4FC3F7) else Color(0xFF90A4AE),
                            fontSize = 11.sp,
                            fontWeight = if (prob >= 30) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyForecastSection(dailyList: List<DailyForecast>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF161922))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "7日間 週間天気予報",
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "傘 • 降水量(計) • 降水確率 • 気温",
                color = Color(0xFF78909C),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            dailyList.forEach { day ->
                val (dIcon, dColor) = getWeatherIconAndColor(day.condition)
                val umbrella = day.umbrellaStatus
                val precipSum = day.precipitationSumMm

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (precipSum >= 2.0 || day.precipitationProbability >= 60) Color(0x1A1976D2)
                            else Color(0x0FFFFFFF)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Date label
                    Text(
                        text = day.date,
                        color = if (day.date == "今日") Color(0xFFFFD54F) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (day.date == "今日") FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.width(75.dp)
                    )

                    // Weather Icon & Description
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(115.dp)
                    ) {
                        Icon(
                            imageVector = dIcon,
                            contentDescription = day.conditionText,
                            tint = dColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = day.conditionText,
                            color = Color(0xFFECEFF1),
                            fontSize = 13.sp
                        )
                    }

                    // Umbrella mark
                    Text(
                        text = umbrella.iconText,
                        fontSize = 14.sp,
                        modifier = Modifier.width(26.dp)
                    )

                    // Precipitation sum & probability
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.width(75.dp)
                    ) {
                        if (day.precipitationProbability > 0 || precipSum > 0.0) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${day.precipitationProbability}%",
                                color = Color(0xFF4FC3F7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "--",
                                color = Color(0xFF607D8B),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Low & High Temp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text(
                            text = "${day.lowTemp}°",
                            color = Color(0xFF42A5F5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "/",
                            color = Color(0xFF78909C),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Text(
                            text = "${day.highTemp}°",
                            color = Color(0xFFFF7043),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherMetricsSection(weather: WeatherState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "詳細気象情報",
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.WaterDrop,
                iconColor = Color(0xFF29B6F6),
                title = "湿度",
                value = "${weather.humidityPercent}%",
                subtitle = when {
                    weather.humidityPercent < 35 -> "乾燥気味"
                    weather.humidityPercent > 70 -> "湿度高め"
                    else -> "快適"
                },
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.Air,
                iconColor = Color(0xFF81D4FA),
                title = "風速・風向",
                value = "${weather.windSpeedKmH} km/h",
                subtitle = "${weather.windDirectionText}の風",
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.Thermostat,
                iconColor = Color(0xFFFF8A65),
                title = "体感温度",
                value = "${weather.apparentTempCelsius}°C",
                subtitle = if (weather.apparentTempCelsius > weather.temperatureCelsius) "実際より暖かめ" else "肌寒め",
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.Compress,
                iconColor = Color(0xFFB39DDB),
                title = "気圧",
                value = "${weather.pressureHpa} hPa",
                subtitle = if (weather.pressureHpa < 1005) "低気圧" else "標準気圧",
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.Brightness5,
                iconColor = Color(0xFFFFD54F),
                title = "紫外線 (UV)",
                value = String.format(java.util.Locale.JAPAN, "%.1f", weather.uvIndexMax),
                subtitle = when {
                    weather.uvIndexMax >= 8.0 -> "極めて強い"
                    weather.uvIndexMax >= 6.0 -> "強い"
                    weather.uvIndexMax >= 3.0 -> "中程度"
                    else -> "弱い"
                },
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                icon = Icons.Default.WbTwilight,
                iconColor = Color(0xFFFFB74D),
                title = "日の出 / 日の入り",
                value = "${weather.sunriseTime} / ${weather.sunsetTime}",
                subtitle = "東京 (JST)",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161922))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color(0xFF90A4AE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp
            )
        }
    }
}

private fun getWeatherIconAndColor(condition: WeatherCondition): Pair<ImageVector, Color> {
    return when (condition) {
        WeatherCondition.CLEAR -> Pair(Icons.Default.WbSunny, Color(0xFFFFB300))
        WeatherCondition.PARTLY_CLOUDY -> Pair(Icons.Default.WbCloudy, Color(0xFFFFD54F))
        WeatherCondition.CLOUDY -> Pair(Icons.Default.Cloud, Color(0xFFB0BEC5))
        WeatherCondition.RAIN -> Pair(Icons.Default.WaterDrop, Color(0xFF29B6F6))
        WeatherCondition.THUNDERSTORM -> Pair(Icons.Default.Bolt, Color(0xFFFFCA28))
        WeatherCondition.SNOW -> Pair(Icons.Default.AcUnit, Color(0xFF81D4FA))
    }
}

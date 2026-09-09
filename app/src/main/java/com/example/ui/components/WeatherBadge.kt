package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WeatherCondition
import com.example.model.WeatherState

@Composable
fun WeatherBadge(
    weather: WeatherState,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (weather.condition) {
        WeatherCondition.CLEAR -> Icons.Default.WbSunny
        WeatherCondition.PARTLY_CLOUDY -> Icons.Default.WbCloudy
        WeatherCondition.CLOUDY -> Icons.Default.Cloud
        WeatherCondition.RAIN -> Icons.Default.WaterDrop
        WeatherCondition.THUNDERSTORM -> Icons.Default.Bolt
        WeatherCondition.SNOW -> Icons.Default.AcUnit
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x221A1A22))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("weather_badge"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = weather.conditionText,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${weather.cityName}  ${weather.temperatureCelsius}°C  ${weather.conditionText}",
                color = Color(0xFFE2E2E8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "H:${weather.highTemp}° L:${weather.lowTemp}°  💧${weather.humidityPercent}%",
                color = Color(0xFF888894),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

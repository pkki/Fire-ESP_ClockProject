package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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

    val iconColor = when (weather.condition) {
        WeatherCondition.CLEAR -> Color(0xFFFFB300)
        WeatherCondition.PARTLY_CLOUDY -> Color(0xFFFFD54F)
        WeatherCondition.CLOUDY -> Color(0xFFB0BEC5)
        WeatherCondition.RAIN -> Color(0xFF29B6F6)
        WeatherCondition.THUNDERSTORM -> Color(0xFFFFCA28)
        WeatherCondition.SNOW -> Color(0xFF81D4FA)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x40141420))
            .border(1.2.dp, Color(0x40FFFFFF), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("weather_badge"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Weather icon (Large & vivid)
            Icon(
                imageVector = icon,
                contentDescription = weather.conditionText,
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Main temperature & Condition prominently displayed
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${weather.temperatureCelsius}°C",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = weather.conditionText,
                        color = if (iconColor != Color(0xFFB0BEC5)) iconColor else Color(0xFFE0E0E0),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = weather.cityName,
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "最高 ${weather.highTemp}°",
                        color = Color(0xFFFF7043),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "最低 ${weather.lowTemp}°",
                        color = Color(0xFF42A5F5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (weather.currentUmbrellaStatus != com.example.model.UmbrellaStatus.NONE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${weather.currentUmbrellaStatus.iconText}${weather.currentUmbrellaStatus.label}",
                            color = if (weather.currentUmbrellaStatus == com.example.model.UmbrellaStatus.FOLDING) Color(0xFFFFE082) else Color(0xFF81D4FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (weather.humidityPercent > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "💧${weather.humidityPercent}%",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EspSensorData
import java.util.Locale

@Composable
fun EspSensorBottomBar(
    sensorData: EspSensorData,
    host: String,
    accentColor: Color = Color(0xFF00E5FF),
    onOpenSettings: () -> Unit,
    onRetryConnection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isConnected = sensorData.isConnected
    val hasReadings = sensorData.temperature != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEE121722),
                        Color(0xFA0B0E17)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.4f),
                        Color(0x22FFFFFF),
                        accentColor.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onOpenSettings() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("esp_sensor_bottom_bar")
    ) {
        if (isConnected && hasReadings) {
            // Minimal, clean, large display of environmental data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Temperature (室内温度)
                SensorMetricItem(
                    icon = Icons.Default.DeviceThermostat,
                    iconTint = Color(0xFFFF5252),
                    label = "室内温度",
                    valueStr = String.format(Locale.US, "%.1f", sensorData.temperature),
                    unitStr = "°C",
                    statusLabel = sensorData.heatstrokeRiskLabel,
                    statusColor = when {
                        (sensorData.temperature ?: 0f) >= 31f -> Color(0xFFFF3D00)
                        (sensorData.temperature ?: 0f) >= 28f -> Color(0xFFFFA000)
                        else -> Color(0xFF4ADE80)
                    },
                    modifier = Modifier.weight(1f)
                )

                // Divider
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(1.dp)
                        .background(Color(0x2EFFFFFF))
                )

                // 2. Humidity (室内湿度)
                SensorMetricItem(
                    icon = Icons.Default.WaterDrop,
                    iconTint = Color(0xFF38BDF8),
                    label = "室内湿度",
                    valueStr = String.format(Locale.US, "%.1f", sensorData.humidity),
                    unitStr = "%",
                    statusLabel = sensorData.humidityStatus,
                    statusColor = when {
                        (sensorData.humidity ?: 50f) < 40f -> Color(0xFFF59E0B)
                        (sensorData.humidity ?: 50f) > 70f -> Color(0xFF38BDF8)
                        else -> Color(0xFF4ADE80)
                    },
                    modifier = Modifier.weight(1f)
                )

                // Divider
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(1.dp)
                        .background(Color(0x2EFFFFFF))
                )

                // 3. Pressure (気圧)
                SensorMetricItem(
                    icon = Icons.Default.Compress,
                    iconTint = Color(0xFFFBBF24),
                    label = "気圧",
                    valueStr = String.format(Locale.US, "%.1f", sensorData.pressure),
                    unitStr = "hPa",
                    statusLabel = sensorData.pressureStatus,
                    statusColor = when {
                        (sensorData.pressure ?: 1013f) < 1005f -> Color(0xFFEF4444)
                        else -> Color(0xFFFBBF24)
                    },
                    modifier = Modifier.weight(1.1f)
                )

                // 4. Comfort / Discomfort Index Pill
                sensorData.discomfortIndex?.let { di ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sensorData.mq2SmokeDetected) Color(0x44FF3D00) else Color(0x2038BDF8)
                            )
                            .border(
                                1.dp,
                                if (sensorData.mq2SmokeDetected) Color(0xFFFF5252) else Color(0x3838BDF8),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (sensorData.mq2SmokeDetected) "🔥 火災検知" else "不快指数",
                                color = if (sensorData.mq2SmokeDetected) Color(0xFFFF8A80) else Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (sensorData.mq2SmokeDetected) "火事です" else String.format(Locale.US, "%.0f", di),
                                color = if (sensorData.mq2SmokeDetected) Color(0xFFFF5252) else Color.White,
                                fontSize = if (sensorData.mq2SmokeDetected) 13.sp else 17.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (sensorData.mq2SmokeDetected) "避難確認" else sensorData.discomfortLabel,
                                color = if (sensorData.mq2SmokeDetected) Color(0xFFFF8A80) else Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Disconnected / waiting state: minimalist and non-intrusive
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color(0xFFFF7043),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (sensorData.isConnecting) "通信中..." else "センサー未接続",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BluetoothまたはUSBで自動接続します (タップして設定)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                if (onRetryConnection != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x28FFFFFF))
                            .clickable { onRetryConnection() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "再試行",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "再接続",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorMetricItem(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    valueStr: String,
    unitStr: String,
    statusLabel: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = valueStr,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                lineHeight = 32.sp
            )
            Text(
                text = unitStr,
                color = iconTint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = statusLabel,
            color = statusColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

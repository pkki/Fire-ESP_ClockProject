package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ClockPreferencesState

@Composable
fun ControlDock(
    preferences: ClockPreferencesState,
    onOpenFacePicker: () -> Unit,
    onOpenPalettePicker: () -> Unit,
    onOpenChimeSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    onToggleWeather: () -> Unit,
    onToggleNightMode: () -> Unit,
    onToggleKioskLock: () -> Unit,
    onUnlockLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xDD111116))
                .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("control_dock_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clock Face Switcher
            DockActionButton(
                icon = Icons.Default.ViewCarousel,
                label = "文字盤",
                contentDescription = "文字盤切り替え",
                testTag = "btn_face_picker",
                onClick = onOpenFacePicker
            )

            // Color Palette Switcher
            DockActionButton(
                icon = Icons.Default.Palette,
                label = "カラー",
                contentDescription = "カラー切り替え",
                testTag = "btn_palette_picker",
                tint = preferences.colorPalette.primary,
                onClick = onOpenPalettePicker
            )

            // Hourly Chime settings
            DockActionButton(
                icon = Icons.Default.NotificationsActive,
                label = "時報",
                contentDescription = "時報設定",
                testTag = "btn_chime_settings",
                tint = if (preferences.hourlyChimeEnabled) preferences.colorPalette.primary else Color(0xFF6E6E78),
                onClick = onOpenChimeSettings
            )

            // Desk Timer
            DockActionButton(
                icon = Icons.Default.HourglassBottom,
                label = "タイマー",
                contentDescription = "卓上タイマー",
                testTag = "btn_desk_timer",
                onClick = onOpenTimer
            )

            // Weather toggle
            DockActionButton(
                icon = Icons.Default.WbSunny,
                label = "天気",
                contentDescription = "天気表示切替",
                testTag = "btn_weather_toggle",
                tint = if (preferences.showWeather) preferences.colorPalette.primary else Color(0xFF6E6E78),
                onClick = onToggleWeather
            )

            // Night Stand Mode
            DockActionButton(
                icon = Icons.Default.Bedtime,
                label = "常夜灯",
                contentDescription = "ナイトモード (常夜灯)",
                testTag = "btn_night_mode",
                tint = if (preferences.isNightMode) Color(0xFFFFB300) else Color(0xFF888896),
                onClick = onToggleNightMode
            )

            // Kiosk Lock Toggle (with long-press unlock support)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(preferences.isKioskLocked) {
                        detectTapGestures(
                            onTap = {
                                if (!preferences.isKioskLocked) {
                                    onToggleKioskLock()
                                }
                            },
                            onLongPress = {
                                onUnlockLongPress()
                            }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("btn_kiosk_lock")
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (preferences.isKioskLocked) Color(0x3300E5FF) else Color(0x18FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (preferences.isKioskLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "キオスクロック",
                        tint = if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0xFF888896),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (preferences.isKioskLocked) "ロック中" else "解除中",
                    color = if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0xFF888896),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DockActionButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    testTag: String,
    tint: Color = Color(0xFFEDEDF2),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x18FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color(0xFF9E9EA8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

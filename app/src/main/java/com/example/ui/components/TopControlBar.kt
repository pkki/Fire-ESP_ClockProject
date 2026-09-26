package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ClockViewModel
import com.example.camera.IpCameraStatus
import com.example.model.ClockPreferencesState

@Composable
fun TopControlBar(
    preferences: ClockPreferencesState,
    ipCameraStatus: IpCameraStatus,
    timerSeconds: Int,
    isEspConnected: Boolean,
    isMusicPlaying: Boolean = false,
    onOpenSettings: (SettingsTab) -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMusicPlayer: () -> Unit = {},
    onOpenIrRemote: () -> Unit = {},
    onToggleNightMode: () -> Unit,
    onToggleKioskLock: () -> Unit,
    onUnlockLongPress: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xDD11141D))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("top_control_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Sensor status shortcut (only displayed when disconnected, per user request)
        if (!isEspConnected && preferences.espSensorEnabled) {
            TopActionButton(
                icon = Icons.Default.Sensors,
                label = "センサー未接続",
                testTag = "btn_top_esp_sensor",
                tint = Color(0xFFFF7043),
                showIndicator = false,
                indicatorColor = Color(0xFFFF7043),
                onClick = {
                    onUserInteraction()
                    onOpenSettings(SettingsTab.ESP_SENSOR)
                }
            )
        }

        // 2. Settings button
        TopActionButton(
            icon = Icons.Default.Settings,
            label = "設定",
            testTag = "btn_top_settings",
            tint = Color(0xFF00E5FF),
            onClick = {
                onUserInteraction()
                onOpenSettings(SettingsTab.FACE_PALETTE)
            }
        )

        // 3. IP Camera shortcut
        TopActionButton(
            icon = if (ipCameraStatus.isRunning) Icons.Default.Videocam else Icons.Default.VideocamOff,
            label = if (ipCameraStatus.isRunning) ":${ipCameraStatus.port}" else "カメラ",
            testTag = "btn_top_ip_cam",
            tint = if (ipCameraStatus.isRunning) Color(0xFFFF5252) else Color(0xFF94A3B8),
            showIndicator = ipCameraStatus.isRunning,
            indicatorColor = Color(0xFFFF1744),
            onClick = {
                onUserInteraction()
                onOpenSettings(SettingsTab.IP_CAMERA)
            }
        )

        // 4. Desk Timer
        val timerActive = timerSeconds > 0
        TopActionButton(
            icon = Icons.Default.HourglassBottom,
            label = if (timerActive) {
                String.format(java.util.Locale.US, "%02d:%02d", timerSeconds / 60, timerSeconds % 60)
            } else "タイマー",
            testTag = "btn_top_timer",
            tint = if (timerActive) Color(0xFFFFB300) else Color(0xFF94A3B8),
            showIndicator = timerActive,
            indicatorColor = Color(0xFFFFB300),
            onClick = {
                onUserInteraction()
                onOpenTimer()
            }
        )

        // 4.5 Smart IR Remote Shortcut
        TopActionButton(
            icon = Icons.Default.Sensors,
            label = "リモコン",
            testTag = "btn_top_ir_remote",
            tint = Color(0xFF38BDF8),
            onClick = {
                onUserInteraction()
                onOpenIrRemote()
            }
        )

        // 4.6 Music Player Shortcut
        TopActionButton(
            icon = if (isMusicPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
            label = if (isMusicPlaying) "音楽再生中" else "音楽",
            testTag = "btn_top_music_player",
            tint = if (isMusicPlaying) preferences.colorPalette.primary else Color(0xFF94A3B8),
            showIndicator = isMusicPlaying,
            indicatorColor = preferences.colorPalette.primary,
            onClick = {
                onUserInteraction()
                onOpenMusicPlayer()
            }
        )

        // 5. Night stand mode
        TopActionButton(
            icon = Icons.Default.Bedtime,
            label = "常夜灯",
            testTag = "btn_top_night",
            tint = if (preferences.isNightMode) Color(0xFFFFB300) else Color(0xFF94A3B8),
            onClick = {
                onUserInteraction()
                onToggleNightMode()
            }
        )

        // 6. Kiosk lock (supports long-press unlock)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (preferences.isKioskLocked) Color(0x3300E5FF) else Color(0x18FFFFFF))
                .pointerInput(preferences.isKioskLocked) {
                    detectTapGestures(
                        onTap = {
                            onUserInteraction()
                            onToggleKioskLock()
                        },
                        onLongPress = {
                            onUserInteraction()
                            onUnlockLongPress()
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("btn_top_kiosk_lock"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (preferences.isKioskLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "キオスクロック",
                    tint = if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (preferences.isKioskLocked) "ロック" else "解除",
                    color = if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TopActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color,
    showIndicator: Boolean = false,
    indicatorColor: Color = Color.Green,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x18FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

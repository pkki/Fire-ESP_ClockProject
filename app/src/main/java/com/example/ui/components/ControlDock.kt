package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.IpCameraStatus
import com.example.model.ClockPreferencesState

@Composable
fun ControlDock(
    preferences: ClockPreferencesState,
    ipCameraStatus: IpCameraStatus,
    timerSeconds: Int = 0,
    isEspConnected: Boolean = false,
    onOpenSettings: (SettingsTab) -> Unit,
    onOpenTimer: () -> Unit,
    onOpenIrRemote: () -> Unit = {},
    onToggleNightMode: () -> Unit,
    onToggleKioskLock: () -> Unit,
    onUnlockLongPress: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp, top = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xDD111116))
                .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("control_dock_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Master Settings Button (Clock Face, Color Palette, Weather, Chime, IP Camera, etc.)
            DockActionButton(
                icon = Icons.Default.Settings,
                label = "設定",
                contentDescription = "総合設定",
                testTag = "btn_unified_settings",
                tint = Color(0xFF00E5FF),
                onClick = {
                    onUserInteraction()
                    onOpenSettings(SettingsTab.FACE_PALETTE)
                }
            )

            // IP Camera Status & Shortcut Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onUserInteraction()
                            onOpenSettings(SettingsTab.IP_CAMERA)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("btn_ip_camera_dock")
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (ipCameraStatus.isRunning) Color(0x33FF1744) else Color(0x18FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (ipCameraStatus.isRunning) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "IPカメラ設定",
                        tint = if (ipCameraStatus.isRunning) Color(0xFFFF1744) else Color(0xFF888896),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (ipCameraStatus.isRunning) "配信中" else "IPカメラ",
                    color = if (ipCameraStatus.isRunning) Color(0xFFFF5252) else Color(0xFF9E9EA8),
                    fontSize = 9.sp,
                    fontWeight = if (ipCameraStatus.isRunning) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Desk Timer Shortcut
            DockActionButton(
                icon = Icons.Default.HourglassBottom,
                label = "タイマー",
                contentDescription = "卓上タイマー",
                testTag = "btn_desk_timer",
                onClick = {
                    onUserInteraction()
                    onOpenTimer()
                }
            )

            // Smart IR Remote Shortcut
            DockActionButton(
                icon = Icons.Default.Sensors,
                label = "リモコン",
                contentDescription = "スマート家電リモコン",
                testTag = "btn_ir_remote_dock",
                tint = Color(0xFF38BDF8),
                onClick = {
                    onUserInteraction()
                    onOpenIrRemote()
                }
            )

            // Night Stand Mode Toggle
            DockActionButton(
                icon = Icons.Default.Bedtime,
                label = "常夜灯",
                contentDescription = "ナイトモード (常夜灯)",
                testTag = "btn_night_mode",
                tint = if (preferences.isNightMode) Color(0xFFFFB300) else Color(0xFF888896),
                onClick = {
                    onUserInteraction()
                    onToggleNightMode()
                }
            )

            // Kiosk Lock Toggle (with long-press unlock support)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
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

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import com.example.camera.IpCameraStatus
import com.example.model.ClockPreferencesState

/**
 * Top control bar in 2 organized, easy-to-tap rows per user request:
 * "右上のタイマーとか設定とかロックとか横に並んでるができれば2段に分けてあとボタンを押しやすくしてほしい"
 */
@Composable
fun TopControlBar(
    preferences: ClockPreferencesState,
    ipCameraStatus: IpCameraStatus,
    timerSeconds: Int,
    isStopwatchRunning: Boolean = false,
    stopwatchElapsedMillis: Long = 0L,
    isEspConnected: Boolean,
    isMusicPlaying: Boolean = false,
    isVoiceListening: Boolean = false,
    onOpenSettings: (SettingsTab) -> Unit,
    onOpenTimer: () -> Unit,
    onOpenStopwatch: () -> Unit = {},
    onOpenMusicPlayer: () -> Unit = {},
    onOpenIrRemote: () -> Unit = {},
    onTriggerVoiceAssistant: () -> Unit = {},
    onToggleNightMode: () -> Unit,
    onToggleKioskLock: () -> Unit,
    onUnlockLongPress: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(18.dp))
            .testTag("top_control_bar"),
        color = Color(0xEE111522),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End
        ) {
            // === Row 1: Active Tools & Media (タイマー, 音楽, リモコン, 音声操作) ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Desk Timer
                val timerActive = timerSeconds > 0
                TopLargeActionButton(
                    icon = Icons.Default.HourglassBottom,
                    label = if (timerActive) {
                        String.format(java.util.Locale.US, "%02d:%02d", timerSeconds / 60, timerSeconds % 60)
                    } else "タイマー",
                    testTag = "btn_top_timer",
                    tint = if (timerActive) Color(0xFFFFB300) else Color(0xFFE2E8F0),
                    isActive = timerActive,
                    activeColor = Color(0xFFFFB300),
                    showIndicator = timerActive,
                    indicatorColor = Color(0xFFFFB300),
                    onClick = {
                        onUserInteraction()
                        onOpenTimer()
                    }
                )

                // 1.5 Desk Stopwatch (ストップウォッチ)
                val swActive = isStopwatchRunning || stopwatchElapsedMillis > 0L
                val swSec = stopwatchElapsedMillis / 1000
                val swLabel = when {
                    isStopwatchRunning || stopwatchElapsedMillis > 0L -> {
                        val m = (swSec / 60).toInt()
                        val s = (swSec % 60).toInt()
                        val h = ((stopwatchElapsedMillis % 1000) / 100).toInt()
                        String.format(java.util.Locale.US, "%02d:%02d.%d", m, s, h)
                    }
                    else -> "ストップウォッチ"
                }
                TopLargeActionButton(
                    icon = Icons.Default.Timer,
                    label = swLabel,
                    testTag = "btn_top_stopwatch",
                    tint = if (swActive) Color(0xFF00E5FF) else Color(0xFFE2E8F0),
                    isActive = swActive,
                    activeColor = Color(0xFF00E5FF),
                    showIndicator = isStopwatchRunning,
                    indicatorColor = Color(0xFF00E5FF),
                    onClick = {
                        onUserInteraction()
                        onOpenStopwatch()
                    }
                )

                // 2. Music Player
                TopLargeActionButton(
                    icon = if (isMusicPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                    label = if (isMusicPlaying) "音楽再生中" else "音楽",
                    testTag = "btn_top_music_player",
                    tint = if (isMusicPlaying) preferences.colorPalette.primary else Color(0xFFE2E8F0),
                    isActive = isMusicPlaying,
                    activeColor = preferences.colorPalette.primary,
                    showIndicator = isMusicPlaying,
                    indicatorColor = preferences.colorPalette.primary,
                    onClick = {
                        onUserInteraction()
                        onOpenMusicPlayer()
                    }
                )

                // 3. Smart IR Remote
                TopLargeActionButton(
                    icon = Icons.Default.Sensors,
                    label = "リモコン",
                    testTag = "btn_top_ir_remote",
                    tint = Color(0xFF38BDF8),
                    onClick = {
                        onUserInteraction()
                        onOpenIrRemote()
                    }
                )

                // 4. Voice Assistant (OK クロック / OK Google)
                if (preferences.voiceAssistantEnabled) {
                    TopLargeActionButton(
                        icon = Icons.Default.Mic,
                        label = if (isVoiceListening) "音声認識中" else "音声",
                        testTag = "btn_top_voice_assistant",
                        tint = if (isVoiceListening) Color(0xFFFF5252) else Color(0xFF60A5FA),
                        isActive = isVoiceListening,
                        activeColor = Color(0xFFFF5252),
                        showIndicator = isVoiceListening,
                        indicatorColor = Color(0xFFFF5252),
                        onClick = {
                            onUserInteraction()
                            onTriggerVoiceAssistant()
                        }
                    )
                }
            }

            // === Row 2: System, Devices & Security (設定, カメラ, 常夜灯, ロック, センサー警告) ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. ESP Sensor Disconnected Warning (conditional)
                if (!isEspConnected && preferences.espSensorEnabled) {
                    TopLargeActionButton(
                        icon = Icons.Default.Sensors,
                        label = "センサー未接続",
                        testTag = "btn_top_esp_sensor",
                        tint = Color(0xFFFF7043),
                        isActive = true,
                        activeColor = Color(0xFFFF7043),
                        showIndicator = true,
                        indicatorColor = Color(0xFFFF7043),
                        onClick = {
                            onUserInteraction()
                            onOpenSettings(SettingsTab.ESP_SENSOR)
                        }
                    )
                }

                // 2. Settings button
                TopLargeActionButton(
                    icon = Icons.Default.Settings,
                    label = "設定",
                    testTag = "btn_top_settings",
                    tint = Color(0xFF00E5FF),
                    activeColor = Color(0xFF00E5FF),
                    onClick = {
                        onUserInteraction()
                        onOpenSettings(SettingsTab.FACE_PALETTE)
                    }
                )

                // 3. IP Camera shortcut
                TopLargeActionButton(
                    icon = if (ipCameraStatus.isRunning) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = if (ipCameraStatus.isRunning) ":${ipCameraStatus.port}" else "カメラ",
                    testTag = "btn_top_ip_cam",
                    tint = if (ipCameraStatus.isRunning) Color(0xFFFF5252) else Color(0xFF94A3B8),
                    isActive = ipCameraStatus.isRunning,
                    activeColor = Color(0xFFFF5252),
                    showIndicator = ipCameraStatus.isRunning,
                    indicatorColor = Color(0xFFFF1744),
                    onClick = {
                        onUserInteraction()
                        onOpenSettings(SettingsTab.IP_CAMERA)
                    }
                )

                // 4. Night stand mode
                TopLargeActionButton(
                    icon = Icons.Default.Bedtime,
                    label = "常夜灯",
                    testTag = "btn_top_night",
                    tint = if (preferences.isNightMode) Color(0xFFFFB300) else Color(0xFF94A3B8),
                    isActive = preferences.isNightMode,
                    activeColor = Color(0xFFFFB300),
                    onClick = {
                        onUserInteraction()
                        onToggleNightMode()
                    }
                )

                // 5. Kiosk lock (supports tap to toggle & long-press to unlock)
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (preferences.isKioskLocked) Color(0x3500E5FF) else Color(0x1EFFFFFF)
                        )
                        .border(
                            width = 1.dp,
                            color = if (preferences.isKioskLocked) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0x30FFFFFF),
                            shape = RoundedCornerShape(12.dp)
                        )
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
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("btn_top_kiosk_lock"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (preferences.isKioskLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "キオスクロック",
                            tint = if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (preferences.isKioskLocked) "ロック中" else "ロック",
                            color = if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enhanced, easy-to-press action button with comfortable touch target and clear visual cues.
 */
@Composable
private fun TopLargeActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color,
    isActive: Boolean = false,
    activeColor: Color = Color.White,
    showIndicator: Boolean = false,
    indicatorColor: Color = Color.Green,
    onClick: () -> Unit
) {
    val containerBg = if (isActive) activeColor.copy(alpha = 0.24f) else Color(0x20FFFFFF)
    val borderColor = if (isActive) activeColor.copy(alpha = 0.65f) else Color(0x30FFFFFF)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = tint),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (isActive) activeColor else Color(0xFFE2E8F0),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (label.contains(":")) FontFamily.Monospace else FontFamily.Default
        )
    }
}

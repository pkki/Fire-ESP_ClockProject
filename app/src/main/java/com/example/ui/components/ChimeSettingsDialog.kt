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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.ChimeSound
import com.example.model.ClockPreferencesState

@Composable
fun ChimeSettingsDialog(
    preferences: ClockPreferencesState,
    onDismiss: () -> Unit,
    onToggleHourlyChime: (Boolean) -> Unit,
    onToggleHalfHourlyChime: (Boolean) -> Unit,
    onSelectSound: (ChimeSound) -> Unit,
    onTestSound: (ChimeSound) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onHoursChange: (Int, Int) -> Unit
) {
    val accentColor = preferences.colorPalette.primary

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF5101014))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
                .testTag("chime_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CHIME SETTINGS",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "時報・チャイム機能",
                            color = Color(0xFFEEEEF2),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_chime_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = Color(0xFFAAAAAF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Hourly Chime Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "毎時時報 (00分)",
                            color = Color(0xFFEEEEF2),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "毎時間ちょうどに合成チャイムを鳴らします",
                            color = Color(0xFF888892),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = preferences.hourlyChimeEnabled,
                        onCheckedChange = onToggleHourlyChime,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF6E6E78),
                            uncheckedTrackColor = Color(0xFF222228)
                        ),
                        modifier = Modifier.testTag("switch_hourly_chime")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Half-Hourly Chime Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "30分チャイム",
                            color = Color(0xFFEEEEF2),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "毎時30分に控えめなシングルベルを鳴らします",
                            color = Color(0xFF888892),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = preferences.halfHourlyChimeEnabled,
                        onCheckedChange = onToggleHalfHourlyChime,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF6E6E78),
                            uncheckedTrackColor = Color(0xFF222228)
                        ),
                        modifier = Modifier.testTag("switch_half_hourly_chime")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sound Selection List
                Text(
                    text = "時報音色 (リアルタイム波形合成音源)",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                ChimeSound.values().forEach { sound ->
                    val isSelected = preferences.chimeSound == sound
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0x12FFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectSound(sound) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sound.displayName,
                                color = if (isSelected) accentColor else Color(0xFFE5E5E8),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = sound.description,
                                color = Color(0xFF7A7A85),
                                fontSize = 11.sp
                            )
                        }

                        // Test Play Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x24FFFFFF))
                                .clickable { onTestSound(sound) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "${sound.displayName}を試聴",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Active Hours window
                Text(
                    text = "鳴動時間帯: ${preferences.chimeStartHour}:00 〜 ${preferences.chimeEndHour}:00 (夜間自動ミュート)",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeWindowPresetButton("終日 (0-24時)", active = preferences.chimeStartHour == 0 && preferences.chimeEndHour == 23, accentColor) {
                        onHoursChange(0, 23)
                    }
                    TimeWindowPresetButton("昼間 (8-22時)", active = preferences.chimeStartHour == 8 && preferences.chimeEndHour == 22, accentColor) {
                        onHoursChange(8, 22)
                    }
                    TimeWindowPresetButton("日中 (9-18時)", active = preferences.chimeStartHour == 9 && preferences.chimeEndHour == 18, accentColor) {
                        onHoursChange(9, 18)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "音量",
                            tint = Color(0xFF888894),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "チャイム音量",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "${(preferences.chimeVolume * 100).toInt()}%",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = preferences.chimeVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0xFF2B2B33)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("slider_chime_volume")
                )
            }
        }
    }
}

@Composable
fun TimeWindowPresetButton(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentColor.copy(alpha = 0.2f) else Color(0x1AFFFFFF))
            .border(1.dp, if (active) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (active) accentColor else Color(0xFFB0B0BC),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

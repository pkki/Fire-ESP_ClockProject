package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.DeskTimerState
import com.example.audio.ChimeSound
import com.example.audio.ChimeSynthesizer
import com.example.model.ClockPreferencesState

private enum class TimerTab(val label: String, val iconText: String) {
    SETTING("時間設定", "⏱️"),
    PRESETS("プリセット", "⚡"),
    SOUND_DETAILS("サウンド・詳細", "🔔")
}

data class TimerQuickPreset(
    val title: String,
    val icon: String,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val defaultSound: ChimeSound = ChimeSound.CRYSTAL_BELL
) {
    val totalSeconds: Int get() = (hours * 3600) + (minutes * 60) + seconds
    val formattedDuration: String get() {
        return if (hours > 0) {
            "${hours}時間${if (minutes > 0) "${minutes}分" else ""}"
        } else if (minutes > 0) {
            "${minutes}分${if (seconds > 0) "${seconds}秒" else ""}"
        } else {
            "${seconds}秒"
        }
    }
}

private val QUICK_PRESETS = listOf(
    TimerQuickPreset("カップ麺", "🍜", 0, 3, 0),
    TimerQuickPreset("コーヒー抽出", "☕", 0, 4, 0),
    TimerQuickPreset("半熟ゆで卵", "🍳", 0, 6, 30),
    TimerQuickPreset("ポモドーロ集中", "⏱️", 0, 25, 0),
    TimerQuickPreset("ショート休憩", "🧘", 0, 5, 0),
    TimerQuickPreset("パワーナップ仮眠", "💤", 0, 15, 0),
    TimerQuickPreset("集中ワーク", "💻", 0, 45, 0),
    TimerQuickPreset("ストレッチ", "🏃", 0, 1, 0),
    TimerQuickPreset("1時間タイマー", "🕒", 1, 0, 0)
)

private val LABEL_SUGGESTIONS = listOf(
    "🍜 ラーメン", "☕ カフェ", "⏱️ ポモドーロ", "💤 仮眠", "📖 勉強", "💻 作業", "🍳 料理", "🏋️ 筋トレ"
)

@Composable
fun DeskTimerDialog(
    timerState: DeskTimerState,
    preferences: ClockPreferencesState,
    onDismiss: () -> Unit,
    onSetTimerDetails: (hours: Int, minutes: Int, seconds: Int, label: String, sound: ChimeSound, autoRepeat: Boolean, start: Boolean) -> Unit,
    onAddMinutes: (Int) -> Unit,
    onAddSeconds: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onUpdateSettings: (label: String?, sound: ChimeSound?, autoRepeat: Boolean?) -> Unit = { _, _, _ -> },
    onSwitchToStopwatch: (() -> Unit)? = null
) {
    val accentColor = preferences.colorPalette.primary

    // Editable state for precise setting
    var inputHours by remember(timerState.initialSeconds) {
        mutableIntStateOf(if (timerState.remainingSeconds > 0) timerState.remainingSeconds / 3600 else 0)
    }
    var inputMinutes by remember(timerState.initialSeconds) {
        mutableIntStateOf(
            if (timerState.remainingSeconds > 0) (timerState.remainingSeconds % 3600) / 60
            else if (timerState.initialSeconds == 0) 3
            else 0
        )
    }
    var inputSeconds by remember(timerState.initialSeconds) {
        mutableIntStateOf(if (timerState.remainingSeconds > 0) timerState.remainingSeconds % 60 else 0)
    }

    var selectedLabel by remember(timerState.label) { mutableStateOf(timerState.label) }
    var selectedSound by remember(timerState.chimeSound) { mutableStateOf(timerState.chimeSound) }
    var autoRepeatEnabled by remember(timerState.autoRepeat) { mutableStateOf(timerState.autoRepeat) }

    var activeTab by remember { mutableStateOf(TimerTab.SETTING) }

    val formattedRemaining = timerState.formattedTime
    val isTimerActive = timerState.remainingSeconds > 0 || timerState.isRunning

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF5101018))
                .border(1.2.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(20.dp)
                .testTag("desk_timer_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Header with Mode Switcher & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Segmented Tab Switcher (タイマー / ストップウォッチ)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Row(modifier = Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = accentColor.copy(alpha = 0.25f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.HourglassBottom,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = accentColor
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("タイマー", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (onSwitchToStopwatch != null) {
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .clickable { onSwitchToStopwatch() }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Timer,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color(0xFF94A3B8)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("ストップウォッチ", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        if (timerState.autoRepeat) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x3300E5FF))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("🔁 ループ", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_timer_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = Color(0xFFAAAAAF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Countdown Display Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22181824))
                        .border(1.dp, if (timerState.isFinished) Color(0xFFFF5252) else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Current Label & Sound Tag
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏷️ ${timerState.label.ifBlank { "タイマー" }}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "🔔 ${timerState.chimeSound.displayName}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Large digits
                        Text(
                            text = formattedRemaining,
                            color = when {
                                timerState.isFinished -> Color(0xFFFF5252)
                                timerState.isRunning -> accentColor
                                isTimerActive -> Color(0xFFE2E8F0)
                                else -> Color(0xFF64748B)
                            },
                            fontSize = 54.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )

                        // Progress Bar
                        if (timerState.initialSeconds > 0) {
                            LinearProgressIndicator(
                                progress = { timerState.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (timerState.isFinished) Color(0xFFFF5252) else accentColor,
                                trackColor = Color(0x33FFFFFF)
                            )
                        }

                        if (timerState.isFinished) {
                            Text(
                                text = "🔔 時間になりました！アラーム音を再生しました",
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Tab Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimerTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tab.iconText} ${tab.label}",
                                color = if (isSelected) Color.Black else Color(0xFFDDDDDE),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        TimerTab.SETTING -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Direct Numeric Steppers (Hours, Minutes, Seconds)
                                item {
                                    Text(
                                        text = "⏱️ 目標時間を設定（時・分・秒）",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TimerUnitStepper(
                                            label = "時間 (H)",
                                            value = inputHours,
                                            onValueChange = { inputHours = it.coerceIn(0, 23) },
                                            modifier = Modifier.weight(1f),
                                            accentColor = accentColor
                                        )
                                        TimerUnitStepper(
                                            label = "分 (M)",
                                            value = inputMinutes,
                                            onValueChange = { inputMinutes = it.coerceIn(0, 59) },
                                            modifier = Modifier.weight(1f),
                                            accentColor = accentColor
                                        )
                                        TimerUnitStepper(
                                            label = "秒 (S)",
                                            value = inputSeconds,
                                            onValueChange = { inputSeconds = it.coerceIn(0, 59) },
                                            modifier = Modifier.weight(1f),
                                            accentColor = accentColor
                                        )
                                    }
                                }

                                // Quick Delta Addition Row
                                item {
                                    Text(
                                        text = "⚡ クイック加算 / 微調整",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item { TimerDeltaChip("+10秒", accentColor) { inputSeconds = (inputSeconds + 10).coerceAtMost(59); onAddSeconds(10) } }
                                        item { TimerDeltaChip("+30秒", accentColor) { inputSeconds = (inputSeconds + 30).coerceAtMost(59); onAddSeconds(30) } }
                                        item { TimerDeltaChip("+1分", accentColor) { inputMinutes = (inputMinutes + 1).coerceAtMost(59); onAddMinutes(1) } }
                                        item { TimerDeltaChip("+3分", accentColor) { inputMinutes = (inputMinutes + 3).coerceAtMost(59); onAddMinutes(3) } }
                                        item { TimerDeltaChip("+5分", accentColor) { inputMinutes = (inputMinutes + 5).coerceAtMost(59); onAddMinutes(5) } }
                                        item { TimerDeltaChip("+10分", accentColor) { inputMinutes = (inputMinutes + 10).coerceAtMost(59); onAddMinutes(10) } }
                                        item { TimerDeltaChip("+15分", accentColor) { inputMinutes = (inputMinutes + 15).coerceAtMost(59); onAddMinutes(15) } }
                                        item { TimerDeltaChip("+30分", accentColor) { inputMinutes = (inputMinutes + 30).coerceAtMost(59); onAddMinutes(30) } }
                                        item { TimerDeltaChip("+1時間", accentColor) { inputHours = (inputHours + 1).coerceAtMost(23); onAddMinutes(60) } }
                                    }
                                }

                                // Quick Label Tag Suggestions
                                item {
                                    Text(
                                        text = "🏷️ タイマー名・用途タグ",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(LABEL_SUGGESTIONS) { suggestion ->
                                            val isSel = selectedLabel == suggestion
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) accentColor else Color(0x18FFFFFF))
                                                    .border(1.dp, if (isSel) accentColor else Color(0x2EFFFFFF), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedLabel = suggestion
                                                        onUpdateSettings(suggestion, null, null)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = suggestion,
                                                    color = if (isSel) Color.Black else Color(0xFFEEEEF2),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TimerTab.PRESETS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text(
                                        text = "よく使われるタイマープリセットをワンタップでセット＆スタートできます",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                items(QUICK_PRESETS) { preset ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x18FFFFFF))
                                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                                            .clickable {
                                                inputHours = preset.hours
                                                inputMinutes = preset.minutes
                                                inputSeconds = preset.seconds
                                                selectedLabel = "${preset.icon} ${preset.title}"
                                                onSetTimerDetails(
                                                    preset.hours,
                                                    preset.minutes,
                                                    preset.seconds,
                                                    "${preset.icon} ${preset.title}",
                                                    preset.defaultSound,
                                                    autoRepeatEnabled,
                                                    true
                                                )
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(preset.icon, fontSize = 20.sp)
                                            Column {
                                                Text(
                                                    text = preset.title,
                                                    color = Color(0xFFF1F5F9),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = preset.formattedDuration,
                                                    color = accentColor,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                inputHours = preset.hours
                                                inputMinutes = preset.minutes
                                                inputSeconds = preset.seconds
                                                selectedLabel = "${preset.icon} ${preset.title}"
                                                onSetTimerDetails(
                                                    preset.hours,
                                                    preset.minutes,
                                                    preset.seconds,
                                                    "${preset.icon} ${preset.title}",
                                                    preset.defaultSound,
                                                    autoRepeatEnabled,
                                                    true
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentColor,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("開始", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        TimerTab.SOUND_DETAILS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Auto-repeat Switch
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x18FFFFFF))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.Repeat, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                                Text("自動ループ再生 (オートリピート)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "タイマー終了時にチャイムを鳴らし、再度設定秒数から自動スタート",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = autoRepeatEnabled,
                                            onCheckedChange = {
                                                autoRepeatEnabled = it
                                                onUpdateSettings(null, null, it)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = accentColor
                                            )
                                        )
                                    }
                                }

                                // Sound Selection List
                                item {
                                    Text(
                                        text = "🔔 終了時アラーム音の選択",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                items(ChimeSound.entries) { sound ->
                                    val isSelected = selectedSound == sound
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color(0x28FFFFFF) else Color(0x10FFFFFF))
                                            .border(1.dp, if (isSelected) accentColor else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedSound = sound
                                                onUpdateSettings(null, sound, null)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sound.displayName,
                                                color = if (isSelected) accentColor else Color(0xFFE2E8F0),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = sound.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                ChimeSynthesizer.playChime(sound, preferences.chimeVolume)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0x22FFFFFF),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "試聴", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("試聴", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Bottom Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    Button(
                        onClick = onReset,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x22FFFFFF),
                            contentColor = Color(0xFFDDDDDE)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "リセット", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("リセット")
                    }

                    // Apply and Start (or Pause/Resume)
                    val totalInput = (inputHours * 3600) + (inputMinutes * 60) + inputSeconds

                    if (isTimerActive) {
                        Button(
                            onClick = onTogglePause,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (timerState.isRunning) Color(0xFFEAB308) else accentColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (timerState.isRunning) "一時停止" else "再開",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (timerState.isRunning) "一時停止" else "再開", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                onSetTimerDetails(
                                    inputHours,
                                    inputMinutes,
                                    inputSeconds,
                                    selectedLabel,
                                    selectedSound,
                                    autoRepeatEnabled,
                                    true
                                )
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            enabled = totalInput > 0
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "スタート", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("タイマー開始", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerUnitStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x18FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // + Button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable { onValueChange(value + 1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = String.format(java.util.Locale.US, "%02d", value),
            color = accentColor,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // - Button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable { onValueChange((value - 1).coerceAtLeast(0)) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Remove, contentDescription = "-1", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TimerDeltaChip(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x18FFFFFF))
            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFEEEEF2),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

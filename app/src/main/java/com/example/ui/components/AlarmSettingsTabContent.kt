package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ClockViewModel
import com.example.model.ClockPreferencesState

@Composable
fun AlarmSettingsTabContent(
    viewModel: ClockViewModel,
    preferences: ClockPreferencesState,
    modifier: Modifier = Modifier
) {
    val espSensorData by viewModel.espSensorData.collectAsState()
    val lastBtnEvent by viewModel.lastPhysicalButtonEvent.collectAsState()

    var alarmEnabled by remember(preferences.alarmEnabled) { mutableStateOf(preferences.alarmEnabled) }
    var alarmHour by remember(preferences.alarmHour) { mutableIntStateOf(preferences.alarmHour) }
    var alarmMinute by remember(preferences.alarmMinute) { mutableIntStateOf(preferences.alarmMinute) }
    var alarmDays by remember(preferences.alarmDays) { mutableStateOf(preferences.alarmDays) }
    var alarmSoundType by remember(preferences.alarmSoundType) { mutableStateOf(preferences.alarmSoundType) }
    var alarmVolume by remember(preferences.alarmVolume) { mutableFloatStateOf(preferences.alarmVolume) }
    var alarmSnoozeMinutes by remember(preferences.alarmSnoozeMinutes) { mutableIntStateOf(preferences.alarmSnoozeMinutes) }
    var alarmVibration by remember(preferences.alarmVibration) { mutableStateOf(preferences.alarmVibration) }
    var alarmTriggerIr by remember(preferences.alarmTriggerIr) { mutableStateOf(preferences.alarmTriggerIr) }

    fun saveAlarmSettings() {
        viewModel.updateAlarmPreferences(
            enabled = alarmEnabled,
            hour = alarmHour,
            minute = alarmMinute,
            days = alarmDays,
            soundType = alarmSoundType,
            volume = alarmVolume,
            snoozeMinutes = alarmSnoozeMinutes,
            vibration = alarmVibration,
            triggerIr = alarmTriggerIr
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Alarm Master Switch & Time Picker Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161822)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (alarmEnabled) Color(0xFFFF5252).copy(alpha = 0.6f) else Color(0x33FFFFFF)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (alarmEnabled) Color(0xFFFF3D00) else Color(0xFF37474F)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (alarmEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "目覚ましアラーム",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (alarmEnabled) "設定時刻にアラームが鳴動します" else "アラームは停止中（無効）です",
                                    fontSize = 12.sp,
                                    color = if (alarmEnabled) Color(0xFFFF8A80) else Color(0xFF90A4AE)
                                )
                            }
                        }

                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = {
                                alarmEnabled = it
                                saveAlarmSettings()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF3D00)
                            ),
                            modifier = Modifier.testTag("alarm_master_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Time Setter Row
                    Surface(
                        color = Color(0xFF0F1017),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "アラーム時刻",
                                fontSize = 12.sp,
                                color = Color(0xFFB0BEC5)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Hour Control
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            alarmHour = (alarmHour + 1) % 24
                                            saveAlarmSettings()
                                        }
                                    ) {
                                        Text("▲", color = Color(0xFF00E5FF), fontSize = 16.sp)
                                    }
                                    Text(
                                        text = "%02d".format(alarmHour),
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (alarmEnabled) Color.White else Color(0xFF78909C)
                                    )
                                    IconButton(
                                        onClick = {
                                            alarmHour = if (alarmHour > 0) alarmHour - 1 else 23
                                            saveAlarmSettings()
                                        }
                                    ) {
                                        Text("▼", color = Color(0xFF00E5FF), fontSize = 16.sp)
                                    }
                                }

                                Text(
                                    text = ":",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                // Minute Control
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            alarmMinute = (alarmMinute + 5) % 60
                                            saveAlarmSettings()
                                        }
                                    ) {
                                        Text("▲", color = Color(0xFF00E5FF), fontSize = 16.sp)
                                    }
                                    Text(
                                        text = "%02d".format(alarmMinute),
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (alarmEnabled) Color.White else Color(0xFF78909C)
                                    )
                                    IconButton(
                                        onClick = {
                                            alarmMinute = if (alarmMinute >= 5) alarmMinute - 5 else 55
                                            saveAlarmSettings()
                                        }
                                    ) {
                                        Text("▼", color = Color(0xFF00E5FF), fontSize = 16.sp)
                                    }
                                }
                            }

                            // Repeat Days of Week
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "繰り返し曜日",
                                fontSize = 12.sp,
                                color = Color(0xFF90A4AE)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val dayNames = listOf("月" to 1, "火" to 2, "水" to 3, "木" to 4, "金" to 5, "土" to 6, "日" to 7)
                                dayNames.forEach { (name, dayNum) ->
                                    val isSelected = alarmDays.contains(dayNum)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) Color(0xFFFF5252) else Color(0xFF263238),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val newDays = alarmDays.toMutableSet()
                                                if (isSelected) {
                                                    newDays.remove(dayNum)
                                                } else {
                                                    newDays.add(dayNum)
                                                }
                                                alarmDays = newDays
                                                saveAlarmSettings()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color(0xFFB0BEC5)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Sound & Volume Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161822)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "アラーム音 & 音量調節",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sound Type Chips
                    val sounds = listOf(
                        "DIGITAL_BEEP" to "電子音 (ピピピッ)",
                        "TWIN_BELL" to "ツインベル (ジリリリ)",
                        "MELODY" to "目覚ましメロディ",
                        "JAPANESE" to "和風鐘の音",
                        "CHIME" to "ウエストミンスター"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sounds.forEach { (typeKey, label) ->
                            val isSelected = alarmSoundType == typeKey
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0x3300E5FF) else Color(0xFF0F1017),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        alarmSoundType = typeKey
                                        saveAlarmSettings()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF37474F)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color.White
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.testAlarmSound(typeKey, alarmVolume) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("試聴", fontSize = 11.sp, color = Color(0xFF00E5FF))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Volume Slider (鳴らす時の音量)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "アラーム鳴動音量",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "${(alarmVolume * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }

                    Slider(
                        value = alarmVolume,
                        onValueChange = {
                            alarmVolume = it
                            saveAlarmSettings()
                        },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E676),
                            activeTrackColor = Color(0xFF00E676),
                            inactiveTrackColor = Color(0xFF263238)
                        ),
                        modifier = Modifier.testTag("alarm_volume_slider")
                    )

                    Text(
                        text = "※ スライダー操作時に現在のスマホ音量は変わりません。アラーム鳴動時のみこの音量で再生され、停止後に元の音量へ自動復元されます。",
                        fontSize = 11.sp,
                        color = Color(0xFF78909C)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Snooze & Vibration Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("スヌーズ時間", fontSize = 14.sp, color = Color.White)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val snoozeOptions = listOf(3, 5, 10, 15)
                            snoozeOptions.forEach { mins ->
                                val isCur = alarmSnoozeMinutes == mins
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCur) Color(0xFFFFB74D) else Color(0xFF263238),
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            alarmSnoozeMinutes = mins
                                            saveAlarmSettings()
                                        }
                                ) {
                                    Text(
                                        text = "${mins}分",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCur) Color.Black else Color(0xFFCFD8DC),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Vibration Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = Color(0xFFB0BEC5),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("バイブレーション振動", fontSize = 14.sp, color = Color.White)
                        }
                        Switch(
                            checked = alarmVibration,
                            onCheckedChange = {
                                alarmVibration = it
                                saveAlarmSettings()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Smart IR Lighting Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("アラーム時 照明ON連動", fontSize = 14.sp, color = Color.White)
                                Text("鳴動時に学習済み赤外線信号を送信", fontSize = 11.sp, color = Color(0xFF90A4AE))
                            }
                        }
                        Switch(
                            checked = alarmTriggerIr,
                            onCheckedChange = {
                                alarmTriggerIr = it
                                saveAlarmSettings()
                            }
                        )
                    }
                }
            }
        }

        // 2. MQ-2 Fire & Smoke Emergency Detection Card (ユーザー要望: 火災検知機能)
        item {
            var fireEnabled by remember(preferences.fireAlertEnabled) { mutableStateOf(preferences.fireAlertEnabled) }
            var fireSound by remember(preferences.fireAlertSoundEnabled) { mutableStateOf(preferences.fireAlertSoundEnabled) }
            var fireVibration by remember(preferences.fireAlertVibration) { mutableStateOf(preferences.fireAlertVibration) }
            var fireTts by remember(preferences.fireAlertVoiceTts) { mutableStateOf(preferences.fireAlertVoiceTts) }
            var mq2Threshold by remember(preferences.mq2SensitivityThreshold) { mutableIntStateOf(preferences.mq2SensitivityThreshold) }

            fun saveFireSettings() {
                viewModel.updateFireAlertPreferences(
                    enabled = fireEnabled,
                    soundEnabled = fireSound,
                    vibration = fireVibration,
                    voiceTts = fireTts,
                    sensitivityThreshold = mq2Threshold
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF200F12)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (fireEnabled) Color(0xFFFF5252) else Color(0x33FFFFFF)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (fireEnabled) Color(0xFFFF3D00) else Color(0xFF37474F)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MQ-2 火災・煙検知アラート",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "煙・可燃性ガス検知時にタブレットへ「火事です」警報を発報",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF8A80)
                                )
                            }
                        }

                        Switch(
                            checked = fireEnabled,
                            onCheckedChange = {
                                fireEnabled = it
                                saveFireSettings()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF5252),
                                checkedTrackColor = Color(0x66FF5252)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-time MQ-2 sensor status badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (espSensorData.mq2SmokeDetected) Color(0xFFD32F2F) else Color(0xFF2C1618),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = if (espSensorData.mq2SmokeDetected) Color.Yellow else Color(0xFFFF8A80),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "MQ-2 センサー現在値: ${espSensorData.mq2RawValue ?: "待機中 (未接続)"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = espSensorData.mq2StatusLabel,
                                        fontSize = 11.sp,
                                        color = if (espSensorData.mq2SmokeDetected) Color.Yellow else Color(0xFFFFAB91)
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.testFireAlert() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF3D00),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("テスト発報", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("緊急サイレン音", fontSize = 14.sp, color = Color.White)
                        Switch(
                            checked = fireSound,
                            onCheckedChange = {
                                fireSound = it
                                saveFireSettings()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("バイブレーション振動", fontSize = 14.sp, color = Color.White)
                        Switch(
                            checked = fireVibration,
                            onCheckedChange = {
                                fireVibration = it
                                saveFireSettings()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("日本語音声読み上げ", fontSize = 14.sp, color = Color.White)
                            Text("「火事です！火事です！」と自動アナウンス", fontSize = 11.sp, color = Color(0xFF90A4AE))
                        }
                        Switch(
                            checked = fireTts,
                            onCheckedChange = {
                                fireTts = it
                                saveFireSettings()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sensitivity slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("検知閾値 (感度調整)", fontSize = 13.sp, color = Color(0xFFCFD8DC))
                        Text("${mq2Threshold}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A80))
                    }

                    Slider(
                        value = mq2Threshold.toFloat(),
                        onValueChange = {
                            mq2Threshold = it.toInt()
                            saveFireSettings()
                        },
                        valueRange = 300f..2500f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF5252),
                            activeTrackColor = Color(0xFFFF5252),
                            inactiveTrackColor = Color(0xFF37474F)
                        )
                    )
                }
            }
        }

        // 3. PCF8574P Physical Buttons Guide & Status Card (6-Button Specification)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141A22)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PCF8574P 物理ボタン (厳選6Key仕様)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "I2Cアドレス: 0x20 | 5機能 + 1アラーム停止ボタン",
                                    fontSize = 12.sp,
                                    color = Color(0xFF00E676)
                                )
                            }
                        }

                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (espSensorData.isConnected) Color(0x3300E676) else Color(0x33FF5252)
                        ) {
                            Text(
                                text = if (espSensorData.isConnected) "ESP32 接続中" else "ESP32 未接続",
                                color = if (espSensorData.isConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ESP32-C3のSDA(GPIO4) / SCL(GPIO5)にPCF8574Pを接続し、P0〜P5ピンとGNDの間にボタンを配置します。P0は大ボタンを推奨（アラーム・タイマー・火災警報の瞬時停止）。",
                        fontSize = 12.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6 Physical Buttons Function Grid (P0: Alarm/Fire Stop + P1-P5)
                    val buttonSpecs = listOf(
                        Triple(0, "ALARM_STOP", "🚨 アラーム・火災警報停止 (大ボタン推奨) - 目覚ましやタイマー、火災警報を即座に停止"),
                        Triple(1, "NIGHT_MODE", "🌙 夜間常夜灯モード - 画面を暗くして常夜灯表示切替"),
                        Triple(2, "NEXT_FACE", "🎨 文字盤切替 - デジタル/アナログ/ニキシー管など循環"),
                        Triple(3, "TIMER_START_STOP", "⏱️ デスクタイマー - 5分間の集中タイマー開始/リセット"),
                        Triple(4, "LIGHT_TOGGLE", "💡 照明ON/OFF - 学習済み赤外線リモコン信号を送信"),
                        Triple(5, "TIME_CHIME", "🔔 現在時報チャイム - 今の時刻チャイムを手動再生")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        buttonSpecs.forEach { (pin, name, desc) ->
                            val isP0 = pin == 0
                            val isLastPressed = lastBtnEvent?.buttonId == pin

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isLastPressed -> Color(0x4400E676)
                                    isP0 -> Color(0xFF1E2730)
                                    else -> Color(0xFF0F1218)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isLastPressed) Color(0xFF00E676) else if (isP0) Color(0x88FF5252) else Color(0x22FFFFFF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        // Trigger software emulation of this physical button
                                        viewModel.handlePhysicalButton(pin, name)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isP0) Color(0xFFFF3D00) else Color(0xFF00E676)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "P$pin",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isP0) Color(0xFFFF8A80) else Color.White
                                            )
                                            if (isP0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFFF3D00)
                                                ) {
                                                    Text(
                                                        text = "MAIN STOP KEY",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = desc,
                                            fontSize = 11.sp,
                                            color = Color(0xFFB0BEC5)
                                        )
                                    }

                                    Text(
                                        text = "テスト押下",
                                        fontSize = 10.sp,
                                        color = Color(0xFF00E5FF),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.DeskStopwatchState
import com.example.StopwatchLap
import com.example.model.ClockPreferencesState

@Composable
fun DeskStopwatchDialog(
    stopwatchState: DeskStopwatchState,
    preferences: ClockPreferencesState,
    onDismiss: () -> Unit,
    onToggleStartPause: () -> Unit,
    onRecordLap: () -> Unit,
    onReset: () -> Unit,
    onSwitchToTimer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = preferences.colorPalette.primary

    // Pulsing animation when running
    val infiniteTransition = rememberInfiniteTransition(label = "stopwatch_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stopwatch_ring"
    )

    val isRunning = stopwatchState.isRunning
    val hasTime = stopwatchState.elapsedMillis > 0L

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D121F))
                .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
                .testTag("dialog_desk_stopwatch")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Switch between Timer & Stopwatch + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented Tab Switcher (タイマー / ストップウォッチ)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Row(modifier = Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (onSwitchToTimer != null) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .clickable { onSwitchToTimer() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.HourglassBottom,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFF94A3B8)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("タイマー", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = accentColor.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = accentColor
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("ストップウォッチ", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Main Stopwatch Big Digital Display
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isRunning) accentColor.copy(alpha = 0.6f) else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isRunning -> accentColor.copy(alpha = 0.2f)
                                hasTime -> Color(0x33F59E0B)
                                else -> Color(0x2294A3B8)
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = when {
                                    isRunning -> "● 計測中"
                                    hasTime -> "❚❚ 一時停止中"
                                    else -> "○ 待機中"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isRunning -> accentColor
                                    hasTime -> Color(0xFFFBBF24)
                                    else -> Color(0xFF94A3B8)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Large Digital Display: MM : SS . mm
                        val totalSec = stopwatchState.elapsedMillis / 1000
                        val minutes = (totalSec / 60).toInt()
                        val seconds = (totalSec % 60).toInt()
                        val hundredths = ((stopwatchState.elapsedMillis % 1000) / 10).toInt()
                        val hours = (totalSec / 3600).toInt()

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (hours > 0) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%02d:", hours),
                                    fontSize = 42.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds),
                                fontSize = if (hours > 0) 42.sp else 52.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = String.format(java.util.Locale.US, ".%02d", hundredths),
                                fontSize = if (hours > 0) 24.sp else 30.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(bottom = if (hours > 0) 4.dp else 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Primary Control Buttons (Play/Pause, Lap, Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    OutlinedButton(
                        onClick = onReset,
                        enabled = hasTime || isRunning,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE2E8F0),
                            disabledContentColor = Color(0xFF64748B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasTime || isRunning) Color(0x66FFFFFF) else Color(0x22FFFFFF)
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("リセット", fontWeight = FontWeight.SemiBold)
                    }

                    // Lap Button
                    Button(
                        onClick = onRecordLap,
                        enabled = hasTime && isRunning,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            disabledContainerColor = Color(0xFF1E293B)
                        )
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (stopwatchState.laps.isEmpty()) "ラップ" else "ラップ (${stopwatchState.laps.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Start / Pause Toggle Button
                    Button(
                        onClick = onToggleStartPause,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFEF4444) else accentColor
                        )
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "一時停止" else if (hasTime) "再開" else "スタート",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Lap History Table
                AnimatedVisibility(visible = stopwatchState.laps.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ラップタイム履歴 (${stopwatchState.laps.size}件)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )

                            // Copy Laps to Clipboard
                            TextButton(
                                onClick = {
                                    val sb = StringBuilder("【ストップウォッチ ラップ記録】\n")
                                    stopwatchState.laps.forEach { lap ->
                                        sb.append("ラップ ${lap.lapIndex}: ${lap.formattedLapTime} (累計: ${lap.formattedOverallTime})\n")
                                    }
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Stopwatch Laps", sb.toString()))
                                    Toast.makeText(context, "ラップ履歴をコピーしました", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor)
                                Spacer(Modifier.width(4.dp))
                                Text("コピー", fontSize = 11.sp, color = accentColor)
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                items(stopwatchState.laps, key = { it.lapIndex }) { lap ->
                                    LapRowItem(lap = lap, accentColor = accentColor)
                                    if (lap != stopwatchState.laps.last()) {
                                        HorizontalDivider(
                                            color = Color(0x1AFFFFFF),
                                            modifier = Modifier.padding(horizontal = 12.dp)
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
}

@Composable
private fun LapRowItem(
    lap: StopwatchLap,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ラップ %02d".format(lap.lapIndex),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )

            if (lap.isBest) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0x3310B981)
                ) {
                    Text(
                        text = "最速",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            } else if (lap.isWorst) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0x33EF4444)
                ) {
                    Text(
                        text = "最遅",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "+${lap.formattedLapTime}",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = when {
                    lap.isBest -> Color(0xFF10B981)
                    lap.isWorst -> Color(0xFFEF4444)
                    else -> Color.White
                }
            )

            Text(
                text = lap.formattedOverallTime,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

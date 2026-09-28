package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.DeskStopwatchState

/**
 * High-visibility HUD banner for active Desk Stopwatch on the clock display.
 */
@Composable
fun DeskStopwatchHudBanner(
    stopwatchState: DeskStopwatchState,
    accentColor: Color,
    onOpenStopwatchDialog: () -> Unit,
    onTogglePause: () -> Unit,
    onRecordLap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = stopwatchState.isRunning || stopwatchState.elapsedMillis > 0L

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "stopwatch_hud_pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        val isRunning = stopwatchState.isRunning

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = if (isRunning) {
                            listOf(
                                Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                                accentColor.copy(alpha = pulseAlpha)
                            )
                        } else {
                            listOf(
                                Color(0xFF64748B),
                                Color(0xFF475569)
                            )
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onOpenStopwatchDialog() }
                .testTag("desk_stopwatch_hud_banner"),
            color = Color(0xF00D1322),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Stopwatch Icon + Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0x3364748B)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isRunning) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ストップウォッチ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                            )
                            if (stopwatchState.laps.isNotEmpty()) {
                                Text(
                                    text = " • ラップ ${stopwatchState.laps.size}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        // Big Digital Time: MM:SS.mm
                        val totalSec = stopwatchState.elapsedMillis / 1000
                        val minutes = (totalSec / 60).toInt()
                        val seconds = (totalSec % 60).toInt()
                        val hundredths = ((stopwatchState.elapsedMillis % 1000) / 10).toInt()
                        val hours = (totalSec / 3600).toInt()

                        Row(verticalAlignment = Alignment.Bottom) {
                            if (hours > 0) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%02d:", hours),
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds),
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = String.format(java.util.Locale.US, ".%02d", hundredths),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }

                // Right: Quick Controls (Pause/Resume, Lap, Reset)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Record Lap Button
                    if (isRunning) {
                        IconButton(
                            onClick = onRecordLap,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF2563EB).copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "ラップ記録",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Play / Pause Button
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isRunning) Color(0xFFEF4444).copy(alpha = 0.25f)
                                else Color(0xFF00E5FF).copy(alpha = 0.25f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "一時停止" else "再開",
                            tint = if (isRunning) Color(0xFFF87171) else Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Reset Button
                    if (!isRunning && stopwatchState.elapsedMillis > 0L) {
                        IconButton(
                            onClick = onReset,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF334155), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "リセット",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

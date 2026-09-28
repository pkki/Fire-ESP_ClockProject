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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DeskTimerState

/**
 * Prominent HUD banner displayed on the desk clock screen when a timer is running or active.
 * User requirement: "タイマー動いてるときはできれば少し大きくタイマーを時計の何処かに表示"
 */
@Composable
fun DeskTimerHudBanner(
    timerState: DeskTimerState,
    accentColor: Color,
    onOpenTimerDialog: () -> Unit,
    onTogglePause: () -> Unit,
    onAddMinute: () -> Unit,
    onReset: () -> Unit,
    onDismissFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTimerActive = timerState.isRunning || timerState.remainingSeconds > 0 || timerState.isFinished

    AnimatedVisibility(
        visible = isTimerActive,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "timer_pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        val isFinished = timerState.isFinished

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isFinished) 2.dp else 1.2.dp,
                    color = if (isFinished) Color(0xFFFF5252) else accentColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    if (isFinished) {
                        onDismissFinished()
                    } else {
                        onOpenTimerDialog()
                    }
                }
                .testTag("desk_timer_hud_banner"),
            color = if (isFinished) Color(0xEE2A0808) else Color(0xDE11141C),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Timer Icon & Label & Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFinished) Color(0xFFFF5252).copy(alpha = 0.25f)
                                    else accentColor.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFinished) Icons.Default.NotificationsActive else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isFinished) Color(0xFFFF5252) else accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = timerState.label.ifBlank { "タイマー" },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (timerState.autoRepeat) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "🔁",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = when {
                                    isFinished -> "時間になりました！"
                                    timerState.isRunning -> "計測中 (タップで詳細設定)"
                                    else -> "一時停止中"
                                },
                                color = if (isFinished) Color(0xFFFF8A80) else Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Center/Right: Prominent Large Timer Digits
                    Text(
                        text = if (isFinished) "00:00" else timerState.formattedTime,
                        color = if (isFinished) Color(0xFFFF5252) else accentColor.copy(alpha = if (timerState.isRunning) pulseAlpha else 1f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Right Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isFinished) {
                            // Dismiss button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFF5252))
                                    .clickable { onDismissFinished() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "停止",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // +1m Quick Add Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { onAddMinute() }
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+1分",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Play / Pause Button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.25f))
                                    .clickable { onTogglePause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (timerState.isRunning) "一時停止" else "再開",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Reset / Stop Button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .clickable { onReset() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "リセット",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Smooth Progress Bar
                if (!isFinished && timerState.initialSeconds > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { timerState.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor,
                        trackColor = Color(0x33FFFFFF)
                    )
                }
            }
        }
    }
}

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VoiceAssistantState

/**
 * High-polish Voice Assistant Floating HUD Overlay
 */
@Composable
fun VoiceAssistantOverlay(
    state: VoiceAssistantState,
    onDismiss: () -> Unit,
    onSendTextCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isActivelyListening || state.isSpeaking,
        enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 2 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF2101524),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF4285F4), // Google Blue
                            Color(0xFFEA4335), // Google Red
                            Color(0xFFFBBC05), // Google Yellow
                            Color(0xFF34A853)  // Google Green
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color(0x664285F4))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Top Bar: Animated Assistant Glowing Visualizer & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistantVoiceWaveIndicator(
                                rmsLevel = state.soundLevelRms,
                                isSpeaking = state.isSpeaking
                            )

                            Column {
                                Text(
                                    text = if (state.isSpeaking) "🗣️ 音声アシスタント応答中" else "🎙️ OK Google / OK クロック",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.isSpeaking) "音声で回答中..." else "音声コマンドを受付中",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "閉じる",
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Recognized User Speech transcription
                    if (state.recognizedText.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FFFFFF))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "「${state.recognizedText}」",
                                color = Color(0xFFE0F2FE),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Assistant Reply or Prompt
                    if (state.assistantResponseText.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1838BDF8))
                                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFF34A853),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.assistantResponseText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Shortcut Command Chips (for easy tap or silent command)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "🕒 いま何時？" to "いま何時？",
                            "☀️ 今日の天気" to "今日の天気は？",
                            "🎵 音楽かけて" to "音楽かけて",
                            "⏱ 3分タイマー" to "3分タイマー",
                            "💡 電気つけて" to "電気つけて",
                            "🌙 夜間モード" to "夜間モードにして"
                        ).forEach { (label, command) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x1EFFFFFF))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                                    .clickable { onSendTextCommand(command) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = Color(0xFFDDDDDD),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Google Assistant 4-Dot / Wave Visualizer
 */
@Composable
private fun AssistantVoiceWaveIndicator(
    rmsLevel: Float,
    isSpeaking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "assistant_pulse")

    val dotColors = listOf(
        Color(0xFF4285F4), // Blue
        Color(0xFFEA4335), // Red
        Color(0xFFFBBC05), // Yellow
        Color(0xFF34A853)  // Green
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dotColors.forEachIndexed { index, color ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            val dynamicHeight = if (isSpeaking || rmsLevel > 0.05f) {
                (8.dp + (16.dp * rmsLevel * scale)).coerceIn(8.dp, 28.dp)
            } else {
                8.dp * scale
            }

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(dynamicHeight)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Floating Microphone button for triggering voice assistant
 */
@Composable
fun FloatingVoiceAssistantButton(
    isListening: Boolean,
    isWakeWordActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = if (isListening) Color(0xFFEA4335) else Color(0x441E293B),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            if (isListening) Color(0xFFFF5252) else if (isWakeWordActive) Color(0xFF4285F4) else Color(0x44FFFFFF)
        ),
        modifier = modifier
            .size(42.dp)
            .shadow(6.dp, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "音声アシスタント起動",
                tint = if (isListening) Color.White else if (isWakeWordActive) Color(0xFF60A5FA) else Color(0xFFAAAAAA),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

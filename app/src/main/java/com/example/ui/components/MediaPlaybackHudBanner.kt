package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveBackgroundVideo
import com.example.model.ChimeVideoSourceType
import com.example.model.CustomAudioItem
import java.io.File

/**
 * HUD banner displayed gracefully right above the bottom sensor bar (humidity/center area)
 * when music or video is playing. Features smooth marquee scrolling for long titles.
 */
@Composable
fun MediaPlaybackHudBanner(
    activeVideo: ActiveBackgroundVideo?,
    playingAudioPath: String?,
    customAudioList: List<CustomAudioItem>,
    accentColor: Color = Color(0xFF00E5FF),
    onDismissVideo: () -> Unit,
    onDismissAudio: () -> Unit,
    onOpenMusicPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isVideoPlaying = activeVideo != null && activeVideo.videoSourceType != ChimeVideoSourceType.NONE
    val isAudioPlaying = !playingAudioPath.isNullOrBlank()
    val isMediaActive = isVideoPlaying || isAudioPlaying

    AnimatedVisibility(
        visible = isMediaActive,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        val title = when {
            isVideoPlaying -> {
                activeVideo?.customVideoName
                    ?: activeVideo?.videoSourceType?.displayName
                    ?: "背景動画"
            }
            isAudioPlaying -> {
                val foundItem = customAudioList.find { it.filePath == playingAudioPath }
                foundItem?.name
                    ?: try {
                        File(playingAudioPath ?: "").nameWithoutExtension.ifEmpty { "カスタム音声" }
                    } catch (_: Exception) {
                        "カスタム音声"
                    }
            }
            else -> ""
        }

        val typeLabel = when {
            isVideoPlaying && activeVideo?.playVideoAudio == true -> "動画+音声再生中"
            isVideoPlaying -> "背景動画再生中"
            else -> "音声再生中"
        }

        val icon = when {
            isVideoPlaying -> Icons.Default.Videocam
            else -> Icons.Default.GraphicEq
        }

        val iconTint = when {
            isVideoPlaying -> Color(0xFF64B5F6)
            else -> Color(0xFF4ADE80)
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .widthIn(min = 220.dp, max = 460.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE161D2B),
                            Color(0xFA0E131F)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.6f),
                            Color(0x33FFFFFF),
                            accentColor.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .then(
                    if (onOpenMusicPlayer != null) {
                        Modifier.clickable { onOpenMusicPlayer.invoke() }
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("media_playback_hud_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated / Themed Playback Icon
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = typeLabel,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Text details with marquee title
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Marquee Scrolling Title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 35.dp
                            )
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(iconTint)
                        )
                        Text(
                            text = typeLabel,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Stop / Close Button
                Surface(
                    shape = CircleShape,
                    color = Color(0x33FFFFFF),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            if (isVideoPlaying) {
                                onDismissVideo()
                            } else {
                                onDismissAudio()
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "停止",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

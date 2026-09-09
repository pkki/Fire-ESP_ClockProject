package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.ActiveBackgroundVideo
import com.example.model.ChimeVideoSourceType
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BackgroundVideoLayer(
    activeVideo: ActiveBackgroundVideo?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeVideo != null && activeVideo.videoSourceType != ChimeVideoSourceType.NONE,
        enter = fadeIn(animationSpec = tween(600)),
        exit = fadeOut(animationSpec = tween(600)),
        modifier = modifier
    ) {
        if (activeVideo != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Background Content (Custom video or preset animated effect)
                when (activeVideo.videoSourceType) {
                    ChimeVideoSourceType.CUSTOM_FILE -> {
                        if (!activeVideo.customVideoPath.isNullOrBlank()) {
                            CustomVideoTexturePlayer(
                                videoPath = activeVideo.customVideoPath,
                                playAudio = activeVideo.playVideoAudio
                            )
                        }
                    }
                    ChimeVideoSourceType.PRESET_AURORA -> PresetAuroraBackground()
                    ChimeVideoSourceType.PRESET_FIREPLACE -> PresetFireplaceBackground()
                    ChimeVideoSourceType.PRESET_STARRY_NIGHT -> PresetStarryNightBackground()
                    ChimeVideoSourceType.PRESET_RAIN -> PresetRainBackground()
                    ChimeVideoSourceType.PRESET_SUNRISE -> PresetSunriseBackground()
                    ChimeVideoSourceType.NONE -> { /* no-op */ }
                }

                // 2. Translucent dark tint layer to keep clock digits crystal clear and visible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            )
                        )
                )

                // 3. Floating Indicator Badge & Close Button on Top Edge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "動画再生中",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "背景動画: ${activeVideo.customVideoName ?: activeVideo.videoSourceType.displayName}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color(0x44FFFFFF),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onDismiss() }
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
}

@Composable
fun CustomVideoTexturePlayer(
    videoPath: String,
    playAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(videoPath, playAudio) {
        val file = File(videoPath)
        if (!file.exists()) {
            return@DisposableEffect onDispose {}
        }

        val player = MediaPlayer().apply {
            try {
                setDataSource(context, Uri.fromFile(file))
                isLooping = true
                if (!playAudio) {
                    setVolume(0f, 0f)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = player

        onDispose {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        mediaPlayer?.let { player ->
                            try {
                                val surface = Surface(surfaceTexture)
                                player.setSurface(surface)
                                player.prepareAsync()
                                player.setOnPreparedListener {
                                    it.start()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        mediaPlayer?.setSurface(null)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

// -------------------------------------------------------------
// Preset Ambient Animated Backgrounds
// -------------------------------------------------------------

@Composable
fun PresetAuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Deep night sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF030914),
                    Color(0xFF061B2E),
                    Color(0xFF02101E)
                )
            )
        )

        // Aurora green/cyan/purple waving bands
        val auroraGreen = Color(0x6600E599)
        val auroraCyan = Color(0x5500B4D8)
        val auroraPurple = Color(0x447209B7)

        val steps = 30
        for (i in 0 until steps) {
            val x = (width / steps) * i
            val yOffset1 = sin(phase + i * 0.35f) * (height * 0.18f) + height * 0.40f
            val yOffset2 = cos(phase * 0.8f + i * 0.4f) * (height * 0.15f) + height * 0.50f

            drawCircle(
                color = auroraGreen,
                radius = width * 0.25f,
                center = Offset(x, yOffset1)
            )
            drawCircle(
                color = auroraCyan,
                radius = width * 0.30f,
                center = Offset(x + 40, yOffset2)
            )
            drawCircle(
                color = auroraPurple,
                radius = width * 0.20f,
                center = Offset(x - 30, yOffset1 + 60)
            )
        }
    }
}

@Composable
fun PresetFireplaceBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fire")
    val flicker1 by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker1"
    )
    val flicker2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Dark charcoal background
        drawRect(Color(0xFF0D0604))

        // Center ember glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFA726).copy(alpha = 0.5f * flicker1),
                    Color(0xFFFF5722).copy(alpha = 0.35f * flicker2),
                    Color(0xFF8D1B00).copy(alpha = 0.2f),
                    Color.Transparent
                ),
                center = Offset(width / 2f, height * 0.8f),
                radius = width * 0.6f * flicker1
            ),
            center = Offset(width / 2f, height * 0.8f),
            radius = width * 0.6f
        )
    }
}

@Composable
fun PresetStarryNightBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "stars")
    val twinkle by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )
    val shootingStarProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shooting"
    )

    val random = remember { Random(42) }
    val stars = remember {
        List(70) {
            Triple(random.nextFloat(), random.nextFloat(), random.nextFloat() * 2f + 1f)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF050813),
                    Color(0xFF090E24),
                    Color(0xFF130E26)
                )
            )
        )

        // Draw stars
        stars.forEachIndexed { index, (relX, relY, starSize) ->
            val alpha = ((twinkle + (index % 5) * 0.15f) % 1f).coerceIn(0.2f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = starSize,
                center = Offset(relX * width, relY * height)
            )
        }

        // Draw shooting star
        if (shootingStarProgress in 0.1f..0.6f) {
            val p = (shootingStarProgress - 0.1f) / 0.5f
            val startX = width * (0.8f - p * 0.5f)
            val startY = height * (0.1f + p * 0.35f)
            val endX = startX + 60f
            val endY = startY - 40f

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.5f
            )
        }
    }
}

@Composable
fun PresetRainBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rain")
    val rainOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainOffset"
    )

    val random = remember { Random(99) }
    val raindrops = remember {
        List(60) {
            Pair(random.nextFloat(), random.nextFloat())
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A1118),
                    Color(0xFF101B24),
                    Color(0xFF15222E)
                )
            )
        )

        // Draw falling rain streaks
        raindrops.forEach { (relX, startRelY) ->
            val curY = ((startRelY + rainOffset) % 1f) * height
            val x = relX * width
            drawLine(
                color = Color(0x6690CAF9),
                start = Offset(x, curY),
                end = Offset(x - 4f, curY + 28f),
                strokeWidth = 1.8f
            )
        }
    }
}

@Composable
fun PresetSunriseBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sunrise")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sunrisePulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0D1B2A),
                    Color(0xFF2C1938),
                    Color(0xFF7A2E3B),
                    Color(0xFFE26D45),
                    Color(0xFFFFB300)
                )
            )
        )

        // Sunrise orb glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFE082).copy(alpha = 0.6f * pulse),
                    Color(0xFFFF8A65).copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(width * 0.5f, height * 0.85f),
                radius = width * 0.5f * pulse
            ),
            center = Offset(width * 0.5f, height * 0.85f),
            radius = width * 0.5f
        )
    }
}

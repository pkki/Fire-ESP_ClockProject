package com.example.ui.components

import android.media.AudioAttributes
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.font.FontFamily
import com.example.audio.VideoPlayerManager
import com.example.audio.VideoPlayerState
import com.example.audio.VideoAspectRatio
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
                                playAudio = activeVideo.playVideoAudio,
                                volume = activeVideo.volume,
                                durationSeconds = activeVideo.durationSeconds,
                                onVideoEnded = onDismiss
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
            }
        }
    }
}

/**
 * 前面フルスクリーン動画プレイヤー (Foreground Video Overlay)
 * 時計の文字盤の手前（最前面）に動画を表示し、画面タップで操作HUD（背景切替・停止・タイトル）を表示します。
 */
@Composable
fun ForegroundVideoOverlay(
    activeVideo: ActiveBackgroundVideo?,
    onDismiss: () -> Unit,
    onToggleDisplayLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHud by remember { mutableStateOf(true) }
    val videoState by VideoPlayerManager.playerState.collectAsState()
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = activeVideo != null && activeVideo.videoSourceType != ChimeVideoSourceType.NONE,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier
    ) {
        if (activeVideo != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showHud = !showHud }
            ) {
                // 1. Video content in foreground
                when (activeVideo.videoSourceType) {
                    ChimeVideoSourceType.CUSTOM_FILE -> {
                        if (!activeVideo.customVideoPath.isNullOrBlank()) {
                            CustomVideoTexturePlayer(
                                videoPath = activeVideo.customVideoPath,
                                playAudio = activeVideo.playVideoAudio,
                                volume = activeVideo.volume,
                                durationSeconds = activeVideo.durationSeconds,
                                onVideoEnded = onDismiss
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

                // 2. Animated HUD Overlay for Foreground playback
                AnimatedVisibility(
                    visible = showHud,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top HUD Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xEE0A0E17), Color.Transparent)
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x3300E5FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.padding(6.dp).size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = activeVideo.customVideoName ?: activeVideo.videoSourceType.displayName,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "📺 前面フルスクリーン再生中",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Switch to Background Mode Button
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0x441E293B),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier.clickable { onToggleDisplayLayer() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = "🔄", fontSize = 12.sp)
                                        Text(
                                            text = "時計の背景に切り替え",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Close / Stop Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x44EF4444),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                                    modifier = Modifier.clickable { onDismiss() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "閉じる",
                                        tint = Color.White,
                                        modifier = Modifier.padding(8.dp).size(18.dp)
                                    )
                                }
                            }
                        }

                        // Center Quick Controls (Rewind, Play/Pause, Forward)
                        if (activeVideo.videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { VideoPlayerManager.seekRelative(-10000L) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "-10秒",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                        .clickable { VideoPlayerManager.togglePlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (videoState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (videoState.isPlaying) "一時停止" else "再生",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { VideoPlayerManager.seekRelative(10000L) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "+10秒",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Comprehensive HUD Controls Bar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xF00A0E17))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            if (activeVideo.videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) {
                                // 1. Seekbar Track & Time Indicators
                                val frac = if (isSeeking) seekProgress else videoState.progressFraction
                                Slider(
                                    value = frac,
                                    onValueChange = {
                                        isSeeking = true
                                        seekProgress = it
                                    },
                                    onValueChangeFinished = {
                                        val target = (seekProgress * videoState.durationMs.coerceAtLeast(1L)).toLong()
                                        VideoPlayerManager.seekTo(target)
                                        isSeeking = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00E5FF),
                                        activeTrackColor = Color(0xFF00E5FF),
                                        inactiveTrackColor = Color(0x44FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentMs = if (isSeeking) (seekProgress * videoState.durationMs).toLong() else videoState.currentPositionMs
                                    Text(
                                        text = "${VideoPlayerState.formatTimeMs(currentMs)} / ${videoState.durationFormatted}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Playback Speed Options
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                                            val isSel = (videoState.playbackSpeed - speed).let { kotlin.math.abs(it) < 0.05f }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSel) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                                modifier = Modifier.clickable { VideoPlayerManager.setPlaybackSpeed(speed) }
                                            ) {
                                                Text(
                                                    text = "${speed}x",
                                                    color = if (isSel) Color.Black else Color.White,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Loop Toggle
                                        IconButton(
                                            onClick = { VideoPlayerManager.setLooping(!videoState.isLooping) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (videoState.isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                                contentDescription = "ループ切替",
                                                tint = if (videoState.isLooping) Color(0xFF00E5FF) else Color(0xFF888896),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Mute Toggle
                                        IconButton(
                                            onClick = { VideoPlayerManager.toggleMute() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (videoState.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                                contentDescription = "ミュート",
                                                tint = if (videoState.isMuted) Color(0xFFFF5252) else Color(0xFF4ADE80),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Preset ambient background label
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "画面タップで操作バーの表示/非表示",
                                        color = Color(0xAAFFFFFF),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = if (activeVideo.playVideoAudio) "🔊 音声出力中" else "🔇 映像のみ",
                                        color = if (activeVideo.playVideoAudio) Color(0xFF4ADE80) else Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
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

@Composable
fun CustomVideoTexturePlayer(
    videoPath: String,
    playAudio: Boolean,
    volume: Float = 0.85f,
    durationSeconds: Int = 60,
    onVideoEnded: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentOnVideoEnded by androidx.compose.runtime.rememberUpdatedState(onVideoEnded)

    AndroidView(
        factory = { ctx ->
            var player: MediaPlayer? = null
            var activeSurface: Surface? = null

            fun startPlayback(texture: android.graphics.SurfaceTexture) {
                try {
                    player?.stop()
                    player?.release()
                    player = null
                    activeSurface?.release()
                    activeSurface = null

                    val file = File(videoPath)
                    if (!file.exists()) {
                        android.util.Log.e("CustomVideoTexturePlayer", "Video file does not exist: $videoPath")
                        currentOnVideoEnded?.invoke()
                        return
                    }

                    val surface = Surface(texture)
                    activeSurface = surface

                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                        )
                        java.io.FileInputStream(file).use { fis ->
                            setDataSource(fis.fd)
                        }
                        setSurface(surface)
                        isLooping = (durationSeconds != -1)
                        if (durationSeconds == -1) {
                            setOnCompletionListener {
                                currentOnVideoEnded?.invoke()
                            }
                        }
                        setOnErrorListener { _, what, extra ->
                            android.util.Log.e("CustomVideoTexturePlayer", "MediaPlayer error: what=$what, extra=$extra")
                            currentOnVideoEnded?.invoke()
                            true
                        }
                        if (!playAudio) {
                            setVolume(0f, 0f)
                        } else {
                            setVolume(volume, volume)
                        }
                        setOnPreparedListener { p ->
                            val sessionId = p.audioSessionId
                            if (sessionId > 0) {
                                com.example.audio.AudioEqualizerManager.registerAudioSession(sessionId)
                            }
                            p.start()
                        }
                        prepareAsync()
                    }
                    player = mp
                } catch (e: Exception) {
                    android.util.Log.e("CustomVideoTexturePlayer", "Error initializing MediaPlayer", e)
                    currentOnVideoEnded?.invoke()
                }
            }

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
                        startPlayback(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surfaceTexture: android.graphics.SurfaceTexture): Boolean {
                        try {
                            player?.let { p ->
                                val sessionId = try { p.audioSessionId } catch (_: Exception) { 0 }
                                if (sessionId > 0) {
                                    com.example.audio.AudioEqualizerManager.unregisterAudioSession(sessionId)
                                }
                                p.stop()
                                p.release()
                            }
                            player = null
                            activeSurface?.release()
                            activeSurface = null
                        } catch (_: Exception) {}
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) {}
                }

                if (isAvailable && surfaceTexture != null) {
                    startPlayback(surfaceTexture!!)
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

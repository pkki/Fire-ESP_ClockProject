package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ClockViewModel
import com.example.audio.MusicRepeatMode
import com.example.model.ClockPreferencesState
import com.example.model.CustomAudioItem
import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import com.example.model.ChimeVideoSourceType
import com.example.model.CustomVideoItem
import com.example.model.VideoDisplayLayer
import com.example.model.EqualizerPreset
import com.example.model.BassCutMode
import com.example.audio.AudioEqualizerManager

enum class MusicRightTab(val label: String) {
    PLAYLIST("プレイリスト"),
    VIDEO("動画・MV"),
    EQUALIZER("音質・イコライザー")
}

@Composable
fun MusicPlayerDialog(
    viewModel: ClockViewModel,
    preferences: ClockPreferencesState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customAudioList by viewModel.customAudioList.collectAsState()
    val customVideoList by viewModel.customVideoList.collectAsState()
    val playerState by viewModel.musicPlayerState.collectAsState()
    val eqState by viewModel.equalizerState.collectAsState()
    val isSilencePlaying by viewModel.isSilenceKeepAlivePlaying.collectAsState()
    val accentColor = preferences.colorPalette.primary
    var rightTab by remember { mutableStateOf(MusicRightTab.PLAYLIST) }

    var isVideoMode by remember { mutableStateOf(false) }
    var activeVideoItem by remember { mutableStateOf<CustomVideoItem?>(null) }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomAudio(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    val updated = viewModel.customAudioList.value
                    if (updated.isNotEmpty() && !playerState.isPlaying) {
                        viewModel.playMusic(updated.last(), updated)
                    }
                }
            }
        }
    }

    // Video file picker launcher (User Requested: 音楽プレイヤーに動画もお願い)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomVideo(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    val updated = viewModel.customVideoList.value
                    if (updated.isNotEmpty()) {
                        activeVideoItem = updated.last()
                        isVideoMode = true
                        rightTab = MusicRightTab.VIDEO
                    }
                }
            }
        }
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .testTag("music_player_dialog"),
            color = Color(0xFF10131C)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 1. Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "卓上音楽プレイヤー",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "お気に入りの音楽・BGMを高音質で連続再生",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x2200E5FF)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "曲を追加",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22F59E0B)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "動画を追加",
                                color = Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "閉じる",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Main Content Layout (Responsive 2 Columns or Stack)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Left Column: Now Playing Hero Card & Controls
                    Card(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B28)),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x25FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Animated Visualizer or Embedded Video Player Hero Box
                            if (isVideoMode && activeVideoItem != null) {
                                val currentVid = activeVideoItem!!
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black)
                                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CustomVideoTexturePlayer(
                                            videoPath = currentVid.filePath,
                                            playAudio = true,
                                            volume = playerState.volume,
                                            durationSeconds = -1,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Overlay Title Tag
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                                            Text(
                                                text = currentVid.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Video Controls Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.previewBackgroundVideo(
                                                    ChimeVideoSourceType.CUSTOM_FILE,
                                                    customVideoPath = currentVid.filePath,
                                                    customVideoName = currentVid.name,
                                                    durationSeconds = -2,
                                                    displayLayer = VideoDisplayLayer.FOREGROUND
                                                )
                                                Toast.makeText(context, "前面全画面で動画を再生中（停止するまで継続）", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("全画面再生", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.previewBackgroundVideo(
                                                    ChimeVideoSourceType.CUSTOM_FILE,
                                                    customVideoPath = currentVid.filePath,
                                                    customVideoName = currentVid.name,
                                                    durationSeconds = -2,
                                                    displayLayer = VideoDisplayLayer.BACKGROUND
                                                )
                                                Toast.makeText(context, "時計の背景で動画を再生中", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                                        ) {
                                            Text("背景再生", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                isVideoMode = false
                                                activeVideoItem = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF), contentColor = Color(0xFFCBD5E1)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("音楽へ", fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    accentColor.copy(alpha = 0.20f),
                                                    Color(0xFF0B0E17)
                                                )
                                            )
                                        )
                                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        MusicVisualizerBars(
                                            isPlaying = playerState.isPlaying,
                                            accentColor = accentColor
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = playerState.currentTrack?.name ?: if (customAudioList.isNotEmpty()) "曲を選択して再生" else "音楽ファイルがありません",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = when {
                                                playerState.isPlaying -> "再生中 • ${playerState.repeatMode.label}"
                                                playerState.isPaused -> "一時停止中"
                                                else -> "停止中"
                                            },
                                            color = if (playerState.isPlaying) accentColor else Color(0xFF94A3B8),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Progress Track / Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val currentFrac = if (isSeeking) seekProgress else playerState.progressFraction
                                Slider(
                                    value = currentFrac,
                                    onValueChange = {
                                        isSeeking = true
                                        seekProgress = it
                                    },
                                    onValueChangeFinished = {
                                        val targetMs = (seekProgress * playerState.durationMs).toLong()
                                        viewModel.seekMusicTo(targetMs)
                                        isSeeking = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val currentMs = if (isSeeking) (seekProgress * playerState.durationMs).toLong() else playerState.currentPositionMs
                                    Text(
                                        text = com.example.audio.MusicPlayerState.formatTimeMs(currentMs),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = playerState.durationFormatted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Playback Buttons Row (Prev, Play/Pause, Next, Repeat, Stop)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Repeat mode button
                                IconButton(
                                    onClick = { viewModel.cycleMusicRepeatMode() },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x18FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = when (playerState.repeatMode) {
                                            MusicRepeatMode.ONE -> Icons.Default.RepeatOne
                                            MusicRepeatMode.SHUFFLE -> Icons.Default.Shuffle
                                            else -> Icons.Default.Repeat
                                        },
                                        contentDescription = playerState.repeatMode.label,
                                        tint = if (playerState.repeatMode == MusicRepeatMode.OFF) Color(0xFF64748B) else accentColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // Previous Track
                                IconButton(
                                    onClick = { viewModel.previousMusicTrack() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x1EFFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "前の曲",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Main Play / Pause Button
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .clickable {
                                            if (playerState.currentTrack == null && customAudioList.isNotEmpty()) {
                                                viewModel.playMusic(customAudioList.first(), customAudioList)
                                            } else {
                                                viewModel.toggleMusicPlayPause()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playerState.isPlaying) "一時停止" else "再生",
                                        tint = Color.Black,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                // Next Track
                                IconButton(
                                    onClick = { viewModel.nextMusicTrack() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x1EFFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "次の曲",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Stop Button
                                IconButton(
                                    onClick = { viewModel.stopMusic() },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x18FFFFFF))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "停止",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // 3. Music Player Volume Control Section (Requested Feature)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F131D))
                                    .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                playerState.volume <= 0.01f -> Icons.Default.VolumeMute
                                                playerState.volume < 0.5f -> Icons.Default.VolumeDown
                                                else -> Icons.Default.VolumeUp
                                            },
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "音楽プレイヤー音量",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "${(playerState.volume * 100).roundToInt()}%",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                }

                                Slider(
                                    value = playerState.volume,
                                    onValueChange = { newVol ->
                                        viewModel.setMusicPlayerVolume(newVol)
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(30.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Quick Equalizer Status Link
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1800E5FF))
                                        .clickable { rightTab = MusicRightTab.EQUALIZER }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (eqState.isEnabled) "EQ: ${eqState.currentPreset.displayName}" else "イコライザー: OFF",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = if (eqState.bassCutMode != BassCutMode.OFF) "低音カット: ${eqState.bassCutMode.displayName}" else "設定 >",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        }
                    }

                    // Right Column: Playlist or Equalizer Panel
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B28)),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x25FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            // Tab Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F131D))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MusicRightTab.values().forEach { tab ->
                                    val isSel = rightTab == tab
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSel) accentColor.copy(alpha = 0.5f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { rightTab = tab }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (tab) {
                                                    MusicRightTab.PLAYLIST -> Icons.Default.QueueMusic
                                                    MusicRightTab.VIDEO -> Icons.Default.Videocam
                                                    MusicRightTab.EQUALIZER -> Icons.Default.GraphicEq
                                                },
                                                contentDescription = null,
                                                tint = if (isSel) accentColor else Color(0xFF94A3B8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = when (tab) {
                                                    MusicRightTab.PLAYLIST -> "楽曲 (${customAudioList.size})"
                                                    MusicRightTab.VIDEO -> "動画 (${customVideoList.size})"
                                                    MusicRightTab.EQUALIZER -> tab.label
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (rightTab == MusicRightTab.PLAYLIST) {
                                if (customAudioList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x0AFFFFFF))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color(0xFF475569),
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "登録された楽曲がありません",
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "上部の「曲を追加」またはWebダッシュボードからMP3/WAV等をアップロードしてください",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF64748B),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(customAudioList) { index, track ->
                                            val isCurrent = playerState.currentTrack?.id == track.id
                                            val isCurrentlyPlaying = isCurrent && playerState.isPlaying

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isCurrent) accentColor.copy(alpha = 0.18f) else Color(0x0EFFFFFF)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isCurrent) accentColor.copy(alpha = 0.5f) else Color.Transparent,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        if (isCurrent) {
                                                            viewModel.toggleMusicPlayPause()
                                                        } else {
                                                            viewModel.playMusic(track, customAudioList)
                                                        }
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isCurrent) accentColor else Color(0x1FFFFFFF)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = when {
                                                                isCurrentlyPlaying -> Icons.Default.GraphicEq
                                                                isCurrent -> Icons.Default.PlayArrow
                                                                else -> Icons.Default.MusicNote
                                                            },
                                                            contentDescription = null,
                                                            tint = if (isCurrent) Color.Black else Color.White,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = track.name,
                                                            color = if (isCurrent) Color.White else Color(0xFFCBD5E1),
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (isCurrentlyPlaying) {
                                                            Text(
                                                                text = "再生中",
                                                                fontSize = 10.sp,
                                                                color = accentColor,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                // Quick Play/Pause Action
                                                IconButton(
                                                    onClick = {
                                                        if (isCurrent) {
                                                            viewModel.toggleMusicPlayPause()
                                                        } else {
                                                            viewModel.playMusic(track, customAudioList)
                                                        }
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = if (isCurrent) accentColor else Color(0xFF94A3B8),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (rightTab == MusicRightTab.VIDEO) {
                                // VIDEO TAB CONTENT (User Requested: 音楽プレイヤーに動画もお願い)
                                if (customVideoList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x0AFFFFFF))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = Color(0xFF475569),
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "登録された動画がありません",
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "上部の「動画を追加」からMP4/MKV等の動画ファイルを追加してください",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF64748B),
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { videoPickerLauncher.launch("video/*") },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("動画を追加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(customVideoList) { _, video ->
                                            val isPlayingInPlayer = isVideoMode && activeVideoItem?.filePath == video.filePath
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isPlayingInPlayer) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0x10FFFFFF))
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isPlayingInPlayer) Color(0xFFF59E0B).copy(alpha = 0.4f) else Color.Transparent,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(if (isPlayingInPlayer) Color(0xFFF59E0B) else Color(0x22FFFFFF)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Videocam,
                                                                    contentDescription = null,
                                                                    tint = if (isPlayingInPlayer) Color.Black else Color(0xFFF59E0B),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = video.name,
                                                                    color = if (isPlayingInPlayer) Color(0xFFF59E0B) else Color.White,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = if (isPlayingInPlayer) "▶ プレイヤー内で再生中" else "動画ファイル",
                                                                    color = if (isPlayingInPlayer) Color(0xFFFCD34D) else Color(0xFF94A3B8),
                                                                    fontSize = 10.sp
                                                                )
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = { viewModel.deleteCustomVideo(video) },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Action buttons for each video
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                activeVideoItem = video
                                                                isVideoMode = true
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isPlayingInPlayer) Color(0xFFF59E0B) else Color(0x28F59E0B),
                                                                contentColor = if (isPlayingInPlayer) Color.Black else Color(0xFFFCD34D)
                                                            ),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 3.dp)
                                                        ) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text("プレイヤー再生", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                viewModel.previewBackgroundVideo(
                                                                    ChimeVideoSourceType.CUSTOM_FILE,
                                                                    customVideoPath = video.filePath,
                                                                    customVideoName = video.name,
                                                                    durationSeconds = -2,
                                                                    displayLayer = VideoDisplayLayer.FOREGROUND
                                                                )
                                                                Toast.makeText(context, "前面全画面で動画を再生中", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF), contentColor = Color.White),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 3.dp)
                                                        ) {
                                                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(13.dp))
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text("全画面表示", fontSize = 10.5.sp)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                viewModel.previewBackgroundVideo(
                                                                    ChimeVideoSourceType.CUSTOM_FILE,
                                                                    customVideoPath = video.filePath,
                                                                    customVideoName = video.name,
                                                                    durationSeconds = -2,
                                                                    displayLayer = VideoDisplayLayer.BACKGROUND
                                                                )
                                                                Toast.makeText(context, "時計の背景で動画を再生中", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x18FFFFFF), contentColor = Color(0xFFCBD5E1)),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 3.dp)
                                                        ) {
                                                            Text("時計背景", fontSize = 10.5.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // EQUALIZER TAB CONTENT
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 1. Equalizer Master Toggle & Reset
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF0F131D))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("イコライザー有効", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { viewModel.resetEqualizerToFlat() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("リセット", fontSize = 10.sp, color = Color.White)
                                                }
                                                androidx.compose.material3.Switch(
                                                    checked = eqState.isEnabled,
                                                    onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = Color(0xFF00E5FF)
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // 1.5 Earphone Jack Anti-Noise Keep-Alive (常時無音再生 / ノイズ防止)
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF0F131D))
                                                .border(1.dp, if (preferences.antiNoiseSilenceEnabled) Color(0x6600E5FF) else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.VolumeMute,
                                                    contentDescription = null,
                                                    tint = if (preferences.antiNoiseSilenceEnabled) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("イヤホン・ノイズ防止（常時無音再生）", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        if (isSilencePlaying) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF00E5FF))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("出力中", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        "端子の待機ジー音や再生開始時のプチッ音を防止",
                                                        fontSize = 9.5.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                            androidx.compose.material3.Switch(
                                                checked = preferences.antiNoiseSilenceEnabled,
                                                onCheckedChange = { viewModel.setAntiNoiseSilenceEnabled(it) },
                                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFF00E5FF)
                                                )
                                            )
                                        }
                                    }

                                    // 2. Speaker Bass Cut Filter (Small speaker anti-rattling)
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF0F131D))
                                                .border(1.dp, Color(0x33FA541C), RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "🛡️ 低音カット・スピーカー保護フィルター",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFA541C)
                                            )
                                            Text(
                                                text = "小型スピーカーの低音歪み・音割れ・ビビリを強力にカット",
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                BassCutMode.values().forEach { mode ->
                                                    val isSel = eqState.bassCutMode == mode
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isSel) Color(0xFFFA541C) else Color(0x14FFFFFF))
                                                            .clickable { viewModel.setEqualizerBassCutMode(mode) }
                                                            .padding(vertical = 5.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = when (mode) {
                                                                BassCutMode.OFF -> "OFF"
                                                                BassCutMode.LIGHT -> "弱(-4dB)"
                                                                BassCutMode.MEDIUM -> "中(-8dB)"
                                                                BassCutMode.STRONG -> "強(-12dB)"
                                                                BassCutMode.EXTREME -> "極(-16dB)"
                                                            },
                                                            fontSize = 9.5.sp,
                                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSel) Color.White else Color(0xFFCCCCCC)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 3. Presets
                                    item {
                                        Text("プリセット選択:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val presets = listOf(
                                            EqualizerPreset.FLAT,
                                            EqualizerPreset.BASS_REDUCE,
                                            EqualizerPreset.BASS_CUT_LIGHT,
                                            EqualizerPreset.VOCAL,
                                            EqualizerPreset.TREBLE_BOOST,
                                            EqualizerPreset.NIGHT_RELAX,
                                            EqualizerPreset.POP,
                                            EqualizerPreset.ROCK,
                                            EqualizerPreset.CLASSICAL,
                                            EqualizerPreset.BASS_BOOST
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            presets.chunked(3).forEach { rowPresets ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    rowPresets.forEach { p ->
                                                        val isSel = eqState.currentPreset == p
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSel) Color(0x3300E5FF) else Color(0x12FFFFFF))
                                                                .border(
                                                                    1.dp,
                                                                    if (isSel) Color(0xFF00E5FF) else Color.Transparent,
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .clickable { viewModel.setEqualizerPreset(p) }
                                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = p.displayName.split(" ")[0],
                                                                fontSize = 9.5.sp,
                                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSel) Color(0xFF00E5FF) else Color.White,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    // Pad row if needed
                                                    if (rowPresets.size < 3) {
                                                        repeat(3 - rowPresets.size) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 3.5 Sound Enhancement Bars: Bass Boost & 3D Virtualizer
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF0F131D))
                                                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(10.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "🔊 サウンドエフェクト・強化バー",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF)
                                            )

                                            // Bass Boost Slider
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("💥 低音ブースト (Bass Boost):", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                        Text("重低音強化", fontSize = 9.sp, color = Color(0xFF888888))
                                                    }
                                                    Text(
                                                        text = "${eqState.bassBoostStrength / 10}%",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (eqState.bassBoostStrength > 0) Color(0xFF00E5FF) else Color(0xFF888888)
                                                    )
                                                }
                                                Slider(
                                                    value = eqState.bassBoostStrength.toFloat(),
                                                    onValueChange = { newVal ->
                                                        viewModel.setEqualizerBassBoost(newVal.toInt())
                                                    },
                                                    valueRange = 0f..1000f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(0xFF00E5FF),
                                                        activeTrackColor = Color(0xFF00E5FF),
                                                        inactiveTrackColor = Color(0x33FFFFFF)
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }

                                            // Virtualizer Slider
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("🎧 立体音響 (Virtualizer):", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                        Text("サラウンド空間", fontSize = 9.sp, color = Color(0xFF888888))
                                                    }
                                                    Text(
                                                        text = "${eqState.virtualizerStrength / 10}%",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (eqState.virtualizerStrength > 0) Color(0xFF38BDF8) else Color(0xFF888888)
                                                    )
                                                }
                                                Slider(
                                                    value = eqState.virtualizerStrength.toFloat(),
                                                    onValueChange = { newVal ->
                                                        viewModel.setEqualizerVirtualizer(newVal.toInt())
                                                    },
                                                    valueRange = 0f..1000f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(0xFF38BDF8),
                                                        activeTrackColor = Color(0xFF38BDF8),
                                                        inactiveTrackColor = Color(0x33FFFFFF)
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    // 4. 5-Band Graphic Sliders
                                    item {
                                        Text("5バンド手動調整 (-15dB 〜 +15dB):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    }

                                    itemsIndexed(listOf("低音(60Hz)", "低中音(230Hz)", "中音(910Hz)", "中高音(3.6kHz)", "高音(14kHz)")) { idx, label ->
                                        val gain = eqState.bandGainsDb.getOrElse(idx) { 0 }
                                        val effGain = eqState.getEffectiveBandGains().getOrElse(idx) { gain }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF0F131D))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(label, fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "${if (gain > 0) "+$gain" else "$gain"} dB ${if (effGain != gain) "(実効: ${effGain}dB)" else ""}",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (gain > 0) Color(0xFF00E5FF) else if (gain < 0) Color(0xFFFA541C) else Color(0xFF94A3B8)
                                                )
                                            }
                                            Slider(
                                                value = gain.toFloat(),
                                                onValueChange = { newVal ->
                                                    viewModel.setEqualizerBandGain(idx, newVal.toInt())
                                                },
                                                valueRange = -15f..15f,
                                                steps = 29,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFF00E5FF),
                                                    activeTrackColor = Color(0xFF00E5FF),
                                                    inactiveTrackColor = Color(0x33FFFFFF)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(26.dp)
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
}

/**
 * Animated equalizer bars reflecting active music playback state
 */
@Composable
private fun MusicVisualizerBars(
    isPlaying: Boolean,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "music_visualizer")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.95f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = if (isPlaying) 1.0f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = if (isPlaying) 0.85f else 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 0.90f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(26.dp)
    ) {
        val heights = listOf(h1, h2, h3, h4, h2, h1)
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(4.5.dp)
                    .height((26 * frac).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPlaying) accentColor else Color(0x55FFFFFF))
            )
        }
    }
}

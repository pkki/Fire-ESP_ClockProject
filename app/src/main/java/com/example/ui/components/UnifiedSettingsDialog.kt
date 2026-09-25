package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ClockViewModel
import com.example.audio.ChimeSound
import com.example.camera.IpCameraConfig
import com.example.camera.IpCameraStatus
import com.example.model.ChimeAudioSourceType
import com.example.model.IrDeviceCategory
import com.example.model.IrRemoteButton
import com.example.model.ChimeVideoSourceType
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.data.CrashLogManager
import com.example.model.EewScaleLevel
import com.example.model.EewTestScenario
import com.example.model.ScheduledChime
import com.example.model.WeatherState

enum class SettingsTab(val title: String, val icon: ImageVector) {
    FACE_PALETTE("文字盤・デザイン", Icons.Default.Palette),
    ALARM("目覚まし・物理ボタン", Icons.Default.Alarm),
    ESP_SENSOR("ESP温湿度気圧", Icons.Default.Sensors),
    IR_REMOTE("スマート家電・赤外線", Icons.Default.Sensors),
    IP_CAMERA("IPカメラ配信", Icons.Default.Videocam),
    EEW("緊急地震速報", Icons.Default.Warning),
    CHIMES("時報・チャイム", Icons.Default.NotificationsActive),
    MEDIA("音声・動画管理", Icons.Default.Audiotrack),
    WEATHER("天気・地域", Icons.Default.WbSunny),
    NIGHT_STAND("常夜灯・Kiosk保護", Icons.Default.Security),
    DIAGNOSTICS("システム診断・ログ", Icons.Default.BugReport)
}

@Composable
fun UnifiedSettingsDialog(
    viewModel: ClockViewModel,
    preferences: ClockPreferencesState,
    weatherState: WeatherState,
    scheduledChimes: List<ScheduledChime>,
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    initialTab: SettingsTab = SettingsTab.FACE_PALETTE,
    onOpenWeatherDetail: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val ipCameraConfig by viewModel.ipCameraConfig.collectAsState()
    val ipCameraStatus by viewModel.ipCameraStatus.collectAsState()
    val cameraPreviewBitmap by viewModel.ipCameraPreviewBitmap.collectAsState()
    val cameraFps by viewModel.ipCameraFps.collectAsState()
    val activeBackgroundVideo by viewModel.activeBackgroundVideo.collectAsState()
    val espSensorData by viewModel.espSensorData.collectAsState()
    val irButtons by viewModel.irButtons.collectAsState()
    val irLearnState by viewModel.irLearnState.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9000000))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                    .testTag("unified_settings_dialog"),
                color = Color(0xFF14141B)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with title and close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x2600E5FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "卓上デスククロック 設定",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "文字盤・内カメラIP配信・時報・天気・画面保護",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E8E9A)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                                .testTag("btn_close_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "閉じる",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Tab Navigation Row (Scrollable for full visibility on all screens)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF101016))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingsTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0x3300E5FF) else Color(0x0FFFFFFF))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0x8000E5FF) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("tab_${tab.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF9E9EA8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFC0C0CC),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Tab Content Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        when (selectedTab) {
                            SettingsTab.FACE_PALETTE -> FaceAndPaletteSettingsContent(
                                preferences = preferences,
                                viewModel = viewModel
                            )
                            SettingsTab.ALARM -> AlarmSettingsTabContent(
                                viewModel = viewModel,
                                preferences = preferences
                            )
                            SettingsTab.ESP_SENSOR -> EspSensorSettingsContent(
                                viewModel = viewModel,
                                preferences = preferences
                            )
                            SettingsTab.IR_REMOTE -> IrRemoteSettingsTab(
                                viewModel = viewModel,
                                espSensorData = espSensorData,
                                irButtons = irButtons,
                                irLearnState = irLearnState
                            )
                            SettingsTab.IP_CAMERA -> IpCameraSettingsContent(
                                config = ipCameraConfig,
                                status = ipCameraStatus,
                                previewBitmap = cameraPreviewBitmap,
                                fps = cameraFps,
                                onToggleCamera = { viewModel.toggleIpCamera() },
                                onRestartCamera = { viewModel.restartIpCamera() },
                                onUpdateConfig = { viewModel.setIpCameraConfig(it) },
                                onSwitchLens = { viewModel.switchIpCameraLens() }
                            )
                            SettingsTab.EEW -> EewSettingsContent(
                                preferences = preferences,
                                viewModel = viewModel,
                                onLaunchLiveMap = {
                                    onDismiss()
                                    viewModel.triggerTestEewScenario(EewTestScenario.HYUGANADA_M71)
                                }
                            )
                            SettingsTab.CHIMES -> ChimesSettingsContent(
                                preferences = preferences,
                                scheduledChimes = scheduledChimes,
                                customAudioList = customAudioList,
                                customVideoList = customVideoList,
                                viewModel = viewModel,
                                onNavigateToMedia = { selectedTab = SettingsTab.MEDIA }
                            )
                            SettingsTab.MEDIA -> MediaManagementSettingsContent(
                                customAudioList = customAudioList,
                                customVideoList = customVideoList,
                                viewModel = viewModel
                            )
                            SettingsTab.WEATHER -> WeatherSettingsContent(
                                preferences = preferences,
                                weather = weatherState,
                                viewModel = viewModel,
                                onOpenWeatherDetail = onOpenWeatherDetail
                            )
                            SettingsTab.NIGHT_STAND -> NightStandSettingsContent(
                                preferences = preferences,
                                viewModel = viewModel
                            )
                            SettingsTab.DIAGNOSTICS -> DiagnosticsSettingsContent(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }

            // Background Video Preview Overlay (Directly visible on device inside Settings)
            if (activeBackgroundVideo != null && activeBackgroundVideo?.videoSourceType != com.example.model.ChimeVideoSourceType.NONE) {
                BackgroundVideoLayer(
                    activeVideo = activeBackgroundVideo,
                    onDismiss = { viewModel.dismissBackgroundVideo() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: FACE & PALETTE
// -------------------------------------------------------------
@Composable
private fun FaceAndPaletteSettingsContent(
    preferences: ClockPreferencesState,
    viewModel: ClockViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "文字盤スタイル (全${ClockFace.values().size}種類)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(310.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ClockFace.values()) { face ->
                    val isSelected = preferences.clockFace == face
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0x3300E5FF) else Color(0x18FFFFFF))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.selectClockFace(face) }
                            .padding(12.dp)
                            .testTag("select_face_${face.name}")
                    ) {
                        Text(
                            text = face.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = face.subtitle,
                            fontSize = 10.sp,
                            color = Color(0xFF8E8E9A),
                            maxLines = 2
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "カラーパレット",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorPalette.values().forEach { palette ->
                    val isSelected = preferences.colorPalette == palette
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0x3300E5FF) else Color(0x18FFFFFF))
                            .border(
                                1.dp,
                                if (isSelected) palette.primary else Color(0x22FFFFFF),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.selectColorPalette(palette) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = palette.name.replace("_", " "),
                            fontSize = 10.sp,
                            color = if (isSelected) palette.primary else Color(0xFFA0A0AA),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "表示オプション",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingToggleCard(
                    title = "24時間表示",
                    subtitle = if (preferences.is24Hour) "24時間制 (14:30)" else "12時間制 (PM 2:30)",
                    checked = preferences.is24Hour,
                    onCheckedChange = { viewModel.toggle24Hour() },
                    modifier = Modifier.weight(1f)
                )
                SettingToggleCard(
                    title = "秒針・秒数表示",
                    subtitle = if (preferences.showSeconds) "秒表示あり" else "時分のみ表示",
                    checked = preferences.showSeconds,
                    onCheckedChange = { viewModel.toggleShowSeconds() },
                    modifier = Modifier.weight(1f)
                )
                SettingToggleCard(
                    title = "天気ウィジェット",
                    subtitle = if (preferences.showWeather) "天気を表示中" else "時計のみ集中",
                    checked = preferences.showWeather,
                    onCheckedChange = { viewModel.toggleShowWeather() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: IP CAMERA (FRONT/REAR WEBCAM STREAMER)
// -------------------------------------------------------------
@Composable
private fun IpCameraSettingsContent(
    config: IpCameraConfig,
    status: IpCameraStatus,
    previewBitmap: android.graphics.Bitmap?,
    fps: Float,
    onToggleCamera: () -> Unit,
    onRestartCamera: () -> Unit,
    onUpdateConfig: (IpCameraConfig) -> Unit,
    onSwitchLens: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            onToggleCamera()
        } else {
            Toast.makeText(context, "IPカメラの動作にはカメラ権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Camera Error recovery banner
        if (status.errorMessage != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x33FF5252))
                        .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("カメラエラーが発生しました", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                            Text(status.errorMessage, fontSize = 11.sp, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onRestartCamera,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("再起動・復旧", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Main ON/OFF Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (status.isRunning) Color(0x3300E5FF) else Color(0x18FFFFFF))
                    .border(
                        1.dp,
                        if (status.isRunning) Color(0xFF00E5FF) else Color(0x33FFFFFF),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (status.isRunning) Color(0xFF00E5FF) else Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (status.isRunning) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = if (status.isRunning) Color.Black else Color(0xFF888896),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "IPカメラ バックグラウンド配信",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (status.isRunning) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF1744))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "● 配信中 (${status.clientCount}台接続)",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (status.isRunning)
                                "時計画面の裏で内カメラの映像をローカルWi-Fiに常時ストリーミング配信中"
                            else
                                "使わなくなった端末の内カメラを防犯・見守り・Webカメラとして活用",
                            fontSize = 11.sp,
                            color = Color(0xFFA0A0B0)
                        )
                    }
                }

                Switch(
                    checked = status.isRunning,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (hasCameraPermission) {
                                onToggleCamera()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        } else {
                            onToggleCamera()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0x4D00E5FF)
                    ),
                    modifier = Modifier.testTag("switch_ip_camera")
                )
            }
        }

        // Live URL & Preview Section
        if (status.isRunning) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stream URL Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "ブラウザ / 外部視聴アクセスURL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF08080C))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = status.serverUrl,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("IP Camera URL", status.serverUrl))
                                    Toast.makeText(context, "URLをコピーしました: ${status.serverUrl}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "コピー",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "同一Wi-Fi内のPC、スマホ、Home Assistant等から上記URLを開くだけで映像を確認できます。\nMJPEG直接URL: ${status.serverUrl}/video",
                            fontSize = 10.sp,
                            color = Color(0xFF888896),
                            lineHeight = 14.sp
                        )
                    }

                    // Mini Live Preview
                    Box(
                        modifier = Modifier
                            .size(width = 220.dp, height = 140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Camera Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("カメラ映像取得中...", color = Color.Gray, fontSize = 11.sp)
                        }

                        // FPS Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${String.format("%.1f", fps)} FPS / ${config.resolutionWidth}x${config.resolutionHeight}",
                                color = Color(0xFF00E5FF),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Camera Options
        item {
            Text(
                text = "カメラ & 配信設定",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Front / Rear Lens Switch
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(12.dp)
                ) {
                    Text("使用カメラレンズ", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onUpdateConfig(config.copy(useFrontCamera = true)) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (config.useFrontCamera) Color(0x3300E5FF) else Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraFront, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("内カメラ", fontSize = 11.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { onUpdateConfig(config.copy(useFrontCamera = false)) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (!config.useFrontCamera) Color(0x3300E5FF) else Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraRear, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("外カメラ", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                // Target FPS setting
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("フレームレート", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${config.targetFps} FPS", fontSize = 12.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5, 10, 15, 20).forEach { fpsOpt ->
                            OutlinedButton(
                                onClick = { onUpdateConfig(config.copy(targetFps = fpsOpt)) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (config.targetFps == fpsOpt) Color(0x3300E5FF) else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$fpsOpt", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }

                // Resolution
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(12.dp)
                ) {
                    Text("配信解像度", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            Triple("QVGA", 320, 240),
                            Triple("VGA", 640, 480),
                            Triple("HD", 1280, 720)
                        ).forEach { (label, w, h) ->
                            val isSel = config.resolutionWidth == w
                            OutlinedButton(
                                onClick = { onUpdateConfig(config.copy(resolutionWidth = w, resolutionHeight = h)) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) Color(0x3300E5FF) else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Mini preview on clock toggle
        item {
            SettingToggleCard(
                title = "時計画面にカメラのミニ小窓を表示",
                subtitle = "時計を見ながらインカメラの映像をリアルタイムで小さくモニタリング",
                checked = config.showMiniPreviewOnClock,
                onCheckedChange = { onUpdateConfig(config.copy(showMiniPreviewOnClock = it)) }
            )
        }

        // Live Microphone Audio Section
        item {
            var hasAudioPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            }
            val audioPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasAudioPermission = granted
                if (granted) {
                    onUpdateConfig(config.copy(enableAudio = true))
                } else {
                    Toast.makeText(context, "音声配信にはマイク権限が必要です", Toast.LENGTH_SHORT).show()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (config.enableAudio) Color(0x3300E676) else Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = if (config.enableAudio) Color(0xFF00E676) else Color(0xFF888896),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("マイク音声のライブ取得・同時配信", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                if (config.enableAudio && status.isRunning) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x3300E676))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("● 音声LIVE (${status.audioClientCount}台)", color = Color(0xFF00E676), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text("端末のマイクで拾った音声をリアルタイムにWebダッシュボードやVLC/OBSへ配信", fontSize = 10.sp, color = Color(0xFF8E8E9A))
                        }
                    }

                    Switch(
                        checked = config.enableAudio,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (hasAudioPermission) {
                                    onUpdateConfig(config.copy(enableAudio = true))
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                onUpdateConfig(config.copy(enableAudio = false))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E676),
                            checkedTrackColor = Color(0x4D00E676)
                        )
                    )
                }

                if (config.enableAudio) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("マイク感度・増幅ゲイン", fontSize = 11.sp, color = Color(0xFFA0A0B0))
                                Text("${String.format("%.1f", config.audioGain)}x", fontSize = 11.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = config.audioGain,
                                onValueChange = { onUpdateConfig(config.copy(audioGain = it)) },
                                valueRange = 0.5f..3.0f,
                                steps = 24,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E676),
                                    activeTrackColor = Color(0xFF00E676)
                                )
                            )
                        }

                        if (status.isRunning) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("音声ストリーム URL", fontSize = 11.sp, color = Color(0xFFA0A0B0))
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF08080C))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val audioUrl = "${status.serverUrl}/audio.wav"
                                    Text(audioUrl, color = Color(0xFF00E676), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("IP Camera Audio URL", audioUrl))
                                            Toast.makeText(context, "音声URLをコピーしました", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "コピー", tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Camera Troubleshooting & Hardware Reset
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("カメラが起動しない・フリーズ時の修復", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("他アプリとの競合やOS側のリソース解放待ちでカメラが有効化できなくなった場合、カメラサブシステムを完全再起動して復旧します", fontSize = 10.sp, color = Color(0xFF8E8E9A))
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onRestartCamera,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x2200E5FF))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("カメラ再初期化", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: CHIMES & BACKGROUND VIDEOS
// -------------------------------------------------------------
@Composable
private fun ChimesSettingsContent(
    preferences: ClockPreferencesState,
    scheduledChimes: List<ScheduledChime>,
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    viewModel: ClockViewModel,
    onNavigateToMedia: () -> Unit = {}
) {
    val context = LocalContext.current
    var editingChime by remember { mutableStateOf<ScheduledChime?>(null) }
    var isCreatingChime by remember { mutableStateOf(false) }

    var renamingAudioItem by remember { mutableStateOf<CustomAudioItem?>(null) }
    var renamingVideoItem by remember { mutableStateOf<CustomVideoItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    val playingAudioPath by viewModel.playingAudioPath.collectAsState()
    val irButtons by viewModel.irButtons.collectAsState()

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomAudio(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Video file picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomVideo(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Direct link banner to Media Management Tab
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x2400E5FF))
                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(12.dp))
                    .clickable { onNavigateToMedia() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("📁 音声・動画ファイル管理画面を開く", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        Text("ファイルの追加・名前変更（リネーム）・直接テスト鳴動・削除はこちらから操作できます", fontSize = 11.sp, color = Color(0xFFC0E8F8))
                    }
                }
                Icon(Icons.Default.PlayArrow, contentDescription = "移動", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
            }
        }

        // Hourly Chime Master Switch
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("毎正時 時報チャイム", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("毎時00分に時報を鳴動 (スケジュールと被る場合はスケジュールを最優先)", fontSize = 11.sp, color = Color(0xFF9E9EA8))
                }
                Switch(
                    checked = preferences.hourlyChimeEnabled,
                    onCheckedChange = { viewModel.setHourlyChime(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                )
            }
        }

        // Half Hourly Chime
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("30分ごとの半時報 (プチチャイム)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("毎時30分に控えめな単音ベルで時刻をお知らせ", fontSize = 11.sp, color = Color(0xFF9E9EA8))
                }
                Switch(
                    checked = preferences.halfHourlyChimeEnabled,
                    onCheckedChange = { viewModel.setHalfHourlyChime(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                )
            }
        }

        // Hourly Chime Sound Selector (8 types)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("毎正時チャイムの音色変更", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    OutlinedButton(
                        onClick = { viewModel.testCurrentChime() },
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("現在の音を試聴", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Sound selection grid (Built-in sounds)
                val soundList = listOf(
                    ChimeSound.WESTMINSTER to "ウェストミンスター (定番)",
                    ChimeSound.TUBULAR_BELLS to "チューブラーベル (重厚な鐘)",
                    ChimeSound.CRYSTAL_BELL to "クリスタルベル (爽快)",
                    ChimeSound.BIRD_CHIRP to "小鳥のさえずり (朝の自然)",
                    ChimeSound.SOFT_MARIMBA to "マリンバ (木製打楽器)",
                    ChimeSound.ZEN_BELL to "静寂の和鐘 (禅)",
                    ChimeSound.GRANDFATHER to "アンティーク柱時計",
                    ChimeSound.DIGITAL_SIGNAL to "デジタル時報 (ピッピッピーン)"
                )

                soundList.chunked(2).forEach { rowSounds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSounds.forEach { (sound, label) ->
                            val isSelected = preferences.hourlyChimeSourceType == ChimeAudioSourceType.BUILT_IN && preferences.chimeSound == sound
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x3300E5FF) else Color(0x10FFFFFF))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectChimeSound(sound) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.testChimeSound(sound) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "試聴",
                                        tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFFAAAAAA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Custom Audio selection for Hourly Chime
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁 取り込んだカスタム音声 (${customAudioList.size}件)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    OutlinedButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("音声を追加", fontSize = 10.sp, color = Color(0xFF00E5FF))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (customAudioList.isEmpty()) {
                    Text("まだ音声ファイルがありません。「＋ 音声を追加」またはWeb管理画面からMP3/WAVを追加できます。", fontSize = 10.sp, color = Color(0xFF888888), modifier = Modifier.padding(vertical = 4.dp))
                } else {
                    customAudioList.chunked(2).forEach { rowAudios ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowAudios.forEach { audio ->
                                val isSelected = preferences.hourlyChimeSourceType == ChimeAudioSourceType.CUSTOM_FILE && preferences.hourlyCustomAudioId == audio.id
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0x3300E5FF) else Color(0x10FFFFFF))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { viewModel.selectHourlyCustomAudio(audio) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = audio.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.testCustomAudio(audio.filePath) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "試聴",
                                            tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFFAAAAAA),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            if (rowAudios.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                // Volume slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "時報チャイム音量: ${(preferences.chimeVolume * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "時報が鳴る時の音量です（鳴動時のみ一時適用され、終了後に元の端末音量へ戻ります）",
                            fontSize = 10.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.testCurrentChime() },
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("音量テスト", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }
                Slider(
                    value = preferences.chimeVolume,
                    onValueChange = { viewModel.setChimeVolume(it) },
                    valueRange = 0.05f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
        }

        // Scheduled Chimes Header + Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "登録済みスケジュールチャイム (${scheduledChimes.size}件)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "指定時刻にカスタム音・背景動画演出を最優先再生",
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF)
                    )
                }
                Button(
                    onClick = { isCreatingChime = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新規追加", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Scheduled Chimes List
        if (scheduledChimes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x10FFFFFF))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("スケジュールチャイムが登録されていません", color = Color(0xFF888896), fontSize = 12.sp)
                }
            }
        } else {
            items(scheduledChimes.size) { index ->
                val chime = scheduledChimes[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            text = chime.formattedTime,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = chime.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(${chime.repeatDaysText})", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (chime.sourceType == ChimeAudioSourceType.VIDEO_SOUND) {
                                    Text(
                                        text = "🎬 動画のみ (音源: ${chime.videoDisplayName})",
                                        color = Color(0xFFFF88AA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "🎵 ${chime.soundDisplayName}",
                                        color = Color(0xFF88D8FF),
                                        fontSize = 10.sp
                                    )
                                    if (chime.videoSourceType != ChimeVideoSourceType.NONE) {
                                        Text(
                                            text = "🎬 ${chime.videoDisplayName}",
                                            color = Color(0xFFFF88AA),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (chime.remoteActionDisplayName != null) {
                                    Text(
                                        text = chime.remoteActionDisplayName ?: "",
                                        color = Color(0xFFFFB74D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.testPlayScheduledChime(chime) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "試聴", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { editingChime = chime }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "編集", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.deleteScheduledChime(chime.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                        Switch(
                            checked = chime.isEnabled,
                            onCheckedChange = { viewModel.toggleScheduledChime(chime.id) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }
                }
            }
        }

        // Custom Audio Management Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🎵 カスタム音声ファイル管理", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("端末内またはWebから取り込んだ音声 (${customAudioList.size}件)", fontSize = 11.sp, color = Color(0xFF9E9EA8))
                    }
                    OutlinedButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("音声ファイルを追加", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }

                if (customAudioList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    customAudioList.forEach { audio ->
                        val isPlaying = (playingAudioPath == audio.filePath)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPlaying) Color(0x2A00E5FF) else Color(0x12FFFFFF))
                                .border(
                                    width = 1.dp,
                                    color = if (isPlaying) Color(0xFF00E5FF) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(audio.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (isPlaying) {
                                    Text("▶ 鳴動中...", color = Color(0xFF00E5FF), fontSize = 10.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rename Button
                                IconButton(
                                    onClick = {
                                        renamingAudioItem = audio
                                        renameText = audio.name
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "名前を変更", tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                // Test Play / Stop toggle
                                if (isPlaying) {
                                    IconButton(
                                        onClick = { viewModel.stopAudioPlayback() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = { viewModel.testPlayCustomAudio(audio.filePath) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "テスト鳴動", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(onClick = { viewModel.deleteCustomAudio(audio) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Video & Background Video Effects Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🎬 背景動画演出 & カスタム動画管理", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("チャイム鳴動時に全画面で美しい背景動画を演出", fontSize = 11.sp, color = Color(0xFF9E9EA8))
                    }
                    OutlinedButton(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("動画を追加", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("プリセット動画演出のプレビュー:", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ChimeVideoSourceType.PRESET_AURORA to "オーロラ",
                        ChimeVideoSourceType.PRESET_FIREPLACE to "暖炉",
                        ChimeVideoSourceType.PRESET_STARRY_NIGHT to "星空",
                        ChimeVideoSourceType.PRESET_RAIN to "雨滴",
                        ChimeVideoSourceType.PRESET_SUNRISE to "朝焼け"
                    ).forEach { (vType, label) ->
                        OutlinedButton(
                            onClick = { viewModel.previewBackgroundVideo(vType, durationSeconds = 10) },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
                        ) {
                            Text(label, fontSize = 10.sp, color = Color.White)
                        }
                    }
                }

                if (customVideoList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("取り込んだカスタム動画 (${customVideoList.size}件):", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    Spacer(modifier = Modifier.height(6.dp))
                    customVideoList.forEach { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x12FFFFFF))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rename Button
                                IconButton(
                                    onClick = {
                                        renamingVideoItem = video
                                        renameText = video.name
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "名前を変更", tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                // Video + Audio Play
                                OutlinedButton(
                                    onClick = { viewModel.testPlayCustomVideo(video, withAudio = true) },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("映像+音声", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Audio only
                                OutlinedButton(
                                    onClick = { viewModel.testPlayCustomAudio(video.filePath) },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("音声のみ", fontSize = 10.sp, color = Color(0xFF81C784))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { viewModel.deleteCustomVideo(video) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Audio Rename Dialog
    val audioToRename = renamingAudioItem
    if (audioToRename != null) {
        AlertDialog(
            onDismissRequest = { renamingAudioItem = null },
            title = { Text("音声ファイル名を変更", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("新しい名前を入力してください:", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameCustomAudio(audioToRename.id, renameText.trim())
                        }
                        renamingAudioItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("変更", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingAudioItem = null }) {
                    Text("キャンセル", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    // Video Rename Dialog
    val videoToRename = renamingVideoItem
    if (videoToRename != null) {
        AlertDialog(
            onDismissRequest = { renamingVideoItem = null },
            title = { Text("動画ファイル名を変更", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("新しい名前を入力してください:", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameCustomVideo(videoToRename.id, renameText.trim())
                        }
                        renamingVideoItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("変更", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingVideoItem = null }) {
                    Text("キャンセル", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    // Modal dialog for Add / Edit Chime
    if (isCreatingChime || editingChime != null) {
        val targetChime = editingChime ?: ScheduledChime(
            hour = 12,
            minute = 0,
            label = "チャイム"
        )
        EditScheduledChimeDialog(
            chime = targetChime,
            isNew = isCreatingChime,
            customAudioList = customAudioList,
            customVideoList = customVideoList,
            irButtons = irButtons,
            onDismiss = {
                isCreatingChime = false
                editingChime = null
            },
            onSave = { savedChime ->
                viewModel.saveScheduledChime(savedChime)
                isCreatingChime = false
                editingChime = null
            },
            onTestChime = { testChime ->
                viewModel.testPlayScheduledChime(testChime)
            },
            onTestSendIr = { buttonId ->
                viewModel.sendIrButtonById(buttonId)
            }
        )
    }
}

@Composable
private fun EditScheduledChimeDialog(
    chime: ScheduledChime,
    isNew: Boolean,
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    irButtons: List<IrRemoteButton> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ScheduledChime) -> Unit,
    onTestChime: (ScheduledChime) -> Unit,
    onTestSendIr: ((String) -> Unit)? = null
) {
    var hour by remember { mutableStateOf(chime.hour) }
    var minute by remember { mutableStateOf(chime.minute) }
    var label by remember { mutableStateOf(chime.label) }
    var daysOfWeek by remember { mutableStateOf(chime.daysOfWeek) }
    var sourceType by remember { mutableStateOf(chime.sourceType) }
    var builtInSound by remember { mutableStateOf(chime.builtInSound) }
    var customAudioId by remember { mutableStateOf(chime.customAudioId) }
    var customAudioName by remember { mutableStateOf(chime.customAudioName) }
    var customAudioPath by remember { mutableStateOf(chime.customAudioPath) }
    var volume by remember { mutableStateOf(chime.volume) }
    var irSendEnabled by remember { mutableStateOf(chime.irSendEnabled) }
    var irButtonId by remember { mutableStateOf(chime.irButtonId) }
    var irButtonName by remember { mutableStateOf(chime.irButtonName) }
    var videoSourceType by remember { mutableStateOf(chime.videoSourceType) }
    var customVideoId by remember { mutableStateOf(chime.customVideoId) }
    var customVideoName by remember { mutableStateOf(chime.customVideoName) }
    var customVideoPath by remember { mutableStateOf(chime.customVideoPath) }
    var videoDurationSeconds by remember { mutableStateOf(chime.videoDurationSeconds) }
    var isCustomDurationSelected by remember {
        mutableStateOf(chime.videoDurationSeconds !in listOf(ScheduledChime.DURATION_VIDEO_LENGTH, ScheduledChime.DURATION_MANUAL_STOP, 30, 60, 180, 300))
    }
    var customDurationInput by remember {
        mutableStateOf(
            if (chime.videoDurationSeconds !in listOf(ScheduledChime.DURATION_VIDEO_LENGTH, ScheduledChime.DURATION_MANUAL_STOP, 30, 60, 180, 300)) {
                chime.videoDurationSeconds.toString()
            } else {
                "45"
            }
        )
    }
    var playVideoAudio by remember { mutableStateOf(chime.playVideoAudio) }

    fun buildCurrentChime(): ScheduledChime {
        val isVideoSound = sourceType == ChimeAudioSourceType.VIDEO_SOUND
        return chime.copy(
            hour = hour,
            minute = minute,
            label = label,
            daysOfWeek = daysOfWeek,
            sourceType = sourceType,
            builtInSound = builtInSound,
            customAudioId = customAudioId,
            customAudioName = customAudioName,
            customAudioPath = customAudioPath,
            volume = volume,
            videoSourceType = videoSourceType,
            customVideoId = customVideoId,
            customVideoName = customVideoName,
            customVideoPath = customVideoPath,
            videoDurationSeconds = videoDurationSeconds,
            playVideoAudio = playVideoAudio || isVideoSound,
            irSendEnabled = irSendEnabled,
            irButtonId = if (irSendEnabled) irButtonId else null,
            irButtonName = if (irSendEnabled) irButtonName else null
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF14151F),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "⏰ 新規スケジュールチャイム追加" else "⏰ チャイムの編集",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Time selector
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .padding(12.dp)
                        ) {
                            Text("時刻設定 (24時間表記)", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { hour = (hour - 1 + 24) % 24 }) { Text("-", color = Color.White) }
                                    Text(
                                        text = String.format("%02d", hour),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("+", color = Color.White) }
                                }
                                Text(" : ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { minute = (minute - 5 + 60) % 60 }) { Text("-", color = Color.White) }
                                    Text(
                                        text = String.format("%02d", minute),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    OutlinedButton(onClick = { minute = (minute + 5) % 60 }) { Text("+", color = Color.White) }
                                }
                            }
                        }
                    }

                    // Label input
                    item {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("ラベル (例: 朝礼、お昼休憩、定時)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            )
                        )
                    }

                    // Repeat Days of Week
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("繰り返し曜日", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { daysOfWeek = setOf(1, 2, 3, 4, 5) },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                    ) {
                                        Text("平日", fontSize = 10.sp, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7) },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                    ) {
                                        Text("毎日", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val days = listOf(1 to "月", 2 to "火", 3 to "水", 4 to "木", 5 to "金", 6 to "土", 7 to "日")
                                days.forEach { (dInt, dLabel) ->
                                    val isSelected = daysOfWeek.contains(dInt)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                            .clickable {
                                                daysOfWeek = if (isSelected) daysOfWeek - dInt else daysOfWeek + dInt
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sound Source Selection
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .padding(12.dp)
                        ) {
                            Text("🎵 チャイム音源の選択", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))

                            // 0. NO AUDIO / SILENT ROUTINE OPTION
                            val isNoSound = sourceType == ChimeAudioSourceType.NONE
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isNoSound) Color(0x3300E5FF) else Color(0x0EFFFFFF))
                                    .border(
                                        width = if (isNoSound) 1.5.dp else 1.dp,
                                        color = if (isNoSound) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        sourceType = ChimeAudioSourceType.NONE
                                        playVideoAudio = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "🔇 チャイム音なし (スマートリモコン専用・サイレント操作)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isNoSound) Color(0xFF00E5FF) else Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "チャイム音を鳴らさずに、指定時刻に照明やエアコンのリモコン送信・背景動画の起動のみを実行します",
                                        fontSize = 11.sp,
                                        color = if (isNoSound) Color(0xFFB2EBF2) else Color(0xFFAAAAAA)
                                    )
                                }
                                if (isNoSound) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "選択中", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 1. VIDEO ONLY / VIDEO SOUND OPTION (Top Priority Feature)
                            val isVideoSound = sourceType == ChimeAudioSourceType.VIDEO_SOUND
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isVideoSound) Color(0x33FF0055) else Color(0x0EFFFFFF))
                                    .border(
                                        width = if (isVideoSound) 1.5.dp else 1.dp,
                                        color = if (isVideoSound) Color(0xFFFF4081) else Color(0x22FFFFFF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        sourceType = ChimeAudioSourceType.VIDEO_SOUND
                                        playVideoAudio = true
                                        if (videoSourceType == ChimeVideoSourceType.NONE && customVideoList.isNotEmpty()) {
                                            val firstVid = customVideoList.first()
                                            videoSourceType = ChimeVideoSourceType.CUSTOM_FILE
                                            customVideoId = firstVid.id
                                            customVideoName = firstVid.name
                                            customVideoPath = firstVid.filePath
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "🎬 動画の音楽を使用 (動画のみ再生)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVideoSound) Color(0xFFFF80AB) else Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE && !customVideoName.isNullOrBlank()) {
                                            "選択中: 『$customVideoName』の音声をそのまま鳴らします"
                                        } else if (videoSourceType == ChimeVideoSourceType.NONE) {
                                            "※ 下の「背景動画演出」から流したい動画を選択してください"
                                        } else {
                                            "動画内の音声・BGMをチャイム音源として鳴らします (追加音源不要)"
                                        },
                                        fontSize = 11.sp,
                                        color = if (isVideoSound) Color(0xFFFFC1E3) else Color(0xFFAAAAAA)
                                    )
                                }
                                if (isVideoSound) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "選択中", tint = Color(0xFFFF4081), modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Built-in sounds
                            Text("内蔵高音質サウンド:", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                            Spacer(modifier = Modifier.height(4.dp))
                            listOf(
                                ChimeSound.WESTMINSTER to "ウェストミンスター寺院の鐘",
                                ChimeSound.TUBULAR_BELLS to "チューブラーベル",
                                ChimeSound.CRYSTAL_BELL to "クリスタルベル",
                                ChimeSound.BIRD_CHIRP to "小鳥のさえずり",
                                ChimeSound.SOFT_MARIMBA to "マリンバ",
                                ChimeSound.ZEN_BELL to "静寂の和鐘",
                                ChimeSound.GRANDFATHER to "アンティーク柱時計",
                                ChimeSound.DIGITAL_SIGNAL to "デジタル時報"
                            ).forEach { (bSound, sLabel) ->
                                val isSelected = sourceType == ChimeAudioSourceType.BUILT_IN && builtInSound == bSound
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0x3300E5FF) else Color(0x0CFFFFFF))
                                        .clickable {
                                            sourceType = ChimeAudioSourceType.BUILT_IN
                                            builtInSound = bSound
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(sLabel, fontSize = 12.sp, color = if (isSelected) Color(0xFF00E5FF) else Color.White)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Custom audio selection
                            if (customAudioList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("取り込んだカスタム音声:", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                Spacer(modifier = Modifier.height(4.dp))
                                customAudioList.forEach { audItem ->
                                    val isSelected = sourceType == ChimeAudioSourceType.CUSTOM_FILE && customAudioId == audItem.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0x3300E5FF) else Color(0x0CFFFFFF))
                                        .clickable {
                                            sourceType = ChimeAudioSourceType.CUSTOM_FILE
                                            customAudioId = audItem.id
                                            customAudioName = audItem.name
                                            customAudioPath = audItem.filePath
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📁 ${audItem.name}", fontSize = 12.sp, color = if (isSelected) Color(0xFF00E5FF) else Color.White)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Background Video Effect Selection
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(12.dp)
                    ) {
                        Text("🎬 背景動画演出 (チャイム時の全画面演出)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Video Only switch if video is selected
                        if (videoSourceType == ChimeVideoSourceType.CUSTOM_FILE) {
                            val isAudioFromVideo = sourceType == ChimeAudioSourceType.VIDEO_SOUND
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAudioFromVideo) Color(0x28FF0055) else Color(0x0AFFFFFF))
                                    .border(1.dp, if (isAudioFromVideo) Color(0xFFFF4081) else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isAudioFromVideo) {
                                            sourceType = ChimeAudioSourceType.BUILT_IN
                                            playVideoAudio = false
                                        } else {
                                            sourceType = ChimeAudioSourceType.VIDEO_SOUND
                                            playVideoAudio = true
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "動画の音楽をチャイム音源にする (動画単体)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAudioFromVideo) Color(0xFFFF80AB) else Color.White
                                    )
                                    Text(
                                        "動画内の音声・BGMをそのまま鳴らします (別音源なし)",
                                        fontSize = 10.sp,
                                        color = if (isAudioFromVideo) Color(0xFFFFC1E3) else Color(0xFFAAAAAA)
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = isAudioFromVideo,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            sourceType = ChimeAudioSourceType.VIDEO_SOUND
                                            playVideoAudio = true
                                        } else {
                                            sourceType = ChimeAudioSourceType.BUILT_IN
                                            playVideoAudio = false
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        listOf(
                            ChimeVideoSourceType.NONE to "動画演出なし (時計画面のまま)",
                            ChimeVideoSourceType.PRESET_AURORA to "オーロラ・夜空 (幻想的な緑と紫)",
                            ChimeVideoSourceType.PRESET_FIREPLACE to "暖炉・キャンドル (温かみのある炎)",
                            ChimeVideoSourceType.PRESET_STARRY_NIGHT to "満天の星空・流星",
                            ChimeVideoSourceType.PRESET_RAIN to "癒しの雨滴・リフレッシュ",
                            ChimeVideoSourceType.PRESET_SUNRISE to "朝焼け・サンライズ"
                        ).forEach { (vType, vLabel) ->
                            val isSelected = videoSourceType == vType
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x33FF0055) else Color(0x0CFFFFFF))
                                    .clickable {
                                        videoSourceType = vType
                                        if (vType == ChimeVideoSourceType.NONE && sourceType == ChimeAudioSourceType.VIDEO_SOUND) {
                                            sourceType = ChimeAudioSourceType.BUILT_IN
                                            playVideoAudio = false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(vLabel, fontSize = 12.sp, color = if (isSelected) Color(0xFFFF4081) else Color.White)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFF4081), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Custom Video Selection
                        if (customVideoList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("取り込んだカスタム動画:", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                            Spacer(modifier = Modifier.height(4.dp))
                            customVideoList.forEach { vidItem ->
                                val isSelected = videoSourceType == ChimeVideoSourceType.CUSTOM_FILE && customVideoId == vidItem.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0x33FF0055) else Color(0x0CFFFFFF))
                                        .clickable {
                                            videoSourceType = ChimeVideoSourceType.CUSTOM_FILE
                                            customVideoId = vidItem.id
                                            customVideoName = vidItem.name
                                            customVideoPath = vidItem.filePath
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🎥 ${vidItem.name}", fontSize = 12.sp, color = if (isSelected) Color(0xFFFF4081) else Color.White)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFF4081), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (videoSourceType != ChimeVideoSourceType.NONE) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("動画演出の再生時間", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    val currentLabel = when {
                                        isCustomDurationSelected -> "カスタム (${videoDurationSeconds}秒)"
                                        videoDurationSeconds == ScheduledChime.DURATION_VIDEO_LENGTH -> "動画の長さ (1周)"
                                        videoDurationSeconds == ScheduledChime.DURATION_MANUAL_STOP -> "停止まで"
                                        videoDurationSeconds == 30 -> "30秒"
                                        videoDurationSeconds == 60 -> "1分"
                                        videoDurationSeconds == 180 -> "3分"
                                        videoDurationSeconds == 300 -> "5分"
                                        else -> "${videoDurationSeconds}秒"
                                    }
                                    Text("設定中: $currentLabel", fontSize = 11.sp, color = Color(0xFFFF4081))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Video length (once through)
                                    val isVidLen = !isCustomDurationSelected && videoDurationSeconds == ScheduledChime.DURATION_VIDEO_LENGTH
                                    OutlinedButton(
                                        onClick = {
                                            isCustomDurationSelected = false
                                            videoDurationSeconds = ScheduledChime.DURATION_VIDEO_LENGTH
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isVidLen) Color(0x33FF0055) else Color.Transparent
                                        ),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("動画の長さ", fontSize = 10.sp, color = if (isVidLen) Color(0xFFFF4081) else Color.White)
                                    }

                                    listOf(30 to "30秒", 60 to "1分", 180 to "3分", 300 to "5分").forEach { (sec, sLabel) ->
                                        val isSel = !isCustomDurationSelected && videoDurationSeconds == sec
                                        OutlinedButton(
                                            onClick = {
                                                isCustomDurationSelected = false
                                                videoDurationSeconds = sec
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSel) Color(0x33FF0055) else Color.Transparent
                                            ),
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(sLabel, fontSize = 10.sp, color = if (isSel) Color(0xFFFF4081) else Color.White)
                                        }
                                    }

                                    // Manual stop
                                    val isManual = !isCustomDurationSelected && videoDurationSeconds == ScheduledChime.DURATION_MANUAL_STOP
                                    OutlinedButton(
                                        onClick = {
                                            isCustomDurationSelected = false
                                            videoDurationSeconds = ScheduledChime.DURATION_MANUAL_STOP
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isManual) Color(0x33FF0055) else Color.Transparent
                                        ),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("停止まで", fontSize = 10.sp, color = if (isManual) Color(0xFFFF4081) else Color.White)
                                    }

                                    // Custom option button
                                    OutlinedButton(
                                        onClick = {
                                            isCustomDurationSelected = true
                                            videoDurationSeconds = customDurationInput.toIntOrNull()?.coerceAtLeast(1) ?: 45
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isCustomDurationSelected) Color(0x33FF0055) else Color.Transparent
                                        ),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("カスタム", fontSize = 10.sp, color = if (isCustomDurationSelected) Color(0xFFFF4081) else Color.White)
                                    }
                                }

                                // Custom input field when selected
                                if (isCustomDurationSelected) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x18FFFFFF))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("任意の再生秒数を入力: ", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        OutlinedTextField(
                                            value = customDurationInput,
                                            onValueChange = { newVal ->
                                                val filtered = newVal.filter { it.isDigit() }
                                                customDurationInput = filtered
                                                val sec = filtered.toIntOrNull()
                                                if (sec != null && sec > 0) {
                                                    videoDurationSeconds = sec
                                                }
                                            },
                                            modifier = Modifier
                                                .width(90.dp)
                                                .height(44.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFFF4081),
                                                unfocusedBorderColor = Color(0x44FFFFFF)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("秒", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // IR Remote Action Section (Smart Appliance Routines)
                item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .border(
                                    width = if (irSendEnabled) 1.5.dp else 0.5.dp,
                                    color = if (irSendEnabled) Color(0xFFF59E0B) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📡 赤外線リモコン連動", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("スマート家電・ルーティン", fontSize = 10.sp, color = Color(0xFFF59E0B))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "スケジュール時刻に照明やエアコンのリモコン信号を自動送信します",
                                        fontSize = 11.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                                Switch(
                                    checked = irSendEnabled,
                                    onCheckedChange = { checked ->
                                        irSendEnabled = checked
                                        if (checked && irButtonId.isNullOrEmpty() && irButtons.isNotEmpty()) {
                                            val firstBtn = irButtons.first()
                                            irButtonId = firstBtn.id
                                            irButtonName = firstBtn.name
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFF59E0B),
                                        checkedTrackColor = Color(0x66F59E0B)
                                    )
                                )
                            }

                            if (irSendEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (irButtons.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x22F59E0B))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            "⚠️ 登録済みのリモコンボタンがありません。「スマート家電・赤外線」タブまたはWebダッシュボードからボタンを学習・登録してください。",
                                            color = Color(0xFFFFD54F),
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    Text("送信するリモコンボタンを選択:", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        irButtons.forEach { btn ->
                                            val isSelected = irButtonId == btn.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) Color(0x33F59E0B) else Color(0x0CFFFFFF))
                                                    .border(
                                                        width = if (isSelected) 1.dp else 0.5.dp,
                                                        color = if (isSelected) Color(0xFFF59E0B) else Color(0x18FFFFFF),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        irButtonId = btn.id
                                                        irButtonName = btn.name
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    val iconTint = try {
                                                        Color(android.graphics.Color.parseColor(btn.colorHex))
                                                    } catch (_: Exception) {
                                                        Color(0xFFF59E0B)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(iconTint.copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = when (btn.category) {
                                                                IrDeviceCategory.LIGHTING -> Icons.Default.Lightbulb
                                                                IrDeviceCategory.AIR_CONDITIONER -> Icons.Default.AcUnit
                                                                IrDeviceCategory.TV -> Icons.Default.Tv
                                                                else -> Icons.Default.Sensors
                                                            },
                                                            contentDescription = null,
                                                            tint = iconTint,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = btn.name,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) Color(0xFFFFD54F) else Color.White
                                                        )
                                                        Text(
                                                            text = "${btn.category.displayName} • ${btn.protocol} ${btn.hexCode}",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF888888)
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedButton(
                                                        onClick = { onTestSendIr?.invoke(btn.id) },
                                                        modifier = Modifier.height(26.dp),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("送信テスト", fontSize = 10.sp, color = Color(0xFFF59E0B))
                                                    }
                                                    if (isSelected) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "選択中", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions (Test, Cancel, Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onTestChime(buildCurrentChime()) }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("試聴・演出確認", fontSize = 12.sp, color = Color(0xFF00E5FF))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("キャンセル", fontSize = 12.sp, color = Color.White)
                        }
                        Button(
                            onClick = { onSave(buildCurrentChime()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("保存する", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }

// -------------------------------------------------------------
// TAB: MEDIA MANAGEMENT (AUDIO & VIDEO)
// -------------------------------------------------------------
@Composable
private fun MediaManagementSettingsContent(
    customAudioList: List<CustomAudioItem>,
    customVideoList: List<CustomVideoItem>,
    viewModel: ClockViewModel
) {
    val context = LocalContext.current
    var renamingAudioItem by remember { mutableStateOf<CustomAudioItem?>(null) }
    var renamingVideoItem by remember { mutableStateOf<CustomVideoItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    val playingAudioPath by viewModel.playingAudioPath.collectAsState()

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomAudio(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Video file picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomVideo(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Overview Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("音声・動画ファイル管理", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "端末内の音声・動画ファイルを取り込んで管理できます。各ファイルの名前変更（リネーム）、本体スピーカーでの直接テスト鳴動、背景動画の全画面プレビューがここから直接行えます。",
                    fontSize = 11.sp,
                    color = Color(0xFFC0C0CC),
                    lineHeight = 16.sp
                )
            }
        }

        // Custom Audio Management Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎵 カスタム音声ファイル", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3300E5FF))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("${customAudioList.size} 件", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("音声を追加", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("チャイム音源として使用できる音声 (MP3, WAV, OGG, AAC等)。リネームや直接テスト鳴動が可能です。", fontSize = 11.sp, color = Color(0xFF9E9EA8))
                Spacer(modifier = Modifier.height(10.dp))

                if (customAudioList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x0CFFFFFF))
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("まだ音声ファイルがありません", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("右上の「音声を追加」ボタンまたはWeb管理画面から追加できます", fontSize = 10.sp, color = Color(0xFF777777))
                        }
                    }
                } else {
                    customAudioList.forEach { audio ->
                        val isPlaying = playingAudioPath == audio.filePath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPlaying) Color(0x2A00E5FF) else Color(0x12FFFFFF))
                                .border(
                                    width = 1.dp,
                                    color = if (isPlaying) Color(0xFF00E5FF) else Color(0x1EFFFFFF),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(audio.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (isPlaying) {
                                    Text("▶ 端末スピーカーでテスト鳴動中...", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rename Button
                                OutlinedButton(
                                    onClick = {
                                        renamingAudioItem = audio
                                        renameText = audio.name
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "名前変更", tint = Color(0xFFCCCCCC), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("リネーム", fontSize = 10.sp, color = Color(0xFFCCCCCC))
                                }
                                Spacer(modifier = Modifier.width(6.dp))

                                // Test Play / Stop toggle
                                if (isPlaying) {
                                    Button(
                                        onClick = { viewModel.stopAudioPlayback() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("停止", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.testPlayCustomAudio(audio.filePath) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "直接テスト鳴動", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("テスト鳴動", fontSize = 10.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))

                                // Delete
                                IconButton(onClick = { viewModel.deleteCustomAudio(audio) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Video & Background Effects Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎬 背景動画演出 & カスタム動画", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FF0055))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("${customVideoList.size} 件", fontSize = 11.sp, color = Color(0xFFFF4081), fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("動画を追加", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("チャイム鳴動時に全画面背景として流れる動画 (MP4, WebM等)。動画の長さでの再生やカスタム秒数再生に対応。", fontSize = 11.sp, color = Color(0xFF9E9EA8))
                Spacer(modifier = Modifier.height(10.dp))

                Text("プリセット動画演出の即時プレビュー:", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ChimeVideoSourceType.PRESET_AURORA to "オーロラ",
                        ChimeVideoSourceType.PRESET_FIREPLACE to "暖炉",
                        ChimeVideoSourceType.PRESET_STARRY_NIGHT to "星空",
                        ChimeVideoSourceType.PRESET_RAIN to "雨滴",
                        ChimeVideoSourceType.PRESET_SUNRISE to "朝焼け"
                    ).forEach { (vType, label) ->
                        OutlinedButton(
                            onClick = { viewModel.previewBackgroundVideo(vType, durationSeconds = 10) },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
                        ) {
                            Text(label, fontSize = 10.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("取り込んだカスタム動画 (${customVideoList.size}件):", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                Spacer(modifier = Modifier.height(6.dp))

                if (customVideoList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x0CFFFFFF))
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("まだ動画ファイルがありません", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("右上の「動画を追加」ボタンまたはWeb管理画面から追加できます", fontSize = 10.sp, color = Color(0xFF777777))
                        }
                    }
                } else {
                    customVideoList.forEach { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x12FFFFFF))
                                .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rename Button
                                OutlinedButton(
                                    onClick = {
                                        renamingVideoItem = video
                                        renameText = video.name
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "名前変更", tint = Color(0xFFCCCCCC), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("リネーム", fontSize = 10.sp, color = Color(0xFFCCCCCC))
                                }
                                Spacer(modifier = Modifier.width(6.dp))

                                // Video + Audio Play on device
                                Button(
                                    onClick = { viewModel.testPlayCustomVideo(video, withAudio = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF0055)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4081)),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF4081), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("映像+音声テスト", fontSize = 10.sp, color = Color(0xFFFF4081), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))

                                // Audio only
                                OutlinedButton(
                                    onClick = { viewModel.testPlayCustomAudio(video.filePath) },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("音声のみ", fontSize = 10.sp, color = Color(0xFF81C784))
                                }
                                Spacer(modifier = Modifier.width(4.dp))

                                // Stop Button
                                OutlinedButton(
                                    onClick = {
                                        viewModel.dismissBackgroundVideo()
                                        viewModel.stopAudioPlayback()
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("停止", fontSize = 10.sp, color = Color(0xFFFF5252))
                                }
                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(onClick = { viewModel.deleteCustomVideo(video) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Audio Rename Dialog
    val audioToRename = renamingAudioItem
    if (audioToRename != null) {
        AlertDialog(
            onDismissRequest = { renamingAudioItem = null },
            title = { Text("音声ファイル名を変更", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("新しい名前を入力してください:", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameCustomAudio(audioToRename.id, renameText.trim())
                        }
                        renamingAudioItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("変更", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingAudioItem = null }) {
                    Text("キャンセル", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    // Video Rename Dialog
    val videoToRename = renamingVideoItem
    if (videoToRename != null) {
        AlertDialog(
            onDismissRequest = { renamingVideoItem = null },
            title = { Text("動画ファイル名を変更", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("新しい名前を入力してください:", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameCustomVideo(videoToRename.id, renameText.trim())
                        }
                        renamingVideoItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("変更", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingVideoItem = null }) {
                    Text("キャンセル", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }
}

// -------------------------------------------------------------
// TAB 4: WEATHER & REGIONAL SETTINGS
// -------------------------------------------------------------
@Composable
private fun WeatherSettingsContent(
    preferences: ClockPreferencesState,
    weather: WeatherState,
    viewModel: ClockViewModel,
    onOpenWeatherDetail: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isDetectingLocation by viewModel.isDetectingLocation.collectAsState()
    var searchText by remember { mutableStateOf("") }

    val popularCities = listOf(
        "札幌市", "仙台市", "新宿区", "横浜市", "名古屋市", 
        "京都市", "大阪市", "神戸市", "広島市", "福岡市", "那覇市"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Detailed weather shortcut banner
        if (onOpenWeatherDetail != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x2200E5FF))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenWeatherDetail)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "詳細な天気予報・24時間/週間予報を確認",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "降水確率・体感温度・風速・気圧・UV指数・日の出日の入り",
                                fontSize = 11.sp,
                                color = Color(0xFF80DEEA)
                            )
                        }
                    }
                    Text(
                        text = "開く ›",
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Current location status
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("現在設定中の地域", fontSize = 12.sp, color = Color(0xFF888896))
                    Text(
                        text = "${preferences.selectedPrefecture} ${preferences.selectedCityName}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${weather.conditionText} / 現在 ${weather.temperatureCelsius}℃ (最高 ${weather.highTemp}℃ / 最低 ${weather.lowTemp}℃)",
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.detectAndSetCurrentLocation { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    enabled = !isDetectingLocation
                ) {
                    Text(
                        text = if (isDetectingLocation) "GPS測位中..." else "GPS現在地取得",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick City Select Chips
        item {
            Column {
                Text("主要都市から選択", fontSize = 12.sp, color = Color(0xFF888896))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(popularCities) { city ->
                        val isSelected = preferences.selectedCityName.contains(city)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF00E5FF) else Color(0x1EFFFFFF))
                                .clickable {
                                    viewModel.searchMunicipalities(city)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = city,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Search Municipality Field & Results
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x12FFFFFF))
                    .padding(14.dp)
            ) {
                Text("市区町村を検索して変更", fontSize = 12.sp, color = Color(0xFF888896))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        viewModel.searchMunicipalities(it)
                    },
                    placeholder = { Text("例: 新宿区, 札幌, 横浜, 豊島区...", color = Color(0xFF666666), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("検索中...", fontSize = 11.sp, color = Color(0xFF00E5FF))
                }

                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchResults.take(6).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x18FFFFFF))
                                    .clickable {
                                        viewModel.selectMunicipality(item)
                                        searchText = ""
                                        viewModel.clearSearchResults()
                                        Toast.makeText(context, "${item.prefecture} ${item.name} に設定しました", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.prefecture} ${item.name}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "選択",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Warnings Toggle & Preview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingToggleCard(
                    title = "気象警報・注意報表示",
                    subtitle = "気象庁の発表する大雨・洪水・暴風などの警報をリアルタイム表示",
                    checked = preferences.showWarnings,
                    onCheckedChange = { viewModel.toggleShowWarnings() },
                    modifier = Modifier.weight(1f)
                )
                SettingToggleCard(
                    title = "警報デモプレビュー",
                    subtitle = "画面上部への特別警報バナー演出をテスト確認",
                    checked = preferences.demoWarningsPreview,
                    onCheckedChange = { viewModel.toggleDemoWarnings() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 5: NIGHT STAND & SCREEN PROTECTION
// -------------------------------------------------------------
@Composable
private fun NightStandSettingsContent(
    preferences: ClockPreferencesState,
    viewModel: ClockViewModel
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Kiosk Mode Lock/Unlock Controller
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (preferences.isKioskLocked) Color(0x2E00E5FF) else Color(0x18FFFFFF))
                    .border(
                        1.5.dp,
                        if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0x33FFFFFF),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (preferences.isKioskLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (preferences.isKioskLocked) Color.Black else Color(0xFF888896),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "キオスク離脱防止・固定モード",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (preferences.isKioskLocked) Color(0xFF00E5FF) else Color(0x33888896))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (preferences.isKioskLocked) "施錠中 (固定中)" else "未施錠",
                                        color = if (preferences.isKioskLocked) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (preferences.isKioskLocked)
                                    "画面ピン留めとハードウェアキーガードによりアプリからの離脱を完全防止中"
                                else
                                    "卓上専用端末化: ホーム画面戻りや電源ボタン誤操作、戻るタップを防御",
                                fontSize = 11.sp,
                                color = Color(0xFFA0A0B0)
                            )
                        }
                    }

                    Switch(
                        checked = preferences.isKioskLocked,
                        onCheckedChange = { viewModel.toggleKioskLock() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x4D00E5FF)
                        ),
                        modifier = Modifier.testTag("switch_kiosk_lock_settings")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action button to Lock or Unlock directly
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (preferences.isKioskLocked) {
                        Button(
                            onClick = { viewModel.toggleKioskLock() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33333E)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("施錠を解除する", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.toggleKioskLock() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("今すぐ施錠する (アプリ固定)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Protection explanation bullet points
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0D0D12))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "• 画面ピン留め（Lock Task）: ホームボタン、履歴一覧、通知パネルの引き出しを完全ブロック",
                        fontSize = 10.sp,
                        color = Color(0xFF8E8E9A),
                        lineHeight = 14.sp
                    )
                    Text(
                        text = "• 戻るボタン/ジェスチャー: アプリ終了操作を完全にインターセプトして無効化",
                        fontSize = 10.sp,
                        color = Color(0xFF8E8E9A),
                        lineHeight = 14.sp
                    )
                    Text(
                        text = "• 電源ボタン・画面消灯対策: 常時点灯（スリープ無効）＋ロック画面バイパス（SHOW_WHEN_LOCKED）により電源ボタンを押しても常に本アプリが復帰",
                        fontSize = 10.sp,
                        color = Color(0xFF8E8E9A),
                        lineHeight = 14.sp
                    )
                    Text(
                        text = "• 解除方法: この設定画面のボタン、または時計画面下の鍵アイコンを長押し（3秒）で即座に解除可能",
                        fontSize = 10.sp,
                        color = Color(0xFF00E5FF),
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Home Launcher / Default App Setting
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("端末のデフォルトホームアプリとして設定", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "端末再起動時にも自動的に本時計アプリが立ち上がり、ホームボタンを押しても本アプリに戻るようにAndroidシステム設定を変更できます。",
                        fontSize = 10.sp,
                        color = Color(0xFF8E8E9A),
                        lineHeight = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(AndroidSettings.ACTION_HOME_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "端末の設定アプリを開けませんでした", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x2200E5FF))
                ) {
                    Text("設定を開く", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            SettingToggleCard(
                title = "常夜灯 (ナイトスタンドモード)",
                subtitle = "就寝時や暗い寝室向けに輝度を極限まで抑制。画面タップまたは上部ボタンでいつでもワンタップ解除可能",
                checked = preferences.isNightMode,
                onCheckedChange = { viewModel.toggleNightMode() }
            )
        }

        item {
            SettingToggleCard(
                title = "有機EL / 液晶 焼き付き防止 (バーンイン保護)",
                subtitle = "24時間365日の連続点灯時に、表示位置を数分ごとに超微細シフトさせて素子の劣化を防止",
                checked = preferences.burnInProtection,
                onCheckedChange = { /* Burn in is auto-managed */ }
            )
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x18FFFFFF))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = Color(0xFF8E8E9A))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00E5FF),
                checkedTrackColor = Color(0x4D00E5FF)
            )
        )
    }
}

// -------------------------------------------------------------
// TAB 7: SYSTEM DIAGNOSTICS & RESILIENCE LOGS
// -------------------------------------------------------------
@Composable
private fun DiagnosticsSettingsContent(
    viewModel: ClockViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ipCamStatus by viewModel.ipCameraStatus.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (success, message) = viewModel.installApkFromUri(uri)
            updateStatusMessage = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val espBinPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUpdating = true
            updateStatusMessage = "ESPファームウェア(.bin)をOTA転送中..."
            coroutineScope.launch {
                val (success, message) = viewModel.flashEspFirmwareFromUri(
                    uri = uri,
                    host = preferences.espSensorHost.ifBlank { null },
                    port = preferences.espSensorPort
                )
                isUpdating = false
                updateStatusMessage = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    var diagReport by remember { mutableStateOf(CrashLogManager.getDiagnosticsReport()) }
    var crashLogsText by remember { mutableStateOf(CrashLogManager.getCrashLogsText()) }
    var recentLogs by remember { mutableStateOf(CrashLogManager.getRecentLogs(100)) }
    var showTestCrashDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        diagReport = CrashLogManager.getDiagnosticsReport()
        crashLogsText = CrashLogManager.getCrashLogsText()
        recentLogs = CrashLogManager.getRecentLogs(100)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Remote Updates & OTA (Web & Local)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x333B82F6), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "リモートアップデート & OTA (Over-The-Air)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "ブラウザまたは本機からDeskClockアプリ(APK)およびESP32/ESP8266ファームウェア(.bin)を遠隔更新",
                            fontSize = 11.sp,
                            color = Color(0xFF8E8E9A),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x263B82F6))
                            .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OTA更新対応",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Web update guidance
                val webUpdateUrl = if (ipCamStatus.localIpAddress.isNotBlank()) {
                    "http://${ipCamStatus.localIpAddress}:${ipCamStatus.port}/#updates"
                } else {
                    "http://<端末IP>:${ipCamStatus.port}/#updates"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x221E293B))
                        .border(1.dp, Color(0x33475569), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "🌐 Webブラウザから更新 (推奨・進捗バー付き)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF93C5FD)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PCやスマートフォンのブラウザで下記URLを開くと、転送進捗率・通信速度・残り時間付きでAPKやESPファームウェア、動画・音声を快適にアップロードできます。",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = webUpdateUrl,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("DeskClock Web Update URL", webUpdateUrl))
                                Toast.makeText(context, "Web更新URLをコピーしました", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "URLコピー",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("URLコピー", fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // On-device direct update buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            apkPickerLauncher.launch("application/vnd.android.package-archive")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("端末内APKから直接更新", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            espBinPickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ESPファームウェアOTA (.bin)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Status message display
                if (isUpdating || updateStatusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33000000))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = updateStatusMessage ?: "処理中...",
                            fontSize = 11.sp,
                            color = Color(0xFFF1F5F9)
                        )
                    }
                }
            }
        }

        // 1. System Health & Stability Watchdog
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "システム安定性 & 自動復帰監視",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "未処理の例外を検知して自動再起動し、ログを完全永続化します",
                            fontSize = 11.sp,
                            color = Color(0xFF8E8E9A),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (diagReport.crashRecoveryCount > 0) Color(0x33F59E0B) else Color(0x3310B981))
                            .border(
                                1.dp,
                                if (diagReport.crashRecoveryCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (diagReport.crashRecoveryCount > 0) "自動復帰完了 (稼働中)" else "正常稼働中 (監視有効)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (diagReport.crashRecoveryCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagMetricBox(
                        label = "プロセス稼働時間",
                        value = diagReport.uptimeFormatted,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f)
                    )
                    DiagMetricBox(
                        label = "累計起動 / Javaクラッシュ",
                        value = "${diagReport.totalStarts}回 / ${diagReport.crashRecoveryCount}回",
                        color = if (diagReport.crashRecoveryCount > 0) Color(0xFFF59E0B) else Color.White,
                        modifier = Modifier.weight(1.1f)
                    )
                    DiagMetricBox(
                        label = "OS強制キル検知 (LMK)",
                        value = "${diagReport.abnormalTerminationCount}回",
                        color = if (diagReport.abnormalTerminationCount > 0) Color(0xFFF59E0B) else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    DiagMetricBox(
                        label = "メモリ (RAM)",
                        value = "${diagReport.usedMemoryMb}MB / ${diagReport.maxMemoryMb}MB",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "端末: ${diagReport.deviceModel}  |  OS: ${diagReport.osVersion}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E8E9A)
                )

                // Last Abnormal Termination Warning Banner (if any)
                if (!diagReport.lastAbnormalTerminationTime.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x22F59E0B))
                            .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⚠️ OS強制終了 / スリープによるプロセス破棄を検知",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                                Text(
                                    text = diagReport.lastAbnormalTerminationTime ?: "",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                            Text(
                                text = diagReport.lastAbnormalTerminationMessage ?: "Android OS Low-Memory Killer (SIGKILL) またはスリープによるActivity破棄",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Last Crash Warning Banner (if any)
                if (!diagReport.lastCrashTime.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x22EF4444))
                            .border(1.dp, Color(0x66EF4444), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "過去のJavaクラッシュから自動復帰しました",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF87171)
                                )
                                Text(
                                    text = diagReport.lastCrashTime ?: "",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                            Text(
                                text = diagReport.lastCrashMessage ?: "",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFECACA),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Action Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { refreshData() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "ログ更新", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("DeskClock Logs", CrashLogManager.getAllLogsText())
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "全ログをクリップボードにコピーしました", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "ログを全コピー", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        CrashLogManager.clearAllLogs()
                        refreshData()
                        android.widget.Toast.makeText(context, "ログを消去しました", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "ログ消去", fontSize = 12.sp)
                }

                Button(
                    onClick = { showTestCrashDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "復帰テスト", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Crash Logs Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090B10))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "クラッシュ記録 (crash_logs.txt)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${diagReport.crashLogCount}件記録",
                        fontSize = 11.sp,
                        color = Color(0xFF8E8E9A)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x11000000))
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (crashLogsText.isBlank()) "クラッシュ履歴はありません。安定して連続稼働しています。" else crashLogsText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (crashLogsText.isBlank()) Color(0xFF8E8E9A) else Color(0xFFFCA5A5),
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // 4. Live Event Log Stream
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090B10))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "リアルタイムイベントログ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "最新${recentLogs.size}件",
                        fontSize = 11.sp,
                        color = Color(0xFF8E8E9A)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (recentLogs.isEmpty()) {
                        Text(
                            text = "記録されたイベントはありません",
                            fontSize = 11.sp,
                            color = Color(0xFF8E8E9A),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        recentLogs.forEach { logItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = logItem.timestamp,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            when (logItem.level) {
                                                "CRASH" -> Color(0x33EF4444)
                                                "ERROR" -> Color(0x33F87171)
                                                "WARN" -> Color(0x33F59E0B)
                                                else -> Color(0x333B82F6)
                                            }
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = logItem.level,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = when (logItem.level) {
                                            "CRASH" -> Color(0xFFEF4444)
                                            "ERROR" -> Color(0xFFF87171)
                                            "WARN" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF3B82F6)
                                        }
                                    )
                                }
                                Text(
                                    text = "[${logItem.tag}] ${logItem.message}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTestCrashDialog) {
        AlertDialog(
            onDismissRequest = { showTestCrashDialog = false },
            title = { Text(text = "クラッシュ自動復帰テスト", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "意図的に未処理の致命的エラーを発生させます。\nWatchdogがこれを瞬時に捕捉し、crash_logs.txtにスタックトレースを記録した上で約0.6秒後に自動で再起動復帰します。\n\nテストを実行しますか？",
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTestCrashDialog = false
                        CrashLogManager.triggerTestCrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(text = "強制クラッシュ実行", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestCrashDialog = false }) {
                    Text(text = "キャンセル", color = Color(0xFF8E8E9A))
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }
}

@Composable
private fun DiagMetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x18000000))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF8E8E9A))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
    }
}

@Composable
private fun EewSettingsContent(
    preferences: ClockPreferencesState,
    viewModel: ClockViewModel,
    onLaunchLiveMap: () -> Unit
) {
    val liveState by viewModel.eewLiveState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Status & Overview Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF131A26))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (preferences.eewEnabled) Color(0xFF22C55E) else Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (preferences.eewEnabled) "P2P地震情報 WebSocket 監視中" else "EEW監視: 停止中",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x333B82F6))
                            .border(1.dp, Color(0x663B82F6), RoundedCornerShape(20.dp))
                            .clickable { onLaunchLiveMap() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🗺️ マップを開く",
                            color = Color(0xFF60A5FA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "気象庁の緊急地震速報（EEW）および各地の震度速報を受信すると、画面いっぱいにダークテーママップ、P波（初期微動・青）・S波（主要動・赤）の正確な伝播円、J-SHIS（地震ハザードステーション）表層地盤増幅率 (ARV) 連動による全国各地域・震度1までの予測震度、主要動到達カウントダウンを完全再現して自動表示します。",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // 2. Settings Card (Toggles & Threshold)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1A1A22))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "受信・通知設定",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: EEW Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "緊急地震速報 (EEW) 自動受信", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "P2P地震情報サーバーと常時接続し、速報を瞬時に検知します", color = Color(0xFF8E8E9A), fontSize = 11.sp)
                    }
                    Switch(
                        checked = preferences.eewEnabled,
                        onCheckedChange = { viewModel.updateEewPreferences(enabled = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFEF4444)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle: Sound Alarm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "警報音・チャイムを再生", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "緊急地震速報の発表時に注意喚起チャイムやカウントダウン音を鳴動します", color = Color(0xFF8E8E9A), fontSize = 11.sp)
                    }
                    Switch(
                        checked = preferences.eewSoundEnabled,
                        onCheckedChange = { viewModel.updateEewPreferences(soundEnabled = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFEF4444)
                        )
                    )
                }

                if (preferences.eewSoundEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "警報サウンドの種類:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val soundModes = listOf(
                            Triple("SYNTH_BEEP", "⚡ 電子音(推奨・低負荷)", "遅延0ms・CPU負荷ゼロの確実な電子音"),
                            Triple("VOICE", "🗣️ 音声アナウンス", "内蔵音声による発話案内"),
                            Triple("MUTE", "🔕 消音", "画面表示のみ通知")
                        )
                        soundModes.forEach { (mode, title, desc) ->
                            val isSelected = preferences.eewSoundMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x3322C55E) else Color(0x22FFFFFF))
                                    .border(1.5.dp, if (isSelected) Color(0xFF22C55E) else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.updateEewPreferences(soundMode = mode) }
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color(0xFF4ADE80) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        color = Color(0xFF8E8E9A),
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Map rendering mode selection
                Text(
                    text = "マップ描画方式:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isLight = preferences.eewLightweightMap
                    // Lightweight Vector Radar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLight) Color(0x3338BDF8) else Color(0x22FFFFFF))
                            .border(1.5.dp, if (isLight) Color(0xFF38BDF8) else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .clickable { viewModel.updateEewPreferences(lightweightMap = true) }
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "⚡ 超軽量レーダー (推奨)",
                                color = if (isLight) Color(0xFF38BDF8) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "通信ゼロ・DOMゼロ・60fps。タブレットでも一切カクつかず滑らかに動きます",
                                color = Color(0xFF8E8E9A),
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                    // Standard Web Map
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isLight) Color(0x3338BDF8) else Color(0x22FFFFFF))
                            .border(1.5.dp, if (!isLight) Color(0xFF38BDF8) else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .clickable { viewModel.updateEewPreferences(lightweightMap = false) }
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "🗺️ 詳細Web地図",
                                color = if (!isLight) Color(0xFF38BDF8) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "オンライン地図タイルを用いた標準表示（高性能端末向け）",
                                color = Color(0xFF8E8E9A),
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: Vibration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "バイブレーション警告", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "端末の振動機能で緊急通知します", color = Color(0xFF8E8E9A), fontSize = 11.sp)
                    }
                    Switch(
                        checked = preferences.eewVibrationEnabled,
                        onCheckedChange = { viewModel.updateEewPreferences(vibrationEnabled = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFEF4444)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Minimum scale filter
                Text(
                    text = "全画面マップ起動の最小震度閾値:",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EewScaleLevel.values()) { scale ->
                        val isSelected = preferences.eewMinScale == scale.scaleValue
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFEF4444) else Color(0x22FFFFFF))
                                .border(1.dp, if (isSelected) Color(0xFFFF8A80) else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.updateEewPreferences(minScale = scale.scaleValue)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${scale.label}${if (scale == EewScaleLevel.SCALE_5_LOWER) " (標準)" else ""}",
                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 3. Test Scenarios (テスト配信・シミュレーション)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1A1A22))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "緊急地震速報テスト配信（シミュレーション）",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "項目をタップすると設定画面を閉じて、本番と全く同一の全画面マップ・P/S波拡大アニメーション・震度カードが即座に起動します。",
                    color = Color(0xFF8E8E9A),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EewTestScenario.values().forEach { scenario ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF131A26))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .clickable {
                                    onLaunchLiveMap()
                                    viewModel.triggerTestEewScenario(scenario)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scenario.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = scenario.subtitle,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEF4444))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "震度 ${scenario.severity}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

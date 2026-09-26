package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ClockViewModel
import com.example.model.ClockFace
import com.example.model.IrRemoteButton
import com.example.model.WarningSeverity
import com.example.ui.clockfaces.DigitalStationClockView
import com.example.ui.clockfaces.MatrixDotsClockView
import com.example.ui.clockfaces.NeonCyberpunkClockView
import com.example.ui.clockfaces.NixieTubeClockView
import com.example.ui.clockfaces.RetroLcdGoldClockView
import com.example.ui.clockfaces.SevenSegmentClockView
import com.example.ui.clockfaces.SplitFlapClockView
import com.example.ui.clockfaces.StationAnalogClockView
import com.example.ui.clockfaces.StudioBoldClockView
import com.example.ui.clockfaces.SwissAnalogClockView
import com.example.ui.clockfaces.TypographicBauhausClockView
import com.example.ui.components.AlarmRingingOverlay
import com.example.ui.components.BackgroundVideoLayer
import com.example.ui.components.ControlDock
import com.example.ui.components.DeskTimerDialog
import com.example.ui.components.EewFullScreenOverlay
import com.example.ui.components.EspSensorBottomBar
import com.example.ui.components.FireAlertOverlay
import com.example.ui.components.IrQuickControlsSheet
import com.example.ui.components.MediaPlaybackHudBanner
import com.example.ui.components.MusicPlayerDialog
import com.example.ui.components.PhysicalButtonHudBanner
import com.example.ui.components.SettingsTab
import com.example.ui.components.TopControlBar
import com.example.ui.components.UnifiedSettingsDialog
import com.example.ui.components.WeatherBadge
import com.example.ui.components.WeatherDetailDialog
import com.example.ui.components.WeatherWarningBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeskClockMainScreen(
    viewModel: ClockViewModel,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences by viewModel.preferences.collectAsState()
    val timeState by viewModel.timeState.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()
    val isWeatherRefreshing by viewModel.isWeatherRefreshing.collectAsState()
    val scheduledChimes by viewModel.scheduledChimes.collectAsState()
    val customAudioList by viewModel.customAudioList.collectAsState()
    val customVideoList by viewModel.customVideoList.collectAsState()
    val activeBackgroundVideo by viewModel.activeBackgroundVideo.collectAsState()
    val playingAudioPath by viewModel.playingAudioPath.collectAsState()
    val musicPlayerState by viewModel.musicPlayerState.collectAsState()
    var showMusicPlayerDialog by remember { mutableStateOf(false) }

    // IP Camera states
    val ipCameraConfig by viewModel.ipCameraConfig.collectAsState()
    val ipCameraStatus by viewModel.ipCameraStatus.collectAsState()
    val ipCameraPreviewBitmap by viewModel.ipCameraPreviewBitmap.collectAsState()
    val ipCameraFps by viewModel.ipCameraFps.collectAsState()

    // ESP8266 / ESP32 Sensor & IR state
    val espSensorData by viewModel.espSensorData.collectAsState()
    val irButtons by viewModel.irButtons.collectAsState()
    var showIrQuickSheet by remember { mutableStateOf(false) }

    // Alarm Clock and Physical Button states
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val isFireAlertRinging by viewModel.isFireAlertRinging.collectAsState()
    val fireAlertDetails by viewModel.fireAlertDetails.collectAsState()
    val lastPhysicalButtonEvent by viewModel.lastPhysicalButtonEvent.collectAsState()

    // Weather Detail Dialog state
    var showWeatherDetailDialog by remember { mutableStateOf(false) }

    // Unified Settings Dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }
    var currentSettingsTab by remember { mutableStateOf(SettingsTab.FACE_PALETTE) }

    // Desk Timer Dialog state
    var showTimerDialog by remember { mutableStateOf(false) }

    // Kiosk lock notification banner
    var kioskWarningText by remember { mutableStateOf<String?>(null) }

    // Intercept back button when kiosk lock is enabled
    BackHandler(enabled = preferences.isKioskLocked) {
        kioskWarningText = "キオスク固定モード中 (鍵アイコン長押しで解除)"
        coroutineScope.launch {
            vibrate(context, 50)
            delay(3000)
            kioskWarningText = null
        }
    }

    // Main background canvas
    val bgColor = when {
        preferences.isNightMode -> Color(0xFF020203)
        preferences.clockFace == ClockFace.RETRO_LCD_GOLD -> Color(0xFFE5E272)
        else -> preferences.colorPalette.background
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("desk_clock_main_screen")
            .then(
                if (preferences.isNightMode) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.toggleNightMode()
                        vibrate(context, 40)
                        kioskWarningText = "☀️ 夜間モードを解除しました"
                        coroutineScope.launch {
                            delay(2500)
                            kioskWarningText = null
                        }
                    }
                } else Modifier
            )
    ) {
        // 0. Active Background Video Layer (Rendered when chime triggers or during video preview)
        BackgroundVideoLayer(
            activeVideo = activeBackgroundVideo,
            onDismiss = { viewModel.dismissBackgroundVideo() }
        )

        // 1. Burn-in prevention container (subtle 1-2 pixel drift)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp, top = 64.dp)
                .offset(x = timeState.burnInShiftX.dp, y = timeState.burnInShiftY.dp),
            contentAlignment = Alignment.Center
        ) {
            when (preferences.clockFace) {
                ClockFace.SEVEN_SEGMENT -> SevenSegmentClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.ANALOG_SWISS -> SwissAnalogClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.TYPOGRAPHIC -> TypographicBauhausClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.FLIP_CLOCK -> SplitFlapClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.MATRIX_DOTS -> MatrixDotsClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.NEON_CYBERPUNK -> NeonCyberpunkClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.NIXIE_TUBE -> NixieTubeClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.MINIMAL_BOLD -> StudioBoldClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.ANALOG_STATION -> StationAnalogClockView(
                    timeState = timeState,
                    preferences = preferences
                )
                ClockFace.DIGITAL_STATION_BLUE -> DigitalStationClockView(
                    timeState = timeState,
                    preferences = preferences,
                    isMatrixTime = false
                )
                ClockFace.DIGITAL_STATION_MATRIX -> DigitalStationClockView(
                    timeState = timeState,
                    preferences = preferences,
                    isMatrixTime = true
                )
                ClockFace.RETRO_LCD_GOLD -> RetroLcdGoldClockView(
                    timeState = timeState,
                    preferences = preferences
                )
            }
        }

        // 2. Top Information & Control Bar (or Floating Night Mode Exit Banner)
        if (preferences.isNightMode) {
            // Prominent Night Mode Exit Pill at Top Center (Ensures the user can instantly exit)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xE61E1E28))
                    .border(1.2.dp, Color(0xFFFFB300), RoundedCornerShape(30.dp))
                    .clickable {
                        viewModel.toggleNightMode()
                        vibrate(context, 50)
                        kioskWarningText = "☀️ 夜間モードを解除しました"
                        coroutineScope.launch {
                            delay(2500)
                            kioskWarningText = null
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "夜間常夜灯モード中",
                    color = Color(0xFFFFE082),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFB300))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "タップで解除",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Weather Badge + Compact Weather Warnings right below it
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (preferences.showWeather) {
                        WeatherBadge(
                            weather = weatherState,
                            accentColor = preferences.colorPalette.primary,
                            onClick = { showWeatherDetailDialog = true }
                        )
                    }

                    // Compact warning badges right below weather ("警報系は天気のしたにちょこっとでよい")
                    if (preferences.showWarnings && weatherState.warnings.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            weatherState.warnings.take(3).forEach { warning ->
                                val isSevere = warning.severity == WarningSeverity.WARNING || warning.severity == WarningSeverity.SPECIAL_WARNING
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSevere) Color(0x44EF4444) else Color(0x44F59E0B))
                                        .border(0.8.dp, if (isSevere) Color(0xFFEF4444) else Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                        .clickable { showWeatherDetailDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "⚠️ ${warning.title}",
                                            color = if (isSevere) Color(0xFFFF8A80) else Color(0xFFFFD54F),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right: Controls and Settings moved to Top ("その設定とかそれらは上に移動してほしい")
                TopControlBar(
                    preferences = preferences,
                    ipCameraStatus = ipCameraStatus,
                    timerSeconds = timerState.remainingSeconds,
                    isEspConnected = espSensorData.isConnected,
                    isMusicPlaying = musicPlayerState.isPlaying,
                    onOpenSettings = { tab ->
                        currentSettingsTab = tab
                        showSettingsDialog = true
                    },
                    onOpenTimer = { showTimerDialog = true },
                    onOpenMusicPlayer = { showMusicPlayerDialog = true },
                    onOpenIrRemote = { showIrQuickSheet = true },
                    onToggleNightMode = {
                        val willBeNight = !preferences.isNightMode
                        viewModel.toggleNightMode()
                        vibrate(context, 40)
                        kioskWarningText = if (willBeNight) "🌙 夜間常夜灯モード (画面タップでいつでも解除可能)" else "☀️ 通常モードに復帰しました"
                        coroutineScope.launch {
                            delay(3000)
                            kioskWarningText = null
                        }
                    },
                    onToggleKioskLock = {
                        if (preferences.isKioskLocked) {
                            kioskWarningText = "施錠中: 鍵アイコン長押し（3秒）または設定画面から解除できます"
                            vibrate(context, 40)
                            coroutineScope.launch {
                                delay(3000)
                                kioskWarningText = null
                            }
                        } else {
                            viewModel.toggleKioskLock()
                            vibrate(context, 40)
                            kioskWarningText = "キオスク固定モードを施錠しました (ホーム/戻る/離脱を防止)"
                            coroutineScope.launch {
                                delay(3000)
                                kioskWarningText = null
                            }
                        }
                    },
                    onUnlockLongPress = {
                        viewModel.toggleKioskLock()
                        vibrate(context, 80)
                        kioskWarningText = if (preferences.isKioskLocked) "キオスク施錠を解除しました" else "キオスク固定モードを施錠しました"
                        coroutineScope.launch {
                            delay(2500)
                            kioskWarningText = null
                        }
                    },
                    onUserInteraction = onUserInteraction
                )
            }
        }

        // 3. Optional Mini Camera Preview floating on Clock screen (above bottom bar)
        if (ipCameraConfig.showMiniPreviewOnClock && ipCameraStatus.isRunning && ipCameraPreviewBitmap != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 104.dp)
                    .size(width = 160.dp, height = 110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(14.dp))
                    .clickable {
                        currentSettingsTab = SettingsTab.IP_CAMERA
                        showSettingsDialog = true
                    }
            ) {
                Image(
                    bitmap = ipCameraPreviewBitmap!!.asImageBitmap(),
                    contentDescription = "Mini IP Camera Preview",
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "● LIVE ${String.format("%.0f", ipCameraFps)}fps",
                        color = Color(0xFF00E5FF),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 4. Bottom Section: Media Playback Banner + Large Sensor Display
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Media Playback HUD Banner (Displayed right above the sensor bar / humidity area)
            MediaPlaybackHudBanner(
                activeVideo = activeBackgroundVideo,
                playingAudioPath = playingAudioPath,
                customAudioList = customAudioList,
                accentColor = preferences.colorPalette.primary,
                onDismissVideo = { viewModel.dismissBackgroundVideo() },
                onDismissAudio = { viewModel.stopAudioPlayback() },
                onOpenMusicPlayer = { showMusicPlayerDialog = true }
            )

            if (preferences.showEspSensorOnClock && !preferences.isNightMode) {
                EspSensorBottomBar(
                    sensorData = espSensorData,
                    host = preferences.espSensorHost,
                    accentColor = preferences.colorPalette.primary,
                    onOpenSettings = {
                        currentSettingsTab = SettingsTab.ESP_SENSOR
                        showSettingsDialog = true
                    },
                    onRetryConnection = {
                        viewModel.retryEspSensorConnection()
                    }
                )
            }
        }

        // 5. Kiosk warning toast / banner
        AnimatedVisibility(
            visible = kioskWarningText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp)
        ) {
            kioskWarningText?.let { text ->
                Box(
                    modifier = Modifier
                        .background(Color(0xE6202028), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = text,
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 6. Detailed Weather Dialog
        if (showWeatherDetailDialog) {
            WeatherDetailDialog(
                weather = weatherState,
                isRefreshing = isWeatherRefreshing,
                isAutoLocation = preferences.isAutoLocationEnabled,
                accentColor = preferences.colorPalette.primary,
                onRefresh = { viewModel.refreshWeather() },
                onChangeLocation = {
                    showWeatherDetailDialog = false
                    currentSettingsTab = SettingsTab.WEATHER
                    showSettingsDialog = true
                },
                onDismiss = {
                    showWeatherDetailDialog = false
                    onUserInteraction()
                }
            )
        }

        // 7. Unified Settings Dialog (Consolidates Face, Palette, IP Camera, Chimes, Weather, Protection)
        if (showSettingsDialog) {
            UnifiedSettingsDialog(
                viewModel = viewModel,
                preferences = preferences,
                weatherState = weatherState,
                scheduledChimes = scheduledChimes,
                customAudioList = customAudioList,
                customVideoList = customVideoList,
                initialTab = currentSettingsTab,
                onOpenWeatherDetail = {
                    showSettingsDialog = false
                    showWeatherDetailDialog = true
                },
                onDismiss = {
                    showSettingsDialog = false
                    onUserInteraction()
                }
            )
        }

        // 7. Desk Timer Dialog
        if (showTimerDialog) {
            DeskTimerDialog(
                timerState = timerState,
                preferences = preferences,
                onDismiss = {
                    showTimerDialog = false
                    onUserInteraction()
                },
                onAddMinutes = { viewModel.addTimerMinutes(it) },
                onTogglePause = { viewModel.toggleTimerPause() },
                onReset = { viewModel.resetTimer() }
            )
        }

        // 8. Smart IR Remote Quick Controls Sheet
        if (showIrQuickSheet) {
            IrQuickControlsSheet(
                buttons = irButtons,
                isConnected = espSensorData.isConnected,
                onSend = { viewModel.sendIrButton(it) },
                onOpenSettings = {
                    currentSettingsTab = SettingsTab.IR_REMOTE
                    showSettingsDialog = true
                },
                onDismiss = {
                    showIrQuickSheet = false
                    onUserInteraction()
                }
            )
        }

        // 8.5 Dedicated Music Player Dialog
        if (showMusicPlayerDialog) {
            MusicPlayerDialog(
                viewModel = viewModel,
                preferences = preferences,
                onDismiss = {
                    showMusicPlayerDialog = false
                    onUserInteraction()
                }
            )
        }

        // 9. Emergency Earthquake Warning (EEW) Full-screen Live Map Overlay
        EewFullScreenOverlay(
            viewModel = viewModel
        )

        // 10. PCF8574P Physical Button HUD Notification Banner
        PhysicalButtonHudBanner(
            event = lastPhysicalButtonEvent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // 11. Alarm Ringing Full-Screen Overlay
        AlarmRingingOverlay(
            isRinging = isAlarmRinging,
            currentTimeText = "%02d:%02d".format(timeState.hour24, timeState.minute),
            preferences = preferences,
            onStopAlarm = { viewModel.stopAlarm() },
            onSnoozeAlarm = { viewModel.snoozeAlarm() }
        )

        // 12. MQ-2 Fire & Smoke Emergency Detection Full-Screen Overlay (「火事です！」)
        FireAlertOverlay(
            isFireAlert = isFireAlertRinging,
            alertInfo = fireAlertDetails,
            sensorData = espSensorData,
            onDismissAlert = { viewModel.dismissFireAlert() }
        )
    }
}

private fun vibrate(context: Context, durationMs: Long) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {}
}

package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ClockViewModel
import com.example.model.ClockFace
import com.example.ui.clockfaces.MatrixDotsClockView
import com.example.ui.clockfaces.SevenSegmentClockView
import com.example.ui.clockfaces.SplitFlapClockView
import com.example.ui.clockfaces.SwissAnalogClockView
import com.example.ui.clockfaces.TypographicBauhausClockView
import com.example.ui.components.BackgroundVideoLayer
import com.example.ui.components.ChimeSettingsDialog
import com.example.ui.components.ControlDock
import com.example.ui.components.DeskTimerDialog
import com.example.ui.components.FaceAndPaletteDialog
import com.example.ui.components.WeatherAndWarningDialog
import com.example.ui.components.WeatherBadge
import com.example.ui.components.WeatherWarningBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeskClockMainScreen(
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences by viewModel.preferences.collectAsState()
    val timeState by viewModel.timeState.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isDetectingLocation by viewModel.isDetectingLocation.collectAsState()
    val scheduledChimes by viewModel.scheduledChimes.collectAsState()
    val customAudioList by viewModel.customAudioList.collectAsState()
    val customVideoList by viewModel.customVideoList.collectAsState()
    val activeBackgroundVideo by viewModel.activeBackgroundVideo.collectAsState()

    // Dialog visibility states
    var showChimeSettings by remember { mutableStateOf(false) }
    var showFacePicker by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showWeatherDialog by remember { mutableStateOf(false) }

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
    val bgColor = if (preferences.isNightMode) Color(0xFF020203) else preferences.colorPalette.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("desk_clock_main_screen")
    ) {
        // 0. Active Background Video Layer (Rendered when chime triggers or during video preview)
        BackgroundVideoLayer(
            activeVideo = activeBackgroundVideo,
            onDismiss = { viewModel.dismissBackgroundVideo() }
        )

        // Burn-in prevention container (subtle 1-2 pixel drift)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 68.dp, top = 36.dp)
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
            }
        }

        // Top Information Bar: Weather Badge, Warning Badges, Active Timer
        if (!preferences.isNightMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Weather Badge
                if (preferences.showWeather) {
                    WeatherBadge(
                        weather = weatherState,
                        accentColor = preferences.colorPalette.primary,
                        onClick = { showWeatherDialog = true }
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Center: Weather Warning Pills (matching user's reference image)
                if (preferences.showWarnings && weatherState.warnings.isNotEmpty()) {
                    WeatherWarningBanner(
                        warnings = weatherState.warnings,
                        regionName = preferences.selectedCityName,
                        onClick = { showWeatherDialog = true },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Right: Active timer indicator tag if running
                if (timerState.remainingSeconds > 0) {
                    val minutes = timerState.remainingSeconds / 60
                    val seconds = timerState.remainingSeconds % 60
                    Box(
                        modifier = Modifier
                            .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .clickable { showTimerDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "TIMER %02d:%02d", minutes, seconds),
                            color = preferences.colorPalette.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }

        // Bottom Permanently Visible Control Dock
        ControlDock(
            preferences = preferences,
            onOpenFacePicker = { showFacePicker = true },
            onOpenPalettePicker = { showFacePicker = true },
            onOpenChimeSettings = { showChimeSettings = true },
            onOpenTimer = { showTimerDialog = true },
            onToggleWeather = { showWeatherDialog = true },
            onToggleNightMode = { viewModel.toggleNightMode() },
            onToggleKioskLock = {
                viewModel.toggleKioskLock()
                vibrate(context, 40)
            },
            onUnlockLongPress = {
                viewModel.toggleKioskLock()
                vibrate(context, 80)
                kioskWarningText = if (preferences.isKioskLocked) "キオスクロックを解除しました" else "キオスク固定モードを有効にしました"
                coroutineScope.launch {
                    delay(2500)
                    kioskWarningText = null
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Kiosk warning toast / banner
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

        // Dialogs
        if (showChimeSettings) {
            ChimeSettingsDialog(
                preferences = preferences,
                scheduledChimes = scheduledChimes,
                customAudioList = customAudioList,
                customVideoList = customVideoList,
                onDismiss = { showChimeSettings = false },
                onSaveChime = { viewModel.saveScheduledChime(it) },
                onDeleteChime = { viewModel.deleteScheduledChime(it) },
                onToggleChime = { viewModel.toggleScheduledChime(it) },
                onImportCustomAudio = { uri, onResult -> viewModel.importCustomAudio(uri, onResult) },
                onDeleteCustomAudio = { viewModel.deleteCustomAudio(it) },
                onImportCustomVideo = { uri, onResult -> viewModel.importCustomVideo(uri, onResult) },
                onDeleteCustomVideo = { viewModel.deleteCustomVideo(it) },
                onTestPlayChime = { viewModel.testPlayScheduledChime(it) },
                onTestPlayAudioFile = { viewModel.testPlayCustomAudio(it) },
                onPreviewVideo = { videoType, path, name, playAudio, duration ->
                    viewModel.previewBackgroundVideo(videoType, path, name, playAudio, duration)
                },
                onTestSound = { viewModel.testChimeSound(it) },
                onStopAudio = { viewModel.stopAudioPlayback() },
                onToggleHourlyChime = { viewModel.setHourlyChime(it) },
                onToggleHalfHourlyChime = { viewModel.setHalfHourlyChime(it) },
                onSelectSound = { viewModel.selectChimeSound(it) },
                onVolumeChange = { viewModel.setChimeVolume(it) },
                onHoursChange = { start, end -> viewModel.setChimeHours(start, end) }
            )
        }

        if (showFacePicker) {
            FaceAndPaletteDialog(
                preferences = preferences,
                onDismiss = { showFacePicker = false },
                onSelectFace = { viewModel.selectClockFace(it) },
                onSelectPalette = { viewModel.selectColorPalette(it) },
                onToggle24Hour = { viewModel.toggle24Hour() },
                onToggleSeconds = { viewModel.toggleShowSeconds() },
                onToggleWeather = { viewModel.toggleShowWeather() }
            )
        }

        if (showTimerDialog) {
            DeskTimerDialog(
                timerState = timerState,
                preferences = preferences,
                onDismiss = { showTimerDialog = false },
                onAddMinutes = { viewModel.addTimerMinutes(it) },
                onTogglePause = { viewModel.toggleTimerPause() },
                onReset = { viewModel.resetTimer() }
            )
        }

        if (showWeatherDialog) {
            WeatherAndWarningDialog(
                preferences = preferences,
                weather = weatherState,
                searchResults = searchResults,
                isSearching = isSearching,
                isDetectingLocation = isDetectingLocation,
                onDismiss = {
                    showWeatherDialog = false
                    viewModel.clearSearchResults()
                },
                onSearch = { viewModel.searchMunicipalities(it) },
                onClearSearch = { viewModel.clearSearchResults() },
                onSelectMunicipality = {
                    viewModel.selectMunicipality(it)
                    showWeatherDialog = false
                },
                onSelectPrefecture = { viewModel.selectPrefecture(it) },
                onDetectLocation = { onResult ->
                    viewModel.detectAndSetCurrentLocation(onResult)
                },
                onToggleWeather = { viewModel.toggleShowWeather() },
                onToggleWarnings = { viewModel.toggleShowWarnings() },
                onToggleDemoWarnings = { viewModel.toggleDemoWarnings() },
                onRefresh = { viewModel.refreshWeather() }
            )
        }
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

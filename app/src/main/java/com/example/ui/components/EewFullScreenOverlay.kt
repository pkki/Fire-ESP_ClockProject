package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ClockViewModel
import com.example.model.EewTestScenario

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EewFullScreenOverlay(
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier
) {
    val isVisible by viewModel.isEewOverlayVisible.collectAsState()
    val liveState by viewModel.eewLiveState.collectAsState()
    val pendingScenario by viewModel.eewPendingScenario.collectAsState()
    val pendingJson by viewModel.eewPendingJson.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebLoaded by remember { mutableStateOf(false) }

    BackHandler(enabled = isVisible) {
        viewModel.dismissEewOverlay()
    }

    // When scenario changes, evaluate JS
    LaunchedEffect(pendingScenario, isWebLoaded) {
        if (isWebLoaded && pendingScenario != null) {
            webViewRef?.evaluateJavascript("if(window.runTestScenario){ window.runTestScenario('$pendingScenario'); }", null)
        }
    }

    // When JSON payload arrives, evaluate JS
    LaunchedEffect(pendingJson, isWebLoaded) {
        if (isWebLoaded && pendingJson != null) {
            val safeJson = pendingJson!!.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "")
            webViewRef?.evaluateJavascript("if(window.processRawP2PMessage){ window.processRawP2PMessage(JSON.parse('$safeJson')); }", null)
        }
    }

    // Update min scale
    LaunchedEffect(preferences.eewMinScale, isWebLoaded) {
        if (isWebLoaded) {
            webViewRef?.evaluateJavascript("if(window.setMinScale){ window.setMinScale(${preferences.eewMinScale}); }", null)
        }
    }

    // Update user location for pinpoint intensity and J-SHIS calculation
    LaunchedEffect(preferences.selectedCityName, preferences.selectedPrefecture, preferences.customLatitude, preferences.customLongitude, isWebLoaded) {
        if (isWebLoaded) {
            val name = preferences.selectedCityName
            val pref = preferences.selectedPrefecture
            val lat = preferences.customLatitude ?: 35.6895
            val lon = preferences.customLongitude ?: 139.6917
            webViewRef?.evaluateJavascript("if(window.setUserLocation){ window.setUserLocation('$name', '$pref', $lat, $lon); }", null)
        }
    }

    // Toggle lightweight map in WebView
    LaunchedEffect(preferences.eewLightweightMap, isWebLoaded) {
        if (isWebLoaded) {
            webViewRef?.evaluateJavascript("if(window.setLightweightMode){ window.setLightweightMode(${preferences.eewLightweightMap}); }", null)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E17))
                .testTag("eew_fullscreen_overlay")
        ) {
            // Leaflet Map Webview
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(0xFF0A0E17.toInt())
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = false
                            displayZoomControls = false
                        }

                        addJavascriptInterface(
                            EewJsBridge(
                                onStatusChanged = { active, summary ->
                                    viewModel.onEewJsStatusChanged(active, summary)
                                },
                                onAlarm = { soundType ->
                                    viewModel.onEewJsAlarm(soundType)
                                },
                                onVoiceAnnounce = { text, flush ->
                                    viewModel.onEewJsVoiceAnnounce(text, flush)
                                },
                                onClose = {
                                    viewModel.dismissEewOverlay()
                                }
                            ),
                            "AndroidEEW"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isWebLoaded = true
                                val name = preferences.selectedCityName
                                val pref = preferences.selectedPrefecture
                                val lat = preferences.customLatitude ?: 35.6895
                                val lon = preferences.customLongitude ?: 139.6917
                                view?.evaluateJavascript("if(window.setUserLocation){ window.setUserLocation('$name', '$pref', $lat, $lon); }", null)
                                view?.evaluateJavascript("if(window.setMinScale){ window.setMinScale(${preferences.eewMinScale}); }", null)
                                view?.evaluateJavascript("if(window.setLightweightMode){ window.setLightweightMode(${preferences.eewLightweightMap}); }", null)
                                pendingScenario?.let { sc ->
                                    view?.evaluateJavascript("if(window.runTestScenario){ window.runTestScenario('$sc'); }", null)
                                }
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl("file:///android_asset/eew/index.html")
                        webViewRef = this
                    }
                },
                update = {
                    webViewRef = it
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top action bar overlay: Controls & Close button
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Map Mode Toggle (⚡ 超軽量レーダー / 🗺️ 詳細Web地図)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (preferences.eewLightweightMap) Color(0xE60F172A) else Color(0xE61E293B))
                        .border(1.5.dp, if (preferences.eewLightweightMap) Color(0xFF38BDF8) else Color(0xFF64748B), RoundedCornerShape(20.dp))
                        .clickable { viewModel.toggleEewLightweightMap() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("eew_map_mode_toggle")
                ) {
                    Text(
                        text = if (preferences.eewLightweightMap) "⚡ 超軽量レーダー" else "🗺️ 詳細Web地図",
                        color = if (preferences.eewLightweightMap) Color(0xFF38BDF8) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sound Mode Toggle (⚡電子音 / 🗣️音声 / 🔕消音)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xE61E293B))
                        .border(1.5.dp, when (preferences.eewSoundMode) {
                            "SYNTH_BEEP" -> Color(0xFF22C55E)
                            "VOICE" -> Color(0xFF3B82F6)
                            else -> Color(0xFFEF4444)
                        }, RoundedCornerShape(20.dp))
                        .clickable { viewModel.cycleEewSoundMode() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("eew_sound_mode_toggle")
                ) {
                    Text(
                        text = when (preferences.eewSoundMode) {
                            "SYNTH_BEEP" -> "⚡ 警報音: 電子音(低負荷)"
                            "VOICE" -> "🗣️ 警報音: 音声案内"
                            else -> "🔕 警報音: 消音"
                        },
                        color = when (preferences.eewSoundMode) {
                            "SYNTH_BEEP" -> Color(0xFF4ADE80)
                            "VOICE" -> Color(0xFF93C5FD)
                            else -> Color(0xFFFCA5A5)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (liveState.isTestMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xE6FFB300))
                            .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "訓練中",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Close Button (Return to Clock)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xE61E293B))
                        .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                        .clickable { viewModel.dismissEewOverlay() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("eew_close_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "時計に戻る",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

class EewJsBridge(
    private val onStatusChanged: (Boolean, String) -> Unit,
    private val onAlarm: (String) -> Unit,
    private val onVoiceAnnounce: (String, Boolean) -> Unit,
    private val onClose: () -> Unit
) {
    @JavascriptInterface
    fun onEewStatusChanged(isActive: Boolean, summary: String) {
        onStatusChanged(isActive, summary)
    }

    @JavascriptInterface
    fun onPlayAlarmSound(soundType: String) {
        onAlarm(soundType)
    }

    @JavascriptInterface
    fun onVoiceAnnounce(text: String, flush: Boolean) {
        onVoiceAnnounce(text, flush)
    }

    @JavascriptInterface
    fun onCloseRequested() {
        onClose()
    }
}

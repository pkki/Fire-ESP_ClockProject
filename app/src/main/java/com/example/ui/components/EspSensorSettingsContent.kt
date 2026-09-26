package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ClockViewModel
import com.example.model.ClockPreferencesState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun EspSensorSettingsContent(
    viewModel: ClockViewModel,
    preferences: ClockPreferencesState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sensorData by viewModel.espSensorData.collectAsState()

    var isFlashingOta by remember { mutableStateOf(false) }
    var otaStatusMessage by remember { mutableStateOf<String?>(null) }
    var otaPercent by remember { mutableStateOf(0) }
    var otaSpeedKbps by remember { mutableStateOf(0f) }
    var otaWrittenBytes by remember { mutableStateOf(0) }
    var otaTotalBytes by remember { mutableStateOf(0) }

    val espBinPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isFlashingOta = true
            otaPercent = 0
            otaSpeedKbps = 0f
            otaWrittenBytes = 0
            otaTotalBytes = 0
            otaStatusMessage = "ESP32ファームウェア(.bin)をOTA書き込み準備中..."
            coroutineScope.launch {
                val (success, msg) = viewModel.flashEspFirmwareFromUri(
                    uri = uri,
                    host = preferences.espSensorHost.ifBlank { null },
                    port = preferences.espSensorPort,
                    onProgress = { percent, written, total, speed, status ->
                        otaPercent = percent
                        otaWrittenBytes = written
                        otaTotalBytes = total
                        otaSpeedKbps = speed
                        otaStatusMessage = status
                    }
                )
                isFlashingOta = false
                otaStatusMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val currentMode = preferences.espConnectionMode // "BLE", "USB", "WIFI"
    val isBleMode = currentMode == "BLE"
    val isUsbMode = currentMode == "USB"
    val isWifiMode = currentMode == "WIFI"

    var bleNameText by remember(preferences.espBleDeviceName) { mutableStateOf(preferences.espBleDeviceName) }
    var hostText by remember(preferences.espSensorHost) { mutableStateOf(preferences.espSensorHost) }
    var portText by remember(preferences.espSensorPort) { mutableStateOf(preferences.espSensorPort.toString()) }
    var tempOffText by remember(preferences.espTempOffset) { mutableStateOf(preferences.espTempOffset.toString()) }
    var humOffText by remember(preferences.espHumOffset) { mutableStateOf(preferences.espHumOffset.toString()) }
    var pressOffText by remember(preferences.espPressOffset) { mutableStateOf(preferences.espPressOffset.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("esp_sensor_settings_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Master Enable Toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1E2B))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "環境センサー連携を有効化",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "AHT20 + BMP280 からリアルタイムで室内温湿度・気圧を取得",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = preferences.espSensorEnabled,
                    onCheckedChange = { viewModel.updateEspSensorPreferences(enabled = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E5FF)
                    )
                )
            }
        }

        // 2. Real-time Status Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161B28))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        sensorData.isConnected -> Color(0xFF22C55E)
                                        sensorData.isConnecting -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    }
                                )
                        )
                        Text(
                            text = if (sensorData.isConnected) "センサー接続中 (${sensorData.connectionType})" else if (sensorData.isConnecting) "自動接続・検索中..." else "センサー未接続",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.retryEspSensorConnection()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "再接続",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp
                        )
                    }
                }

                val errMsg = sensorData.errorMessage
                if (errMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isWarningOrInfo = errMsg.contains("中") || errMsg.contains("待機") || errMsg.contains("検出")
                    Text(
                        text = "ステータス: $errMsg",
                        color = if (isWarningOrInfo) Color(0xFF38BDF8) else Color(0xFFEF4444),
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mini Readings Dashboard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusBox(
                        label = "室内温度",
                        value = sensorData.temperature?.let { String.format(Locale.US, "%.1f °C", it) } ?: "--",
                        color = Color(0xFFFF5252),
                        modifier = Modifier.weight(1f)
                    )
                    StatusBox(
                        label = "室内湿度",
                        value = sensorData.humidity?.let { String.format(Locale.US, "%.1f %%", it) } ?: "--",
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f)
                    )
                    StatusBox(
                        label = "大気圧",
                        value = sensorData.pressure?.let { String.format(Locale.US, "%.1f hPa", it) } ?: "--",
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Connection Method Selector (BLE vs USB Direct vs Wi-Fi)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF171A26))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cable,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "通信方式の選択",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // BLE Option (ESP32-C3 Auto-connect Recommended)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBleMode) Color(0x3300E5FF) else Color(0x18FFFFFF))
                            .border(
                                width = if (isBleMode) 1.5.dp else 1.dp,
                                color = if (isBleMode) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                viewModel.updateEspSensorPreferences(mode = "BLE")
                            }
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (isBleMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Bluetooth",
                                    color = if (isBleMode) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "ESP32-C3専用。起動するだけでアプリが自動検出＆自動接続します",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // USB Option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isUsbMode) Color(0x3300E5FF) else Color(0x18FFFFFF))
                            .border(
                                width = if (isUsbMode) 1.5.dp else 1.dp,
                                color = if (isUsbMode) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                viewModel.updateEspSensorPreferences(mode = "USB")
                            }
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = if (isUsbMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "USB直結",
                                    color = if (isUsbMode) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "OTGケーブルでタブレットに挿すだけ。給電と通信を1本で実現",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // Wi-Fi Option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isWifiMode) Color(0x3300E5FF) else Color(0x18FFFFFF))
                            .border(
                                width = if (isWifiMode) 1.5.dp else 1.dp,
                                color = if (isWifiMode) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                viewModel.updateEspSensorPreferences(mode = "WIFI")
                            }
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (isWifiMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Wi-Fi",
                                    color = if (isWifiMode) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "同一LAN上のESPからHTTP通信で取得します",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                if (isBleMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = bleNameText,
                        onValueChange = {
                            bleNameText = it
                            viewModel.updateEspSensorPreferences(bleDeviceName = it)
                        },
                        label = { Text("自動検出するBLEデバイス名", fontSize = 11.sp) },
                        placeholder = { Text("ESP32C3-Sensor", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x44FFFFFF)
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "※ ESP32-C3の電源を入れると、アプリが自動でスキャンしペアリング不要で直接接続します。",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp
                    )
                } else if (isUsbMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "シリアル通信ボーレート (Baud Rate):",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val baudRates = listOf(115200, 9600, 57600)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        baudRates.forEach { baud ->
                            val isSelected = preferences.espBaudRate == baud
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                    .clickable {
                                        viewModel.updateEspSensorPreferences(baud = baud)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (baud == 115200) "$baud (標準)" else "$baud",
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                } else {
                    // Wi-Fi details (Host, Port, Interval)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = hostText,
                            onValueChange = {
                                hostText = it
                                viewModel.updateEspSensorPreferences(host = it)
                            },
                            label = { Text("ESP IPアドレス", fontSize = 11.sp) },
                            placeholder = { Text("例: 192.168.1.100", fontSize = 11.sp) },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = portText,
                            onValueChange = {
                                portText = it
                                it.toIntOrNull()?.let { p ->
                                    viewModel.updateEspSensorPreferences(port = p)
                                }
                            },
                            label = { Text("ポート", fontSize = 11.sp) },
                            placeholder = { Text("80", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "更新間隔:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val intervals = listOf(1, 2, 5, 10, 30)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        intervals.forEach { sec ->
                            val isSelected = preferences.espSensorIntervalSeconds == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                    .clickable {
                                        viewModel.updateEspSensorPreferences(intervalSeconds = sec)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sec}秒",
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Multi-point NTC Thermistors (PCF8574P 0x21 + GPIO 1)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141D2B))
                    .border(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceThermostat,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "PCF8574P (0x21) マルチNTC温度計 (最大8点)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // PCF8574P 0x21 ハードウェア検出バッジ
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (sensorData.pcfNtcReady) Color(0x3300E676) else Color(0x33FF5252)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (sensorData.pcfNtcReady) "0x21 検出済み" else "0x21 未検出",
                            color = if (sensorData.pcfNtcReady) Color(0xFF00E676) else Color(0xFFFF8A80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ESP32 GPIO 1 (ADC) と 2個目のPCF8574P (0x21) を用いて、固定抵抗1本で複数箇所の温度をスキャン測定します。",
                    color = Color(0xFF90CAF9),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 更新頻度切替＆即時更新ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "更新間隔:",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val ntcIntervals = listOf(1, 2, 5)
                        ntcIntervals.forEach { sec ->
                            val isSel = preferences.espSensorIntervalSeconds == sec
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Color(0xFF29B6F6) else Color(0x22FFFFFF))
                                    .clickable {
                                        viewModel.updateEspSensorPreferences(intervalSeconds = sec)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${sec}秒" + if (sec == 1) "(高速)" else "",
                                    color = if (isSel) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // 今すぐ更新 (即時リフレッシュ)
                    Button(
                        onClick = { viewModel.refreshEspSensor() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "今すぐ取得",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "今すぐ測定",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi-temperature grid (P0 to P7)
                val defaultLabels = listOf(
                    "CH0 (P0) 時計内部基板",
                    "CH1 (P1) ケース裏面",
                    "CH2 (P2) 外部プローブ 1",
                    "CH3 (P3) 外部プローブ 2",
                    "CH4 (P4) 予備プローブ 3",
                    "CH5 (P5) 予備プローブ 4",
                    "CH6 (P6) 予備プローブ 5",
                    "CH7 (P7) 予備プローブ 6"
                )

                val temps = sensorData.ntcTemperatures
                val raws = sensorData.ntcRawValues

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 2) {
                                val idx = row * 2 + col
                                val t = temps.getOrNull(idx)
                                val rawVal = raws.getOrNull(idx)
                                val label = defaultLabels.getOrElse(idx) { "CH$idx" }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (t != null) Color(0x221E88E5) else Color(0x11FFFFFF)
                                        )
                                        .border(
                                            1.dp,
                                            if (t != null) Color(0x4464B5F6) else Color(0x22FFFFFF),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            color = Color(0xFFB0BEC5),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (t != null) "%.1f ℃".format(Locale.US, t) else "-- ℃ (未接続)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (t != null) Color(0xFF64B5F6) else Color(0xFF78909C)
                                        )
                                        if (rawVal != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (rawVal >= 3980) "ADC: $rawVal (開放)" else if (rawVal <= 50) "ADC: $rawVal (短絡)" else "ADC: $rawVal",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (rawVal in 100..3970) Color(0xFF81C784) else Color(0xFF90A4AE)
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

        // 5. MQ-2 Gas, Smoke & Fire Alert Integration (リアルタイムモニター＆感度スライダー)
        item {
            var fireSensitivity by remember(preferences.mq2SensitivityThreshold) {
                mutableStateOf(preferences.mq2SensitivityThreshold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF221115))
                    .border(
                        1.dp,
                        if (sensorData.mq2SmokeDetected) Color(0xFFFF1744) else Color(0x44FF5252),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🔥", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "MQ-2 火災・煙検知 (GPIO 0 ADC)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "煙・ガス急増時にタブレットへ「火事です！」警報を発報",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Switch(
                        checked = preferences.fireAlertEnabled,
                        onCheckedChange = { viewModel.updateFireAlertPreferences(enabled = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF5252)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time raw value card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sensorData.mq2SmokeDetected) Color(0xFFB71C1C) else Color(0x22FFFFFF))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "センサー現在測定値: ${sensorData.mq2RawValue ?: "待機中"}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sensorData.mq2StatusLabel,
                            color = if (sensorData.mq2SmokeDetected) Color.Yellow else Color(0xFFFFAB91),
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.testFireAlert() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("テスト発報", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "検知閾値 (感度調整):",
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$fireSensitivity (ESP32即時反映)",
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                androidx.compose.material3.Slider(
                    value = fireSensitivity.toFloat(),
                    onValueChange = {
                        fireSensitivity = it.toInt()
                        viewModel.updateFireAlertPreferences(sensitivityThreshold = fireSensitivity)
                    },
                    valueRange = 300f..2500f,
                    steps = 22,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5252),
                        activeTrackColor = Color(0xFFFF5252),
                        inactiveTrackColor = Color(0xFF455A64)
                    )
                )

                Text(
                    text = "※ スライダーを動かすとESP32へ即時送信され、タブレット側のリアルタイム監視にも直ちに適用されます。",
                    color = Color(0xFF90A4AE),
                    fontSize = 10.5.sp
                )
            }
        }

        // 6. Calibration Offsets
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1E2B))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "センサー測定値のオフセット校正",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "個体差やケース内発熱に合わせて微調整できます",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = tempOffText,
                        onValueChange = {
                            tempOffText = it
                            it.toFloatOrNull()?.let { v -> viewModel.updateEspSensorPreferences(tempOffset = v) }
                        },
                        label = { Text("温度補正 (°C)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = humOffText,
                        onValueChange = {
                            humOffText = it
                            it.toFloatOrNull()?.let { v -> viewModel.updateEspSensorPreferences(humOffset = v) }
                        },
                        label = { Text("湿度補正 (%)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pressOffText,
                        onValueChange = {
                            pressOffText = it
                            it.toFloatOrNull()?.let { v -> viewModel.updateEspSensorPreferences(pressOffset = v) }
                        },
                        label = { Text("気圧補正 (hPa)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // 7. ESP32-C3 Firmware Wireless Update (Bluetooth BLE OTA / Wi-Fi OTA)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1E2B))
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "ESP32 ファームウェア無線更新 (BLE OTA)",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sensorData.isConnected) Color(0x2810B981) else Color(0x28EF4444))
                            .border(1.dp, if (sensorData.isConnected) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (sensorData.isConnected) "Bluetooth接続中 (書き込み可能)" else "未接続",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sensorData.isConnected) Color(0xFF4ADE80) else Color(0xFFFF8A80)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Arduino IDE（スケッチ → コンパイル済みバイナリをエクスポート）やPlatformIOで出力された .bin ファイルを選択するだけで、Bluetooth (BLE) 経由でESP32へ直接ファームウェアをフラッシュ書き込みします。",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { espBinPicker.launch("*/*") },
                        enabled = !isFlashingOta,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFlashingOta) "書き込み中..." else "ファームウェア(.bin)を選択して更新",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            val sketch = viewModel.getArduinoSketchCode()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("ESP32-C3 Sketch", sketch))
                            Toast.makeText(context, "最新スケッチコードをクリップボードにコピーしました", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("スケッチコピー", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                }

                if (isFlashingOta || otaStatusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33000000))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isFlashingOta) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = otaStatusMessage ?: "処理中...",
                                    fontSize = 11.sp,
                                    color = Color(0xFFF1F5F9)
                                )
                            }
                            if (isFlashingOta && otaTotalBytes > 0) {
                                Text(
                                    text = "$otaPercent% (${String.format(Locale.US, "%.1f", otaSpeedKbps)} KB/s)",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        if (isFlashingOta && otaTotalBytes > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { otaPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0x3338BDF8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x18FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

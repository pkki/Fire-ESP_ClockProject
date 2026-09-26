package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.EspSensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class EspSensorManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pollingJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _sensorData = MutableStateFlow(EspSensorData())
    val sensorData: StateFlow<EspSensorData> = _sensorData.asStateFlow()

    private var connectionMode: String = "BLE" // "BLE", "USB" or "WIFI"
    private var bleDeviceName: String = "ESP32C3-Sensor"
    private var baudRate: Int = 115200
    private var host: String = "192.168.1.100"
    private var port: Int = 80
    private var intervalSeconds: Int = 5
    private var isEnabled: Boolean = true
    private var tempOffset: Float = 0.0f
    private var humOffset: Float = 0.0f
    private var pressOffset: Float = 0.0f

    var onPhysicalButtonEvent: ((btnId: Int, btnName: String) -> Unit)? = null
    var onFireAlertEvent: ((detected: Boolean, mq2Raw: Int?, message: String?) -> Unit)? = null
    var onMq2TelemetryEvent: ((rawVal: Int?, voltage: Float?, smoke: Boolean) -> Unit)? = null

    val bleSensorManager: BleSensorManager = BleSensorManager(
        context = context,
        onDataReceived = { rawTemp, rawHum, rawPress, rssi ->
            val now = System.currentTimeMillis()
            val finalTemp = rawTemp?.let { it + tempOffset }
            val finalHum = rawHum?.let { (it + humOffset).coerceIn(0f, 100f) }
            val finalPress = rawPress?.let { it + pressOffset }

            _sensorData.value = _sensorData.value.copy(
                temperature = finalTemp,
                humidity = finalHum,
                pressure = finalPress,
                rssi = rssi,
                ahtOk = rawTemp != null || rawHum != null,
                bmpOk = rawPress != null,
                lastUpdatedEpochMs = now,
                isConnected = true,
                isConnecting = false,
                connectionType = "BLE",
                errorMessage = null
            )
        },
        onStatusChanged = { connected, connecting, message ->
            _sensorData.value = _sensorData.value.copy(
                isConnected = connected,
                isConnecting = connecting,
                connectionType = "BLE",
                errorMessage = message
            )
        }
    ).apply {
        onButtonEvent = { id, name ->
            onPhysicalButtonEvent?.invoke(id, name)
        }
        onPcfStatusChanged = { ready ->
            _sensorData.value = _sensorData.value.copy(pcfReady = ready)
        }
        onFireAlertReceived = { detected, rawVal, msg ->
            _sensorData.value = _sensorData.value.copy(
                mq2SmokeDetected = detected,
                mq2RawValue = rawVal ?: _sensorData.value.mq2RawValue
            )
            onFireAlertEvent?.invoke(detected, rawVal, msg)
        }
        onMq2DataReceived = { rawVal, voltage, smoke ->
            _sensorData.value = _sensorData.value.copy(
                mq2RawValue = rawVal,
                mq2Voltage = voltage,
                mq2SmokeDetected = smoke
            )
            onMq2TelemetryEvent?.invoke(rawVal, voltage, smoke)
        }
        onNtcDataReceived = { temperatures, rawValues, ready ->
            _sensorData.value = _sensorData.value.copy(
                ntcTemperatures = temperatures,
                ntcRawValues = rawValues,
                pcfNtcReady = ready
            )
        }
    }

    val usbSensorManager: UsbSensorManager = UsbSensorManager(
        context = context,
        onDataReceived = { rawTemp, rawHum, rawPress ->
            val now = System.currentTimeMillis()
            val finalTemp = rawTemp?.let { it + tempOffset }
            val finalHum = rawHum?.let { (it + humOffset).coerceIn(0f, 100f) }
            val finalPress = rawPress?.let { it + pressOffset }

            _sensorData.value = _sensorData.value.copy(
                temperature = finalTemp,
                humidity = finalHum,
                pressure = finalPress,
                ahtOk = rawTemp != null || rawHum != null,
                bmpOk = rawPress != null,
                lastUpdatedEpochMs = now,
                isConnected = true,
                isConnecting = false,
                connectionType = "USB",
                errorMessage = null
            )
        },
        onStatusChanged = { connected, message ->
            _sensorData.value = _sensorData.value.copy(
                isConnected = connected,
                isConnecting = false,
                connectionType = "USB",
                errorMessage = message
            )
        }
    ).apply {
        onButtonEvent = { id, name ->
            onPhysicalButtonEvent?.invoke(id, name)
        }
        onPcfStatusChanged = { ready ->
            _sensorData.value = _sensorData.value.copy(pcfReady = ready)
        }
    }

    fun updateConfig(
        enabled: Boolean,
        mode: String = "BLE",
        targetBleName: String = "ESP32C3-Sensor",
        baud: Int = 115200,
        newHost: String = "192.168.1.100",
        newPort: Int = 80,
        newInterval: Int = 5,
        newTempOffset: Float = 0.0f,
        newHumOffset: Float = 0.0f,
        newPressOffset: Float = 0.0f
    ) {
        val modeChanged = connectionMode != mode
        val enabledChanged = isEnabled != enabled
        val bleNameChanged = bleDeviceName != targetBleName
        val baudChanged = baudRate != baud

        isEnabled = enabled
        connectionMode = mode
        bleDeviceName = targetBleName
        baudRate = baud
        host = newHost.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        port = newPort
        intervalSeconds = newInterval.coerceIn(1, 300)
        tempOffset = newTempOffset
        humOffset = newHumOffset
        pressOffset = newPressOffset

        if (modeChanged || enabledChanged || bleNameChanged || baudChanged) {
            start()
        }
    }

    fun start() {
        if (!isEnabled) {
            stop()
            _sensorData.value = _sensorData.value.copy(
                isConnected = false,
                isConnecting = false,
                errorMessage = "センサー無効化中"
            )
            return
        }

        when (connectionMode) {
            "BLE" -> {
                pollingJob?.cancel()
                pollingJob = null
                usbSensorManager.stop()
                bleSensorManager.start(targetName = bleDeviceName)
            }
            "USB" -> {
                pollingJob?.cancel()
                pollingJob = null
                bleSensorManager.stop()
                usbSensorManager.start(baud = baudRate)
            }
            else -> {
                bleSensorManager.stop()
                usbSensorManager.stop()
                restartWifiPolling()
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        bleSensorManager.stop()
        usbSensorManager.stop()
    }

    fun retryConnection() {
        when (connectionMode) {
            "BLE" -> bleSensorManager.retryConnection()
            "USB" -> usbSensorManager.connectPort()
            else -> refreshNow()
        }
    }

    fun retryUsbConnection() {
        retryConnection()
    }

    fun refreshNow() {
        when (connectionMode) {
            "BLE" -> {
                if (bleSensorManager.isConnected) {
                    bleSensorManager.requestImmediateReading()
                } else {
                    bleSensorManager.retryConnection()
                }
            }
            "USB" -> usbSensorManager.connectPort()
            else -> {
                scope.launch {
                    fetchSensorData()
                }
            }
        }
    }

    /**
     * ESP32へセンサー送信間隔（更新頻度）を即座に送信
     */
    fun sendSensorInterval(intervalSeconds: Int): Boolean {
        return if (connectionMode == "BLE") {
            bleSensorManager.sendSensorInterval(intervalSeconds)
        } else {
            false
        }
    }

    private fun restartWifiPolling() {
        pollingJob?.cancel()
        if (host.isBlank()) {
            _sensorData.value = _sensorData.value.copy(
                isConnected = false,
                isConnecting = false,
                connectionType = "Wi-Fi",
                errorMessage = "ホスト未設定"
            )
            return
        }

        pollingJob = scope.launch {
            while (isActive) {
                fetchSensorData()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    private suspend fun fetchSensorData() {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        if (cleanHost.isBlank()) return

        val url = "http://$cleanHost:$port/data"
        _sensorData.value = _sensorData.value.copy(isConnecting = true, connectionType = "Wi-Fi")

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)

                val rawTemp = if (json.has("temperature")) json.getDouble("temperature").toFloat() else null
                val rawHum = if (json.has("humidity")) json.getDouble("humidity").toFloat() else null
                val rawPress = if (json.has("pressure")) json.getDouble("pressure").toFloat() else null
                val altitude = if (json.has("altitude")) json.getDouble("altitude").toFloat() else null
                val rssi = if (json.has("rssi")) json.getInt("rssi") else null
                val uptime = if (json.has("uptime_sec")) json.getLong("uptime_sec") else null
                val ahtOk = json.optBoolean("aht_ok", true)
                val bmpOk = json.optBoolean("bmp_ok", true)

                _sensorData.value = EspSensorData(
                    temperature = rawTemp?.let { it + tempOffset },
                    humidity = rawHum?.let { (it + humOffset).coerceIn(0f, 100f) },
                    pressure = rawPress?.let { it + pressOffset },
                    altitude = altitude,
                    rssi = rssi,
                    uptimeSec = uptime,
                    ahtOk = ahtOk,
                    bmpOk = bmpOk,
                    lastUpdatedEpochMs = System.currentTimeMillis(),
                    isConnected = true,
                    isConnecting = false,
                    connectionType = "Wi-Fi",
                    errorMessage = null
                )
            } else {
                _sensorData.value = _sensorData.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    connectionType = "Wi-Fi",
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                )
            }
        } catch (e: Exception) {
            Log.w("EspSensorManager", "Fetch failed from $url: ${e.localizedMessage}")
            _sensorData.value = _sensorData.value.copy(
                isConnected = false,
                isConnecting = false,
                connectionType = "Wi-Fi",
                errorMessage = "接続待機中 (${e.localizedMessage ?: "タイムアウト"})"
            )
        }
    }

    /**
     * MQ-2 火災検知閾値をESP32へ同期送信
     */
    fun sendFireThreshold(threshold: Int): Boolean {
        return if (connectionMode == "BLE") {
            bleSensorManager.sendFireThreshold(threshold)
        } else {
            false
        }
    }

    /**
     * assetsからESP32-C3用またはESP8266用のArduinoスケッチコード（.ino）を読み込む
     */
    fun loadArduinoSketchCode(): String {
        val assetPath = if (connectionMode == "BLE") {
            "arduino/esp32c3_aht20_bmp280_ble.ino"
        } else {
            "arduino/esp8266_aht20_bmp280.ino"
        }
        return try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            "スケッチの読み込みに失敗しました: ${e.localizedMessage}"
        }
    }

    /**
     * ESP32 / ESP8266 へファームウェア(.bin)をOTA書き込み
     * BLE接続時はBluetooth経由 (BLE OTA)、Wi-Fi設定時はHTTP経由 (Wi-Fi OTA) を自動選択
     * @return エラーメッセージ（空文字列なら成功）
     */
    suspend fun flashEspFirmware(
        fileName: String,
        binBytes: ByteArray,
        targetHost: String? = null,
        targetPort: Int? = null,
        onProgress: ((percent: Int, writtenBytes: Int, totalBytes: Int, speedKbps: Float, statusMsg: String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (bleSensorManager.isConnected) {
            Log.i("EspSensorManager", "Flashing ESP32-C3 firmware via Bluetooth BLE OTA (${binBytes.size} bytes)...")
            return@withContext bleSensorManager.flashFirmwareBle(binBytes, onProgress)
        }

        onProgress?.invoke(0, 0, binBytes.size, 0f, "Wi-Fi経由でESPへアップロード中...")
        val res = flashEspOta(fileName, binBytes, targetHost, targetPort)
        if (res.isEmpty()) {
            onProgress?.invoke(100, binBytes.size, binBytes.size, 0f, "ファームウェア更新完了！ESPが再起動しました")
        }
        res
    }

    /**
     * ESP8266 / ESP32 へWi-Fi経由でファームウェアバイナリ(.bin)をOTAアップデート
     * @return エラーメッセージ（空文字列なら成功）
     */
    suspend fun flashEspOta(
        fileName: String,
        binBytes: ByteArray,
        targetHost: String? = null,
        targetPort: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val h = (targetHost ?: host).trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val p = targetPort ?: port
        if (h.isBlank()) {
            return@withContext "ESPのIPアドレス/ホスト名が設定されていません。ESP設定タブまたはOTA画面でIPを指定してください。"
        }

        val url = "http://$h:$p/update"
        try {
            val otaClient = httpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()

            val mediaType = "application/octet-stream".toMediaTypeOrNull()
            val filePart = binBytes.toRequestBody(mediaType)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("update", fileName.ifBlank { "firmware.bin" }, filePart)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = otaClient.newCall(request).execute()
            if (response.isSuccessful) {
                "" // 成功
            } else {
                "ESP側OTAエラー: HTTP ${response.code} ${response.message}"
            }
        } catch (e: Exception) {
            "ESP接続エラー ($url): ${e.localizedMessage ?: "接続タイムアウト"}"
        }
    }
}

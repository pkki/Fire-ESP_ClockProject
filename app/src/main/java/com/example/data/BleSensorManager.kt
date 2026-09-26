package com.example.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * ESP32-C3 BLE センサー自動接続・通信マネージャー
 *
 * ESP32-C3のBLE GATTサービスをスキャンし、
 * アプリ起動時やセンサー起動時に自動的に検出・接続して
 * AHT20/BMP280の温湿度・気圧データを取得します。
 *
 * Nordic UART Service (NUS) 標準UUIDまたは
 * カスタム環境センサーUUID (0x181A) の両方を透過的にサポート。
 */
class BleSensorManager(
    private val context: Context,
    private val onDataReceived: (temp: Float?, hum: Float?, press: Float?, rssi: Int?) -> Unit,
    private val onStatusChanged: (connected: Boolean, connecting: Boolean, message: String?) -> Unit
) {
    companion object {
        private const val TAG = "BleSensorManager"

        // Nordic UART Service (ESP32-C3 Arduino BLE で最も一般的)
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX_WRITE_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write to ESP32 (Commands)
        val NUS_TX_NOTIFY_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify from ESP32

        // 標準環境センシングUUID (フォールバック)
        val ENV_SERVICE_UUID: UUID = UUID.fromString("0000181A-0000-1000-8000-00805F9B34FB")

        // CCCD descriptor (0x2902)
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isStarted = false
    private var isConnectedInternal = false
    val isConnected: Boolean get() = isConnectedInternal
    private var targetDeviceName = "ESP32C3-Sensor"

    // IR Remote Callbacks
    var onIrSignalReceived: ((protocol: String, hex: String, bits: Int, raw: String) -> Unit)? = null
    var onIrStatusChanged: ((learning: Boolean, message: String?) -> Unit)? = null
    var onIrSendResult: ((status: String) -> Unit)? = null
    var onRawLogReceived: ((rawText: String) -> Unit)? = null
    var onButtonEvent: ((btnId: Int, btnName: String) -> Unit)? = null
    var onPcfStatusChanged: ((ready: Boolean) -> Unit)? = null
    var onFireAlertReceived: ((detected: Boolean, mq2Raw: Int?, msg: String?) -> Unit)? = null
    var onMq2DataReceived: ((rawVal: Int?, voltage: Float?, smokeDetected: Boolean) -> Unit)? = null
    var onNtcDataReceived: ((temperatures: List<Float?>, rawValues: List<Int?>, ready: Boolean) -> Unit)? = null
    var onOtaProgress: ((percent: Int, writtenBytes: Int, totalBytes: Int, speedKbps: Float, statusMsg: String) -> Unit)? = null
    var onOtaStatus: ((status: String, message: String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var autoReconnectJob: Job? = null
    private val lineBuffer = StringBuilder()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val scanRec = result.scanRecord
                val devName = device.name ?: scanRec?.deviceName ?: ""
                val devAddress = device.address

                Log.d(TAG, "BLE Scan Result: name='$devName', addr='$devAddress', rssi=${result.rssi}")

                // 判定1: デバイス名が一致、またはESP32/ESP32C3/Sensor等のキーワードが含まれる
                val nameMatches = matchesTarget(devName)

                // 判定2: NUSサービスUUIDがアドバタイズに含まれているか確認（名前が取得できなくても即接続）
                val uuidMatches = scanRec?.serviceUuids?.any { it.uuid == NUS_SERVICE_UUID } ?: false

                if (nameMatches || uuidMatches) {
                    Log.i(TAG, "Target ESP32-C3 detected: name='$devName', addr='$devAddress', uuidMatch=$uuidMatches. Connecting...")
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            isScanning = false
            val errorDesc = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "スキャン多重実行"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "BLE登録失敗 (Bluetoothを再起動してください)"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "BLE内部エラー"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE非対応"
                else -> "エラー: $errorCode"
            }
            onStatusChanged(false, false, "BLEスキャン失敗 ($errorDesc)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status: $status, newState: $newState")

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server. Requesting MTU and discovering services...")
                isConnectedInternal = true
                onStatusChanged(true, true, "サービス検出中...")

                var serviceDiscovered = false
                val fallbackRunnable = Runnable {
                    if (!serviceDiscovered && isConnectedInternal) {
                        Log.w(TAG, "MTU negotiation timeout, discovering services directly...")
                        try {
                            serviceDiscovered = true
                            gatt.discoverServices()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to discover services in fallback", e)
                        }
                    }
                }
                mainHandler.postDelayed(fallbackRunnable, 600)

                mainHandler.postDelayed({
                    try {
                        gatt.requestMtu(512)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to request MTU, discovering services directly", e)
                        if (!serviceDiscovered) {
                            serviceDiscovered = true
                            mainHandler.removeCallbacks(fallbackRunnable)
                            gatt.discoverServices()
                        }
                    }
                }, 150)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from GATT server. status=$status")
                isConnectedInternal = false
                closeGatt()
                onStatusChanged(false, false, "切断されました。再接続待機中...")
                triggerAutoReconnect()
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT connection failed with status: $status. Reconnecting...")
                isConnectedInternal = false
                closeGatt()
                onStatusChanged(false, false, "接続エラー (status=$status)。再検索中...")
                triggerAutoReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU changed to $mtu, status: $status. Discovering services...")
            mainHandler.postDelayed({
                try {
                    gatt.discoverServices()
                } catch (e: Exception) {
                    Log.e(TAG, "Error discovering services after MTU change", e)
                }
            }, 100)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully. Setting up notification...")
                setupNotifications(gatt)
            } else {
                Log.w(TAG, "Service discovery failed with status: $status")
                onStatusChanged(true, false, "サービス検索失敗 (status=$status)")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            handleIncomingBytes(data)
        }

        // Android 13 (Tiramisu) or newer callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "CCCD descriptor written successfully. Ready to receive notifications!")
                onStatusChanged(true, false, null)
            } else {
                Log.w(TAG, "Failed to write CCCD descriptor, status: $status")
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // RSSI updated
            }
        }
    }

    private fun matchesTarget(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.equals(targetDeviceName, ignoreCase = true)) return true
        val lower = name.lowercase()
        return lower.contains("esp32") || lower.contains("esp32c3") || lower.contains("sensor")
    }

    fun setTargetDeviceName(name: String) {
        if (name.isNotBlank()) {
            targetDeviceName = name.trim()
        }
    }

    /**
     * BLE自動接続・監視開始
     */
    fun start(targetName: String = "ESP32C3-Sensor") {
        setTargetDeviceName(targetName)
        isStarted = true
        Log.i(TAG, "Starting BLE Sensor Manager with target: $targetDeviceName")
        startAutoScanLoop()
    }

    /**
     * BLE停止
     */
    fun stop() {
        isStarted = false
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        stopScan()
        closeGatt()
        onStatusChanged(false, false, "BLE停止中")
    }

    /**
     * 手動再スキャン / 再接続リクエスト
     */
    fun retryConnection() {
        if (!isStarted) {
            start(targetDeviceName)
            return
        }
        stopScan()
        closeGatt()
        triggerAutoReconnect()
    }

    private fun startAutoScanLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            while (isActive && isStarted) {
                if (bluetoothGatt == null && !isConnectedInternal) {
                    startScan()
                }
                delay(10000) // 10秒ごとに接続状況を維持・監視
            }
        }
    }

    private fun triggerAutoReconnect() {
        scope.launch {
            delay(1500)
            if (isStarted && bluetoothGatt == null && !isConnectedInternal) {
                startScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onStatusChanged(false, false, "端末のBluetoothがOFFになっています")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onStatusChanged(false, false, "BLEスキャナーが利用できません")
            return
        }

        if (isScanning) return

        onStatusChanged(false, true, "ESP32-C3を検索中...")
        isScanning = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // フィルターなしで開始（アドバタイズ内の名前またはサービスUUIDでScanCallback内で即判定）
        try {
            scanner.startScan(null, settings, scanCallback)
            Log.d(TAG, "BLE scan started...")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for startScan", e)
            onStatusChanged(false, false, "Bluetooth権限が必要です (アプリ権限を確認してください)")
            isScanning = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            isScanning = false
        }

        // 10秒後にスキャンを停止して再検索
        mainHandler.postDelayed({
            if (isScanning && bluetoothGatt == null) {
                stopScan()
                if (isStarted && !isConnectedInternal) {
                    onStatusChanged(false, false, "ESP32-C3を探しています... (通電確認中)")
                }
            }
        }, 10000)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "BLE scan stopped.")
        } catch (_: Exception) {}
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        val devDisplayName = device.name ?: device.address
        onStatusChanged(false, true, "接続中: $devDisplayName...")
        try {
            closeGatt()
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth connect permission error", e)
            onStatusChanged(false, false, "Bluetooth接続権限が必要です")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting GATT", e)
            onStatusChanged(false, false, "接続エラー: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupNotifications(gatt: BluetoothGatt) {
        // Find characteristics for notifications & commands
        var notifyChar: BluetoothGattCharacteristic? = null
        writeCharacteristic = null

        // 1. Try Nordic UART Service first
        val nusService = gatt.getService(NUS_SERVICE_UUID)
        if (nusService != null) {
            notifyChar = nusService.getCharacteristic(NUS_TX_NOTIFY_CHAR_UUID)
            writeCharacteristic = nusService.getCharacteristic(NUS_RX_WRITE_CHAR_UUID)
        }

        // 2. Fallback: Search all characteristics
        if (notifyChar == null) {
            for (service in gatt.services) {
                for (ch in service.characteristics) {
                    val props = ch.properties
                    if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    ) {
                        notifyChar = ch
                    }
                    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    ) {
                        if (writeCharacteristic == null) {
                            writeCharacteristic = ch
                        }
                    }
                }
            }
        }

        if (notifyChar != null) {
            try {
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                }
                Log.i(TAG, "Notification configured on ${notifyChar.uuid}, writeChar=${writeCharacteristic?.uuid}")
                isConnectedInternal = true
                onStatusChanged(true, false, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable notification", e)
            }
        } else {
            Log.w(TAG, "No notification characteristic found in device services.")
            onStatusChanged(true, false, "通知キャラクタリスティック未検出")
        }
    }

    /**
     * ESP32-C3へコマンドJSONを送信（赤外線送信・学習開始/終了など）
     */
    @SuppressLint("MissingPermission")
    fun sendCommand(jsonCommand: String): Boolean {
        val gatt = bluetoothGatt ?: run {
            Log.w(TAG, "Cannot send command: BLE is not connected")
            return false
        }
        val writeChar = writeCharacteristic ?: run {
            Log.w(TAG, "Cannot send command: Write characteristic not found")
            return false
        }

        try {
            val payload = if (jsonCommand.endsWith("\n")) jsonCommand else "$jsonCommand\n"
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val writeType = if ((writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                gatt.writeCharacteristic(writeChar, bytes, writeType)
            } else {
                @Suppress("DEPRECATION")
                writeChar.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(writeChar)
            }
            Log.d(TAG, "BLE Command sent: $payload")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing BLE command", e)
            return false
        }
    }

    /**
     * 赤外線学習モード開始
     */
    fun startIrLearning() {
        sendCommand("{\"cmd\":\"ir_learn_start\"}")
    }

    /**
     * 赤外線学習モード停止
     */
    fun stopIrLearning() {
        sendCommand("{\"cmd\":\"ir_learn_stop\"}")
    }

    /**
     * 赤外線信号を送信 (プロトコル/HEXまたはRawパルス列)
     */
    fun sendIrSignal(protocol: String, hexCode: String, bits: Int, rawCode: String = ""): Boolean {
        val json = JSONObject().apply {
            put("cmd", "ir_send")
            put("protocol", protocol)
            put("hex", hexCode)
            put("bits", bits)
            if (rawCode.isNotBlank()) {
                put("raw", rawCode)
            }
        }
        return sendCommand(json.toString())
    }

    /**
     * 火災・煙検知の感度閾値をESP32へ送信
     */
    fun sendFireThreshold(threshold: Int): Boolean {
        val json = JSONObject().apply {
            put("cmd", "set_fire_threshold")
            put("threshold", threshold)
        }
        return sendCommand(json.toString())
    }

    /**
     * ESP32のセンサー送信間隔（更新頻度）を設定
     */
    fun sendSensorInterval(intervalSeconds: Int): Boolean {
        val ms = (intervalSeconds * 1000).coerceIn(200, 60000)
        val json = JSONObject().apply {
            put("cmd", "set_sensor_interval")
            put("interval_ms", ms)
            put("interval_sec", intervalSeconds)
        }
        return sendCommand(json.toString())
    }

    /**
     * ESP32へ今すぐ測定・送信を要求 (即時リフレッシュ)
     */
    fun requestImmediateReading(): Boolean {
        return sendCommand("{\"cmd\":\"read_sensor\"}")
    }

    /**
     * ESP32-C3 へ Bluetooth Low Energy (BLE OTA) 経由でファームウェアバイナリ(.bin)を直接書き込み
     * @return エラーメッセージ (空文字なら成功)
     */
    suspend fun flashFirmwareBle(
        binBytes: ByteArray,
        onProgress: ((percent: Int, writtenBytes: Int, totalBytes: Int, speedKbps: Float, statusMsg: String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val gatt = bluetoothGatt
        val writeChar = writeCharacteristic
        if (!isConnectedInternal || gatt == null || writeChar == null) {
            return@withContext "BLE未接続: ESP32-C3とBluetooth接続されていることを確認してください"
        }
        if (binBytes.isEmpty()) {
            return@withContext "ファームウェアデータが空です"
        }

        try {
            // MTU 517 を要求して高速転送を有効化
            try {
                gatt.requestMtu(517)
                delay(200)
            } catch (_: Exception) {}

            var isReady = false
            var isSuccess = false
            var isAborted = false
            var errorMsg = ""

            val prevOtaStatus = onOtaStatus
            onOtaStatus = { status, msg ->
                Log.i(TAG, "BLE OTA Status update: $status ($msg)")
                if (status == "ready") {
                    isReady = true
                } else if (status == "success") {
                    isSuccess = true
                } else if (status == "error" || status == "aborted") {
                    isAborted = true
                    errorMsg = msg
                }
            }

            // 1. OTA開始要求コマンドを送信
            onProgress?.invoke(0, 0, binBytes.size, 0f, "ESP32へBLE OTA開始要求を送信中...")
            val startCmd = "{\"cmd\":\"ota_start\",\"size\":${binBytes.size}}\n"
            sendCommand(startCmd)

            // ESP32からの準備完了(ready)応答を待機 (最大5秒)
            val startWait = System.currentTimeMillis()
            while (!isReady && !isAborted && System.currentTimeMillis() - startWait < 5000) {
                delay(100)
            }

            if (isAborted) {
                onOtaStatus = prevOtaStatus
                return@withContext "ESP32 OTA初期化エラー: $errorMsg"
            }

            // 2. ファームウェアバイナリをBLEチャンク分割で連続転送
            val chunkSize = 240
            val totalBytes = binBytes.size
            var offset = 0
            val startTime = System.currentTimeMillis()
            var lastProgressTime = startTime

            onProgress?.invoke(0, 0, totalBytes, 0f, "ファームウェア書き込み中...")

            while (offset < totalBytes) {
                if (!isConnectedInternal) {
                    onOtaStatus = prevOtaStatus
                    return@withContext "転送中にBLE接続が切断されました"
                }

                val currentChunkSize = minOf(chunkSize, totalBytes - offset)
                val chunk = ByteArray(currentChunkSize)
                System.arraycopy(binBytes, offset, chunk, 0, currentChunkSize)

                val success = writeRawBytes(gatt, writeChar, chunk)
                if (!success) {
                    delay(25)
                    writeRawBytes(gatt, writeChar, chunk)
                }

                offset += currentChunkSize
                val now = System.currentTimeMillis()

                if (now - lastProgressTime >= 150 || offset >= totalBytes) {
                    lastProgressTime = now
                    val elapsedSec = (now - startTime) / 1000f
                    val speedKbps = if (elapsedSec > 0.05f) (offset / 1024f) / elapsedSec else 0f
                    val percent = ((offset.toFloat() / totalBytes.toFloat()) * 100).toInt().coerceIn(0, 99)
                    onProgress?.invoke(percent, offset, totalBytes, speedKbps, "ESP32へ転送・フラッシュ書込中 ($percent%)...")
                }

                // ESP32フラッシュ書き込みのバッファオーバーラン防止用微小ウェイト
                delay(12)
            }

            // 3. OTA完了・終了コマンドを送信
            onProgress?.invoke(99, totalBytes, totalBytes, 0f, "書き込み完了を検証中...")
            val endCmd = "{\"cmd\":\"ota_end\"}\n"
            sendCommand(endCmd)

            // ESP32の書き込み検証および再起動応答を待機 (最大7秒)
            val endWait = System.currentTimeMillis()
            while (!isSuccess && !isAborted && System.currentTimeMillis() - endWait < 7000) {
                delay(200)
            }

            onOtaStatus = prevOtaStatus

            if (isAborted) {
                return@withContext "ESP32書き込み検証エラー: $errorMsg"
            }

            onProgress?.invoke(100, totalBytes, totalBytes, 0f, "ファームウェア更新完了！ESP32が再起動しました")
            "" // 成功
        } catch (e: Exception) {
            Log.e(TAG, "BLE OTA exception", e)
            "BLE OTA通信エラー: ${e.localizedMessage ?: e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeRawBytes(
        gatt: BluetoothGatt,
        writeChar: BluetoothGattCharacteristic,
        bytes: ByteArray
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val writeType = if ((writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                val res = gatt.writeCharacteristic(writeChar, bytes, writeType)
                res == 0 // BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                writeChar.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(writeChar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing raw BLE bytes", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        writeCharacteristic = null
        isConnectedInternal = false
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val str = String(bytes, StandardCharsets.UTF_8)
        synchronized(lineBuffer) {
            lineBuffer.append(str)

            // 1. Extract and parse all complete or boundary-detected JSON objects { ... } from the stream
            val jsonObjects = extractJsonObjects(lineBuffer)
            for (jsonStr in jsonObjects) {
                mainHandler.post {
                    onRawLogReceived?.invoke(jsonStr.trim())
                }
                parseLine(jsonStr)
            }

            // 2. Also process any remaining non-JSON newline-separated lines (e.g. text logs)
            var newlineIndex = lineBuffer.indexOf("\n")
            while (newlineIndex != -1) {
                val line = lineBuffer.substring(0, newlineIndex).trim()
                lineBuffer.delete(0, newlineIndex + 1)
                if (line.isNotEmpty()) {
                    mainHandler.post {
                        onRawLogReceived?.invoke(line)
                    }
                    parseLine(line)
                }
                newlineIndex = lineBuffer.indexOf("\n")
            }

            // 3. Safety limit buffer size
            if (lineBuffer.length > 8192) {
                lineBuffer.setLength(0)
            }
        }
    }

    /**
     * バッファ内の完全なJSONオブジェクト {...} を抽出
     * 複数パケット分割、MTU境界での切断、改行あり・なし混在、連続JSONを安全に再構築・修復
     */
    private fun extractJsonObjects(buffer: StringBuilder): List<String> {
        val results = mutableListOf<String>()
        var i = 0
        while (i < buffer.length) {
            val start = buffer.indexOf('{', i)
            if (start == -1) {
                // '{' がなければ余分な前置ゴミをクリア
                if (buffer.length > 2048) buffer.setLength(0)
                break
            }

            var depth = 0
            var inQuotes = false
            var escape = false
            var end = -1

            for (j in start until buffer.length) {
                val c = buffer[j]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    continue
                }
                if (!inQuotes) {
                    if (c == '{') {
                        depth++
                    } else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            end = j
                            break
                        }
                    }
                } else {
                    // もしクォート内なのに次の新しいトップレベルJSON ("{\"type": や "{\"cmd":) が開始された場合、
                    // 前のJSONがBLEパケット落ちやMTU制限で未完了のまま切断されたと判定
                    if (c == '{' && j + 6 < buffer.length) {
                        val sub = buffer.substring(j, (j + 8).coerceAtMost(buffer.length))
                        if (sub.contains("\"type\"") || sub.contains("\"cmd\"") || sub.contains("\"temp\"")) {
                            end = j - 1
                            break
                        }
                    }
                }
            }

            if (end != -1) {
                val json = buffer.substring(start, end + 1)
                results.add(json)
                buffer.delete(0, end + 1)
                i = 0
            } else {
                // もし改行 '\n' が含まれていれば、送信側がそこで行を終了しているため、不完全でも行単位で抽出して修復パーサーへ
                val newlineIdx = buffer.indexOf("\n", start)
                if (newlineIdx != -1) {
                    val lineJson = buffer.substring(start, newlineIdx).trim()
                    if (lineJson.isNotEmpty()) {
                        results.add(lineJson)
                    }
                    buffer.delete(0, newlineIdx + 1)
                    i = 0
                    continue
                }

                // まだ受信中の不完全なJSONがある場合
                if (start > 0) {
                    buffer.delete(0, start)
                }
                break
            }
        }
        return results
    }

    private fun parseLine(line: String) {
        Log.d(TAG, "BLE Received: $line")
        try {
            // 1. Try flexible IR parsing (JSON or text/regex, with auto-repair if truncated)
            val parsedIr = IrParserHelper.tryParse(line)
            if (parsedIr != null) {
                Log.i(TAG, "IR signal successfully detected & decoded: ${parsedIr.protocol} ${parsedIr.hexCode} (${parsedIr.bits} bits, rawLen=${parsedIr.rawCode.length})")
                mainHandler.post {
                    val rawSummary = if (parsedIr.rawCode.isNotBlank()) " [RAW=${parsedIr.rawCode.length}字]" else ""
                    onRawLogReceived?.invoke("受信: ${parsedIr.protocol} ${parsedIr.hexCode} (${parsedIr.bits}bit)$rawSummary")
                    onIrSignalReceived?.invoke(parsedIr.protocol, parsedIr.hexCode, parsedIr.bits, parsedIr.rawCode)
                }
                return
            }

            // 2. Try standard JSON status, button event or sensor messages
            val jsonObj = IrParserHelper.tryRepairAndParseJson(line)
            if (jsonObj != null) {
                // BLE OTA Status & Progress
                if (jsonObj.optString("type") == "ota_status") {
                    val status = jsonObj.optString("status", "")
                    val msg = jsonObj.optString("msg", jsonObj.optString("message", ""))
                    Log.i(TAG, "BLE OTA Status: $status ($msg)")
                    mainHandler.post {
                        onOtaStatus?.invoke(status, msg)
                    }
                    return
                }

                if (jsonObj.optString("type") == "ota_progress") {
                    val percent = jsonObj.optInt("percent", 0)
                    val written = jsonObj.optInt("written", 0)
                    val total = jsonObj.optInt("total", 0)
                    mainHandler.post {
                        onOtaProgress?.invoke(percent, written, total, 0f, "ESP32転送中 ($percent%)...")
                    }
                    return
                }

                // Physical Button Event (PCF8574P) - Ultra Low Latency
                if (jsonObj.optString("type") == "btn") {
                    val btnId = jsonObj.optInt("id", 0)
                    val btnName = jsonObj.optString("name", "BTN_$btnId")
                    Log.i(TAG, "⚡ [PHYSICAL BUTTON EVENT] PCF8574P P$btnId: $btnName pressed!")
                    mainHandler.post {
                        onButtonEvent?.invoke(btnId, btnName)
                    }
                    return
                }

                // Fire & Smoke Alert Notification (MQ-2 Sensor Emergency)
                if (jsonObj.optString("type") == "fire_alert" || jsonObj.optBoolean("fire_alert", false)) {
                    val detected = jsonObj.optBoolean("detected", true)
                    val rawVal = if (jsonObj.has("mq2_raw")) jsonObj.optInt("mq2_raw") else jsonObj.optInt("mq2", 0)
                    val msg = jsonObj.optString("message", "火災・煙を検知しました")
                    Log.w(TAG, "🔥 [FIRE ALERT EMERGENCY] MQ-2 Sensor triggered! rawVal=$rawVal, msg=$msg")
                    mainHandler.post {
                        onFireAlertReceived?.invoke(detected, rawVal, msg)
                    }
                }

                // MQ-2 Gas & Smoke Telemetry
                if (jsonObj.has("mq2") || jsonObj.has("mq2_raw") || jsonObj.has("smoke")) {
                    val rawVal = if (jsonObj.has("mq2_raw")) jsonObj.optInt("mq2_raw") else if (jsonObj.has("mq2")) jsonObj.optInt("mq2") else null
                    val smoke = jsonObj.optBoolean("smoke", false) || jsonObj.optBoolean("mq2_smoke", false)
                    val voltage = if (jsonObj.has("mq2_v")) jsonObj.getDouble("mq2_v").toFloat() else null
                    mainHandler.post {
                        onMq2DataReceived?.invoke(rawVal, voltage, smoke)
                    }
                }

                // IR status notification
                if (jsonObj.optString("type") == "ir_status") {
                    val isLearning = jsonObj.optBoolean("learning", false)
                    val msg = jsonObj.optString("message", null)
                    mainHandler.post {
                        onIrStatusChanged?.invoke(isLearning, msg)
                    }
                    return
                }

                // IR send result
                if (jsonObj.optString("type") == "ir_sent") {
                    val status = jsonObj.optString("status", "ok")
                    mainHandler.post {
                        onIrSendResult?.invoke(status)
                    }
                    return
                }

                if (jsonObj.has("pcf_ready")) {
                    val pcfReady = jsonObj.optBoolean("pcf_ready", false)
                    mainHandler.post {
                        onPcfStatusChanged?.invoke(pcfReady)
                    }
                }

                // PCF8574P (0x21) Multiplexed NTC Thermistor Temperatures
                if (jsonObj.has("ntc") || jsonObj.has("ntc_ready") || jsonObj.has("ntc_raw")) {
                    val ntcReady = jsonObj.optBoolean("ntc_ready", true)
                    val ntcList = mutableListOf<Float?>()
                    val rawList = mutableListOf<Int?>()

                    val ntcArr = jsonObj.optJSONArray("ntc")
                    val rawArr = jsonObj.optJSONArray("ntc_raw")

                    if (rawArr != null) {
                        for (i in 0 until rawArr.length()) {
                            if (rawArr.isNull(i)) {
                                rawList.add(null)
                            } else {
                                val r = rawArr.optInt(i, -1)
                                rawList.add(if (r >= 0) r else null)
                            }
                        }
                    }

                    if (ntcArr != null) {
                        for (i in 0 until ntcArr.length()) {
                            if (ntcArr.isNull(i)) {
                                ntcList.add(null)
                            } else {
                                val valD = ntcArr.optDouble(i, -999.0)
                                if (valD > -900.0) {
                                    ntcList.add(valD.toFloat())
                                } else {
                                    ntcList.add(null)
                                }
                            }
                        }
                    }

                    // クライアント側フォールバック計算: もし温度がnullだが生ADC値が正常範囲(100〜3950)ならSteinhart-Hartで計算
                    if (rawList.isNotEmpty()) {
                        for (i in 0 until 8) {
                            if (i >= ntcList.size) {
                                ntcList.add(null)
                            }
                            if (ntcList[i] == null) {
                                val rawVal = rawList.getOrNull(i)
                                if (rawVal != null && rawVal in 100..3980) {
                                    try {
                                        val pullup = 10000.0
                                        val nominalR = 10000.0
                                        val nominalT = 25.0
                                        val bCoef = 3950.0
                                        val resistance = pullup * (rawVal.toDouble() / (4095.0 - rawVal.toDouble()))
                                        var steinhart = resistance / nominalR
                                        steinhart = Math.log(steinhart)
                                        steinhart /= bCoef
                                        steinhart += 1.0 / (nominalT + 273.15)
                                        steinhart = 1.0 / steinhart
                                        val calcT = (steinhart - 273.15).toFloat()
                                        if (calcT in -30.0f..125.0f) {
                                            ntcList[i] = calcT
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    mainHandler.post {
                        onNtcDataReceived?.invoke(ntcList, rawList, ntcReady)
                    }
                }

                // Environmental Sensor data
                val temp = if (jsonObj.has("temperature")) jsonObj.getDouble("temperature").toFloat()
                else if (jsonObj.has("temp")) jsonObj.getDouble("temp").toFloat()
                else null

                val hum = if (jsonObj.has("humidity")) jsonObj.getDouble("humidity").toFloat()
                else if (jsonObj.has("hum")) jsonObj.getDouble("hum").toFloat()
                else null

                val press = if (jsonObj.has("pressure")) jsonObj.getDouble("pressure").toFloat()
                else if (jsonObj.has("press")) jsonObj.getDouble("press").toFloat()
                else null

                if (temp != null || hum != null || press != null) {
                    onDataReceived(temp, hum, press, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Line parse exception: ${e.message}")
        }
    }
}

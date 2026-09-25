package com.example.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

class UsbSensorManager(
    private val context: Context,
    private val onDataReceived: (temp: Float?, hum: Float?, press: Float?) -> Unit,
    private val onStatusChanged: (connected: Boolean, message: String?) -> Unit
) : SerialInputOutputManager.Listener {

    companion object {
        private const val TAG = "UsbSensorManager"
        const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val lineBuffer = StringBuilder()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null
    private var isStarted = false
    private var baudRate = 115200

    // IR Remote Callbacks
    var onIrSignalReceived: ((protocol: String, hex: String, bits: Int, raw: String) -> Unit)? = null
    var onIrStatusChanged: ((learning: Boolean, message: String?) -> Unit)? = null
    var onIrSendResult: ((status: String) -> Unit)? = null
    var onRawLogReceived: ((rawText: String) -> Unit)? = null
    var onButtonEvent: ((btnId: Int, btnName: String) -> Unit)? = null
    var onPcfStatusChanged: ((ready: Boolean) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            Log.d(TAG, "USB Permission granted for device: ${device.deviceName}")
                            connectPort()
                        } else {
                            Log.w(TAG, "USB Permission denied")
                            onStatusChanged(false, "USB権限が拒否されました")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB Device Attached")
                    scope.launch {
                        delay(500)
                        connectPort()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB Device Detached")
                    disconnect()
                    onStatusChanged(false, "USB切断 (ケーブル未接続)")
                }
            }
        }
    }

    fun start(baud: Int = 115200) {
        baudRate = baud
        if (isStarted) return
        isStarted = true

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        connectPort()
        startPeriodicCheck()
    }

    fun stop() {
        isStarted = false
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        disconnect()
    }

    private fun startPeriodicCheck() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive && isStarted) {
                delay(5000)
                if (usbSerialPort == null || ioManager == null) {
                    connectPort()
                }
            }
        }
    }

    fun connectPort() {
        val manager = usbManager ?: run {
            onStatusChanged(false, "USBサービス利用不可")
            return
        }

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            onStatusChanged(false, "USB未接続 (ケーブルを接続してください)")
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!manager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            manager.requestPermission(device, permissionIntent)
            onStatusChanged(false, "USB接続の許可を待機中...")
            return
        }

        val connection = manager.openDevice(device) ?: run {
            onStatusChanged(false, "USBポートを開けませんでした")
            return
        }

        try {
            disconnect()
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // ESP8266 NodeMCU auto-reset circuit precaution:
            // Setting DTR/RTS to false prevents holding ESP in bootloader or reset
            try {
                port.dtr = false
                port.rts = false
            } catch (_: Exception) {}

            usbSerialPort = port
            val managerIo = SerialInputOutputManager(port, this)
            ioManager = managerIo
            executor.submit(managerIo)

            onStatusChanged(true, null)
            Log.i(TAG, "USB Serial connected successfully at $baudRate baud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open USB port", e)
            disconnect()
            onStatusChanged(false, "接続エラー: ${e.localizedMessage}")
        }
    }

    fun disconnect() {
        try {
            ioManager?.listener = null
            ioManager?.stop()
        } catch (_: Exception) {}
        ioManager = null

        try {
            usbSerialPort?.close()
        } catch (_: Exception) {}
        usbSerialPort = null
    }

    /**
     * Send command string to USB Serial (ESP32)
     */
    fun sendCommand(command: String): Boolean {
        val port = usbSerialPort ?: return false
        return try {
            val payload = if (command.endsWith("\n")) command else "$command\n"
            val bytes = payload.toByteArray(Charsets.UTF_8)
            port.write(bytes, 1000)
            Log.d(TAG, "USB Serial command sent: $payload")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write USB command", e)
            false
        }
    }

    fun startIrLearning() {
        sendCommand("{\"cmd\":\"ir_learn_start\"}")
    }

    fun stopIrLearning() {
        sendCommand("{\"cmd\":\"ir_learn_stop\"}")
    }

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

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val text = String(data, Charsets.UTF_8)
        synchronized(lineBuffer) {
            lineBuffer.append(text)

            // 1. Extract and process complete or boundary-delimited JSON objects
            val jsonObjects = extractJsonObjects(lineBuffer)
            for (jsonStr in jsonObjects) {
                onRawLogReceived?.invoke(jsonStr.trim())
                processIncomingLine(jsonStr)
            }

            // 2. Process any remaining newline-separated text lines
            var newlineIndex: Int
            while (lineBuffer.indexOf("\n").also { newlineIndex = it } != -1) {
                val line = lineBuffer.substring(0, newlineIndex).trim()
                lineBuffer.delete(0, newlineIndex + 1)
                if (line.isNotEmpty()) {
                    onRawLogReceived?.invoke(line)
                    processIncomingLine(line)
                }
            }

            if (lineBuffer.length > 8192) {
                lineBuffer.setLength(0)
            }
        }
    }

    private fun extractJsonObjects(buffer: StringBuilder): List<String> {
        val results = mutableListOf<String>()
        var i = 0
        while (i < buffer.length) {
            val start = buffer.indexOf('{', i)
            if (start == -1) {
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
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            end = j
                            break
                        }
                    }
                } else {
                    // もしクォート内なのに次の新しいトップレベルJSON開始が現れた場合
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

                if (start > 0) {
                    buffer.delete(0, start)
                }
                break
            }
        }
        return results
    }

    private fun processIncomingLine(line: String) {
        try {
            // 1. Try flexible IR parsing (JSON or text/regex, with auto-repair)
            val parsedIr = IrParserHelper.tryParse(line)
            if (parsedIr != null) {
                Log.i(TAG, "USB IR detected & decoded: ${parsedIr.protocol} ${parsedIr.hexCode} (${parsedIr.bits} bits, rawLen=${parsedIr.rawCode.length})")
                val rawSummary = if (parsedIr.rawCode.isNotBlank()) " [RAW=${parsedIr.rawCode.length}字]" else ""
                onRawLogReceived?.invoke("受信: ${parsedIr.protocol} ${parsedIr.hexCode} (${parsedIr.bits}bit)$rawSummary")
                onIrSignalReceived?.invoke(parsedIr.protocol, parsedIr.hexCode, parsedIr.bits, parsedIr.rawCode)
                return
            }

            // 2. Check for JSON: e.g. status, button event or env sensor (with auto-repair)
            val json = IrParserHelper.tryRepairAndParseJson(line)
            if (json != null) {
                // Physical Button Event (PCF8574P) - Ultra Low Latency
                if (json.optString("type") == "btn") {
                    val btnId = json.optInt("id", 0)
                    val btnName = json.optString("name", "BTN_$btnId")
                    Log.i(TAG, "⚡ [USB PHYSICAL BUTTON EVENT] PCF8574P P$btnId: $btnName pressed!")
                    onButtonEvent?.invoke(btnId, btnName)
                    return
                }

                if (json.optString("type") == "ir_status") {
                    val isLearning = json.optBoolean("learning", false)
                    val msg = json.optString("message", null)
                    onIrStatusChanged?.invoke(isLearning, msg)
                    return
                }

                if (json.optString("type") == "ir_sent") {
                    val status = json.optString("status", "ok")
                    onIrSendResult?.invoke(status)
                    return
                }

                if (json.has("pcf_ready")) {
                    val pcfReady = json.optBoolean("pcf_ready", false)
                    onPcfStatusChanged?.invoke(pcfReady)
                }

                val temp = when {
                    json.has("temperature") -> json.getDouble("temperature").toFloat()
                    json.has("temp") -> json.getDouble("temp").toFloat()
                    else -> null
                }
                val hum = when {
                    json.has("humidity") -> json.getDouble("humidity").toFloat()
                    json.has("hum") -> json.getDouble("hum").toFloat()
                    else -> null
                }
                val press = when {
                    json.has("pressure") -> json.getDouble("pressure").toFloat()
                    json.has("press") -> json.getDouble("press").toFloat()
                    else -> null
                }
                if (temp != null || hum != null || press != null) {
                    onDataReceived(temp, hum, press)
                }
            } else if (line.contains("temp", ignoreCase = true) || line.contains("T:")) {
                // Fallback key-value format (e.g., T:24.5,H:52.1,P:1013.2)
                var parsedTemp: Float? = null
                var parsedHum: Float? = null
                var parsedPress: Float? = null

                val tokens = line.split(",", ";", " ")
                for (token in tokens) {
                    val pair = token.split(":", "=")
                    if (pair.size == 2) {
                        val key = pair[0].trim().lowercase()
                        val value = pair[1].trim().toFloatOrNull() ?: continue
                        when {
                            key == "t" || key == "temp" || key == "temperature" -> parsedTemp = value
                            key == "h" || key == "hum" || key == "humidity" -> parsedHum = value
                            key == "p" || key == "press" || key == "pressure" -> parsedPress = value
                        }
                    }
                }
                if (parsedTemp != null || parsedHum != null || parsedPress != null) {
                    onDataReceived(parsedTemp, parsedHum, parsedPress)
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "Line parse skipped: $line (${e.message})")
        }
    }

    override fun onRunError(e: Exception?) {
        Log.w(TAG, "USB Serial error: ${e?.localizedMessage}")
        disconnect()
        onStatusChanged(false, "通信エラー: ${e?.localizedMessage}")
    }
}

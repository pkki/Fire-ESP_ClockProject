package com.example.camera

import android.content.Context
import android.util.Log
import com.example.audio.ChimeSound
import com.example.data.CrashLogManager
import com.example.model.ChimeAudioSourceType
import com.example.model.ChimeVideoSourceType
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import com.example.model.ColorPalette
import com.example.model.CustomAudioItem
import com.example.model.CustomVideoItem
import com.example.model.ScheduledChime
import com.example.model.WeatherState
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class IpCameraServer(
    private val port: Int = 8080,
    private val context: Context? = null,
    private val stateProvider: () -> ClockPreferencesState = { ClockPreferencesState() },
    private val weatherProvider: () -> WeatherState = { WeatherState() },
    private val scheduledChimesProvider: () -> List<ScheduledChime> = { emptyList() },
    private val customAudioProvider: () -> List<CustomAudioItem> = { emptyList() },
    private val customVideoProvider: () -> List<CustomVideoItem> = { emptyList() },
    private val cameraConfigProvider: () -> IpCameraConfig = { IpCameraConfig() },
    private val onUpdatePreferences: (ClockPreferencesState) -> Unit = {},
    private val onAddOrUpdateChime: (ScheduledChime) -> Unit = {},
    private val onDeleteChime: (String) -> Unit = {},
    private val onToggleChime: (String) -> Unit = {},
    private val onTestChime: (ScheduledChime) -> Unit = {},
    private val onTestSound: (ChimeSound, Float) -> Unit = { _, _ -> },
    private val onTestCustomAudio: (filePath: String, volume: Float) -> Unit = { _, _ -> },
    private val onPreviewVideo: (type: ChimeVideoSourceType, path: String?, name: String?, duration: Int) -> Unit = { _, _, _, _ -> },
    private val onUploadAudio: (name: String, data: ByteArray) -> Unit = { _, _ -> },
    private val onUploadVideo: (name: String, data: ByteArray) -> Unit = { _, _ -> },
    private val onDeleteAudio: (String) -> Unit = {},
    private val onDeleteVideo: (String) -> Unit = {},
    private val onRenameAudio: (id: String, newName: String) -> Unit = { _, _ -> },
    private val onRenameVideo: (id: String, newName: String) -> Unit = { _, _ -> },
    private val onStopAudio: () -> Unit = {},
    private val onDismissVideo: () -> Unit = {},
    private val onSetDeviceVolume: (Float) -> Unit = {},
    private val onToggleCameraLens: () -> Unit = {},
    private val onUpdateCameraConfig: (IpCameraConfig) -> Unit = {},
    private val onUpdateAudioGain: (Float) -> Unit = {},
    private val onClientCountChanged: (Int) -> Unit = {},
    private val onAudioClientCountChanged: (Int) -> Unit = {},
    private val irButtonsProvider: () -> List<com.example.model.IrRemoteButton> = { emptyList() },
    private val irLearnStateProvider: () -> com.example.model.IrLearnState = { com.example.model.IrLearnState() },
    private val espSensorDataProvider: () -> com.example.model.EspSensorData = { com.example.model.EspSensorData() },
    private val onSendIrButton: (com.example.model.IrRemoteButton) -> Boolean = { false },
    private val onSendCustomIr: (protocol: String, hex: String, bits: Int, raw: String) -> Boolean = { _, _, _, _ -> false },
    private val onStartIrLearning: () -> Unit = {},
    private val onStopIrLearning: () -> Unit = {},
    private val onClearLearnedSignal: () -> Unit = {},
    private val onSaveIrButton: (com.example.model.IrRemoteButton) -> Unit = {},
    private val onDeleteIrButton: (String) -> Unit = {},
    private val onRefreshEspSensor: () -> Unit = {},
    private val onRetryEspConnection: () -> Unit = {},
    private val onGetArduinoSketch: () -> String = { "" },
    private val onExportIrButtonsJson: () -> String = { "[]" },
    private val onImportIrButtonsJson: (String) -> Boolean = { false },
    private val onUpdateEspConfig: (
        enabled: Boolean,
        mode: String,
        bleDeviceName: String,
        baud: Int,
        host: String,
        port: Int,
        intervalSeconds: Int,
        tempOffset: Float,
        humOffset: Float,
        pressOffset: Float,
        showOnClock: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    private val onUpdateApk: (name: String, data: ByteArray) -> Pair<Boolean, String> = { _, _ -> Pair(false, "未対応") },
    private val onDownloadAndInstallApk: suspend (url: String) -> Pair<Boolean, String> = { _ -> Pair(false, "未対応") },
    private val onEspOtaUpdate: suspend (name: String, data: ByteArray, host: String?, port: Int?) -> String = { _, _, _, _ -> "未対応" }
) {
    companion object {
        private const val TAG = "IpCameraServer"

        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val host = address.hostAddress
                            if (host != null && (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172."))) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get local IP", e)
            }
            return "127.0.0.1"
        }

        fun createWavHeader(sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val totalDataLen = 0x7FFFFFF0
            val totalAudioLen = totalDataLen + 36

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalAudioLen and 0xff).toByte()
            header[5] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[6] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[7] = ((totalAudioLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // PCM format
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = blockAlign.toByte()
            header[33] = 0
            header[34] = bitsPerSample.toByte()
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalDataLen and 0xff).toByte()
            header[41] = ((totalDataLen shr 8) and 0xff).toByte()
            header[42] = ((totalDataLen shr 16) and 0xff).toByte()
            header[43] = ((totalDataLen shr 24) and 0xff).toByte()
            return header
        }
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverThread: Thread? = null

    // Latest JPEG frame
    private val latestFrame = AtomicReference<ByteArray?>(null)

    // Active client video streams
    private val clientStreams = CopyOnWriteArrayList<OutputStream>()
    private val activeClients = AtomicInteger(0)

    // Active client audio streams
    private val audioClientStreams = CopyOnWriteArrayList<OutputStream>()
    private val activeAudioClients = AtomicInteger(0)
    private val currentAudioLevel = AtomicInteger(0)

    fun isRunning(): Boolean = isRunning.get()
    fun getPort(): Int = port
    fun getAudioLevel(): Int = currentAudioLevel.get()

    fun start(): Boolean {
        if (isRunning.get()) return true
        return try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            isRunning.set(true)

            serverThread = Thread({
                Log.d(TAG, "IP Camera & Web Dashboard server started on port $port")
                while (isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        Thread({
                            handleClient(clientSocket)
                        }, "WebDashboard-Client-${clientSocket.inetAddress.hostAddress}").start()
                    } catch (e: SocketException) {
                        if (!isRunning.get()) break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accepting client", e)
                    }
                }
            }, "IpCameraServerThread").apply { start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start IP Camera Server on port $port", e)
            false
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null

        // Close all client streams
        for (stream in clientStreams) {
            try {
                stream.close()
            } catch (_: Exception) {}
        }
        clientStreams.clear()
        activeClients.set(0)
        onClientCountChanged(0)

        // Close all audio client streams
        for (stream in audioClientStreams) {
            try {
                stream.close()
            } catch (_: Exception) {}
        }
        audioClientStreams.clear()
        activeAudioClients.set(0)
        currentAudioLevel.set(0)
        onAudioClientCountChanged(0)

        serverThread?.interrupt()
        serverThread = null
    }

    /**
     * Broadcast a new camera frame to all connected MJPEG clients
     */
    fun pushFrame(jpegData: ByteArray) {
        latestFrame.set(jpegData)
        if (clientStreams.isEmpty()) return

        val header = ("--frame\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${jpegData.size}\r\n\r\n").toByteArray()

        val deadStreams = mutableListOf<OutputStream>()
        for (out in clientStreams) {
            try {
                out.write(header)
                out.write(jpegData)
                out.write("\r\n".toByteArray())
                out.flush()
            } catch (e: Exception) {
                deadStreams.add(out)
            }
        }

        if (deadStreams.isNotEmpty()) {
            clientStreams.removeAll(deadStreams)
            val currentCount = activeClients.addAndGet(-deadStreams.size).coerceAtLeast(0)
            onClientCountChanged(currentCount)
        }
    }

    /**
     * Broadcast raw PCM audio chunk to all connected audio clients
     */
    fun pushAudioChunk(pcmData: ByteArray, level: Int) {
        currentAudioLevel.set(level)
        if (audioClientStreams.isEmpty()) return

        val deadStreams = mutableListOf<OutputStream>()
        for (out in audioClientStreams) {
            try {
                out.write(pcmData)
                out.flush()
            } catch (e: Exception) {
                deadStreams.add(out)
            }
        }

        if (deadStreams.isNotEmpty()) {
            audioClientStreams.removeAll(deadStreams)
            val currentCount = activeAudioClients.addAndGet(-deadStreams.size).coerceAtLeast(0)
            onAudioClientCountChanged(currentCount)
        }
    }

    fun getActiveAudioClientCount(): Int = activeAudioClients.get()

    fun getLatestFrame(): ByteArray? = latestFrame.get()
    fun getActiveClientCount(): Int = activeClients.get()

    private fun handleClient(socket: Socket) {
        try {
            val rawIn = socket.getInputStream()
            val out = BufferedOutputStream(socket.getOutputStream())

            // Read request line and headers
            val headerBytes = ByteArrayOutputStream()
            var prev = -1
            var matchCount = 0
            while (true) {
                val b = rawIn.read()
                if (b == -1) break
                headerBytes.write(b)
                if (prev == '\r'.code && b == '\n'.code) {
                    matchCount++
                    if (matchCount == 2) break
                } else if (b != '\r'.code && b != '\n'.code) {
                    matchCount = 0
                }
                prev = b
            }

            val headerText = headerBytes.toString(Charsets.UTF_8.name())
            val lines = headerText.split("\r\n")
            if (lines.isEmpty() || lines[0].isBlank()) {
                socket.close()
                return
            }

            val requestParts = lines[0].split(" ")
            if (requestParts.size < 2) {
                socket.close()
                return
            }

            val method = requestParts[0].uppercase()
            val fullPath = requestParts[1]
            val path = if (fullPath.contains("?")) fullPath.substringBefore("?") else fullPath

            // Parse headers into map
            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val contentType = headers["content-type"] ?: ""

            when {
                // MJPEG Video Stream
                path == "/video" || path == "/stream" || path == "/mjpeg" -> {
                    val header = ("HTTP/1.0 200 OK\r\n" +
                            "Server: DeskClock-IPCamera\r\n" +
                            "Connection: close\r\n" +
                            "Max-Age: 0\r\n" +
                            "Expires: 0\r\n" +
                            "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n\r\n").toByteArray()
                    out.write(header)
                    out.flush()

                    clientStreams.add(out)
                    val currentCount = activeClients.incrementAndGet()
                    onClientCountChanged(currentCount)
                    return // Keep connection open
                }

                // Live Audio Stream (WAV 16kHz 16-bit Mono continuous stream)
                path == "/audio" || path == "/audio.wav" || path == "/stream.wav" || path == "/audio.pcm" -> {
                    val isWav = !path.endsWith(".pcm")
                    val contentTypeHeader = if (isWav) "audio/x-wav" else "audio/l16; rate=16000; channels=1"
                    val header = ("HTTP/1.0 200 OK\r\n" +
                            "Server: DeskClock-IPCamera\r\n" +
                            "Connection: close\r\n" +
                            "Max-Age: 0\r\n" +
                            "Expires: 0\r\n" +
                            "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Type: $contentTypeHeader\r\n\r\n").toByteArray()
                    out.write(header)

                    if (isWav) {
                        val wavHeader = createWavHeader(sampleRate = 16000, channels = 1, bitsPerSample = 16)
                        out.write(wavHeader)
                    }
                    out.flush()

                    audioClientStreams.add(out)
                    val currentCount = activeAudioClients.incrementAndGet()
                    onAudioClientCountChanged(currentCount)
                    return // Keep connection open
                }

                // Snapshot JPEG Image
                path == "/snapshot" || path == "/shot.jpg" || path == "/frame.jpg" -> {
                    val frame = latestFrame.get()
                    if (frame != null) {
                        sendResponse(out, 200, "image/jpeg", frame)
                    } else {
                        sendResponse(out, 503, "text/plain", "カメラ起動中...".toByteArray(Charsets.UTF_8))
                    }
                }

                // Stream audio file from device
                path.startsWith("/media/audio/") -> {
                    val id = path.removePrefix("/media/audio/")
                    val item = customAudioProvider().find { it.id == id }
                    if (item != null && File(item.filePath).exists()) {
                        val file = File(item.filePath)
                        sendFileResponse(out, file, "audio/*")
                    } else {
                        sendResponse(out, 404, "text/plain", "Audio not found".toByteArray())
                    }
                }

                // Stream video file from device
                path.startsWith("/media/video/") -> {
                    val id = path.removePrefix("/media/video/")
                    val item = customVideoProvider().find { it.id == id }
                    if (item != null && File(item.filePath).exists()) {
                        val file = File(item.filePath)
                        sendFileResponse(out, file, "video/mp4")
                    } else {
                        sendResponse(out, 404, "text/plain", "Video not found".toByteArray())
                    }
                }

                // API: Get Full Data Status
                path == "/api/data" || path == "/api/status" -> {
                    val json = buildStatusJson()
                    sendResponse(out, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                }

                // API: Update Preferences
                method == "POST" && path == "/api/settings" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    handleUpdateSettings(bodyStr)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Save or Update Scheduled Chime
                method == "POST" && path == "/api/chime/save" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    handleSaveChime(bodyStr)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Delete Scheduled Chime
                method == "POST" && path == "/api/chime/delete" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val id = obj.optString("id")
                    if (id.isNotEmpty()) {
                        onDeleteChime(id)
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Toggle Scheduled Chime
                method == "POST" && path == "/api/chime/toggle" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val id = obj.optString("id")
                    if (id.isNotEmpty()) {
                        onToggleChime(id)
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Test Chime Sound or Scheduled Chime
                method == "POST" && path == "/api/chime/test" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val chimeId = obj.optString("id")
                    if (chimeId.isNotEmpty()) {
                        val chime = scheduledChimesProvider().find { it.id == chimeId }
                        if (chime != null) {
                            onTestChime(chime)
                        }
                    } else {
                        val soundName = obj.optString("sound")
                        val volume = obj.optDouble("volume", 0.8).toFloat()
                        val customPath = obj.optString("customAudioPath").ifEmpty { obj.optString("customPath").ifEmpty { null } }
                        val customId = obj.optString("customAudioId").ifEmpty { obj.optString("customId").ifEmpty { null } }
                        val sourceType = obj.optString("sourceType").ifEmpty { obj.optString("hourlyChimeSourceType").ifEmpty { null } }

                        if (sourceType == "CUSTOM_FILE" || customPath != null || customId != null || soundName.startsWith("CUSTOM_FILE:")) {
                            val resolvedPath = customPath ?: if (soundName.startsWith("CUSTOM_FILE:")) {
                                val id = soundName.substringAfter("CUSTOM_FILE:")
                                customAudioProvider().find { it.id == id || it.name == id }?.filePath
                            } else {
                                customAudioProvider().find { it.id == customId || it.name == soundName || it.id == soundName }?.filePath
                            }
                            if (resolvedPath != null) {
                                onTestCustomAudio(resolvedPath, volume)
                            } else {
                                onTestSound(ChimeSound.WESTMINSTER, volume)
                            }
                        } else {
                            val resolvedSound = when (soundName) {
                                "DIGITAL_BEEP", "DIGITAL_BEP" -> ChimeSound.DIGITAL_SIGNAL
                                else -> try { ChimeSound.valueOf(soundName) } catch (_: Exception) { null }
                            }
                            if (resolvedSound != null) {
                                onTestSound(resolvedSound, volume)
                            } else {
                                val customAudio = customAudioProvider().find {
                                    it.name == soundName || it.id == soundName || it.filePath == soundName
                                }
                                if (customAudio != null) {
                                    onTestCustomAudio(customAudio.filePath, volume)
                                } else {
                                    onTestSound(ChimeSound.WESTMINSTER, volume)
                                }
                            }
                        }
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Test Custom Audio File on Device
                method == "POST" && path == "/api/audio/test" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val filePath = obj.optString("filePath")
                    val volume = obj.optDouble("volume", 0.85).toFloat()
                    if (filePath.isNotEmpty()) {
                        onTestCustomAudio(filePath, volume)
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Preview Video on Clock Screen
                method == "POST" && path == "/api/video/preview" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val videoTypeStr = obj.optString("videoSourceType", "NONE")
                    val customPath = obj.optString("customVideoPath").ifEmpty { null }
                    val customName = obj.optString("customVideoName").ifEmpty { null }
                    val duration = obj.optInt("durationSeconds", 30)
                    val type = try { ChimeVideoSourceType.valueOf(videoTypeStr) } catch (_: Exception) { ChimeVideoSourceType.NONE }
                    onPreviewVideo(type, customPath, customName, duration)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Switch Camera Lens
                method == "POST" && path == "/api/camera/lens" -> {
                    onToggleCameraLens()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Update Microphone Gain
                method == "POST" && path == "/api/camera/audio_gain" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val gain = obj.optDouble("gain", 1.0).toFloat().coerceIn(0.1f, 5.0f)
                    onUpdateAudioGain(gain)
                    sendResponse(out, 200, "application/json", "{\"success\":true,\"gain\":$gain}".toByteArray())
                }

                // API: Toggle / Update Camera Configuration
                method == "POST" && path == "/api/camera/config" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val currentCam = cameraConfigProvider()
                    val updatedCam = currentCam.copy(
                        useFrontCamera = obj.optBoolean("useFrontCamera", currentCam.useFrontCamera),
                        enableAudio = obj.optBoolean("enableAudio", currentCam.enableAudio),
                        audioGain = obj.optDouble("audioGain", currentCam.audioGain.toDouble()).toFloat(),
                        targetFps = obj.optInt("targetFps", currentCam.targetFps),
                        resolutionWidth = obj.optInt("resolutionWidth", currentCam.resolutionWidth),
                        resolutionHeight = obj.optInt("resolutionHeight", currentCam.resolutionHeight),
                        jpegQuality = obj.optInt("jpegQuality", currentCam.jpegQuality)
                    )
                    onUpdateCameraConfig(updatedCam)
                    if (obj.has("audioGain")) {
                        onUpdateAudioGain(updatedCam.audioGain)
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Set Device Volume Directly
                method == "POST" && path == "/api/system/volume" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val vol = obj.optDouble("volume", 0.8).toFloat()
                    onSetDeviceVolume(vol)
                    sendResponse(out, 200, "application/json", "{\"success\":true,\"volume\":$vol}".toByteArray())
                }

                // API: Upload Audio File
                method == "POST" && path == "/api/upload/audio" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    handleFileUpload(bodyBytes, contentType, isVideo = false)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Upload Video File
                method == "POST" && path == "/api/upload/video" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    handleFileUpload(bodyBytes, contentType, isVideo = true)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Remote App Update (Upload APK & Launch Installer)
                method == "POST" && path == "/api/system/update_apk" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val (fileName, apkBytes) = parseMultipartFile(bodyBytes, contentType, "DeskClock_update.apk")
                    if (apkBytes.isNotEmpty()) {
                        val (success, message) = onUpdateApk(fileName, apkBytes)
                        val respObj = JSONObject().apply {
                            put("success", success)
                            put("message", message)
                            put("fileName", fileName)
                            put("fileSize", apkBytes.size)
                        }
                        sendResponse(out, if (success) 200 else 400, "application/json", respObj.toString().toByteArray())
                    } else {
                        sendResponse(out, 400, "application/json", "{\"success\":false,\"message\":\"APKファイルを受信できませんでした\"}".toByteArray())
                    }
                }

                // API: Remote App Update via URL Download
                method == "POST" && path == "/api/system/download_and_install_apk" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val obj = JSONObject(String(bodyBytes, Charsets.UTF_8))
                    val url = obj.optString("url")
                    if (url.isNotBlank()) {
                        val (success, message) = kotlinx.coroutines.runBlocking {
                            onDownloadAndInstallApk(url)
                        }
                        val respObj = JSONObject().apply {
                            put("success", success)
                            put("message", message)
                        }
                        sendResponse(out, if (success) 200 else 400, "application/json", respObj.toString().toByteArray())
                    } else {
                        sendResponse(out, 400, "application/json", "{\"success\":false,\"message\":\"URLが指定されていません\"}".toByteArray())
                    }
                }

                // API: Remote ESP Firmware OTA Update (Upload .bin & Flash ESP)
                method == "POST" && path == "/api/esp/ota_update" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val (fileName, binBytes) = parseMultipartFile(bodyBytes, contentType, "firmware.bin")
                    val query = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""
                    val queryParams = query.split("&").associate {
                        val parts = it.split("=")
                        if (parts.size == 2) parts[0].trim() to URLDecoder.decode(parts[1].trim(), "UTF-8") else "" to ""
                    }
                    val targetHost = headers["x-esp-host"] ?: queryParams["host"]
                    val targetPort = (headers["x-esp-port"] ?: queryParams["port"])?.toIntOrNull()

                    if (binBytes.isNotEmpty()) {
                        val errMsg = kotlinx.coroutines.runBlocking {
                            onEspOtaUpdate(fileName, binBytes, targetHost, targetPort)
                        }
                        val isOk = errMsg.isEmpty()
                        val respObj = JSONObject().apply {
                            put("success", isOk)
                            put("message", if (isOk) "ESPへのファームウェア転送が完了しました！ESPが再起動します。" else errMsg)
                            put("fileName", fileName)
                            put("fileSize", binBytes.size)
                        }
                        sendResponse(out, if (isOk) 200 else 500, "application/json", respObj.toString().toByteArray())
                    } else {
                        sendResponse(out, 400, "application/json", "{\"success\":false,\"message\":\"ファームウェアバイナリ(.bin)を受信できませんでした\"}".toByteArray())
                    }
                }

                // API: Delete Media
                method == "POST" && path == "/api/media/delete" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val obj = JSONObject(String(bodyBytes, Charsets.UTF_8))
                    val id = obj.optString("id")
                    val type = obj.optString("type")
                    if (type == "audio") onDeleteAudio(id) else onDeleteVideo(id)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Rename Media (Audio or Video)
                method == "POST" && path == "/api/media/rename" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val obj = JSONObject(String(bodyBytes, Charsets.UTF_8))
                    val id = obj.optString("id")
                    val type = obj.optString("type")
                    val newName = obj.optString("newName").trim()
                    if (id.isNotEmpty() && newName.isNotEmpty()) {
                        if (type == "audio") onRenameAudio(id, newName) else onRenameVideo(id, newName)
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Stop Audio Playback on Device
                method == "POST" && path == "/api/audio/stop" -> {
                    onStopAudio()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Dismiss / Stop Video Preview on Device
                method == "POST" && path == "/api/video/dismiss" -> {
                    onDismissVideo()
                    onStopAudio()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Get System Diagnostics & Operational Logs
                path == "/api/logs" -> {
                    val report = CrashLogManager.getDiagnosticsReport()
                    val logs = CrashLogManager.getRecentLogs(200)
                    val crashText = CrashLogManager.getCrashLogsText()

                    val root = JSONObject()
                    val diagObj = JSONObject().apply {
                        put("isHealthy", report.isHealthy)
                        put("uptimeFormatted", report.uptimeFormatted)
                        put("totalStarts", report.totalStarts)
                        put("crashRecoveryCount", report.crashRecoveryCount)
                        put("abnormalTerminationCount", report.abnormalTerminationCount)
                        put("lastCrashTime", report.lastCrashTime ?: "")
                        put("lastCrashMessage", report.lastCrashMessage ?: "")
                        put("lastAbnormalTerminationTime", report.lastAbnormalTerminationTime ?: "")
                        put("lastAbnormalTerminationMessage", report.lastAbnormalTerminationMessage ?: "")
                        put("usedMemoryMb", report.usedMemoryMb)
                        put("maxMemoryMb", report.maxMemoryMb)
                        put("freeMemoryMb", report.freeMemoryMb)
                        put("osVersion", report.osVersion)
                        put("deviceModel", report.deviceModel)
                        put("crashLogCount", report.crashLogCount)
                        put("totalLogCount", report.totalLogCount)
                    }
                    root.put("diagnostics", diagObj)
                    root.put("crashLogsText", crashText)

                    val logsArr = JSONArray()
                    logs.forEach { l ->
                        val lObj = JSONObject().apply {
                            put("timestamp", l.timestamp)
                            put("level", l.level)
                            put("tag", l.tag)
                            put("message", l.message)
                            put("stackTrace", l.stackTrace ?: "")
                        }
                        logsArr.put(lObj)
                    }
                    root.put("logs", logsArr)
                    sendResponse(out, 200, "application/json; charset=utf-8", root.toString().toByteArray(Charsets.UTF_8))
                }

                // API: Download Full Diagnostic Logs as Plain Text Attachment
                path == "/api/logs/download" -> {
                    val allLogs = CrashLogManager.getAllLogsText()
                    val bytes = allLogs.toByteArray(Charsets.UTF_8)
                    val header = ("HTTP/1.0 200 OK\r\n" +
                            "Server: DeskClock-Web\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Disposition: attachment; filename=\"deskclock_diagnostics_${System.currentTimeMillis()}.txt\"\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
                    out.write(header)
                    out.write(bytes)
                    out.flush()
                }

                // API: Clear All System & Crash Logs
                method == "POST" && path == "/api/logs/clear" -> {
                    CrashLogManager.clearAllLogs()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Clean Restart App
                method == "POST" && path == "/api/system/restart" -> {
                    sendResponse(out, 200, "application/json", "{\"success\":true,\"message\":\"Restart scheduled in 600ms\"}".toByteArray())
                    val ctx = context
                    if (ctx != null) {
                        Thread {
                            try {
                                Thread.sleep(300)
                                CrashLogManager.scheduleImmediateRestart(ctx)
                                android.os.Process.killProcess(android.os.Process.myPid())
                                System.exit(0)
                            } catch (_: Exception) {}
                        }.start()
                    }
                }

                // API: Trigger Test Crash & Auto-Restart to Verify Resilience
                method == "POST" && path == "/api/system/test_crash" -> {
                    sendResponse(out, 200, "application/json", "{\"success\":true,\"message\":\"Test crash scheduled now\"}".toByteArray())
                    Thread {
                        try {
                            Thread.sleep(300)
                            CrashLogManager.triggerTestCrash()
                        } catch (_: Exception) {}
                    }.start()
                }

                // API: Send IR Signal (by Button ID or direct params)
                method == "POST" && path == "/api/ir/send" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val ok = handleSendIr(bodyStr)
                    sendResponse(out, 200, "application/json", "{\"success\":$ok}".toByteArray())
                }

                // API: Start IR Learning
                method == "POST" && path == "/api/ir/learn/start" -> {
                    onStartIrLearning()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Stop IR Learning
                method == "POST" && path == "/api/ir/learn/stop" -> {
                    onStopIrLearning()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Clear Learned Signal
                method == "POST" && path == "/api/ir/learn/clear" -> {
                    onClearLearnedSignal()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Save or Update IR Button
                method == "POST" && path == "/api/ir/button/save" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    handleSaveIrButton(bodyStr)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Delete IR Button
                method == "POST" && path == "/api/ir/button/delete" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val obj = JSONObject(bodyStr)
                    val id = obj.optString("id")
                    if (id.isNotEmpty()) {
                        onDeleteIrButton(id)
                    }
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Export IR Buttons (JSON Backup)
                method == "GET" && path == "/api/ir/export" -> {
                    val json = onExportIrButtonsJson()
                    sendResponse(out, 200, "application/json", json.toByteArray(Charsets.UTF_8))
                }

                // API: Import IR Buttons (JSON Restore)
                method == "POST" && path == "/api/ir/import" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val ok = onImportIrButtonsJson(bodyStr)
                    sendResponse(out, 200, "application/json", "{\"success\":$ok}".toByteArray())
                }

                // API: Update ESP32 Sensor & Calibration Settings
                method == "POST" && path == "/api/esp/config" -> {
                    val bodyBytes = readExactBytes(rawIn, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    handleUpdateEspConfig(bodyStr)
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Manual Refresh ESP Sensor Telemetry
                method == "POST" && path == "/api/esp/refresh" -> {
                    onRefreshEspSensor()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Retry ESP Connection
                method == "POST" && path == "/api/esp/retry" -> {
                    onRetryEspConnection()
                    sendResponse(out, 200, "application/json", "{\"success\":true}".toByteArray())
                }

                // API: Download or View Arduino Sketch Code
                path == "/api/esp/sketch" -> {
                    val sketch = onGetArduinoSketch()
                    val bytes = sketch.toByteArray(Charsets.UTF_8)
                    val isDownload = fullPath.contains("download=true")
                    val disposition = if (isDownload) "attachment; filename=\"esp32_aht20_bmp280.ino\"" else "inline"
                    val header = ("HTTP/1.0 200 OK\r\n" +
                            "Server: DeskClock-Web\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Disposition: $disposition\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
                    out.write(header)
                    out.write(bytes)
                    out.flush()
                }

                // Web Dashboard HTML
                else -> {
                    val html = getDashboardHtml()
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    sendResponse(out, 200, "text/html; charset=utf-8", bytes)
                }
            }

            socket.close()
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val data = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(data, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return data
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val statusText = if (code == 200) "OK" else if (code == 404) "Not Found" else "Service Unavailable"
        val header = ("HTTP/1.0 $code $statusText\r\n" +
                "Server: DeskClock-Web\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
        out.write(header)
        out.write(body)
        out.flush()
    }

    private fun sendFileResponse(out: OutputStream, file: File, contentType: String) {
        val length = file.length()
        val header = ("HTTP/1.0 200 OK\r\n" +
                "Server: DeskClock-Web\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: $length\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
        out.write(header)
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var n: Int
            while (fis.read(buf).also { n = it } > 0) {
                out.write(buf, 0, n)
            }
        }
        out.flush()
    }

    private fun parseMultipartFile(bodyBytes: ByteArray, contentType: String, defaultName: String): Pair<String, ByteArray> {
        try {
            if (contentType.contains("multipart/form-data")) {
                val boundaryParam = contentType.split(";").find { it.trim().startsWith("boundary=") }
                val boundary = boundaryParam?.substringAfter("boundary=")?.trim()?.removeSurrounding("\"")
                if (boundary != null) {
                    val boundaryBytes = ("--$boundary").toByteArray(Charsets.UTF_8)
                    val headerEndMarker = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
                    var dataStartIndex = -1
                    for (i in 0 until (bodyBytes.size - 4).coerceAtLeast(0)) {
                        if (bodyBytes[i] == headerEndMarker[0] &&
                            bodyBytes[i + 1] == headerEndMarker[1] &&
                            bodyBytes[i + 2] == headerEndMarker[2] &&
                            bodyBytes[i + 3] == headerEndMarker[3]) {
                            dataStartIndex = i + 4
                            break
                        }
                    }

                    if (dataStartIndex != -1) {
                        val headerStr = String(bodyBytes, 0, dataStartIndex, Charsets.UTF_8)
                        val filenameMatch = Regex("filename=\"([^\"]+)\"").find(headerStr)
                        val originalFilename = filenameMatch?.groupValues?.get(1)?.trim() ?: defaultName

                        var dataEndIndex = bodyBytes.size
                        for (i in (bodyBytes.size - boundaryBytes.size - 32).coerceAtLeast(dataStartIndex) until (bodyBytes.size - boundaryBytes.size).coerceAtLeast(0)) {
                            var match = true
                            for (j in boundaryBytes.indices) {
                                if (bodyBytes[i + j] != boundaryBytes[j]) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                var end = i
                                if (end >= 2 && bodyBytes[end - 2] == '\r'.code.toByte() && bodyBytes[end - 1] == '\n'.code.toByte()) {
                                    end -= 2
                                }
                                dataEndIndex = end
                                break
                            }
                        }

                        val fileData = bodyBytes.copyOfRange(dataStartIndex, dataEndIndex.coerceAtLeast(dataStartIndex))
                        return Pair(originalFilename, fileData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing multipart file", e)
        }
        return Pair(defaultName, bodyBytes)
    }

    private fun handleFileUpload(bodyBytes: ByteArray, contentType: String, isVideo: Boolean) {
        try {
            val defaultName = if (isVideo) "uploaded_video_${System.currentTimeMillis()}.mp4" else "uploaded_audio_${System.currentTimeMillis()}.mp3"
            val (filename, fileData) = parseMultipartFile(bodyBytes, contentType, defaultName)
            if (isVideo) {
                onUploadVideo(filename, fileData)
            } else {
                onUploadAudio(filename, fileData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "File upload failed", e)
        }
    }

    private fun handleUpdateSettings(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val current = stateProvider()
            var updated = current

            if (obj.has("clockFace")) {
                updated = updated.copy(clockFace = ClockFace.valueOf(obj.getString("clockFace")))
            }
            if (obj.has("colorPalette")) {
                updated = updated.copy(colorPalette = ColorPalette.valueOf(obj.getString("colorPalette")))
            }
            if (obj.has("is24Hour")) {
                updated = updated.copy(is24Hour = obj.getBoolean("is24Hour"))
            }
            if (obj.has("showSeconds")) {
                updated = updated.copy(showSeconds = obj.getBoolean("showSeconds"))
            }
            if (obj.has("showWeather")) {
                updated = updated.copy(showWeather = obj.getBoolean("showWeather"))
            }
            if (obj.has("hourlyChimeEnabled")) {
                updated = updated.copy(hourlyChimeEnabled = obj.getBoolean("hourlyChimeEnabled"))
            }
            if (obj.has("halfHourlyChimeEnabled")) {
                updated = updated.copy(halfHourlyChimeEnabled = obj.getBoolean("halfHourlyChimeEnabled"))
            }
            if (obj.has("hourlyChimeSourceType")) {
                val stStr = obj.getString("hourlyChimeSourceType")
                val st = try { ChimeAudioSourceType.valueOf(stStr) } catch (_: Exception) { ChimeAudioSourceType.BUILT_IN }
                updated = updated.copy(hourlyChimeSourceType = st)
            }
            if (obj.has("hourlyCustomAudioId")) {
                updated = updated.copy(hourlyCustomAudioId = obj.optString("hourlyCustomAudioId").ifEmpty { null })
            }
            if (obj.has("hourlyCustomAudioName")) {
                updated = updated.copy(hourlyCustomAudioName = obj.optString("hourlyCustomAudioName").ifEmpty { null })
            }
            if (obj.has("hourlyCustomAudioPath")) {
                updated = updated.copy(hourlyCustomAudioPath = obj.optString("hourlyCustomAudioPath").ifEmpty { null })
            }
            if (obj.has("chimeSound")) {
                val sName = obj.getString("chimeSound")
                val parsed = try { ChimeSound.valueOf(sName) } catch (_: Exception) { null }
                if (parsed != null) {
                    updated = updated.copy(chimeSound = parsed)
                }
            }
            if (obj.has("chimeStartHour") && obj.has("chimeEndHour")) {
                updated = updated.copy(
                    chimeStartHour = obj.getInt("chimeStartHour"),
                    chimeEndHour = obj.getInt("chimeEndHour")
                )
            }
            if (obj.has("chimeVolume")) {
                val vol = obj.getDouble("chimeVolume").toFloat()
                updated = updated.copy(chimeVolume = vol)
                onSetDeviceVolume(vol)
            }
            if (obj.has("isNightMode")) {
                updated = updated.copy(isNightMode = obj.getBoolean("isNightMode"))
            }
            if (obj.has("isKioskLocked")) {
                updated = updated.copy(isKioskLocked = obj.getBoolean("isKioskLocked"))
            }
            if (obj.has("selectedPrefecture")) {
                updated = updated.copy(
                    selectedPrefecture = obj.getString("selectedPrefecture"),
                    selectedCityName = obj.optString("selectedCityName", obj.getString("selectedPrefecture"))
                )
            }
            if (obj.has("burnInProtection")) {
                updated = updated.copy(burnInProtection = obj.getBoolean("burnInProtection"))
            }
            if (obj.has("showWarnings")) {
                updated = updated.copy(showWarnings = obj.getBoolean("showWarnings"))
            }
            if (obj.has("eewEnabled")) {
                updated = updated.copy(eewEnabled = obj.getBoolean("eewEnabled"))
            }
            if (obj.has("eewSoundMode")) {
                updated = updated.copy(eewSoundMode = obj.getString("eewSoundMode"))
            }

            onUpdatePreferences(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update settings", e)
        }
    }

    private fun handleSendIr(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            val id = obj.optString("id")
            if (id.isNotEmpty()) {
                val button = irButtonsProvider().find { it.id == id }
                if (button != null) {
                    return onSendIrButton(button)
                }
            }
            val protocol = obj.optString("protocol", "NEC")
            val hex = obj.optString("hex", "")
            val bits = obj.optInt("bits", 32)
            val raw = obj.optString("raw", "")
            onSendCustomIr(protocol, hex, bits, raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send IR", e)
            false
        }
    }

    private fun handleSaveIrButton(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val daysArray = obj.optJSONArray("scheduleDays")
            val daysList = mutableListOf<Int>()
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    daysList.add(daysArray.getInt(i))
                }
            } else {
                daysList.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
            }
            val catStr = obj.optString("category", "LIGHTING")
            val cat = try { com.example.model.IrDeviceCategory.valueOf(catStr) } catch (_: Exception) { com.example.model.IrDeviceCategory.LIGHTING }
            val button = com.example.model.IrRemoteButton(
                id = obj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                name = obj.optString("name", "リモコンボタン"),
                category = cat,
                protocol = obj.optString("protocol", "NEC"),
                hexCode = obj.optString("hexCode", ""),
                bits = obj.optInt("bits", 32),
                rawCode = obj.optString("rawCode", ""),
                iconName = obj.optString("iconName", cat.defaultIcon),
                colorHex = obj.optString("colorHex", "#3B82F6"),
                triggerOnAlarm = obj.optBoolean("triggerOnAlarm", false),
                triggerOnNightMode = obj.optBoolean("triggerOnNightMode", false),
                triggerOnNightExit = obj.optBoolean("triggerOnNightExit", false),
                isScheduleEnabled = obj.optBoolean("isScheduleEnabled", false),
                scheduleHour = obj.optInt("scheduleHour", 7),
                scheduleMinute = obj.optInt("scheduleMinute", 0),
                scheduleDays = daysList
            )
            onSaveIrButton(button)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save IR button", e)
        }
    }

    private fun handleUpdateEspConfig(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val enabled = obj.optBoolean("enabled", true)
            val mode = obj.optString("mode", "BLE")
            val bleName = obj.optString("bleDeviceName", "ESP32C3-Sensor")
            val baud = obj.optInt("baudRate", 115200)
            val host = obj.optString("host", "192.168.1.100")
            val port = obj.optInt("port", 80)
            val interval = obj.optInt("intervalSeconds", 5)
            val tempOffset = obj.optDouble("tempOffset", 0.0).toFloat()
            val humOffset = obj.optDouble("humOffset", 0.0).toFloat()
            val pressOffset = obj.optDouble("pressOffset", 0.0).toFloat()
            val showOnClock = obj.optBoolean("showOnClock", true)

            onUpdateEspConfig(
                enabled, mode, bleName, baud, host, port, interval, tempOffset, humOffset, pressOffset, showOnClock
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update ESP config", e)
        }
    }

    private fun handleSaveChime(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val daysArray = obj.optJSONArray("daysOfWeek")
            val daysSet = mutableSetOf<Int>()
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    daysSet.add(daysArray.getInt(i))
                }
            } else {
                daysSet.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
            }

            val parsedSourceType = try { ChimeAudioSourceType.valueOf(obj.optString("sourceType")) } catch (_: Exception) { ChimeAudioSourceType.BUILT_IN }
            val isVideoSound = parsedSourceType == ChimeAudioSourceType.VIDEO_SOUND
            val chime = ScheduledChime(
                id = obj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                hour = obj.optInt("hour", 8),
                minute = obj.optInt("minute", 0),
                label = obj.optString("label", "チャイム"),
                isEnabled = obj.optBoolean("isEnabled", true),
                daysOfWeek = daysSet,
                sourceType = parsedSourceType,
                builtInSound = try { ChimeSound.valueOf(obj.optString("builtInSound")) } catch (_: Exception) { ChimeSound.WESTMINSTER },
                customAudioId = obj.optString("customAudioId").ifEmpty { null },
                customAudioName = obj.optString("customAudioName").ifEmpty { null },
                customAudioPath = obj.optString("customAudioPath").ifEmpty { null },
                volume = obj.optDouble("volume", 0.85).toFloat(),
                videoSourceType = try { ChimeVideoSourceType.valueOf(obj.optString("videoSourceType")) } catch (_: Exception) { ChimeVideoSourceType.NONE },
                customVideoId = obj.optString("customVideoId").ifEmpty { null },
                customVideoName = obj.optString("customVideoName").ifEmpty { null },
                customVideoPath = obj.optString("customVideoPath").ifEmpty { null },
                videoDurationSeconds = obj.optInt("videoDurationSeconds", 60),
                playVideoAudio = if (isVideoSound) true else obj.optBoolean("playVideoAudio", false),
                irSendEnabled = obj.optBoolean("irSendEnabled", false),
                irButtonId = obj.optString("irButtonId").ifEmpty { null },
                irButtonName = obj.optString("irButtonName").ifEmpty { null }
            )
            onAddOrUpdateChime(chime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chime", e)
        }
    }

    private fun buildStatusJson(): String {
        val p = stateProvider()
        val w = weatherProvider()
        val chimes = scheduledChimesProvider()
        val audios = customAudioProvider()
        val videos = customVideoProvider()
        val cam = cameraConfigProvider()

        val root = JSONObject()
        val prefsObj = JSONObject().apply {
            put("clockFace", p.clockFace.name)
            put("colorPalette", p.colorPalette.name)
            put("is24Hour", p.is24Hour)
            put("showSeconds", p.showSeconds)
            put("showWeather", p.showWeather)
            put("showWarnings", p.showWarnings)
            put("selectedPrefecture", p.selectedPrefecture)
            put("selectedCityName", p.selectedCityName)
            put("hourlyChimeEnabled", p.hourlyChimeEnabled)
            put("halfHourlyChimeEnabled", p.halfHourlyChimeEnabled)
            put("hourlyChimeSourceType", p.hourlyChimeSourceType.name)
            put("hourlyCustomAudioId", p.hourlyCustomAudioId ?: "")
            put("hourlyCustomAudioName", p.hourlyCustomAudioName ?: "")
            put("hourlyCustomAudioPath", p.hourlyCustomAudioPath ?: "")
            put("chimeSound", p.chimeSound.name)
            put("chimeStartHour", p.chimeStartHour)
            put("chimeEndHour", p.chimeEndHour)
            put("chimeVolume", p.chimeVolume)
            put("isKioskLocked", p.isKioskLocked)
            put("isNightMode", p.isNightMode)
            put("burnInProtection", p.burnInProtection)
            put("eewEnabled", p.eewEnabled)
            put("eewSoundMode", p.eewSoundMode)
        }
        root.put("preferences", prefsObj)

        val chimesArr = JSONArray()
        chimes.forEach { c ->
            val cObj = JSONObject().apply {
                put("id", c.id)
                put("hour", c.hour)
                put("minute", c.minute)
                put("label", c.label)
                put("isEnabled", c.isEnabled)
                put("formattedTime", c.formattedTime)
                put("repeatDaysText", c.repeatDaysText)
                put("sourceType", c.sourceType.name)
                put("builtInSound", c.builtInSound.name)
                put("soundDisplayName", c.soundDisplayName)
                put("customAudioId", c.customAudioId ?: "")
                put("customAudioName", c.customAudioName ?: "")
                put("customAudioPath", c.customAudioPath ?: "")
                put("volume", c.volume)
                put("videoSourceType", c.videoSourceType.name)
                put("videoDisplayName", c.videoDisplayName)
                put("customVideoId", c.customVideoId ?: "")
                put("customVideoName", c.customVideoName ?: "")
                put("customVideoPath", c.customVideoPath ?: "")
                put("videoDurationSeconds", c.videoDurationSeconds)
                put("playVideoAudio", c.playVideoAudio)
                put("irSendEnabled", c.irSendEnabled)
                put("irButtonId", c.irButtonId ?: "")
                put("irButtonName", c.irButtonName ?: "")
                put("remoteActionDisplayName", c.remoteActionDisplayName)
                val dArr = JSONArray()
                c.daysOfWeek.forEach { dArr.put(it) }
                put("daysOfWeek", dArr)
            }
            chimesArr.put(cObj)
        }
        root.put("scheduledChimes", chimesArr)

        val audiosArr = JSONArray()
        audios.forEach { a ->
            val aObj = JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("filePath", a.filePath)
            }
            audiosArr.put(aObj)
        }
        root.put("customAudios", audiosArr)

        val videosArr = JSONArray()
        videos.forEach { v ->
            val vObj = JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("filePath", v.filePath)
            }
            videosArr.put(vObj)
        }
        root.put("customVideos", videosArr)

        val camObj = JSONObject().apply {
            put("isEnabled", cam.isEnabled)
            put("port", cam.port)
            put("useFrontCamera", cam.useFrontCamera)
            put("targetFps", cam.targetFps)
            put("resolutionWidth", cam.resolutionWidth)
            put("resolutionHeight", cam.resolutionHeight)
            put("enableAudio", cam.enableAudio)
            put("audioGain", cam.audioGain)
            put("clientCount", activeClients.get())
            put("audioClientCount", activeAudioClients.get())
            put("audioLevelPercent", currentAudioLevel.get())
            put("hasFrame", latestFrame.get() != null)
        }
        root.put("camera", camObj)

        val weatherObj = JSONObject().apply {
            put("cityName", w.cityName)
            put("regionName", w.regionName)
            put("conditionText", w.conditionText)
            put("temperatureCelsius", w.temperatureCelsius)
            put("highTemp", w.highTemp)
            put("lowTemp", w.lowTemp)
        }
        root.put("weather", weatherObj)

        val report = CrashLogManager.getDiagnosticsReport()
        val diagObj = JSONObject().apply {
            put("isHealthy", report.isHealthy)
            put("uptimeFormatted", report.uptimeFormatted)
            put("totalStarts", report.totalStarts)
            put("crashRecoveryCount", report.crashRecoveryCount)
            put("abnormalTerminationCount", report.abnormalTerminationCount)
            put("lastCrashTime", report.lastCrashTime ?: "")
            put("lastCrashMessage", report.lastCrashMessage ?: "")
            put("lastAbnormalTerminationTime", report.lastAbnormalTerminationTime ?: "")
            put("lastAbnormalTerminationMessage", report.lastAbnormalTerminationMessage ?: "")
            put("usedMemoryMb", report.usedMemoryMb)
            put("maxMemoryMb", report.maxMemoryMb)
            put("freeMemoryMb", report.freeMemoryMb)
            put("osVersion", report.osVersion)
            put("deviceModel", report.deviceModel)
            put("crashLogCount", report.crashLogCount)
            put("totalLogCount", report.totalLogCount)

            val pInfo = try { context?.packageManager?.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
            put("appVersionName", pInfo?.versionName ?: "1.0.0")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION") pInfo?.versionCode?.toLong() ?: 1L
            }
            put("appVersionCode", vCode)
            put("packageName", context?.packageName ?: "com.example")
        }
        root.put("diagnostics", diagObj)

        // IR Buttons List
        val irArr = JSONArray()
        irButtonsProvider().forEach { b ->
            val bObj = JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("category", b.category.name)
                put("categoryDisplay", b.category.displayName)
                put("protocol", b.protocol)
                put("hexCode", b.hexCode)
                put("bits", b.bits)
                put("rawCode", b.rawCode)
                put("iconName", b.iconName)
                put("colorHex", b.colorHex)
                put("triggerOnAlarm", b.triggerOnAlarm)
                put("triggerOnNightMode", b.triggerOnNightMode)
                put("triggerOnNightExit", b.triggerOnNightExit)
                put("isScheduleEnabled", b.isScheduleEnabled)
                put("scheduleHour", b.scheduleHour)
                put("scheduleMinute", b.scheduleMinute)
                val daysArr = JSONArray()
                b.scheduleDays.forEach { daysArr.put(it) }
                put("scheduleDays", daysArr)
                put("repeatCount", b.repeatCount)
            }
            irArr.put(bObj)
        }
        root.put("irButtons", irArr)

        // IR Learn State
        val learnState = irLearnStateProvider()
        val learnObj = JSONObject().apply {
            put("isLearning", learnState.isLearning)
            put("statusMessage", learnState.statusMessage)
            if (learnState.lastLearnedSignal != null) {
                val s = learnState.lastLearnedSignal
                val sObj = JSONObject().apply {
                    put("protocol", s.protocol)
                    put("hexCode", s.hexCode)
                    put("bits", s.bits)
                    put("rawCode", s.rawCode)
                }
                put("lastLearnedSignal", sObj)
            }
        }
        root.put("irLearnState", learnObj)

        // ESP32 Sensor & Telemetry
        val esp = espSensorDataProvider()
        val espObj = JSONObject().apply {
            put("temperature", if (esp.temperature != null) esp.temperature else JSONObject.NULL)
            put("humidity", if (esp.humidity != null) esp.humidity else JSONObject.NULL)
            put("pressure", if (esp.pressure != null) esp.pressure else JSONObject.NULL)
            put("altitude", if (esp.altitude != null) esp.altitude else JSONObject.NULL)
            put("discomfortIndex", if (esp.discomfortIndex != null) esp.discomfortIndex else JSONObject.NULL)
            put("discomfortLabel", esp.discomfortLabel)
            put("heatstrokeRiskLabel", esp.heatstrokeRiskLabel)
            put("isConnected", esp.isConnected)
            put("isConnecting", esp.isConnecting)
            put("connectionType", esp.connectionType)
            put("rssi", if (esp.rssi != null) esp.rssi else JSONObject.NULL)
            put("ahtOk", esp.ahtOk)
            put("bmpOk", esp.bmpOk)
            put("enabled", p.espSensorEnabled)
            put("mode", p.espConnectionMode)
            put("bleDeviceName", p.espBleDeviceName)
            put("baudRate", p.espBaudRate)
            put("intervalSeconds", p.espSensorIntervalSeconds)
            put("host", p.espSensorHost)
            put("port", p.espSensorPort)
            put("tempOffset", p.espTempOffset)
            put("humOffset", p.espHumOffset)
            put("pressOffset", p.espPressOffset)
            put("showOnClock", p.showEspSensorOnClock)
            put("lastUpdatedEpochMs", esp.lastUpdatedEpochMs)
        }
        root.put("espSensor", espObj)

        return root.toString()
    }

    private fun getDashboardHtml(): String {
        val ip = getLocalIpAddress()
        return """
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>DeskClock リモート管理ダッシュボード</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0d0f14;
            --surface: #141721;
            --surface-subtle: #191d2b;
            --surface-hover: #202536;
            --primary: #3b82f6;
            --primary-hover: #2563eb;
            --primary-subtle: rgba(59, 130, 246, 0.12);
            --success: #10b981;
            --success-subtle: rgba(16, 185, 129, 0.12);
            --danger: #ef4444;
            --danger-subtle: rgba(239, 68, 68, 0.12);
            --warning: #f59e0b;
            --text-main: #f3f4f6;
            --text-muted: #9ca3af;
            --text-subtle: #6b7280;
            --border-color: #232838;
            --border-light: #2d3347;
            --radius-sm: 6px;
            --radius-md: 8px;
            --radius-lg: 12px;
            --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; -webkit-font-smoothing: antialiased; }
        body { background: var(--bg-color); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; }
        
        /* Tailscale-styled Header */
        header { 
            background: var(--surface); 
            border-bottom: 1px solid var(--border-color); 
            padding: 12px 24px; 
            display: flex; 
            align-items: center; 
            justify-content: space-between; 
            position: sticky; 
            top: 0; 
            z-index: 100; 
        }
        .logo-wrap { display: flex; align-items: center; gap: 12px; }
        .tailscale-grid-icon { width: 24px; height: 24px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; align-items: center; justify-items: center; }
        .tailscale-grid-icon span { width: 5px; height: 5px; background: #fff; border-radius: 50%; opacity: 0.9; }
        .tailscale-grid-icon span:nth-child(2), .tailscale-grid-icon span:nth-child(4), .tailscale-grid-icon span:nth-child(8) { background: var(--primary); opacity: 1; }
        .logo-text { font-size: 0.95rem; font-weight: 600; color: #fff; letter-spacing: -0.2px; }
        .node-tag { font-size: 0.75rem; color: var(--text-muted); background: var(--surface-subtle); padding: 2px 8px; border-radius: var(--radius-sm); border: 1px solid var(--border-color); font-family: var(--font-mono); }
        
        .header-meta { display: flex; align-items: center; gap: 16px; }
        .status-pill { display: flex; align-items: center; gap: 6px; font-size: 0.8rem; font-weight: 500; color: var(--success); background: var(--success-subtle); padding: 4px 10px; border-radius: 20px; border: 1px solid rgba(16, 185, 129, 0.2); }
        .status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
        .clock-display { font-family: var(--font-mono); font-size: 0.88rem; color: var(--text-muted); }

        /* Tailscale Nav Tabs */
        .nav-tabs-container { background: var(--surface); border-bottom: 1px solid var(--border-color); padding: 0 24px; }
        .nav-tabs { display: flex; gap: 24px; overflow-x: auto; scrollbar-width: none; }
        .nav-tabs::-webkit-scrollbar { display: none; }
        .tab-btn { 
            background: transparent; 
            border: none; 
            border-bottom: 2px solid transparent; 
            color: var(--text-muted); 
            font-size: 0.88rem; 
            font-weight: 500; 
            padding: 12px 2px; 
            cursor: pointer; 
            transition: all 0.15s ease; 
            white-space: nowrap; 
            display: flex; 
            align-items: center; 
            gap: 8px; 
        }
        .tab-btn svg { width: 16px; height: 16px; stroke-width: 2; opacity: 0.7; }
        .tab-btn:hover { color: var(--text-main); }
        .tab-btn:hover svg { opacity: 1; }
        .tab-btn.active { 
            color: #fff; 
            border-bottom-color: var(--primary); 
            font-weight: 600; 
        }
        .tab-btn.active svg { opacity: 1; stroke: var(--primary); }

        main { flex: 1; padding: 24px; max-width: 1160px; margin: 0 auto; width: 100%; }
        .tab-content { display: none; }
        .tab-content.active { display: block; animation: fadeIn 0.15s ease; }
        @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

        /* Tailscale-styled Cards */
        .card { 
            background: var(--surface); 
            border: 1px solid var(--border-color); 
            border-radius: var(--radius-lg); 
            padding: 20px 24px; 
            margin-bottom: 20px; 
        }
        .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
        .card-title { font-size: 0.95rem; font-weight: 600; color: #fff; display: flex; align-items: center; gap: 8px; }
        .card-title svg { width: 18px; height: 18px; stroke: var(--text-muted); }
        .card-description { font-size: 0.82rem; color: var(--text-muted); margin-top: 2px; }

        .grid-2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 20px; }

        /* Video Container */
        .video-box { 
            position: relative; 
            background: #000; 
            border-radius: var(--radius-md); 
            overflow: hidden; 
            aspect-ratio: 16/10; 
            max-width: 680px; 
            margin: 0 auto; 
            border: 1px solid var(--border-color); 
        }
        .video-box img { width: 100%; height: 100%; object-fit: contain; }
        .video-overlay { 
            position: absolute; 
            top: 10px; 
            left: 10px; 
            right: 10px; 
            display: flex; 
            justify-content: space-between; 
            align-items: center; 
            pointer-events: none; 
        }
        .video-badge { 
            background: rgba(13, 15, 20, 0.85); 
            backdrop-filter: blur(4px);
            padding: 4px 8px; 
            border-radius: var(--radius-sm); 
            font-size: 0.75rem; 
            font-family: var(--font-mono); 
            color: var(--text-main); 
            border: 1px solid var(--border-color);
            display: flex; 
            align-items: center; 
            gap: 6px;
        }
        .rec-indicator { width: 6px; height: 6px; border-radius: 50%; background: var(--danger); }

        /* Buttons */
        .btn { 
            background: var(--primary); 
            color: #fff; 
            border: 1px solid transparent; 
            padding: 8px 14px; 
            border-radius: var(--radius-sm); 
            font-weight: 500; 
            font-size: 0.84rem; 
            cursor: pointer; 
            transition: all 0.12s ease; 
            display: inline-flex; 
            align-items: center; 
            gap: 6px; 
            line-height: 1.2;
        }
        .btn svg { width: 14px; height: 14px; }
        .btn:hover { background: var(--primary-hover); }
        .btn:active { transform: translateY(1px); }
        
        .btn-outline { 
            background: var(--surface-subtle); 
            color: var(--text-main); 
            border: 1px solid var(--border-color); 
        }
        .btn-outline:hover { background: var(--surface-hover); border-color: var(--border-light); }
        
        .btn-success { background: var(--success); color: #fff; }
        .btn-success:hover { background: #059669; }

        .btn-danger { background: transparent; color: var(--danger); border: 1px solid var(--border-color); }
        .btn-danger:hover { background: var(--danger-subtle); border-color: var(--danger); }
        
        .btn-sm { padding: 5px 10px; font-size: 0.78rem; }

        /* Forms */
        .form-group { margin-bottom: 16px; }
        .form-label { display: block; font-size: 0.8rem; font-weight: 500; color: var(--text-muted); margin-bottom: 6px; }
        .form-control, select, input[type="text"], input[type="number"] {
            width: 100%; 
            background: var(--surface-subtle); 
            border: 1px solid var(--border-color); 
            color: var(--text-main); 
            padding: 8px 12px; 
            border-radius: var(--radius-sm); 
            font-size: 0.86rem; 
            outline: none;
            transition: border-color 0.15s ease;
        }
        .form-control:focus, select:focus, input[type="text"]:focus, input[type="number"]:focus { 
            border-color: var(--primary); 
        }
        
        /* Clean Switch Row */
        .switch-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--border-color); }
        .switch-row:last-child { border-bottom: none; }
        .switch-title { font-weight: 500; font-size: 0.88rem; color: var(--text-main); }
        .switch-desc { font-size: 0.78rem; color: var(--text-muted); margin-top: 2px; }

        .switch { position: relative; display: inline-block; width: 38px; height: 22px; flex-shrink: 0; }
        .switch input { opacity: 0; width: 0; height: 0; }
        .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: var(--surface-hover); transition: .2s ease; border-radius: 22px; border: 1px solid var(--border-color); }
        .slider:before { position: absolute; content: ""; height: 16px; width: 16px; left: 2px; bottom: 2px; background-color: #fff; transition: .2s ease; border-radius: 50%; }
        input:checked + .slider { background-color: var(--primary); border-color: var(--primary); }
        input:checked + .slider:before { transform: translateX(16px); }

        /* Item Row (Tailscale Table / Device Card Style) */
        .item-card { 
            background: var(--surface-subtle); 
            border: 1px solid var(--border-color); 
            border-radius: var(--radius-md); 
            padding: 12px 16px; 
            margin-bottom: 8px; 
            display: flex; 
            justify-content: space-between; 
            align-items: center; 
            gap: 12px; 
            flex-wrap: wrap; 
        }
        .item-card:hover { border-color: var(--border-light); background: var(--surface-hover); }
        .time-badge { font-size: 1.15rem; font-weight: 700; font-family: var(--font-mono); color: #fff; min-width: 70px; }
        .tag { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: var(--radius-sm); font-size: 0.74rem; font-weight: 500; background: var(--surface-hover); color: var(--text-muted); border: 1px solid var(--border-color); }
        .tag-video { background: var(--primary-subtle); color: var(--primary); border-color: rgba(59, 130, 246, 0.25); }

        /* Upload Area */
        .upload-dropzone { 
            border: 1px dashed var(--border-light); 
            border-radius: var(--radius-md); 
            padding: 24px 16px; 
            text-align: center; 
            background: var(--surface-subtle); 
            cursor: pointer; 
            transition: all 0.15s ease; 
        }
        .upload-dropzone:hover, .upload-dropzone.dragover { border-color: var(--primary); background: var(--surface-hover); }
        .upload-dropzone svg { width: 32px; height: 32px; stroke: var(--text-muted); margin-bottom: 8px; }

        /* Modal */
        .modal-bg { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.7); backdrop-filter: blur(4px); display: none; align-items: center; justify-content: center; z-index: 1000; padding: 16px; }
        .modal-bg.active { display: flex; }
        .modal-card { background: var(--surface); border: 1px solid var(--border-color); border-radius: var(--radius-lg); width: 100%; max-width: 540px; max-height: 90vh; overflow-y: auto; padding: 24px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }

        .quick-day-btn { background: var(--surface-subtle); border: 1px solid var(--border-color); color: var(--text-muted); padding: 3px 8px; border-radius: var(--radius-sm); font-size: 0.74rem; cursor: pointer; }
        .quick-day-btn:hover { color: #fff; background: var(--surface-hover); }

        .toast { position: fixed; bottom: 24px; right: 24px; background: var(--surface); border: 1px solid var(--border-light); color: var(--text-main); font-weight: 500; font-size: 0.84rem; padding: 10px 18px; border-radius: var(--radius-md); z-index: 2000; box-shadow: 0 10px 25px rgba(0,0,0,0.4); transform: translateY(80px); opacity: 0; transition: all 0.2s ease; display: flex; align-items: center; gap: 8px; }
        .toast.show { transform: translateY(0); opacity: 1; }
        
        input[type="range"] {
            -webkit-appearance: none;
            width: 100%;
            height: 4px;
            background: var(--border-color);
            border-radius: 2px;
            outline: none;
            margin: 8px 0;
        }
        input[type="range"]::-webkit-slider-thumb {
            -webkit-appearance: none;
            width: 14px;
            height: 14px;
            border-radius: 50%;
            background: var(--primary);
            cursor: pointer;
        }

        /* Upload Progress Modal & Animated Bar */
        .upload-modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.78);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 10000;
            padding: 16px;
        }
        .upload-modal-overlay.active { display: flex; animation: fadeIn 0.15s ease; }
        .upload-modal-card {
            background: var(--surface);
            border: 1px solid var(--border-light);
            border-radius: var(--radius-lg);
            width: 100%;
            max-width: 460px;
            padding: 24px;
            box-shadow: 0 24px 48px rgba(0,0,0,0.6);
        }
        .progress-track {
            width: 100%;
            height: 10px;
            background: var(--surface-subtle);
            border-radius: 5px;
            overflow: hidden;
            border: 1px solid var(--border-color);
            margin: 14px 0 8px 0;
        }
        .progress-fill {
            height: 100%;
            width: 0%;
            background: linear-gradient(90deg, #00E5FF, #2979FF);
            border-radius: 5px;
            transition: width 0.12s ease-out;
            box-shadow: 0 0 10px rgba(0, 229, 255, 0.4);
        }
        .progress-fill.processing {
            background: linear-gradient(90deg, #f59e0b, #ef4444);
            animation: pulse-stripes 1.2s infinite ease-in-out;
        }
        .progress-fill.success {
            background: linear-gradient(90deg, #10b981, #059669);
            box-shadow: 0 0 10px rgba(16, 185, 129, 0.5);
        }
        .progress-fill.error {
            background: linear-gradient(90deg, #ef4444, #b91c1c);
        }
        @keyframes pulse-stripes {
            0% { opacity: 0.8; }
            50% { opacity: 1; filter: brightness(1.2); }
            100% { opacity: 0.8; }
        }

        ${WebDashboardIrEsp.getIrEspCss()}
    </style>
</head>
<body>
    <header>
        <div class="logo-wrap">
            <div class="tailscale-grid-icon">
                <span></span><span></span><span></span>
                <span></span><span></span><span></span>
                <span></span><span></span><span></span>
            </div>
            <span class="logo-text">DeskClock Console</span>
            <span class="node-tag">$ip:$port</span>
        </div>
        <div class="header-meta">
            <div class="status-pill">
                <span class="status-dot"></span>
                <span>Connected</span>
            </div>
            <div class="clock-display" id="headerClock">--:--:--</div>
            <button class="btn btn-outline btn-sm" onclick="fetchStatus()">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg>
                Sync
            </button>
        </div>
    </header>

    <div class="nav-tabs-container">
        <div class="nav-tabs">
            <button class="tab-btn active" onclick="switchTab('camera', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M23 7l-7 5 7 5V7z"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
                Camera & Audio
            </button>
            <button class="tab-btn" onclick="switchTab('chimes', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
                Chimes & Schedule
            </button>
            <button class="tab-btn" onclick="switchTab('media', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
                Media Files
            </button>
            <button class="tab-btn" onclick="switchTab('clock', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                Display & Theme
            </button>
            <button class="tab-btn" onclick="switchTab('weather', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/></svg>
                Location & Weather
            </button>
            <button class="tab-btn" onclick="switchTab('logs', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
                Diagnostics & Logs
            </button>
            ${WebDashboardIrEsp.getTabNavButtonsHtml()}
            ${WebDashboardUpdates.getUpdatesNavButtonHtml()}
        </div>
    </div>

    <main>
        <!-- TAB 1: CAMERA & AUDIO -->
        <div id="tab-camera" class="tab-content active">
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M23 7l-7 5 7 5V7z"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
                            Live Video Stream
                        </div>
                        <div class="card-description">Real-time low latency MJPEG stream from the device camera</div>
                    </div>
                    <div style="display:flex; gap: 8px; flex-wrap: wrap;">
                        <button class="btn btn-outline btn-sm" onclick="toggleLens()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>
                            Switch Lens
                        </button>
                        <a href="/snapshot" target="_blank" class="btn btn-outline btn-sm" style="text-decoration:none;">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
                            Snapshot
                        </a>
                        <button class="btn btn-outline btn-sm" onclick="reloadStream()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
                            Reload
                        </button>
                    </div>
                </div>
                <div class="video-box">
                    <img id="cameraStreamImg" src="/video" alt="Camera Stream" onerror="setTimeout(() => this.src='/video?' + Date.now(), 2000);">
                    <div class="video-overlay">
                        <div class="video-badge" id="cameraStatsTag">
                            <div class="rec-indicator"></div>
                            <span>Connecting...</span>
                        </div>
                        <div class="video-badge" id="streamUrlTag">
                            <span>http://${ip}:$port/video</span>
                        </div>
                    </div>
                </div>
                <div style="margin-top: 14px; padding: 10px 14px; background: var(--surface-subtle); border-radius: var(--radius-sm); border: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;">
                    <div style="font-size: 0.8rem; color: var(--text-muted); display: flex; align-items: center; gap: 8px;">
                        <span>Direct Stream URL:</span>
                        <code style="background: var(--surface); color: var(--primary); padding: 2px 6px; border-radius: 4px; font-family: var(--font-mono); font-size: 0.8rem;">http://${ip}:$port/video</code>
                    </div>
                    <button class="btn btn-outline btn-sm" onclick="navigator.clipboard.writeText('http://${ip}:$port/video'); showToast('URL copied to clipboard');">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                        Copy URL
                    </button>
                </div>

                <!-- Live Camera Stream Controls -->
                <div style="margin-top: 10px; padding: 12px 14px; background: var(--surface-subtle); border-radius: var(--radius-sm); border: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;">
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span style="font-size: 0.8rem; font-weight:600; color: var(--text-main);">FPS:</span>
                        <select id="selCameraFps" onchange="onCameraSettingChange()" style="padding:4px 8px; border-radius:4px; font-size:0.8rem; background:var(--surface); color:var(--text-main); border:1px solid var(--border-color);">
                            <option value="5">5 FPS</option>
                            <option value="10" selected>10 FPS</option>
                            <option value="15">15 FPS</option>
                            <option value="20">20 FPS</option>
                        </select>
                    </div>
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span style="font-size: 0.8rem; font-weight:600; color: var(--text-main);">Resolution:</span>
                        <select id="selCameraRes" onchange="onCameraSettingChange()" style="padding:4px 8px; border-radius:4px; font-size:0.8rem; background:var(--surface); color:var(--text-main); border:1px solid var(--border-color);">
                            <option value="320x240">QVGA (320x240)</option>
                            <option value="640x480" selected>VGA (640x480)</option>
                            <option value="1280x720">HD (1280x720)</option>
                        </select>
                    </div>
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span style="font-size: 0.8rem; font-weight:600; color: var(--text-main);">Lens:</span>
                        <select id="selCameraLens" onchange="onCameraSettingChange()" style="padding:4px 8px; border-radius:4px; font-size:0.8rem; background:var(--surface); color:var(--text-main); border:1px solid var(--border-color);">
                            <option value="front" selected>Front (In-Camera)</option>
                            <option value="back">Back (Rear)</option>
                        </select>
                    </div>
                </div>
            </div>

            <!-- Microphone Audio Monitoring Card -->
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>
                            Live Audio Monitor
                        </div>
                        <div class="card-description">Microphone audio input level and live streaming playback</div>
                    </div>
                    <div style="display: flex; gap: 8px; align-items: center;">
                        <span id="audioLiveTag" class="status-pill" style="display:none;">
                            <span class="status-dot"></span> Streaming
                        </span>
                        <button id="btnToggleAudio" class="btn btn-sm" onclick="toggleAudioStream()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
                            Listen Live
                        </button>
                    </div>
                </div>
                
                <div class="grid-2">
                    <div style="background: var(--surface-subtle); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <!-- VU Meter -->
                        <div style="margin-bottom: 14px;">
                            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
                                <span style="font-size: 0.78rem; font-weight: 500; color: var(--text-muted);">Input Level (VU)</span>
                                <span id="audioLevelText" style="font-family: var(--font-mono); font-weight: 600; font-size: 0.85rem; color: var(--primary);">0%</span>
                            </div>
                            <div style="height: 8px; background: var(--surface); border-radius: 4px; overflow: hidden; border: 1px solid var(--border-color);">
                                <div id="audioVuBar" style="height: 100%; width: 0%; background: linear-gradient(90deg, #10b981 0%, #3b82f6 65%, #f59e0b 85%, #ef4444 100%); transition: width 0.1s ease-out; border-radius: 4px;"></div>
                            </div>
                        </div>

                        <!-- Live Audio Element -->
                        <audio id="liveAudioPlayer" preload="none"></audio>

                        <!-- Output Volume Slider -->
                        <div class="form-group" style="margin-bottom: 0;">
                            <div style="display:flex; justify-content:space-between;">
                                <label class="form-label" style="margin-bottom:2px;">Monitor Volume</label>
                                <span id="localVolLabel" style="font-family: var(--font-mono); font-size:0.78rem; color:var(--primary); font-weight:600;">100%</span>
                            </div>
                            <input type="range" id="rngLocalVolume" min="0" max="1" step="0.05" value="1" oninput="onLocalVolumeChange(this.value)">
                        </div>
                    </div>

                    <div style="background: var(--surface-subtle); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--border-color); display: flex; flex-direction: column; justify-content: space-between;">
                        <!-- Microphone Gain Slider -->
                        <div class="form-group" style="margin-bottom: 10px;">
                            <div style="display:flex; justify-content:space-between;">
                                <label class="form-label" style="margin-bottom:2px;">Microphone Gain</label>
                                <span id="micGainLabel" style="font-family: var(--font-mono); font-size:0.78rem; color:var(--primary); font-weight:600;">1.0x</span>
                            </div>
                            <input type="range" id="rngMicGain" min="0.5" max="3.0" step="0.1" value="1.0" onchange="saveMicGain(this.value)" oninput="document.getElementById('micGainLabel').innerText = Number(this.value).toFixed(1) + 'x'">
                        </div>

                        <!-- Audio Stream URL -->
                        <div style="padding-top: 8px; border-top: 1px solid var(--border-color);">
                            <div style="font-size: 0.76rem; color: var(--text-muted); margin-bottom: 4px;">Audio Stream (WAV):</div>
                            <div style="display:flex; gap:6px;">
                                <code style="background: var(--surface); padding: 5px 8px; border-radius: var(--radius-sm); color: var(--text-main); flex: 1; overflow-x: auto; font-family: var(--font-mono); font-size: 0.78rem; border: 1px solid var(--border-color);" id="audioUrlCode">http://${ip}:$port/audio.wav</code>
                                <button class="btn btn-outline btn-sm" onclick="copyAudioUrl()">Copy</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 2: CHIMES & ALARMS -->
        <div id="tab-chimes" class="tab-content">
            <div class="grid-2">
                <!-- Hourly Chime Settings -->
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
                                Hourly Time Signal
                            </div>
                            <div class="card-description">Automated chime broadcast at scheduled intervals</div>
                        </div>
                    </div>
                    
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">Hourly Chime</div>
                            <div class="switch-desc">Play signal at every :00 mark</div>
                        </div>
                        <label class="switch">
                            <input type="checkbox" id="chkHourlyChime" onchange="saveGeneralSettings()">
                            <span class="slider"></span>
                        </label>
                    </div>

                    <div class="switch-row">
                        <div>
                            <div class="switch-title">Half-hourly Chime</div>
                            <div class="switch-desc">Play soft single bell at every :30 mark</div>
                        </div>
                        <label class="switch">
                            <input type="checkbox" id="chkHalfHourlyChime" onchange="saveGeneralSettings()">
                            <span class="slider"></span>
                        </label>
                    </div>

                    <div class="form-group" style="margin-top: 14px;">
                        <label class="form-label">Sound Tone</label>
                        <div style="display:flex; gap: 8px;">
                            <select id="selChimeSound" onchange="saveGeneralSettings()" style="flex:1;">
                                <optgroup label="Built-in Chimes">
                                    <option value="WESTMINSTER">Westminster Quarters (Classic)</option>
                                    <option value="TUBULAR_BELLS">Tubular Bells (Deep Gong)</option>
                                    <option value="CRYSTAL_BELL">Crystal Bell (Clear High)</option>
                                    <option value="BIRD_CHIRP">Bird Chirp (Morning Ambience)</option>
                                    <option value="SOFT_MARIMBA">Marimba (Warm Acoustic)</option>
                                    <option value="ZEN_BELL">Zen Temple Bell</option>
                                    <option value="GRANDFATHER">Antique Grandfather Clock</option>
                                    <option value="DIGITAL_SIGNAL">Digital Time Signal (Beep-Pips)</option>
                                </optgroup>
                                <optgroup label="Custom Uploaded Audio" id="selHourlyCustomAudios">
                                </optgroup>
                            </select>
                            <button class="btn btn-outline btn-sm" onclick="testSound()">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                                Test
                            </button>
                        </div>
                    </div>

                    <div style="display:grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                        <div class="form-group">
                            <label class="form-label">Active From (Hour)</label>
                            <input type="number" id="numChimeStart" min="0" max="23" value="8" onchange="saveGeneralSettings()">
                        </div>
                        <div class="form-group">
                            <label class="form-label">Active Until (Hour)</label>
                            <input type="number" id="numChimeEnd" min="0" max="23" value="22" onchange="saveGeneralSettings()">
                        </div>
                    </div>

                    <div class="form-group">
                        <label class="form-label">Chime Playback Volume (<span id="volLabel">75%</span>)</label>
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:6px;">Applied only when chime sounds; restores device volume automatically.</div>
                        <input type="range" id="rngVolume" min="0.05" max="1.0" step="0.05" style="width:100%" oninput="onVolumeSliderChange(this.value)" onchange="saveGeneralSettings()">
                    </div>
                </div>

                <!-- Priority Rule Banner -->
                <div class="card" style="border-left: 3px solid var(--primary);">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                                Schedule Priority Rules
                            </div>
                        </div>
                    </div>
                    <div style="font-size: 0.84rem; color: var(--text-muted); line-height: 1.6;">
                        <p style="margin-bottom: 8px; color: var(--text-main); font-weight: 500;">Custom Schedule Override:</p>
                        <p>When a custom scheduled chime is set at the same time as the hourly chime, the custom schedule with its assigned audio and background visual animation takes full priority.</p>
                        <p style="margin-top: 10px; font-size: 0.8rem;">Upload audio (MP3/WAV) or video (MP4) in the Media Files tab to assign them to custom scheduled chimes.</p>
                    </div>
                </div>
            </div>

            <!-- Scheduled Chimes List -->
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                            Scheduled Chimes & Routines
                        </div>
                        <div class="card-description">Custom programmed alarms, reminders, and daily routines</div>
                    </div>
                    <button class="btn btn-sm" onclick="openAddChimeModal()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                        Add Schedule
                    </button>
                </div>
                <div id="scheduledChimesList">Loading schedules...</div>
            </div>
        </div>

        <!-- TAB 3: MEDIA FILES -->
        <div id="tab-media" class="tab-content">
            <div class="grid-2">
                <!-- Audio Upload Card -->
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
                                Audio Library
                            </div>
                            <div class="card-description">Custom sounds (MP3, WAV, AAC, M4A, OGG)</div>
                        </div>
                    </div>
                    <div class="upload-dropzone" id="audioDropzone" onclick="document.getElementById('audioFileInput').click()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                        <p style="font-weight: 600; font-size: 0.88rem; color: #fff;">Click or drag & drop to upload audio</p>
                        <p style="font-size: 0.76rem; color: var(--text-muted); margin-top: 4px;">Supports MP3, WAV, AAC, M4A, OGG</p>
                        <input type="file" id="audioFileInput" accept="audio/*" style="display:none" onchange="uploadAudio(this.files[0])">
                    </div>
                    <!-- Inline Audio Upload Progress -->
                    <div id="audioUploadInline" style="display:none; margin-top:12px; background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:12px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; font-size:0.8rem;">
                            <span id="audioUploadInlineName" style="color:#fff; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:70%;">track.mp3</span>
                            <span id="audioUploadInlinePercent" style="color:var(--primary); font-family:var(--font-mono); font-weight:700;">0%</span>
                        </div>
                        <div class="progress-track" style="margin:8px 0 6px 0; height:6px;">
                            <div id="audioUploadInlineBar" class="progress-fill" style="width:0%;"></div>
                        </div>
                        <div style="display:flex; justify-content:space-between; font-size:0.72rem; color:var(--text-muted);">
                            <span id="audioUploadInlineBytes">0 / 0 MB</span>
                            <span id="audioUploadInlineSpeed">-- MB/s</span>
                        </div>
                    </div>
                    <div style="margin-top: 16px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 8px;">
                            <div style="font-size: 0.82rem; color: var(--text-muted); font-weight:500;">Uploaded Audio (<span id="audioCount">0</span>)</div>
                        </div>
                        <div id="customAudioList"></div>
                    </div>
                </div>

                <!-- Video Upload Card -->
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/></svg>
                                Background Visuals
                            </div>
                            <div class="card-description">Video animations for chime triggers (MP4, WebM)</div>
                        </div>
                    </div>
                    <div class="upload-dropzone" id="videoDropzone" onclick="document.getElementById('videoFileInput').click()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                        <p style="font-weight: 600; font-size: 0.88rem; color: #fff;">Click or drag & drop to upload video</p>
                        <p style="font-size: 0.76rem; color: var(--text-muted); margin-top: 4px;">Supports MP4, WebM (H.264)</p>
                        <input type="file" id="videoFileInput" accept="video/*" style="display:none" onchange="uploadVideo(this.files[0])">
                    </div>
                    <!-- Inline Video Upload Progress -->
                    <div id="videoUploadInline" style="display:none; margin-top:12px; background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:12px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; font-size:0.8rem;">
                            <span id="videoUploadInlineName" style="color:#fff; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:70%;">video.mp4</span>
                            <span id="videoUploadInlinePercent" style="color:var(--primary); font-family:var(--font-mono); font-weight:700;">0%</span>
                        </div>
                        <div class="progress-track" style="margin:8px 0 6px 0; height:6px;">
                            <div id="videoUploadInlineBar" class="progress-fill" style="width:0%;"></div>
                        </div>
                        <div style="display:flex; justify-content:space-between; font-size:0.72rem; color:var(--text-muted);">
                            <span id="videoUploadInlineBytes">0 / 0 MB</span>
                            <span id="videoUploadInlineSpeed">-- MB/s</span>
                        </div>
                    </div>
                    <div style="margin-top: 16px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 8px;">
                            <div style="font-size: 0.82rem; color: var(--text-muted); font-weight:500;">Uploaded Videos (<span id="videoCount">0</span>)</div>
                        </div>
                        <div id="customVideoList"></div>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 4: DISPLAY & THEME -->
        <div id="tab-clock" class="tab-content">
            <div class="grid-2">
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                                Clock Face & Typography
                            </div>
                            <div class="card-description">Display layout and aesthetic styling</div>
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Clock Face Layout</label>
                        <select id="selClockFace" onchange="saveGeneralSettings()">
                            <option value="SEVEN_SEGMENT">7-Segment LED (Hardware Accurate Display)</option>
                            <option value="ANALOG_SWISS">Swiss Minimal (Bauhaus Analog Hands)</option>
                            <option value="TYPOGRAPHIC">Typographic (High-Contrast Geometric Font)</option>
                            <option value="FLIP_CLOCK">Split-Flap (Mechanical Airport Flap)</option>
                            <option value="MATRIX_DOTS">Luminous Matrix (Dot Grid)</option>
                            <option value="NEON_CYBERPUNK">Cyber Glow Neon (Multilayer Glow Tubes)</option>
                            <option value="NIXIE_TUBE">Vintage Nixie (Warm Vacuum Tubes)</option>
                            <option value="MINIMAL_BOLD">Studio Bold (Ultra-Heavy Modern Numerals)</option>
                            <option value="ANALOG_STATION">Station Railroad (Classic Railway Clock)</option>
                            <option value="DIGITAL_STATION_BLUE">Station 7-Seg Blue (Calendar Matrix + Gauge)</option>
                            <option value="DIGITAL_STATION_MATRIX">Station Matrix Cyan (Full Matrix Telemetry)</option>
                            <option value="RETRO_LCD_GOLD">Retro LCD Gold (Classic Ghost Segment LCD)</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label class="form-label">Color Palette</label>
                        <select id="selColorPalette" onchange="saveGeneralSettings()">
                            <option value="ICE_WHITE">Studio White (Pure White, High Contrast)</option>
                            <option value="AMBER_GLOW">Amber Glow (Warm Amber LED)</option>
                            <option value="CYBER_CYAN">Cyber Neon (Vibrant Cyan)</option>
                            <option value="MINT_VFD">VFD Mint (Fluorescent Green)</option>
                            <option value="CRIMSON_NIGHT">Crimson (Dark Room Night Vision Red)</option>
                        </select>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                                Display Preferences
                            </div>
                            <div class="card-description">Screen protection and visibility controls</div>
                        </div>
                    </div>
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">24-Hour Format</div>
                            <div class="switch-desc">Toggle between 24H and 12H (AM/PM)</div>
                        </div>
                        <label class="switch"><input type="checkbox" id="chk24Hour" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                    </div>
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">Show Seconds</div>
                            <div class="switch-desc">Display active second counter</div>
                        </div>
                        <label class="switch"><input type="checkbox" id="chkShowSeconds" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                    </div>
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">Night Vision Dimming</div>
                            <div class="switch-desc">Ultra-low brightness dark canvas</div>
                        </div>
                        <label class="switch"><input type="checkbox" id="chkNightMode" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                    </div>
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">Kiosk Lock</div>
                            <div class="switch-desc">Hide on-screen setup gear button</div>
                        </div>
                        <label class="switch"><input type="checkbox" id="chkKioskLock" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                    </div>
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">Burn-in Protection (画面焼き付き防止)</div>
                            <div class="switch-desc">有機ELや液晶の焼き付きを防ぐ微細ピクセルシフト</div>
                        </div>
                        <label class="switch"><input type="checkbox" id="chkBurnIn" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                    </div>
                    <div class="switch-row">
                        <div>
                            <div class="switch-title">緊急地震速報 (EEW) 警報受信</div>
                            <div class="switch-desc">気象庁EEW受信時に画面フラッシュ＆警報音を発報</div>
                        </div>
                        <label class="switch"><input type="checkbox" id="chkEew" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                    </div>
                    <div class="form-group" style="margin-top: 10px;">
                        <label class="form-label">EEW 警報音声モード</label>
                        <select id="selEewSound" onchange="saveGeneralSettings()">
                            <option value="VOICE">合成音声ガイダンス + チャイム (推奨)</option>
                            <option value="CHIME">チャイム音のみ</option>
                            <option value="SILENT">消音 (画面フラッシュのみ)</option>
                        </select>
                    </div>
                </div>
            </div>
        </div>

        <!-- TAB 5: LOCATION & WEATHER -->
        <div id="tab-weather" class="tab-content">
            <div class="card" style="max-width: 540px; margin: 0 auto;">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/></svg>
                            Location & Weather Service
                        </div>
                        <div class="card-description">Forecast provider and region selection</div>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Prefecture / Region</label>
                    <select id="selPrefecture" onchange="saveGeneralSettings()">
                        <option value="東京都">東京都 (Tokyo)</option>
                        <option value="神奈川県">神奈川県 (Kanagawa)</option>
                        <option value="大阪府">大阪府 (Osaka)</option>
                        <option value="愛知県">愛知県 (Aichi)</option>
                        <option value="北海道">北海道 (Hokkaido)</option>
                        <option value="福岡県">福岡県 (Fukuoka)</option>
                        <option value="京都府">京都府 (Kyoto)</option>
                        <option value="兵庫県">兵庫県 (Hyogo)</option>
                        <option value="埼玉県">埼玉県 (Saitama)</option>
                        <option value="千葉県">千葉県 (Chiba)</option>
                        <option value="広島県">広島県 (Hiroshima)</option>
                        <option value="宮城県">宮城県 (Miyagi)</option>
                        <option value="静岡県">静岡県 (Shizuoka)</option>
                        <option value="新潟県">新潟県 (Niigata)</option>
                        <option value="沖縄県">沖縄県 (Okinawa)</option>
                    </select>
                </div>
                <div class="switch-row">
                    <div>
                        <div class="switch-title">Show Weather on Display</div>
                        <div class="switch-desc">Overlay current conditions and forecast temperature</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkShowWeather" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                </div>
                <div class="switch-row">
                    <div>
                        <div class="switch-title">気象警報・注意報バナー表示</div>
                        <div class="switch-desc">大雨・洪水・暴風などの気象特別警報・注意報を時計画面に表示</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkShowWarnings" onchange="saveGeneralSettings()"><span class="slider"></span></label>
                </div>
            </div>
        </div>

        <!-- TAB 6: DIAGNOSTICS & SYSTEM LOGS -->
        <div id="tab-logs" class="tab-content">
            <!-- System Health & Stability Card -->
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                            System Stability & Auto-Recovery Watchdog
                        </div>
                        <div class="card-description">Fatal crash interception, automated process restart, and real-time device telemetry</div>
                    </div>
                    <div style="display:flex; gap: 8px; flex-wrap: wrap;">
                        <button class="btn btn-outline btn-sm" onclick="fetchLogs()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg>
                            Refresh
                        </button>
                        <button class="btn btn-outline btn-sm" onclick="downloadLogs()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                            Download Full Log (.txt)
                        </button>
                    </div>
                </div>

                <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 16px;">
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">Watchdog Status</div>
                        <div id="diagStatusBadge" style="display:inline-flex; align-items:center; gap:6px; font-size:0.82rem; font-weight:600; color:var(--success); background:var(--success-subtle); padding:4px 10px; border-radius:12px; border:1px solid rgba(16,185,129,0.3);">
                            <span style="width:6px; height:6px; border-radius:50%; background:var(--success);"></span> Active (Keep-Alive ON)
                        </div>
                    </div>
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">Process Uptime</div>
                        <div id="diagUptime" style="font-family:var(--font-mono); font-size:1.1rem; font-weight:700; color:#fff;">--:--:--</div>
                    </div>
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">App Starts</div>
                        <div id="diagTotalStarts" style="font-family:var(--font-mono); font-size:1.1rem; font-weight:700; color:var(--primary);">1</div>
                    </div>
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">Java Crash Recoveries</div>
                        <div id="diagCrashRecoveries" style="font-family:var(--font-mono); font-size:1.1rem; font-weight:700; color:var(--text-main);">0</div>
                    </div>
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">OS Forced Kills / LMK</div>
                        <div id="diagAbnormalKills" style="font-family:var(--font-mono); font-size:1.1rem; font-weight:700; color:var(--text-main);">0</div>
                    </div>
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">Memory (RAM Used)</div>
                        <div id="diagMemory" style="font-family:var(--font-mono); font-size:0.95rem; font-weight:600; color:#fff;">-- MB / -- MB</div>
                    </div>
                    <div style="background: var(--surface-subtle); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">Device & OS</div>
                        <div id="diagDevice" style="font-family:var(--font-mono); font-size:0.8rem; color:var(--text-muted);">Android Device</div>
                    </div>
                </div>

                <!-- Last Abnormal OS Kill Alert Banner (if any) -->
                <div id="lastAbnormalAlert" style="display:none; background: rgba(245, 158, 11, 0.1); border: 1px solid rgba(245, 158, 11, 0.3); border-radius: var(--radius-md); padding: 12px 16px; margin-bottom: 16px;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="color: #fbbf24; font-weight:600; font-size:0.88rem;">⚠️ OS Forced Termination / Sleep Eviction Detected</span>
                        <span id="lastAbnormalTime" style="font-family:var(--font-mono); font-size:0.8rem; color:#fde68a;"></span>
                    </div>
                    <div id="lastAbnormalMsg" style="font-family:var(--font-mono); font-size:0.82rem; color:#fef3c7; margin-top:4px;"></div>
                </div>

                <!-- Last Crash Summary Banner (if any) -->
                <div id="lastCrashAlert" style="display:none; background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: var(--radius-md); padding: 12px 16px; margin-bottom: 16px;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="color: #f87171; font-weight:600; font-size:0.88rem;">Previous Crash Intercepted & Auto-Recovered</span>
                        <span id="lastCrashTime" style="font-family:var(--font-mono); font-size:0.8rem; color:#fca5a5;"></span>
                    </div>
                    <div id="lastCrashMsg" style="font-family:var(--font-mono); font-size:0.82rem; color:#fecaca; margin-top:4px;"></div>
                </div>

                <!-- Action Controls -->
                <div style="display:flex; gap: 10px; flex-wrap: wrap; padding-top: 8px; border-top: 1px solid var(--border-color);">
                    <button class="btn btn-outline btn-sm" onclick="clearAllLogs()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                        Clear All Logs
                    </button>
                    <button class="btn btn-outline btn-sm" onclick="restartApp()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
                        Clean Restart Process
                    </button>
                    <button class="btn btn-outline btn-sm" style="color:#ef4444; border-color: rgba(239,68,68,0.4);" onclick="triggerTestCrash()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                        Test Crash & Auto-Restart (Verify Recovery)
                    </button>
                </div>
            </div>

            <!-- Crash Logs Card -->
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
                            Crash Log Records (crash_logs.txt)
                        </div>
                        <div class="card-description">Persistent raw crash traces recorded by the watchdog before restarting</div>
                    </div>
                </div>
                <div style="background: #090b10; border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 14px; max-height: 240px; overflow-y: auto;">
                    <pre id="crashLogsPre" style="font-family: var(--font-mono); font-size: 0.78rem; color: #fca5a5; white-space: pre-wrap; line-height: 1.4; margin: 0;">No crashes recorded. System is operating normally.</pre>
                </div>
            </div>

            <!-- Live Event Logs Stream Card -->
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
                            System Operational Event Stream
                        </div>
                        <div class="card-description">Real-time rolling event log (Camera, Audio, Chimes, Network, Watchdog)</div>
                    </div>
                    <div style="display:flex; gap:6px; align-items:center;">
                        <span style="font-size:0.75rem; color:var(--text-muted);">Filter:</span>
                        <button class="btn btn-outline btn-sm log-filter-btn" id="filterBtnALL" onclick="filterLogs('ALL', this)" style="padding:3px 8px; font-size:0.75rem; background:var(--primary); color:#fff; border-color:var(--primary);">ALL</button>
                        <button class="btn btn-outline btn-sm log-filter-btn" id="filterBtnCRASH" onclick="filterLogs('CRASH', this)" style="padding:3px 8px; font-size:0.75rem; color:#ef4444;">CRASH</button>
                        <button class="btn btn-outline btn-sm log-filter-btn" id="filterBtnERROR" onclick="filterLogs('ERROR', this)" style="padding:3px 8px; font-size:0.75rem; color:#f87171;">ERROR</button>
                        <button class="btn btn-outline btn-sm log-filter-btn" id="filterBtnWARN" onclick="filterLogs('WARN', this)" style="padding:3px 8px; font-size:0.75rem; color:#f59e0b;">WARN</button>
                        <button class="btn btn-outline btn-sm log-filter-btn" id="filterBtnINFO" onclick="filterLogs('INFO', this)" style="padding:3px 8px; font-size:0.75rem; color:#3b82f6;">INFO</button>
                    </div>
                </div>
                <div id="logsContainer" style="background: #090b10; border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 10px 14px; max-height: 380px; overflow-y: auto; display:flex; flex-direction:column; gap:6px; font-family:var(--font-mono); font-size:0.78rem;">
                    <!-- Populated dynamically via JS -->
                </div>
            </div>
        </div>
        ${WebDashboardIrEsp.getTabIrHtml()}
        ${WebDashboardIrEsp.getTabEspHtml()}
        ${WebDashboardUpdates.getTabUpdatesHtml()}
    </main>

    <!-- ADD/EDIT CHIME MODAL -->
    <div id="chimeModal" class="modal-bg">
        <div class="modal-card">
            <h3 style="margin-bottom: 16px; font-size: 1rem; font-weight: 600; color: #fff;" id="modalTitle">Schedule Configuration</h3>
            <input type="hidden" id="editChimeId">

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                <div class="form-group">
                    <label class="form-label">Hour (0-23)</label>
                    <input type="number" id="modalHour" min="0" max="23" value="12">
                </div>
                <div class="form-group">
                    <label class="form-label">Minute (0-59)</label>
                    <input type="number" id="modalMinute" min="0" max="59" value="0">
                </div>
            </div>

            <div class="form-group">
                <label class="form-label">Label (e.g. Lunch Break, Meeting, Wake-up)</label>
                <input type="text" id="modalLabel" value="Routine Chime">
            </div>

            <div class="form-group">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
                    <label class="form-label" style="margin-bottom:0;">Repeat Days</label>
                    <div style="display:flex; gap: 4px;">
                        <button type="button" class="quick-day-btn" onclick="setQuickDays([1,2,3,4,5])">Weekdays</button>
                        <button type="button" class="quick-day-btn" onclick="setQuickDays([6,7])">Weekend</button>
                        <button type="button" class="quick-day-btn" onclick="setQuickDays([1,2,3,4,5,6,7])">Daily</button>
                        <button type="button" class="quick-day-btn" onclick="setQuickDays([])">Clear</button>
                    </div>
                </div>
                <div style="display: flex; gap: 12px; flex-wrap: wrap; background: var(--surface-subtle); padding: 8px 12px; border-radius: var(--radius-sm); border: 1px solid var(--border-color);">
                    <label style="font-size:0.82rem; cursor:pointer;"><input type="checkbox" name="modalDay" value="1" checked> Mon</label>
                    <label style="font-size:0.82rem; cursor:pointer;"><input type="checkbox" name="modalDay" value="2" checked> Tue</label>
                    <label style="font-size:0.82rem; cursor:pointer;"><input type="checkbox" name="modalDay" value="3" checked> Wed</label>
                    <label style="font-size:0.82rem; cursor:pointer;"><input type="checkbox" name="modalDay" value="4" checked> Thu</label>
                    <label style="font-size:0.82rem; cursor:pointer;"><input type="checkbox" name="modalDay" value="5" checked> Fri</label>
                    <label style="font-size:0.82rem; cursor:pointer; color:#93c5fd;"><input type="checkbox" name="modalDay" value="6"> Sat</label>
                    <label style="font-size:0.82rem; cursor:pointer; color:#fca5a5;"><input type="checkbox" name="modalDay" value="7"> Sun</label>
                </div>
            </div>

            <div class="form-group">
                <label class="form-label">Audio Tone Source</label>
                <div style="display:flex; gap: 8px;">
                    <select id="modalSoundSource" style="flex:1;">
                        <option value="NONE">None (Silent / Remote Action Only)</option>
                        <option value="VIDEO_SOUND">Video Audio (Play video sound only)</option>
                        <optgroup label="Built-in Chimes">
                            <option value="BUILT_IN:WESTMINSTER">Westminster Quarters</option>
                            <option value="BUILT_IN:TUBULAR_BELLS">Tubular Bells</option>
                            <option value="BUILT_IN:CRYSTAL_BELL">Crystal Bell</option>
                            <option value="BUILT_IN:BIRD_CHIRP">Bird Chirp</option>
                            <option value="BUILT_IN:SOFT_MARIMBA">Marimba</option>
                            <option value="BUILT_IN:ZEN_BELL">Zen Temple Bell</option>
                            <option value="BUILT_IN:GRANDFATHER">Antique Grandfather Clock</option>
                            <option value="BUILT_IN:DIGITAL_SIGNAL">Digital Time Signal</option>
                        </optgroup>
                        <optgroup label="Custom Audio" id="modalCustomAudioOptions">
                        </optgroup>
                    </select>
                    <button type="button" class="btn btn-outline btn-sm" onclick="testModalSound()">Test</button>
                </div>
            </div>

            <div class="form-group" style="background: rgba(245,158,11,0.08); padding: 12px; border-radius: var(--radius-sm); border: 1px solid rgba(245,158,11,0.25);">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
                    <label class="form-label" style="color:#fbbf24; margin-bottom:0; font-weight:600;">📡 IR Remote Control Action</label>
                    <label class="switch"><input type="checkbox" id="modalIrSendEnabled" onchange="toggleModalIrOptions()"><span class="slider"></span></label>
                </div>
                <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:8px;">Automatically transmit an infrared remote command when this schedule triggers.</div>
                <div id="modalIrOptionsContainer" style="display:none; gap:8px; align-items:center;">
                    <select id="modalIrButtonSelect" style="flex:1;"></select>
                    <button type="button" class="btn btn-outline btn-sm" onclick="testModalIr()" style="border-color:#f59e0b; color:#f59e0b;">Send Test</button>
                </div>
            </div>

            <div class="form-group">
                <label class="form-label">Background Visual Effect</label>
                <div style="display:flex; gap: 8px;">
                    <select id="modalVideoSource" style="flex:1;">
                        <option value="NONE">None (Normal clock face)</option>
                        <optgroup label="Preset Ambient Visuals">
                            <option value="PRESET_AURORA">Aurora Night Sky</option>
                            <option value="PRESET_FIREPLACE">Warm Fireplace & Candles</option>
                            <option value="PRESET_STARRY_NIGHT">Starry Constellations</option>
                            <option value="PRESET_RAIN">Gentle Rain Ambience</option>
                            <option value="PRESET_SUNRISE">Morning Sunrise</option>
                        </optgroup>
                        <optgroup label="Custom Video" id="modalCustomVideoOptions">
                        </optgroup>
                    </select>
                    <button type="button" class="btn btn-outline btn-sm" onclick="previewModalVideo()">Preview</button>
                </div>
            </div>

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                <div class="form-group">
                    <label class="form-label">Volume (<span id="modalVolLabel">85%</span>)</label>
                    <input type="range" id="modalVolume" min="0.1" max="1.0" step="0.05" value="0.85" oninput="document.getElementById('modalVolLabel').innerText = Math.round(this.value * 100) + '%'">
                </div>
                <div class="form-group">
                    <label class="form-label">Visual Duration</label>
                    <select id="modalVideoDuration" onchange="onModalVideoDurationChange()">
                        <option value="-1">Single Loop</option>
                        <option value="30">30 Seconds</option>
                        <option value="60" selected>1 Minute</option>
                        <option value="180">3 Minutes</option>
                        <option value="300">5 Minutes</option>
                        <option value="-2">Until Stopped Manually</option>
                        <option value="custom">Custom Seconds...</option>
                    </select>
                    <div id="modalCustomDurationContainer" style="display:none; margin-top:6px; align-items:center; gap:6px;">
                        <input type="number" id="modalCustomDurationInput" min="1" max="3600" value="45" style="width:100px; padding:6px 10px; font-size:0.85rem;" placeholder="Seconds">
                        <span style="font-size:0.8rem; color:var(--text-muted);">sec</span>
                    </div>
                </div>
            </div>

            <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px;">
                <button class="btn btn-outline" onclick="closeChimeModal()">Cancel</button>
                <button class="btn btn-primary" onclick="saveChimeFromModal()">Save Changes</button>
            </div>
        </div>
    </div>

    ${WebDashboardIrEsp.getIrModalHtml()}

    <!-- Floating Universal Upload Progress Card -->
    <div id="globalUploadProgress" style="display:none; position:fixed; bottom:24px; right:24px; z-index:9999; width:340px; background:#181b22; border:1px solid var(--border-color); border-radius:var(--radius-lg); box-shadow:0 12px 36px rgba(0,0,0,0.6); padding:16px; backdrop-filter:blur(10px);">
        <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:8px;">
            <div style="display:flex; align-items:center; gap:8px;">
                <div id="globalUploadIcon" style="width:28px; height:28px; border-radius:6px; background:var(--surface-hover); display:flex; align-items:center; justify-content:center; color:var(--primary); flex-shrink:0;">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:16px; height:16px;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                </div>
                <div>
                    <div id="globalUploadTitle" style="font-size:0.85rem; font-weight:600; color:#fff; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:180px;">file.mp3</div>
                    <div id="globalUploadSubtitle" style="font-size:0.72rem; color:var(--text-muted);">0 / 0 MB</div>
                </div>
            </div>
            <div id="globalUploadPercent" style="font-family:var(--font-mono); font-size:0.88rem; font-weight:700; color:var(--primary);">0%</div>
        </div>
        <div class="progress-track" style="height:6px; margin-bottom:8px;">
            <div id="globalUploadBar" class="progress-fill" style="width:0%;"></div>
        </div>
        <div style="display:flex; justify-content:space-between; font-size:0.72rem; color:var(--text-muted);">
            <span id="globalUploadStatus">アップロード中...</span>
            <span id="globalUploadSpeed">-- MB/s</span>
        </div>
    </div>

    <div id="toast" class="toast">Settings updated</div>

    <script>
        var currentData = null;

        function showToast(msg) {
            var t = document.getElementById('toast');
            t.innerText = msg;
            t.classList.add('show');
            setTimeout(() => t.classList.remove('show'), 2800);
        }

        function switchTab(tabName, btnEl) {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            document.getElementById('tab-' + tabName).classList.add('active');
            if (btnEl) {
                btnEl.classList.add('active');
            } else {
                var targetBtn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.getAttribute('onclick').includes(tabName));
                if (targetBtn) targetBtn.classList.add('active');
            }
        }

        function updateClock() {
            var now = new Date();
            var h = String(now.getHours()).padStart(2, '0');
            var m = String(now.getMinutes()).padStart(2, '0');
            var s = String(now.getSeconds()).padStart(2, '0');
            document.getElementById('headerClock').innerText = h + ':' + m + ':' + s;
        }
        setInterval(updateClock, 1000);
        updateClock();

        function fetchStatus() {
            fetch('/api/data')
                .then(res => res.json())
                .then(data => {
                    currentData = data;
                    renderData(data);
                })
                .catch(err => console.error(err));
        }

        function renderData(data) {
            var p = data.preferences;
            document.getElementById('chkHourlyChime').checked = p.hourlyChimeEnabled;
            document.getElementById('chkHalfHourlyChime').checked = p.halfHourlyChimeEnabled;
            document.getElementById('numChimeStart').value = p.chimeStartHour;
            document.getElementById('numChimeEnd').value = p.chimeEndHour;
            document.getElementById('rngVolume').value = p.chimeVolume;
            document.getElementById('volLabel').innerText = Math.round(p.chimeVolume * 100) + '%';

            // Hourly chime sound options
            var hourlyCustomGroup = document.getElementById('selHourlyCustomAudios');
            hourlyCustomGroup.innerHTML = '';
            if (data.customAudios && data.customAudios.length > 0) {
                data.customAudios.forEach(a => {
                    var opt = document.createElement('option');
                    opt.value = 'CUSTOM_FILE:' + a.id;
                    opt.dataset.path = a.filePath;
                    opt.dataset.name = a.name;
                    opt.dataset.id = a.id;
                    opt.innerText = a.name;
                    hourlyCustomGroup.appendChild(opt);
                });
            }
            if (p.hourlyChimeSourceType === 'CUSTOM_FILE' && p.hourlyCustomAudioId) {
                document.getElementById('selChimeSound').value = 'CUSTOM_FILE:' + p.hourlyCustomAudioId;
            } else {
                document.getElementById('selChimeSound').value = p.chimeSound;
            }

            document.getElementById('selClockFace').value = p.clockFace;
            document.getElementById('selColorPalette').value = p.colorPalette;
            document.getElementById('chk24Hour').checked = p.is24Hour;
            document.getElementById('chkShowSeconds').checked = p.showSeconds;
            document.getElementById('chkNightMode').checked = p.isNightMode;
            document.getElementById('chkKioskLock').checked = p.isKioskLocked;
            document.getElementById('chkBurnIn').checked = !!p.burnInProtection;
            document.getElementById('chkEew').checked = !!p.eewEnabled;
            if (p.eewSoundMode && document.getElementById('selEewSound')) document.getElementById('selEewSound').value = p.eewSoundMode;

            document.getElementById('selPrefecture').value = p.selectedPrefecture;
            document.getElementById('chkShowWeather').checked = p.showWeather;
            document.getElementById('chkShowWarnings').checked = !!p.showWarnings;

            // Render Scheduled Chimes
            var listEl = document.getElementById('scheduledChimesList');
            if (!data.scheduledChimes || data.scheduledChimes.length === 0) {
                listEl.innerHTML = '<p style="color:var(--text-muted); font-size:0.85rem; padding:12px 0;">No custom schedules configured. Click "Add Schedule" to create one.</p>';
            } else {
                var html = '';
                data.scheduledChimes.forEach(c => {
                    var soundLabel = c.sourceType === 'NONE' ? '🔇 Silent (No Audio)' : (c.sourceType === 'VIDEO_SOUND' ? 'Video Audio' : (c.sourceType === 'CUSTOM_FILE' ? (c.customAudioName || 'Custom Audio') : c.soundDisplayName));
                    var videoLabel = c.videoSourceType === 'NONE' ? '' : ('<span class="tag tag-video">' + c.videoDisplayName + '</span>');
                    var remoteBadge = (c.irSendEnabled && c.remoteActionDisplayName) ? ('<span class="tag" style="background:rgba(245,158,11,0.2); color:#f59e0b; border:1px solid rgba(245,158,11,0.4); font-weight:600;">' + c.remoteActionDisplayName + '</span>') : '';
                    var volPct = Math.round(c.volume * 100) + '%';
                    
                    html += '<div class="item-card">' +
                        '<div style="display:flex; align-items:center; gap:16px;">' +
                            '<div class="time-badge">' + c.formattedTime + '</div>' +
                            '<div>' +
                                '<div style="font-weight:600; font-size:0.92rem; color:#fff;">' + c.label + '</div>' +
                                '<div style="font-size:0.78rem; color:var(--text-muted); margin-top:2px;">' + c.repeatDaysText + ' • ' + volPct + ' vol</div>' +
                                '<div style="margin-top:4px; display:flex; gap:6px; flex-wrap:wrap;">' +
                                    '<span class="tag">' + soundLabel + '</span>' +
                                    videoLabel +
                                    remoteBadge +
                                '</div>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display:flex; gap:6px; align-items:center; flex-wrap:wrap;">' +
                            '<button class="btn btn-outline btn-sm" onclick="testChimeSchedule(\'' + c.id + '\')">Test</button>' +
                            '<button class="btn btn-outline btn-sm" onclick="editChime(\'' + c.id + '\')">Edit</button>' +
                            '<button class="btn btn-danger btn-sm" onclick="deleteChime(\'' + c.id + '\')">Delete</button>' +
                            '<label class="switch" style="margin-left:4px;"><input type="checkbox" ' + (c.isEnabled ? 'checked' : '') + ' onchange="toggleChime(\'' + c.id + '\')"><span class="slider"></span></label>' +
                        '</div>' +
                    '</div>';
                });
                listEl.innerHTML = html;
            }

            // Render Custom Audios
            var audios = data.customAudios || [];
            document.getElementById('audioCount').innerText = audios.length;
            var audEl = document.getElementById('customAudioList');
            if (audios.length === 0) {
                audEl.innerHTML = '<p style="color:var(--text-muted); font-size:0.82rem; padding:8px 0;">No audio files uploaded. Upload audio above to use in chimes.</p>';
            } else {
                var aHtml = '';
                audios.forEach(a => {
                    var safePath = encodeURIComponent(a.filePath);
                    var safeName = a.name.replace(/'/g, "\\'");
                    var safeId = a.id;
                    aHtml += '<div class="item-card">' +
                        '<div style="flex:1; min-width:180px;">' +
                            '<div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">' +
                                '<span style="font-size:0.88rem; font-weight:600; color:#fff; word-break:break-all;">' + a.name + '</span>' +
                                '<button class="btn btn-outline btn-sm" style="padding:2px 6px; font-size:0.72rem;" onclick="renameMediaPrompt(\'' + safeId + '\', \'audio\', \'' + safeName + '\')">Rename</button>' +
                            '</div>' +
                            '<div style="margin-top:6px;">' +
                                '<audio src="/media/audio/' + a.id + '" controls style="height:28px; width:100%; max-width:240px;"></audio>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display:flex; gap:6px; flex-wrap:wrap; align-items:center;">' +
                            '<button class="btn btn-outline btn-sm" onclick="openAddChimeWithAudio(\'' + a.id + '\', \'' + safeName + '\', \'' + safePath + '\')">Use in Schedule</button>' +
                            '<button class="btn btn-outline btn-sm" onclick="testCustomAudio(\'' + safePath + '\')">Play on Device</button>' +
                            '<button class="btn btn-outline btn-sm" onclick="stopAudioPlayback()">Stop</button>' +
                            '<button class="btn btn-danger btn-sm" onclick="deleteMedia(\'' + a.id + '\', \'audio\')">Delete</button>' +
                        '</div>' +
                    '</div>';
                });
                audEl.innerHTML = aHtml;
            }

            // Render Custom Videos
            var videos = data.customVideos || [];
            document.getElementById('videoCount').innerText = videos.length;
            var vidEl = document.getElementById('customVideoList');
            if (videos.length === 0) {
                vidEl.innerHTML = '<p style="color:var(--text-muted); font-size:0.82rem; padding:8px 0;">No video files uploaded. Upload MP4 videos above for background visuals.</p>';
            } else {
                var vHtml = '';
                videos.forEach(v => {
                    var safePath = encodeURIComponent(v.filePath);
                    var safeName = v.name.replace(/'/g, "\\'");
                    var safeId = v.id;
                    vHtml += '<div class="item-card">' +
                        '<div style="flex:1; min-width:180px;">' +
                            '<div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">' +
                                '<span style="font-size:0.88rem; font-weight:600; color:#fff; word-break:break-all;">' + v.name + '</span>' +
                                '<button class="btn btn-outline btn-sm" style="padding:2px 6px; font-size:0.72rem;" onclick="renameMediaPrompt(\'' + safeId + '\', \'video\', \'' + safeName + '\')">Rename</button>' +
                            '</div>' +
                            '<div style="margin-top:6px; display:flex; gap:8px; align-items:center;">' +
                                '<a href="/media/video/' + v.id + '" target="_blank" class="tag" style="text-decoration:none;">Preview in Browser</a>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display:flex; gap:6px; flex-wrap:wrap; align-items:center;">' +
                            '<button class="btn btn-outline btn-sm" onclick="openAddChimeWithVideo(\'' + v.id + '\', \'' + safeName + '\', \'' + safePath + '\')">Use in Schedule</button>' +
                            '<button class="btn btn-outline btn-sm" onclick="previewCustomVideoOnDevice(\'' + safePath + '\', \'' + safeName + '\')">Play on Screen</button>' +
                            '<button class="btn btn-outline btn-sm" onclick="dismissVideoOnDevice()">Stop</button>' +
                            '<button class="btn btn-danger btn-sm" onclick="deleteMedia(\'' + v.id + '\', \'video\')">Delete</button>' +
                        '</div>' +
                    '</div>';
                });
                vidEl.innerHTML = vHtml;
            }

            // Render Camera & Audio stats
            if (data.camera) {
                var cam = data.camera;
                var statsTag = document.getElementById('cameraStatsTag');
                if (statsTag) {
                    var lensName = cam.useFrontCamera ? 'Front Camera' : 'Back Camera';
                    var fps = cam.targetFps || 15;
                    var clients = cam.clientCount || 0;
                    var audioClients = cam.audioClientCount || 0;
                    statsTag.innerHTML = '<div class="rec-indicator"></div><span>LIVE • ' + lensName + ' • ' + fps + 'fps • Video: ' + clients + ' / Audio: ' + audioClients + '</span>';
                }

                if (cam.audioGain !== undefined && document.getElementById('rngMicGain') && document.activeElement !== document.getElementById('rngMicGain')) {
                    document.getElementById('rngMicGain').value = cam.audioGain;
                    document.getElementById('micGainLabel').innerText = Number(cam.audioGain).toFixed(1) + 'x';
                }

                if (document.getElementById('selCameraFps') && document.activeElement !== document.getElementById('selCameraFps')) {
                    document.getElementById('selCameraFps').value = String(cam.targetFps || 10);
                }
                if (document.getElementById('selCameraRes') && document.activeElement !== document.getElementById('selCameraRes')) {
                    document.getElementById('selCameraRes').value = (cam.resolutionWidth || 640) + 'x' + (cam.resolutionHeight || 480);
                }
                if (document.getElementById('selCameraLens') && document.activeElement !== document.getElementById('selCameraLens')) {
                    document.getElementById('selCameraLens').value = cam.useFrontCamera ? 'front' : 'back';
                }

                if (cam.audioLevelPercent !== undefined) {
                    updateVuMeter(cam.audioLevelPercent);
                }
            }

            // Render IR Remotes and ESP32 Sensor telemetry
            renderIrData(data);
            renderEspData(data);
        }

        function onCameraSettingChange() {
            var fps = parseInt(document.getElementById('selCameraFps').value) || 10;
            var res = document.getElementById('selCameraRes').value.split('x');
            var w = parseInt(res[0]) || 640;
            var h = parseInt(res[1]) || 480;
            var lens = document.getElementById('selCameraLens').value === 'front';

            fetch('/api/camera/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    targetFps: fps,
                    resolutionWidth: w,
                    resolutionHeight: h,
                    useFrontCamera: lens
                })
            }).then(function(res) { return res.json(); }).then(function(data) {
                showToast('Camera settings updated');
                setTimeout(reloadStream, 600);
            }).catch(function(err) { console.error(err); });
        }

        var isAudioPlaying = false;
        function toggleAudioStream() {
            var audioEl = document.getElementById('liveAudioPlayer');
            var btn = document.getElementById('btnToggleAudio');
            var tag = document.getElementById('audioLiveTag');

            if (!isAudioPlaying) {
                var streamUrl = '/audio.wav?t=' + Date.now();
                audioEl.src = streamUrl;
                audioEl.play().then(function() {
                    isAudioPlaying = true;
                    btn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:14px;height:14px;"><line x1="1" y1="1" x2="23" y2="23"/><path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6"/></svg> Mute Audio';
                    btn.className = 'btn btn-danger btn-sm';
                    if (tag) tag.style.display = 'inline-flex';
                    showToast('Live microphone stream started');
                }).catch(function(err) {
                    console.error('Audio playback failed', err);
                    showToast('Audio playback failed - please allow browser autoplay');
                });
            } else {
                audioEl.pause();
                audioEl.src = '';
                isAudioPlaying = false;
                btn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:14px;height:14px;"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"/></svg> Listen Live';
                btn.className = 'btn btn-sm';
                if (tag) tag.style.display = 'none';
                showToast('Live microphone stream stopped');
            }
        }

        function onLocalVolumeChange(val) {
            var audioEl = document.getElementById('liveAudioPlayer');
            if (audioEl) audioEl.volume = parseFloat(val);
            document.getElementById('localVolLabel').innerText = Math.round(val * 100) + '%';
        }

        var micGainDebounceTimer = null;
        function saveMicGain(val) {
            var gain = parseFloat(val);
            clearTimeout(micGainDebounceTimer);
            micGainDebounceTimer = setTimeout(function() {
                fetch('/api/camera/audio_gain', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ gain: gain })
                }).then(function(res) { return res.json(); }).then(function(data) {
                    showToast('Microphone gain updated to ' + gain.toFixed(1) + 'x');
                }).catch(function(err) { console.error(err); });
            }, 100);
        }

        function updateVuMeter(level) {
            var vuBar = document.getElementById('audioVuBar');
            var vuText = document.getElementById('audioLevelText');
            if (vuBar && vuText) {
                var clamped = Math.max(0, Math.min(100, level));
                vuBar.style.width = clamped + '%';
                vuText.innerText = clamped + '%';
            }
        }

        function copyAudioUrl() {
            var url = document.getElementById('audioUrlCode').innerText;
            navigator.clipboard.writeText(url).then(function() {
                showToast('Audio stream URL copied to clipboard');
            }).catch(function() {
                showToast('Failed to copy URL');
            });
        }

        // Fast polling for VU meter and status
        setInterval(function() {
            fetch('/api/status')
                .then(function(res) { return res.json(); })
                .then(function(data) {
                    if (data.camera && data.camera.audioLevelPercent !== undefined) {
                        updateVuMeter(data.camera.audioLevelPercent);
                    }
                    if (data.camera) {
                        var statsTag = document.getElementById('cameraStatsTag');
                        if (statsTag) {
                            var lensName = data.camera.useFrontCamera ? 'Front Camera' : 'Back Camera';
                            var fps = data.camera.targetFps || 15;
                            var clients = data.camera.clientCount || 0;
                            var audioClients = data.camera.audioClientCount || 0;
                            statsTag.innerHTML = '<div class="rec-indicator"></div><span>LIVE • ' + lensName + ' • ' + fps + 'fps • Video: ' + clients + ' / Audio: ' + audioClients + '</span>';
                        }
                    }
                })
                .catch(function() {});
        }, 1500);

        var volumeDebounceTimer = null;
        function onVolumeSliderChange(val) {
            document.getElementById('volLabel').innerText = Math.round(val * 100) + '%';
            clearTimeout(volumeDebounceTimer);
            volumeDebounceTimer = setTimeout(function() {
                fetch('/api/system/volume', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ volume: parseFloat(val) })
                });
            }, 50);
        }

        function saveGeneralSettings() {
            var selSoundVal = document.getElementById('selChimeSound').value;
            var selOption = document.getElementById('selChimeSound').selectedOptions[0];
            var isCustom = selSoundVal.indexOf('CUSTOM_FILE:') === 0;

            var body = {
                hourlyChimeEnabled: document.getElementById('chkHourlyChime').checked,
                halfHourlyChimeEnabled: document.getElementById('chkHalfHourlyChime').checked,
                hourlyChimeSourceType: isCustom ? 'CUSTOM_FILE' : 'BUILT_IN',
                chimeSound: isCustom ? 'WESTMINSTER' : selSoundVal,
                hourlyCustomAudioId: isCustom ? (selOption ? selOption.dataset.id : selSoundVal.replace('CUSTOM_FILE:', '')) : null,
                hourlyCustomAudioName: isCustom ? (selOption ? selOption.dataset.name : '') : null,
                hourlyCustomAudioPath: isCustom ? (selOption ? selOption.dataset.path : '') : null,
                chimeStartHour: parseInt(document.getElementById('numChimeStart').value) || 8,
                chimeEndHour: parseInt(document.getElementById('numChimeEnd').value) || 22,
                chimeVolume: parseFloat(document.getElementById('rngVolume').value) || 0.75,
                clockFace: document.getElementById('selClockFace').value,
                colorPalette: document.getElementById('selColorPalette').value,
                is24Hour: document.getElementById('chk24Hour').checked,
                showSeconds: document.getElementById('chkShowSeconds').checked,
                isNightMode: document.getElementById('chkNightMode').checked,
                isKioskLocked: document.getElementById('chkKioskLock').checked,
                burnInProtection: document.getElementById('chkBurnIn').checked,
                eewEnabled: document.getElementById('chkEew').checked,
                eewSoundMode: document.getElementById('selEewSound').value,
                selectedPrefecture: document.getElementById('selPrefecture').value,
                showWeather: document.getElementById('chkShowWeather').checked,
                showWarnings: document.getElementById('chkShowWarnings').checked
            };
            fetch('/api/settings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            }).then(() => {
                showToast('Settings saved to device');
            });
        }

        function testSound() {
            var selSoundVal = document.getElementById('selChimeSound').value;
            var selOption = document.getElementById('selChimeSound').selectedOptions[0];
            var volume = parseFloat(document.getElementById('rngVolume').value) || 0.75;
            var isCustom = selSoundVal.indexOf('CUSTOM_FILE:') === 0;

            var body = {
                sound: selSoundVal,
                volume: volume,
                sourceType: isCustom ? 'CUSTOM_FILE' : 'BUILT_IN',
                customAudioPath: isCustom && selOption ? selOption.dataset.path : undefined,
                customAudioId: isCustom && selOption ? selOption.dataset.id : undefined
            };

            fetch('/api/chime/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            showToast('Testing chime tone on device...');
        }

        function testChimeSchedule(id) {
            fetch('/api/chime/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            });
            showToast('Testing schedule on device...');
        }

        function testCustomAudio(path) {
            var decodedPath = decodeURIComponent(path);
            fetch('/api/audio/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ filePath: decodedPath, volume: 0.85 })
            });
            showToast('Playing audio on device...');
        }

        function stopAudioPlayback() {
            fetch('/api/audio/stop', { method: 'POST' });
            showToast('Audio playback stopped');
        }

        function dismissVideoOnDevice() {
            fetch('/api/video/dismiss', { method: 'POST' });
            showToast('Visual/audio display stopped');
        }

        function renameMediaPrompt(id, type, currentName) {
            var newName = prompt('Enter new ' + (type === 'audio' ? 'audio' : 'video') + ' filename:', currentName);
            if (newName && newName.trim() !== '' && newName.trim() !== currentName) {
                fetch('/api/media/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: id, type: type, newName: newName.trim() })
                }).then(() => {
                    fetchStatus();
                    showToast('File renamed to: ' + newName.trim());
                });
            }
        }

        function onModalVideoDurationChange() {
            var sel = document.getElementById('modalVideoDuration');
            var customDiv = document.getElementById('modalCustomDurationContainer');
            if (sel.value === 'custom') {
                customDiv.style.display = 'flex';
            } else {
                customDiv.style.display = 'none';
            }
        }

        function previewCustomVideoOnDevice(path, name) {
            var decodedPath = decodeURIComponent(path);
            fetch('/api/video/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    videoSourceType: 'CUSTOM_FILE',
                    customVideoPath: decodedPath,
                    customVideoName: name,
                    durationSeconds: 30
                })
            });
            showToast('Displaying visual preview on device (30s)...');
        }

        function toggleChime(id) {
            fetch('/api/chime/toggle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            }).then(() => fetchStatus());
        }

        function deleteChime(id) {
            if (!confirm('Delete this scheduled chime?')) return;
            fetch('/api/chime/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            }).then(() => {
                fetchStatus();
                showToast('Schedule deleted');
            });
        }

        function toggleLens() {
            fetch('/api/camera/lens', { method: 'POST' })
                .then(() => {
                    showToast('Switched camera lens');
                    setTimeout(reloadStream, 600);
                });
        }

        function reloadStream() {
            var img = document.getElementById('cameraStreamImg');
            img.src = '/video?' + Date.now();
        }

        function formatBytes(bytes) {
            if (!bytes || bytes === 0) return '0 B';
            var k = 1024;
            var sizes = ['B', 'KB', 'MB', 'GB'];
            var i = Math.floor(Math.log(bytes) / Math.log(k));
            return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i];
        }

        function formatSpeed(bytesPerSec) {
            if (!bytesPerSec || bytesPerSec === 0) return '-- MB/s';
            if (bytesPerSec >= 1048576) {
                return (bytesPerSec / 1048576).toFixed(1) + ' MB/s';
            }
            return (bytesPerSec / 1024).toFixed(0) + ' KB/s';
        }

        function uploadFileWithProgress(options) {
            var file = options.file;
            if (!file) return;

            var url = options.url;
            var fieldName = options.fieldName || 'file';
            var extraHeaders = options.headers || {};
            var typeLabel = options.typeLabel || 'ファイル';
            var inlinePrefix = options.inlinePrefix;

            var startTime = Date.now();
            var lastTime = startTime;
            var lastLoaded = 0;

            var globalCard = document.getElementById('globalUploadProgress');
            var globalTitle = document.getElementById('globalUploadTitle');
            var globalSubtitle = document.getElementById('globalUploadSubtitle');
            var globalPercent = document.getElementById('globalUploadPercent');
            var globalBar = document.getElementById('globalUploadBar');
            var globalStatus = document.getElementById('globalUploadStatus');
            var globalSpeed = document.getElementById('globalUploadSpeed');

            if (globalCard) {
                globalCard.style.display = 'block';
                if (globalTitle) globalTitle.innerText = file.name;
                if (globalSubtitle) globalSubtitle.innerText = '0 / ' + formatBytes(file.size);
                if (globalPercent) globalPercent.innerText = '0%';
                if (globalBar) {
                    globalBar.style.width = '0%';
                    globalBar.style.background = '';
                }
                if (globalStatus) globalStatus.innerText = typeLabel + ' アップロード中...';
                if (globalSpeed) globalSpeed.innerText = '-- MB/s';
            }

            var inlineBox = inlinePrefix ? document.getElementById(inlinePrefix) : null;
            var inlineName = inlinePrefix ? document.getElementById(inlinePrefix + 'Name') : null;
            var inlinePercent = inlinePrefix ? document.getElementById(inlinePrefix + 'Percent') : null;
            var inlineBar = inlinePrefix ? document.getElementById(inlinePrefix + 'Bar') : null;
            var inlineBytes = inlinePrefix ? document.getElementById(inlinePrefix + 'Bytes') : null;
            var inlineSpeed = inlinePrefix ? document.getElementById(inlinePrefix + 'Speed') : null;

            if (inlineBox) {
                inlineBox.style.display = 'block';
                if (inlineName) inlineName.innerText = file.name;
                if (inlinePercent) inlinePercent.innerText = '0%';
                if (inlineBar) {
                    inlineBar.style.width = '0%';
                    inlineBar.style.background = '';
                }
                if (inlineBytes) inlineBytes.innerText = '0 / ' + formatBytes(file.size);
                if (inlineSpeed) inlineSpeed.innerText = '-- MB/s';
            }

            var xhr = new XMLHttpRequest();
            xhr.open('POST', url, true);
            for (var h in extraHeaders) {
                xhr.setRequestHeader(h, extraHeaders[h]);
            }

            xhr.upload.onprogress = function(e) {
                if (e.lengthComputable) {
                    var percent = Math.min(100, Math.round((e.loaded / e.total) * 100));
                    var now = Date.now();
                    var dt = (now - lastTime) / 1000;
                    var speedStr = '-- MB/s';
                    if (dt > 0.2) {
                        var dLoaded = e.loaded - lastLoaded;
                        var bytesPerSec = dLoaded / dt;
                        speedStr = formatSpeed(bytesPerSec);
                        lastTime = now;
                        lastLoaded = e.loaded;
                    }

                    var loadedStr = formatBytes(e.loaded) + ' / ' + formatBytes(e.total);

                    if (globalCard) {
                        if (globalPercent) globalPercent.innerText = percent + '%';
                        if (globalBar) globalBar.style.width = percent + '%';
                        if (globalSubtitle) globalSubtitle.innerText = loadedStr;
                        if (globalSpeed && dt > 0.2) globalSpeed.innerText = speedStr;
                        if (percent >= 100 && globalStatus) {
                            globalStatus.innerText = '端末側で保存・処理中...';
                        }
                    }

                    if (inlineBox) {
                        if (inlinePercent) inlinePercent.innerText = percent + '%';
                        if (inlineBar) inlineBar.style.width = percent + '%';
                        if (inlineBytes) inlineBytes.innerText = loadedStr;
                        if (inlineSpeed && dt > 0.2) inlineSpeed.innerText = speedStr;
                    }

                    if (options.onProgress) {
                        options.onProgress(percent, e.loaded, e.total, speedStr);
                    }
                }
            };

            xhr.onload = function() {
                var isSuccess = xhr.status >= 200 && xhr.status < 300;
                var resp = null;
                try {
                    resp = JSON.parse(xhr.responseText);
                } catch (_) {}

                if (globalCard) {
                    if (globalPercent) globalPercent.innerText = '100%';
                    if (globalBar) {
                        globalBar.style.width = '100%';
                        globalBar.style.background = isSuccess ? 'var(--success)' : 'var(--danger)';
                    }
                    if (globalStatus) {
                        globalStatus.innerText = isSuccess ? ('完了！ ' + (resp && resp.message ? resp.message : '')) : ('エラー: ' + (resp && resp.message ? resp.message : ('HTTP ' + xhr.status)));
                    }
                    setTimeout(function() {
                        globalCard.style.display = 'none';
                        if (globalBar) globalBar.style.background = '';
                    }, 2800);
                }

                if (inlineBox) {
                    if (inlinePercent) inlinePercent.innerText = isSuccess ? '100%' : 'Error';
                    if (inlineBar) {
                        inlineBar.style.width = '100%';
                        inlineBar.style.background = isSuccess ? 'var(--success)' : 'var(--danger)';
                    }
                    setTimeout(function() {
                        inlineBox.style.display = 'none';
                        if (inlineBar) inlineBar.style.background = '';
                    }, 3200);
                }

                if (isSuccess) {
                    showToast(typeLabel + ' アップロード完了: ' + file.name);
                    if (options.onSuccess) options.onSuccess(resp);
                } else {
                    var errMsg = (resp && resp.message) ? resp.message : ('アップロード失敗 (HTTP ' + xhr.status + ')');
                    showToast('エラー: ' + errMsg);
                    if (options.onError) options.onError(errMsg);
                }
            };

            xhr.onerror = function() {
                if (globalCard) {
                    if (globalStatus) globalStatus.innerText = '通信エラーが発生しました';
                    if (globalBar) globalBar.style.background = 'var(--danger)';
                    setTimeout(function() { globalCard.style.display = 'none'; }, 2800);
                }
                if (inlineBox) {
                    if (inlinePercent) inlinePercent.innerText = 'Failed';
                    setTimeout(function() { inlineBox.style.display = 'none'; }, 3200);
                }
                showToast('通信エラーが発生しました');
                if (options.onError) options.onError('ネットワーク接続エラー');
            };

            var formData = new FormData();
            formData.append(fieldName, file, file.name);
            xhr.send(formData);
        }

        function uploadAudio(file) {
            if (!file) return;
            uploadFileWithProgress({
                url: '/api/upload/audio',
                file: file,
                fieldName: 'file',
                typeLabel: '音楽・音声',
                inlinePrefix: 'audioUploadInline',
                onSuccess: function() {
                    fetchStatus();
                    document.getElementById('audioFileInput').value = '';
                }
            });
        }

        function uploadVideo(file) {
            if (!file) return;
            uploadFileWithProgress({
                url: '/api/upload/video',
                file: file,
                fieldName: 'file',
                typeLabel: '動画',
                inlinePrefix: 'videoUploadInline',
                onSuccess: function() {
                    fetchStatus();
                    document.getElementById('videoFileInput').value = '';
                }
            });
        }

        function deleteMedia(id, type) {
            if (!confirm('Delete this file from device?')) return;
            fetch('/api/media/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id, type: type })
            }).then(() => {
                fetchStatus();
                showToast('File deleted');
            });
        }

        function setQuickDays(days) {
            document.querySelectorAll('input[name="modalDay"]').forEach(cb => {
                cb.checked = days.includes(parseInt(cb.value));
            });
        }

        function populateModalOptions() {
            var audGroup = document.getElementById('modalCustomAudioOptions');
            audGroup.innerHTML = '';
            if (currentData && currentData.customAudios && currentData.customAudios.length > 0) {
                currentData.customAudios.forEach(a => {
                    var opt = document.createElement('option');
                    opt.value = 'CUSTOM:' + a.id + ':' + a.name + ':' + encodeURIComponent(a.filePath);
                    opt.innerText = a.name;
                    audGroup.appendChild(opt);
                });
            }

            var vidGroup = document.getElementById('modalCustomVideoOptions');
            vidGroup.innerHTML = '';
            if (currentData && currentData.customVideos && currentData.customVideos.length > 0) {
                currentData.customVideos.forEach(v => {
                    var opt = document.createElement('option');
                    opt.value = 'CUSTOM:' + v.id + ':' + v.name + ':' + encodeURIComponent(v.filePath);
                    opt.innerText = v.name;
                    vidGroup.appendChild(opt);
                });
            }

            var irSel = document.getElementById('modalIrButtonSelect');
            irSel.innerHTML = '';
            var irBtns = (currentData && currentData.irButtons) ? currentData.irButtons : [];
            if (irBtns.length === 0) {
                var opt = document.createElement('option');
                opt.value = '';
                opt.innerText = 'No registered IR buttons';
                irSel.appendChild(opt);
            } else {
                irBtns.forEach(b => {
                    var opt = document.createElement('option');
                    opt.value = b.id + ':' + encodeURIComponent(b.name);
                    opt.innerText = b.name + ' (' + (b.category || 'Appliance') + ')';
                    irSel.appendChild(opt);
                });
            }
        }

        function toggleModalIrOptions() {
            var enabled = document.getElementById('modalIrSendEnabled').checked;
            document.getElementById('modalIrOptionsContainer').style.display = enabled ? 'flex' : 'none';
        }

        function testModalIr() {
            var sel = document.getElementById('modalIrButtonSelect');
            if (!sel || !sel.value) {
                showToast('No IR button selected', true);
                return;
            }
            var btnId = sel.value.split(':')[0];
            fetch('/api/ir/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: btnId })
            }).then(res => res.json()).then(res => {
                if (res.status === 'ok') {
                    showToast('Transmitted IR signal: ' + (res.buttonName || btnId));
                } else {
                    showToast('Failed to send IR: ' + (res.message || 'unknown'), true);
                }
            }).catch(e => {
                showToast('Error sending IR: ' + e, true);
            });
        }

        function openAddChimeModal(preAudioVal, preVideoVal) {
            document.getElementById('modalTitle').innerText = 'Add Schedule';
            document.getElementById('editChimeId').value = '';
            document.getElementById('modalHour').value = '12';
            document.getElementById('modalMinute').value = '0';
            document.getElementById('modalLabel').value = 'Routine Chime';
            document.getElementById('modalVolume').value = '0.85';
            document.getElementById('modalVolLabel').innerText = '85%';
            document.getElementById('modalVideoDuration').value = '60';
            document.getElementById('modalCustomDurationContainer').style.display = 'none';
            document.getElementById('modalCustomDurationInput').value = '45';
            document.getElementById('modalIrSendEnabled').checked = false;
            toggleModalIrOptions();
            setQuickDays([1, 2, 3, 4, 5]);

            populateModalOptions();

            if (preAudioVal) {
                document.getElementById('modalSoundSource').value = preAudioVal;
            } else {
                document.getElementById('modalSoundSource').value = 'BUILT_IN:WESTMINSTER';
            }

            if (preVideoVal) {
                document.getElementById('modalVideoSource').value = preVideoVal;
            } else {
                document.getElementById('modalVideoSource').value = 'NONE';
            }

            document.getElementById('chimeModal').classList.add('active');
        }

        function openAddChimeWithAudio(id, name, path) {
            switchTab('chimes');
            var val = 'CUSTOM:' + id + ':' + name + ':' + path;
            openAddChimeModal(val, null);
        }

        function openAddChimeWithVideo(id, name, path) {
            switchTab('chimes');
            var val = 'CUSTOM:' + id + ':' + name + ':' + path;
            openAddChimeModal(null, val);
        }

        function editChime(id) {
            if (!currentData || !currentData.scheduledChimes) return;
            var c = currentData.scheduledChimes.find(item => item.id === id);
            if (!c) return;

            document.getElementById('modalTitle').innerText = 'Edit Schedule';
            document.getElementById('editChimeId').value = c.id;
            document.getElementById('modalHour').value = c.hour;
            document.getElementById('modalMinute').value = c.minute;
            document.getElementById('modalLabel').value = c.label;
            document.getElementById('modalVolume').value = c.volume;
            document.getElementById('modalVolLabel').innerText = Math.round(c.volume * 100) + '%';
            
            var dur = c.videoDurationSeconds;
            if (dur === -1 || dur === -2) {
                document.getElementById('modalVideoDuration').value = String(dur);
                document.getElementById('modalCustomDurationContainer').style.display = 'none';
            } else if (dur === 30 || dur === 60 || dur === 180 || dur === 300) {
                document.getElementById('modalVideoDuration').value = String(dur);
                document.getElementById('modalCustomDurationContainer').style.display = 'none';
            } else {
                document.getElementById('modalVideoDuration').value = 'custom';
                document.getElementById('modalCustomDurationContainer').style.display = 'flex';
                document.getElementById('modalCustomDurationInput').value = String(dur || 45);
            }

            // Populate days
            document.querySelectorAll('input[name="modalDay"]').forEach(cb => {
                cb.checked = c.daysOfWeek.includes(parseInt(cb.value));
            });

            populateModalOptions();

            // Set IR Remote options
            document.getElementById('modalIrSendEnabled').checked = !!c.irSendEnabled;
            toggleModalIrOptions();
            if (c.irButtonId) {
                var irSel = document.getElementById('modalIrButtonSelect');
                var matchOpt = Array.from(irSel.options).find(o => o.value.startsWith(c.irButtonId + ':'));
                if (matchOpt) irSel.value = matchOpt.value;
            }

            // Set sound source
            if (c.sourceType === 'NONE') {
                document.getElementById('modalSoundSource').value = 'NONE';
            } else if (c.sourceType === 'VIDEO_SOUND') {
                document.getElementById('modalSoundSource').value = 'VIDEO_SOUND';
            } else if (c.sourceType === 'CUSTOM_FILE' && c.customAudioId) {
                var targetVal = 'CUSTOM:' + c.customAudioId + ':' + c.customAudioName + ':' + encodeURIComponent(c.customAudioPath);
                var sel = document.getElementById('modalSoundSource');
                var match = Array.from(sel.options).find(o => o.value.startsWith('CUSTOM:' + c.customAudioId));
                if (match) sel.value = match.value;
                else sel.value = 'BUILT_IN:' + (c.builtInSound || 'WESTMINSTER');
            } else {
                document.getElementById('modalSoundSource').value = 'BUILT_IN:' + (c.builtInSound || 'WESTMINSTER');
            }

            // Set video source
            if (c.videoSourceType === 'CUSTOM_FILE' && c.customVideoId) {
                var vSel = document.getElementById('modalVideoSource');
                var vMatch = Array.from(vSel.options).find(o => o.value.startsWith('CUSTOM:' + c.customVideoId));
                if (vMatch) vSel.value = vMatch.value;
                else vSel.value = 'NONE';
            } else {
                document.getElementById('modalVideoSource').value = c.videoSourceType || 'NONE';
            }

            document.getElementById('chimeModal').classList.add('active');
        }

        function closeChimeModal() {
            document.getElementById('chimeModal').classList.remove('active');
        }

        function testModalSound() {
            var val = document.getElementById('modalSoundSource').value;
            var vol = parseFloat(document.getElementById('modalVolume').value) || 0.85;
            if (val === 'VIDEO_SOUND') {
                previewModalVideo();
                return;
            }
            if (val.startsWith('CUSTOM:')) {
                var parts = val.split(':');
                var path = decodeURIComponent(parts[3]);
                fetch('/api/audio/test', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ filePath: path, volume: vol })
                });
            } else {
                var sound = val.replace('BUILT_IN:', '');
                fetch('/api/chime/test', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ sound: sound, volume: vol })
                });
            }
            showToast('Testing audio tone on device...');
        }

        function previewModalVideo() {
            var val = document.getElementById('modalVideoSource').value;
            var durSel = document.getElementById('modalVideoDuration').value;
            var dur = 30;
            if (durSel === '-1') dur = -1;
            else if (durSel === '-2') dur = -2;
            else if (durSel === 'custom') dur = parseInt(document.getElementById('modalCustomDurationInput').value) || 30;
            else dur = parseInt(durSel) || 30;

            if (val === 'NONE') {
                showToast('No background visual selected');
                return;
            }
            if (val.startsWith('CUSTOM:')) {
                var parts = val.split(':');
                var name = parts[2];
                var path = decodeURIComponent(parts[3]);
                fetch('/api/video/preview', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        videoSourceType: 'CUSTOM_FILE',
                        customVideoPath: path,
                        customVideoName: name,
                        durationSeconds: dur
                    })
                });
            } else {
                fetch('/api/video/preview', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        videoSourceType: val,
                        durationSeconds: dur
                    })
                });
            }
            var durMsg = (dur === -1) ? 'full video' : (dur === -2) ? 'until stopped' : (dur + 's');
            showToast('Displaying visual preview on device (' + durMsg + ')...');
        }

        function saveChimeFromModal() {
            var id = document.getElementById('editChimeId').value;
            var hour = parseInt(document.getElementById('modalHour').value) || 0;
            var min = parseInt(document.getElementById('modalMinute').value) || 0;
            var label = document.getElementById('modalLabel').value || 'Routine Chime';
            var vol = parseFloat(document.getElementById('modalVolume').value) || 0.85;
            
            var durSel = document.getElementById('modalVideoDuration').value;
            var duration = 60;
            if (durSel === '-1') duration = -1;
            else if (durSel === '-2') duration = -2;
            else if (durSel === 'custom') duration = parseInt(document.getElementById('modalCustomDurationInput').value) || 45;
            else duration = parseInt(durSel) || 60;

            var days = [];
            document.querySelectorAll('input[name="modalDay"]:checked').forEach(cb => {
                days.push(parseInt(cb.value));
            });
            if (days.length === 0) {
                days = [1, 2, 3, 4, 5, 6, 7];
            }

            var soundVal = document.getElementById('modalSoundSource').value;
            var sourceType = 'BUILT_IN';
            var builtInSound = 'WESTMINSTER';
            var customAudioId = '';
            var customAudioName = '';
            var customAudioPath = '';

            if (soundVal === 'NONE') {
                sourceType = 'NONE';
            } else if (soundVal === 'VIDEO_SOUND') {
                sourceType = 'VIDEO_SOUND';
            } else if (soundVal.startsWith('CUSTOM:')) {
                sourceType = 'CUSTOM_FILE';
                var parts = soundVal.split(':');
                customAudioId = parts[1];
                customAudioName = parts[2];
                customAudioPath = decodeURIComponent(parts[3]);
            } else {
                builtInSound = soundVal.replace('BUILT_IN:', '');
            }

            var videoVal = document.getElementById('modalVideoSource').value;
            var videoSourceType = 'NONE';
            var customVideoId = '';
            var customVideoName = '';
            var customVideoPath = '';

            if (videoVal.startsWith('CUSTOM:')) {
                videoSourceType = 'CUSTOM_FILE';
                var vParts = videoVal.split(':');
                customVideoId = vParts[1];
                customVideoName = vParts[2];
                customVideoPath = decodeURIComponent(vParts[3]);
            } else if (videoVal.startsWith('PRESET_')) {
                videoSourceType = videoVal;
            }

            var irSend = document.getElementById('modalIrSendEnabled').checked;
            var irBtnId = '';
            var irBtnName = '';
            if (irSend) {
                var selIr = document.getElementById('modalIrButtonSelect');
                if (selIr && selIr.value) {
                    var irParts = selIr.value.split(':');
                    irBtnId = irParts[0];
                    irBtnName = decodeURIComponent(irParts[1] || '');
                }
            }

            var isVideoAudio = (sourceType === 'VIDEO_SOUND');
            var payload = {
                id: id,
                hour: hour,
                minute: min,
                label: label,
                isEnabled: true,
                daysOfWeek: days,
                sourceType: sourceType,
                builtInSound: builtInSound,
                customAudioId: customAudioId,
                customAudioName: customAudioName,
                customAudioPath: customAudioPath,
                volume: vol,
                videoSourceType: videoSourceType,
                customVideoId: customVideoId,
                customVideoName: customVideoName,
                customVideoPath: customVideoPath,
                videoDurationSeconds: duration,
                playVideoAudio: isVideoAudio,
                irSendEnabled: irSend,
                irButtonId: irBtnId,
                irButtonName: irBtnName
            };

            fetch('/api/chime/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            }).then(() => {
                closeChimeModal();
                fetchStatus();
                showToast('Schedule saved');
            });
        }

        // Setup Drag & Drop handlers
        function setupDropzone(dropzoneId, uploadFn) {
            var zone = document.getElementById(dropzoneId);
            if (!zone) return;
            ['dragenter', 'dragover'].forEach(eventName => {
                zone.addEventListener(eventName, e => {
                    e.preventDefault();
                    e.stopPropagation();
                    zone.classList.add('dragover');
                }, false);
            });
            ['dragleave', 'drop'].forEach(eventName => {
                zone.addEventListener(eventName, e => {
                    e.preventDefault();
                    e.stopPropagation();
                    zone.classList.remove('dragover');
                }, false);
            });
            zone.addEventListener('drop', e => {
                var dt = e.dataTransfer;
                var files = dt.files;
                if (files.length > 0) {
                    uploadFn(files[0]);
                }
            }, false);
        }

        setupDropzone('audioDropzone', uploadAudio);
        setupDropzone('videoDropzone', uploadVideo);

        // Diagnostics & System Logs
        var currentLogFilter = 'ALL';
        var cachedLogs = [];

        function fetchLogs() {
            fetch('/api/logs')
                .then(res => res.json())
                .then(data => {
                    renderDiagnostics(data.diagnostics);
                    renderCrashLogs(data.crashLogsText);
                    cachedLogs = data.logs || [];
                    renderFilteredLogs();
                })
                .catch(err => {
                    console.error('Failed to fetch logs:', err);
                });
        }

        function renderDiagnostics(diag) {
            if (!diag) return;
            var badge = document.getElementById('diagStatusBadge');
            if (diag.crashRecoveryCount > 0 || diag.abnormalTerminationCount > 0) {
                badge.style.color = 'var(--warning)';
                badge.style.background = 'var(--warning-subtle)';
                badge.style.borderColor = 'rgba(245, 158, 11, 0.3)';
                badge.innerHTML = '<span style="width:6px; height:6px; border-radius:50%; background:var(--warning);"></span> Active (Keep-Alive Recovered)';
            } else {
                badge.style.color = 'var(--success)';
                badge.style.background = 'var(--success-subtle)';
                badge.style.borderColor = 'rgba(16, 185, 129, 0.3)';
                badge.innerHTML = '<span style="width:6px; height:6px; border-radius:50%; background:var(--success);"></span> Active (Keep-Alive ON)';
            }

            document.getElementById('diagUptime').innerText = diag.uptimeFormatted || '--:--:--';
            document.getElementById('diagTotalStarts').innerText = diag.totalStarts || '1';
            document.getElementById('diagCrashRecoveries').innerText = diag.crashRecoveryCount || '0';
            var abKillsEl = document.getElementById('diagAbnormalKills');
            if (abKillsEl) abKillsEl.innerText = diag.abnormalTerminationCount || '0';
            document.getElementById('diagMemory').innerText = (diag.usedMemoryMb || '--') + ' MB / ' + (diag.maxMemoryMb || '--') + ' MB';
            document.getElementById('diagDevice').innerText = (diag.deviceModel || 'Android') + ' (' + (diag.osVersion || '') + ')';

            var abAlert = document.getElementById('lastAbnormalAlert');
            if (abAlert) {
                if (diag.lastAbnormalTerminationTime && diag.lastAbnormalTerminationTime.length > 0) {
                    abAlert.style.display = 'block';
                    document.getElementById('lastAbnormalTime').innerText = diag.lastAbnormalTerminationTime;
                    document.getElementById('lastAbnormalMsg').innerText = diag.lastAbnormalTerminationMessage || 'OS Process Eviction / Low Memory Killer';
                } else {
                    abAlert.style.display = 'none';
                }
            }

            var lastAlert = document.getElementById('lastCrashAlert');
            if (diag.lastCrashTime && diag.lastCrashTime.length > 0) {
                lastAlert.style.display = 'block';
                document.getElementById('lastCrashTime').innerText = diag.lastCrashTime;
                document.getElementById('lastCrashMsg').innerText = diag.lastCrashMessage || 'Fatal exception caught by watchdog';
            } else {
                lastAlert.style.display = 'none';
            }
        }

        function renderCrashLogs(text) {
            var pre = document.getElementById('crashLogsPre');
            if (!text || text.trim().length === 0) {
                pre.innerText = 'No crash records found. All processes running smoothly without interruptions.';
                pre.style.color = 'var(--text-muted)';
            } else {
                pre.innerText = text;
                pre.style.color = '#fca5a5';
            }
        }

        function filterLogs(level, btnEl) {
            currentLogFilter = level;
            document.querySelectorAll('.log-filter-btn').forEach(b => {
                b.style.background = 'transparent';
                b.style.color = 'var(--text-muted)';
                b.style.borderColor = 'var(--border-color)';
            });
            if (btnEl) {
                btnEl.style.background = 'var(--primary)';
                btnEl.style.color = '#fff';
                btnEl.style.borderColor = 'var(--primary)';
            }
            renderFilteredLogs();
        }

        function renderFilteredLogs() {
            var container = document.getElementById('logsContainer');
            container.innerHTML = '';
            var list = cachedLogs;
            if (currentLogFilter !== 'ALL') {
                list = cachedLogs.filter(l => l.level === currentLogFilter);
            }

            if (list.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted); padding:10px; text-align:center;">No logs matching filter [' + currentLogFilter + ']</div>';
                return;
            }

            list.forEach(item => {
                var row = document.createElement('div');
                row.style.display = 'flex';
                row.style.gap = '8px';
                row.style.padding = '4px 0';
                row.style.borderBottom = '1px solid rgba(255,255,255,0.04)';
                row.style.alignItems = 'flex-start';

                var badgeColor = '#3b82f6';
                var badgeBg = 'rgba(59,130,246,0.15)';
                if (item.level === 'CRASH') { badgeColor = '#ef4444'; badgeBg = 'rgba(239,68,68,0.2)'; }
                else if (item.level === 'ERROR') { badgeColor = '#f87171'; badgeBg = 'rgba(248,113,113,0.15)'; }
                else if (item.level === 'WARN') { badgeColor = '#f59e0b'; badgeBg = 'rgba(245,158,11,0.15)'; }

                row.innerHTML = '<span style="color:var(--text-muted); font-size:0.72rem; white-space:nowrap; min-width:65px;">' + item.timestamp + '</span>' +
                                '<span style="font-size:0.68rem; font-weight:700; color:' + badgeColor + '; background:' + badgeBg + '; padding:1px 5px; border-radius:3px; min-width:48px; text-align:center;">' + item.level + '</span>' +
                                '<span style="color:var(--primary); font-size:0.75rem; white-space:nowrap;">[' + item.tag + ']</span>' +
                                '<span style="color:#e2e8f0; font-size:0.78rem; flex:1; word-break:break-all;">' + item.message + (item.stackTrace ? '<br><span style="color:#fca5a5; font-size:0.72rem;">' + item.stackTrace + '</span>' : '') + '</span>';
                container.appendChild(row);
            });
        }

        function clearAllLogs() {
            if (!confirm('Are you sure you want to clear all operational and crash logs?')) return;
            fetch('/api/logs/clear', { method: 'POST' })
                .then(res => res.json())
                .then(() => {
                    showToast('All logs cleared');
                    fetchLogs();
                });
        }

        function downloadLogs() {
            window.location.href = '/api/logs/download';
        }

        function restartApp() {
            if (!confirm('Restart DeskClock process now?')) return;
            fetch('/api/system/restart', { method: 'POST' })
                .then(() => {
                    showToast('Restarting app process... Reconnecting in 3s');
                    setTimeout(fetchStatus, 3500);
                });
        }

        function triggerTestCrash() {
            if (!confirm('Simulate an unhandled fatal exception? The watchdog will intercept it, write a crash report, and auto-restart in ~600ms.')) return;
            fetch('/api/system/test_crash', { method: 'POST' })
                .then(() => {
                    showToast('Fatal exception triggered. Auto-recovering in 1-2s...');
                    setTimeout(() => {
                        fetchLogs();
                        fetchStatus();
                    }, 3500);
                });
        }

        // Auto-refresh logs when tab is clicked
        var origSwitchTab = switchTab;
        switchTab = function(tabName, btnEl) {
            origSwitchTab(tabName, btnEl);
            if (tabName === 'logs') {
                fetchLogs();
            } else if (tabName === 'esp') {
                loadArduinoSketch();
            } else if (tabName === 'updates') {
                if (typeof syncEspOtaHostFromPrefs === 'function') syncEspOtaHostFromPrefs();
            }
        };

        ${WebDashboardIrEsp.getIrEspScript()}
        ${WebDashboardUpdates.getUpdatesScript()}

        fetchStatus();
        fetchLogs();
    </script>
</body>
</html>
        """.trimIndent()
    }
}

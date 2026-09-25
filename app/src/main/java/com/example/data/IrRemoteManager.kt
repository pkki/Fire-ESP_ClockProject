package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.model.IrDeviceCategory
import com.example.model.IrLearnState
import com.example.model.IrRemoteButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

class IrRemoteManager(
    private val context: Context,
    private val bleSensorManager: BleSensorManager,
    private val usbSensorManager: UsbSensorManager? = null
) {
    companion object {
        private const val TAG = "IrRemoteManager"
        private const val PREFS_NAME = "ir_remote_prefs"
        private const val KEY_BUTTONS_JSON = "buttons_json"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _buttons = MutableStateFlow<List<IrRemoteButton>>(emptyList())
    val buttons: StateFlow<List<IrRemoteButton>> = _buttons.asStateFlow()

    private val _learnState = MutableStateFlow(IrLearnState())
    val learnState: StateFlow<IrLearnState> = _learnState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    // 長押し時のリピート断片パルスで完全なフルフレームが上書きされるのを防止するタイムスタンプとパルス数記録
    private var lastFullSignalTime = 0L
    private var lastFullPulseCount = 0

    init {
        loadButtons()

        // 1. BleSensorManagerのコールバックをセットアップ
        bleSensorManager.onIrSignalReceived = { protocol, hex, bits, raw ->
            handleSignalReceived(protocol, hex, bits, raw, "BLE")
        }

        bleSensorManager.onIrStatusChanged = { isLearning, msg ->
            _learnState.value = _learnState.value.copy(
                isLearning = isLearning,
                statusMessage = msg
            )
        }

        bleSensorManager.onIrSendResult = { status ->
            _learnState.value = _learnState.value.copy(
                lastSentResult = if (status == "ok") "送信成功 (BLE)" else "送信失敗 (BLE)"
            )
        }

        bleSensorManager.onRawLogReceived = { rawText ->
            _learnState.value = _learnState.value.copy(latestRawLog = rawText)
        }

        // 2. UsbSensorManagerのコールバックをセットアップ (USB接続時)
        usbSensorManager?.onIrSignalReceived = { protocol, hex, bits, raw ->
            handleSignalReceived(protocol, hex, bits, raw, "USB")
        }

        usbSensorManager?.onIrStatusChanged = { isLearning, msg ->
            _learnState.value = _learnState.value.copy(
                isLearning = isLearning,
                statusMessage = msg
            )
        }

        usbSensorManager?.onIrSendResult = { status ->
            _learnState.value = _learnState.value.copy(
                lastSentResult = if (status == "ok") "送信成功 (USB)" else "送信失敗 (USB)"
            )
        }

        usbSensorManager?.onRawLogReceived = { rawText ->
            _learnState.value = _learnState.value.copy(latestRawLog = rawText)
        }
    }

    private fun handleSignalReceived(protocol: String, hex: String, bits: Int, raw: String, source: String) {
        val now = System.currentTimeMillis()
        val pulseCount = if (raw.isNotBlank()) raw.split(",").filter { it.isNotBlank() }.size else 0

        // 長押し時のリピート断片パルス防止フィルター:
        // 直前 (2200ms 以内) に完全なフルフレーム (35パルス以上) を受信している場合、
        // 後続で送られてくる断片パルス (pulseCount < 30 かつ bits < 30) は長押しリピートの残骸と判断して無視
        if (now - lastFullSignalTime < 2200L && lastFullPulseCount >= 35 && pulseCount < 30 && bits < 30) {
            Log.d(TAG, "Ignored partial repeat pulse from long press ($bits bits, $pulseCount pulses)")
            return
        }

        if (pulseCount >= 35 || bits >= 30) {
            lastFullSignalTime = now
            lastFullPulseCount = pulseCount
        }

        Log.i(TAG, "IR signal received from $source: $protocol $hex ($bits bits, $pulseCount pulses)")
        triggerHapticFeedback()

        // RAWパルスのリーダー部からプロトコル推定 (日本の照明・エアコン用 AEHA 自動識別)
        var detectedProtocol = protocol
        if (protocol.equals("UNKNOWN", ignoreCase = true) && raw.isNotBlank()) {
            val pulses = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (pulses.size >= 2) {
                val mark = pulses[0]
                val space = pulses[1]
                if (mark in 2800..3900 && space in 1400..2200) {
                    detectedProtocol = "AEHA (家電協/照明)"
                } else if (mark in 7500..10500 && space in 3800..5500) {
                    detectedProtocol = "NEC"
                } else if (mark in 2000..2800 && space in 400..800) {
                    detectedProtocol = "SONY"
                }
            }
        }

        val displayName = if (detectedProtocol.equals("UNKNOWN", ignoreCase = true)) {
            if (raw.isNotBlank()) "学習リモコン (${pulseCount}パルス)" else "学習リモコン ($hex)"
        } else {
            "学習済み ($detectedProtocol)"
        }

        val candidate = IrRemoteButton(
            name = displayName,
            protocol = detectedProtocol,
            hexCode = hex,
            bits = bits,
            rawCode = raw,
            category = IrDeviceCategory.LIGHTING,
            repeatCount = 1
        )
        val rawDetail = if (pulseCount > 0) " [RAW ${pulseCount}パルス]" else ""
        _learnState.value = _learnState.value.copy(
            isLearning = false,
            statusMessage = "✅ 信号を受信しました ($source): $detectedProtocol $hex (${bits}bit)$rawDetail",
            lastLearnedSignal = candidate,
            latestRawLog = "受信完了: $detectedProtocol $hex (${bits}bit)$rawDetail"
        )
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    fun startLearning() {
        _learnState.value = IrLearnState(
            isLearning = true,
            statusMessage = "受信待機中... リモコンをESP32に向けてボタンを押してください",
            lastLearnedSignal = null,
            latestRawLog = null
        )
        bleSensorManager.startIrLearning()
        usbSensorManager?.startIrLearning()
    }

    fun stopLearning() {
        _learnState.value = _learnState.value.copy(
            isLearning = false,
            statusMessage = "学習をキャンセルしました"
        )
        bleSensorManager.stopIrLearning()
        usbSensorManager?.stopIrLearning()
    }

    fun clearLearnedSignal() {
        _learnState.value = _learnState.value.copy(lastLearnedSignal = null)
    }

    fun sendButton(button: IrRemoteButton): Boolean {
        val count = button.repeatCount.coerceIn(1, 10)
        Log.i(TAG, "Sending IR signal for '${button.name}' (${button.protocol} ${button.hexCode}) count=$count")
        
        var anySuccess = false
        for (i in 0 until count) {
            var success = bleSensorManager.sendIrSignal(
                protocol = button.protocol,
                hexCode = button.hexCode,
                bits = button.bits,
                rawCode = button.rawCode
            )
            if (!success && usbSensorManager != null) {
                success = usbSensorManager.sendIrSignal(
                    protocol = button.protocol,
                    hexCode = button.hexCode,
                    bits = button.bits,
                    rawCode = button.rawCode
                )
            }
            if (success) anySuccess = true
            if (i < count - 1) {
                try {
                    Thread.sleep(70) // 連続リピート送信間のパルス間隔
                } catch (_: Exception) {}
            }
        }
        if (anySuccess) {
            updateButtonLastSent(button.id)
        }
        return anySuccess
    }

    fun sendButtonById(id: String): Boolean {
        val btn = _buttons.value.firstOrNull { it.id == id } ?: return false
        return sendButton(btn)
    }

    fun getButtonById(id: String): IrRemoteButton? {
        return _buttons.value.firstOrNull { it.id == id }
    }

    /**
     * 物理ボタン(P5)等から即座に照明ON/OFFをトグル送信
     */
    fun triggerLightToggle(): Boolean {
        val lightButton = _buttons.value.firstOrNull {
            it.category == IrDeviceCategory.LIGHTING || it.name.contains("照明") || it.name.contains("ライト")
        } ?: _buttons.value.firstOrNull() ?: return false
        return sendButton(lightButton)
    }

    fun saveButton(button: IrRemoteButton) {
        val current = _buttons.value.toMutableList()
        val index = current.indexOfFirst { it.id == button.id }
        if (index >= 0) {
            current[index] = button
        } else {
            current.add(button)
        }
        _buttons.value = current
        saveButtonsToPrefs(current)
    }

    fun deleteButton(id: String) {
        val current = _buttons.value.filter { it.id != id }
        _buttons.value = current
        saveButtonsToPrefs(current)
    }

    fun importButtons(newList: List<IrRemoteButton>) {
        _buttons.value = newList
        saveButtonsToPrefs(newList)
    }

    fun exportButtonsJson(): String {
        return prefs.getString(KEY_BUTTONS_JSON, "[]") ?: "[]"
    }

    fun importButtonsFromJson(jsonStr: String): Boolean {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<IrRemoteButton>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val daysList = mutableListOf<Int>()
                val daysArr = obj.optJSONArray("scheduleDays")
                if (daysArr != null) {
                    for (d in 0 until daysArr.length()) {
                        daysList.add(daysArr.getInt(d))
                    }
                } else {
                    daysList.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
                }
                list.add(
                    IrRemoteButton(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = obj.optString("name", "リモコン"),
                        category = try {
                            IrDeviceCategory.valueOf(obj.optString("category", "LIGHTING"))
                        } catch (_: Exception) { IrDeviceCategory.LIGHTING },
                        protocol = obj.optString("protocol", "NEC"),
                        hexCode = obj.optString("hexCode", ""),
                        bits = obj.optInt("bits", 32),
                        rawCode = obj.optString("rawCode", ""),
                        iconName = obj.optString("iconName", "power_settings_new"),
                        colorHex = obj.optString("colorHex", "#3B82F6"),
                        triggerOnAlarm = obj.optBoolean("triggerOnAlarm", false),
                        triggerOnNightMode = obj.optBoolean("triggerOnNightMode", false),
                        triggerOnNightExit = obj.optBoolean("triggerOnNightExit", false),
                        isScheduleEnabled = obj.optBoolean("isScheduleEnabled", false),
                        scheduleHour = obj.optInt("scheduleHour", 7),
                        scheduleMinute = obj.optInt("scheduleMinute", 0),
                        scheduleDays = daysList,
                        repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, 10),
                        lastSentEpochMs = obj.optLong("lastSentEpochMs", 0L)
                    )
                )
            }
            _buttons.value = list
            saveButtonsToPrefs(list)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import buttons from JSON", e)
            false
        }
    }

    private fun updateButtonLastSent(id: String) {
        val current = _buttons.value.map {
            if (it.id == id) it.copy(lastSentEpochMs = System.currentTimeMillis()) else it
        }
        _buttons.value = current
        saveButtonsToPrefs(current)
    }

    /**
     * アラーム発動時（目覚まし／チャイム等）の自動送信
     */
    fun onAlarmTriggered() {
        scope.launch {
            val targets = _buttons.value.filter { it.triggerOnAlarm }
            for (btn in targets) {
                Log.i(TAG, "Alarm triggered: Auto-sending IR signal for '${btn.name}'")
                sendButton(btn)
                kotlinx.coroutines.delay(800) // 連続送信時の混信防止ディレイ
            }
        }
    }

    /**
     * 夜間常夜灯モード突入時（部屋の電気を消す等）
     */
    fun onNightModeEntered() {
        scope.launch {
            val targets = _buttons.value.filter { it.triggerOnNightMode }
            for (btn in targets) {
                Log.i(TAG, "Night Mode entered: Auto-sending IR signal for '${btn.name}'")
                sendButton(btn)
                kotlinx.coroutines.delay(800)
            }
        }
    }

    /**
     * 夜間モード解除時（朝、部屋の電気をつける等）
     */
    fun onNightModeExited() {
        scope.launch {
            val targets = _buttons.value.filter { it.triggerOnNightExit }
            for (btn in targets) {
                Log.i(TAG, "Night Mode exited: Auto-sending IR signal for '${btn.name}'")
                sendButton(btn)
                kotlinx.coroutines.delay(800)
            }
        }
    }

    /**
     * 毎分呼び出されるスケジュール確認
     */
    fun checkScheduledTriggers(hour: Int, minute: Int, dayOfWeek: Int) {
        scope.launch {
            val targets = _buttons.value.filter { btn ->
                btn.isScheduleEnabled &&
                btn.scheduleHour == hour &&
                btn.scheduleMinute == minute &&
                btn.scheduleDays.contains(dayOfWeek)
            }
            for (btn in targets) {
                Log.i(TAG, "Scheduled IR Trigger at $hour:$minute: Sending '${btn.name}'")
                sendButton(btn)
                kotlinx.coroutines.delay(800)
            }
        }
    }

    private fun loadButtons() {
        val jsonStr = prefs.getString(KEY_BUTTONS_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            // 初期サンプルデータ（ユーザーが学習しなくてもUIのイメージを掴めるようプリセット）
            val presets = listOf(
                IrRemoteButton(
                    name = "照明 点灯 (ON)",
                    category = IrDeviceCategory.LIGHTING,
                    protocol = "NEC",
                    hexCode = "0x00FF18E7",
                    bits = 32,
                    iconName = "lightbulb",
                    colorHex = "#F59E0B",
                    triggerOnAlarm = true,
                    triggerOnNightExit = true
                ),
                IrRemoteButton(
                    name = "照明 消灯 (OFF)",
                    category = IrDeviceCategory.LIGHTING,
                    protocol = "NEC",
                    hexCode = "0x00FF4AB5",
                    bits = 32,
                    iconName = "lightbulb_outline",
                    colorHex = "#64748B",
                    triggerOnNightMode = true
                ),
                IrRemoteButton(
                    name = "エアコン 冷房 26℃",
                    category = IrDeviceCategory.AIR_CONDITIONER,
                    protocol = "PANASONIC",
                    hexCode = "0x0220E00400000006",
                    bits = 64,
                    iconName = "ac_unit",
                    colorHex = "#38BDF8"
                ),
                IrRemoteButton(
                    name = "エアコン 停止 (OFF)",
                    category = IrDeviceCategory.AIR_CONDITIONER,
                    protocol = "PANASONIC",
                    hexCode = "0x0220E00400000007",
                    bits = 64,
                    iconName = "power_settings_new",
                    colorHex = "#EF4444"
                )
            )
            _buttons.value = presets
            saveButtonsToPrefs(presets)
            return
        }

        try {
            val list = mutableListOf<IrRemoteButton>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val daysArray = obj.optJSONArray("scheduleDays")
                val days = mutableListOf<Int>()
                if (daysArray != null) {
                    for (d in 0 until daysArray.length()) {
                        days.add(daysArray.getInt(d))
                    }
                } else {
                    days.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
                }

                list.add(
                    IrRemoteButton(
                        id = obj.optString("id"),
                        name = obj.optString("name", "ボタン"),
                        category = try {
                            IrDeviceCategory.valueOf(obj.optString("category", "LIGHTING"))
                        } catch (_: Exception) { IrDeviceCategory.LIGHTING },
                        protocol = obj.optString("protocol", "NEC"),
                        hexCode = obj.optString("hexCode", ""),
                        bits = obj.optInt("bits", 32),
                        rawCode = obj.optString("rawCode", ""),
                        iconName = obj.optString("iconName", "power_settings_new"),
                        colorHex = obj.optString("colorHex", "#3B82F6"),
                        triggerOnAlarm = obj.optBoolean("triggerOnAlarm", false),
                        triggerOnNightMode = obj.optBoolean("triggerOnNightMode", false),
                        triggerOnNightExit = obj.optBoolean("triggerOnNightExit", false),
                        isScheduleEnabled = obj.optBoolean("isScheduleEnabled", false),
                        scheduleHour = obj.optInt("scheduleHour", 7),
                        scheduleMinute = obj.optInt("scheduleMinute", 0),
                        scheduleDays = days,
                        repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, 10),
                        lastSentEpochMs = obj.optLong("lastSentEpochMs", 0L)
                    )
                )
            }
            _buttons.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stored IR buttons JSON", e)
        }
    }

    private fun saveButtonsToPrefs(list: List<IrRemoteButton>) {
        try {
            val array = JSONArray()
            for (b in list) {
                val obj = JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("category", b.category.name)
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
                    put("lastSentEpochMs", b.lastSentEpochMs)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_BUTTONS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save IR buttons", e)
        }
    }
}

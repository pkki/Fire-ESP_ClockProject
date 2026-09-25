package com.example.data

import android.util.Log
import org.json.JSONObject
import java.util.regex.Pattern

data class ParsedIrSignal(
    val protocol: String,
    val hexCode: String,
    val bits: Int,
    val rawCode: String = ""
)

object IrParserHelper {
    private const val TAG = "IrParserHelper"

    // Patterns for various IR serial output formats
    // 1. "Protocol=NEC Address=0x0 Command=0x18 Raw-Data=0x00FF18E7 32 bits"
    private val PATTERN_IRREMOTE_V3 = Pattern.compile(
        """Protocol=([A-Za-z0-9_]+).*?Raw-Data=(0x[0-9A-Fa-f]+|\b[0-9A-Fa-f]{4,16}\b).*?(\d+)\s*bits""",
        Pattern.CASE_INSENSITIVE
    )

    // 2. "Decoded NEC: Value:0x00FF18E7 (32 bits)" or "Decoded NEC: 0x00FF18E7"
    private val PATTERN_DECODED_VAL = Pattern.compile(
        """Decoded\s+([A-Za-z0-9_]+)[:\s]+(?:Value:)?(0x[0-9A-Fa-f]+|[0-9A-Fa-f]{4,16})(?:\s*\((\d+)\s*bits\))?""",
        Pattern.CASE_INSENSITIVE
    )

    // 3. "IR: NEC, 0x00FF18E7, 32" or "ir_learned:NEC:0x00FF18E7:32"
    private val PATTERN_COLON_SEPARATED = Pattern.compile(
        """(?:ir|ir_learned|ir_signal)[:\s,]+([A-Za-z0-9_]+)[:\s,]+(0x[0-9A-Fa-f]+|[0-9A-Fa-f]{4,16})(?:[:\s,]+(\d+))?""",
        Pattern.CASE_INSENSITIVE
    )

    // 4. "Protocol: NEC, Code: 0x00FF18E7, Bits: 32"
    private val PATTERN_KEY_VALUE = Pattern.compile(
        """(?:Protocol|Proto)[:=\s]+([A-Za-z0-9_]+).*?(?:Code|Hex|Value|Data)[:=\s]+(0x[0-9A-Fa-f]+|[0-9A-Fa-f]{4,16})(?:.*?(?:Bits|Bit)[:=\s]+(\d+))?""",
        Pattern.CASE_INSENSITIVE
    )

    // 5. Generic "0x00FF18E7" or "FF18E7" with "NEC" / "SONY" / etc. in line
    private val PATTERN_HEX_WITH_PROTO = Pattern.compile(
        """\b(NEC|PANASONIC|SONY|SAMSUNG|RC5|RC6|LG|JVC|DENON|AIWA|MITSUBISHI|SHARP)\b.*?\b(0x[0-9A-Fa-f]{4,16})\b""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * テキスト行から赤外線信号情報を柔軟に抽出
     */
    fun tryParse(rawText: String): ParsedIrSignal? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        // 1. Try JSON parsing first (direct or repaired)
        tryParseJson(trimmed)?.let { return it }

        // Also check if text contains a JSON object substring e.g. "LOG: {"type":"ir_learned",...}"
        val jsonStart = trimmed.indexOf('{')
        if (jsonStart != -1) {
            val sub = trimmed.substring(jsonStart)
            tryParseJson(sub)?.let { return it }
        }

        // 2. Try Regex Patterns
        var matcher = PATTERN_IRREMOTE_V3.matcher(trimmed)
        if (matcher.find()) {
            val proto = matcher.group(1) ?: "NEC"
            val hex = formatHex(matcher.group(2))
            val bits = matcher.group(3)?.toIntOrNull() ?: 32
            return ParsedIrSignal(proto.uppercase(), hex, bits)
        }

        matcher = PATTERN_DECODED_VAL.matcher(trimmed)
        if (matcher.find()) {
            val proto = matcher.group(1) ?: "NEC"
            val hex = formatHex(matcher.group(2))
            val bits = matcher.group(3)?.toIntOrNull() ?: 32
            return ParsedIrSignal(proto.uppercase(), hex, bits)
        }

        matcher = PATTERN_COLON_SEPARATED.matcher(trimmed)
        if (matcher.find()) {
            val proto = matcher.group(1) ?: "NEC"
            val hex = formatHex(matcher.group(2))
            val bits = matcher.group(3)?.toIntOrNull() ?: 32
            return ParsedIrSignal(proto.uppercase(), hex, bits)
        }

        matcher = PATTERN_KEY_VALUE.matcher(trimmed)
        if (matcher.find()) {
            val proto = matcher.group(1) ?: "NEC"
            val hex = formatHex(matcher.group(2))
            val bits = matcher.group(3)?.toIntOrNull() ?: 32
            return ParsedIrSignal(proto.uppercase(), hex, bits)
        }

        matcher = PATTERN_HEX_WITH_PROTO.matcher(trimmed)
        if (matcher.find()) {
            val proto = matcher.group(1) ?: "NEC"
            val hex = formatHex(matcher.group(2))
            return ParsedIrSignal(proto.uppercase(), hex, 32)
        }

        return null
    }

    /**
     * 不完全または途中で切断されたJSONを自動修復してパース
     */
    fun tryRepairAndParseJson(text: String): JSONObject? {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        if (start == -1) return null

        val candidate = trimmed.substring(start)

        // 1. そのまま完全なJSONとしてパースできるか試行
        try {
            val end = candidate.lastIndexOf('}')
            if (end != -1 && end > 0) {
                return JSONObject(candidate.substring(0, end + 1))
            }
        } catch (_: Exception) {}

        // 2. BLE/シリアルのパケット上限等で末尾が切断された不完全JSONの修復
        try {
            var s = candidate
            // もし途中で別のJSON (例: {"type":"ir_status"...) が連結していれば直前で切断
            val secondObjIndex = s.indexOf("{\"type\"", 1)
            if (secondObjIndex != -1) {
                s = s.substring(0, secondObjIndex).trim()
            }

            // クォート数のバランスを確認
            var quoteCount = 0
            var escape = false
            for (c in s) {
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') quoteCount++
            }

            var repaired = s
            // 奇数個のクォート = 文字列の途中で切断されている
            if (quoteCount % 2 != 0) {
                // 末尾の未完コンマやエスケープを除去してクォートを閉じる
                repaired = repaired.trimEnd(',', '\\', ' ') + "\""
            }

            // 中括弧のバランスを確認
            var openBraces = 0
            var closeBraces = 0
            var inQuotes = false
            escape = false
            for (c in repaired) {
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
                    if (c == '{') openBraces++
                    else if (c == '}') closeBraces++
                }
            }

            // 不足している閉じ括弧を補完
            while (openBraces > closeBraces) {
                repaired += "}"
                closeBraces++
            }

            Log.d(TAG, "Repaired JSON: $repaired")
            return JSONObject(repaired)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to repair JSON: ${e.message}")
            return null
        }
    }

    private fun tryParseJson(text: String): ParsedIrSignal? {
        val json = tryRepairAndParseJson(text) ?: return null
        return try {
            // Check if it's an IR message or contains IR keys
            val isIrType = json.optString("type") in listOf("ir_learned", "ir", "ir_receive", "ir_recv", "ir_data", "ir_code")
            val hasIrKeys = json.has("protocol") || json.has("hex") || json.has("ir_code") || json.has("ir") || json.has("code") || json.has("raw")

            if (!isIrType && !hasIrKeys) return null

            val proto = when {
                json.has("protocol") -> json.getString("protocol")
                json.has("proto") -> json.getString("proto")
                else -> "UNKNOWN"
            }

            val hexRaw = when {
                json.has("hex") -> json.getString("hex")
                json.has("code") -> json.getString("code")
                json.has("value") -> json.getString("value")
                json.has("ir") -> json.getString("ir")
                json.has("ir_code") -> json.getString("ir_code")
                json.has("data") -> json.getString("data")
                else -> ""
            }

            val raw = json.optString("raw", "").trimEnd(',', ' ')

            if (hexRaw.isBlank() && raw.isBlank()) return null

            val hex = formatHex(hexRaw)
            val bits = when {
                json.has("bits") -> json.getInt("bits")
                json.has("bit") -> json.getInt("bit")
                raw.isNotBlank() -> {
                    // rawパルス数から推定 (ヘッダー2パルス除外後、各ビット2パルス)
                    val intervals = raw.split(",").filter { it.isNotBlank() }.size
                    if (intervals >= 4) (intervals - 2) / 2 else 32
                }
                else -> 32
            }

            ParsedIrSignal(proto.uppercase(), hex, bits, raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatHex(raw: String?): String {
        if (raw.isNullOrBlank()) return "0x0"
        val clean = raw.trim()
        return if (clean.startsWith("0x", ignoreCase = true)) {
            "0x" + clean.substring(2).uppercase()
        } else {
            "0x" + clean.uppercase()
        }
    }
}

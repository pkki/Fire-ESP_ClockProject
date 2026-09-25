package com.example.model

data class EspSensorData(
    val temperature: Float? = null,
    val humidity: Float? = null,
    val pressure: Float? = null,
    val altitude: Float? = null,
    val rssi: Int? = null,
    val uptimeSec: Long? = null,
    val ahtOk: Boolean = false,
    val bmpOk: Boolean = false,
    val pcfReady: Boolean = false,
    val mq2RawValue: Int? = null,
    val mq2SmokeDetected: Boolean = false,
    val mq2Voltage: Float? = null,
    val ntcTemperatures: List<Float?> = emptyList(), // PCF8574P (0x21) Multiplexed NTC 10K Thermistors
    val ntcRawValues: List<Int?> = emptyList(),     // PCF8574P (0x21) 各チャンネルADC生値 (0-4095)
    val pcfNtcReady: Boolean = false,
    val lastUpdatedEpochMs: Long = 0L,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionType: String = "BLE",
    val errorMessage: String? = null
) {
    /**
     * MQ-2 ガス・煙・火災検知ステータス
     */
    val mq2StatusLabel: String
        get() {
            if (mq2SmokeDetected) return "🔥 火事です！ 火災・煙検知"
            val raw = mq2RawValue ?: return "MQ-2 待機中"
            return when {
                raw < 400 -> "空気清浄 (安全)"
                raw < 800 -> "正常 (微量ガス)"
                raw < 1200 -> "⚠️ ガス・煙 増加注意"
                else -> "🔥 火災・煙 危険域"
            }
        }

    /**
     * 不快指数 (DI: Discomfort Index)
     * DI = 0.81 * T + 0.01 * H * (0.99 * T - 14.3) + 46.3
     */
    val discomfortIndex: Float?
        get() {
            val t = temperature ?: return null
            val h = humidity ?: return null
            return (0.81f * t + 0.01f * h * (0.99f * t - 14.3f) + 46.3f)
        }

    val discomfortLabel: String
        get() {
            val di = discomfortIndex ?: return "--"
            return when {
                di < 55f -> "寒い"
                di < 60f -> "肌寒い"
                di < 68f -> "快適"
                di < 75f -> "過ごしやすい"
                di < 80f -> "やや暑い"
                di < 85f -> "汗ばむ暑さ"
                else -> "猛暑・不快"
            }
        }

    /**
     * 簡易WBGT / 熱中症警戒レベル目安 (日常生活基準)
     */
    val heatstrokeRiskLabel: String
        get() {
            val t = temperature ?: return "計測中"
            val h = humidity ?: 50f
            return when {
                t < 24f -> "安心 (適温)"
                t < 28f -> if (h > 70f) "熱中症注意 (高湿度)" else "注意 (水分補給)"
                t < 31f -> "熱中症警戒 (冷房推奨)"
                t < 35f -> "厳重警戒 (原則冷房)"
                else -> "危険 (外出・活動中止)"
            }
        }

    /**
     * 湿度コンディション (快適 / 乾燥 / 多湿)
     */
    val humidityStatus: String
        get() {
            val h = humidity ?: return "--"
            return when {
                h < 40f -> "乾燥 (加湿推奨)"
                h <= 60f -> "最適・快適"
                h <= 70f -> "やや高め"
                else -> "多湿 (除湿・換気)"
            }
        }

    /**
     * 気圧状態
     */
    val pressureStatus: String
        get() {
            val p = pressure ?: return "--"
            return when {
                p < 1000f -> "低気圧 (頭痛・不調注意)"
                p < 1008f -> "やや低め"
                p <= 1018f -> "標準気圧 (安定)"
                else -> "高気圧 (晴天安定)"
            }
        }
}

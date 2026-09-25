package com.example.model

import java.util.UUID

enum class IrDeviceCategory(val displayName: String, val defaultIcon: String) {
    LIGHTING("照明・シーリングライト", "lightbulb"),
    AIR_CONDITIONER("エアコン・空調", "ac_unit"),
    TV("テレビ・AV機器", "tv"),
    FAN("扇風機・サーキュレーター", "mode_fan"),
    HEATER("ヒーター・暖房", "whatshot"),
    OTHER("その他家電", "devices")
}

data class IrRemoteButton(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "リモコンボタン",
    val category: IrDeviceCategory = IrDeviceCategory.LIGHTING,
    val protocol: String = "NEC",
    val hexCode: String = "",
    val bits: Int = 32,
    val rawCode: String = "",
    val iconName: String = "power_settings_new",
    val colorHex: String = "#3B82F6",
    
    // 自動トリガー・アラーム連携
    val triggerOnAlarm: Boolean = false,         // アラーム時・朝の起床時に自動送信
    val triggerOnNightMode: Boolean = false,     // 夜間常夜灯モード突入時に自動送信（部屋の電気を消す等）
    val triggerOnNightExit: Boolean = false,     // 夜間モード解除時に自動送信（部屋の電気をつける等）
    
    // 時刻指定スケジュール送信
    val isScheduleEnabled: Boolean = false,
    val scheduleHour: Int = 7,
    val scheduleMinute: Int = 0,
    val scheduleDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=月曜..7=日曜
    
    // 送信リピート回数 (調光の長押し・連続送信等)
    val repeatCount: Int = 1,

    // 最終送信時刻
    val lastSentEpochMs: Long = 0L
)

data class IrLearnState(
    val isLearning: Boolean = false,
    val statusMessage: String? = null,
    val lastLearnedSignal: IrRemoteButton? = null,
    val lastSentResult: String? = null,
    val latestRawLog: String? = null
)

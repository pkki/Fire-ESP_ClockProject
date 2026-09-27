package com.example.model

/**
 * State for Ultra-lightweight Voice Assistant & Wake Word system
 */
data class VoiceAssistantState(
    val isEnabled: Boolean = true,
    val isWakeWordListening: Boolean = false,
    val isActivelyListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val recognizedText: String = "",
    val assistantResponseText: String = "",
    val soundLevelRms: Float = 0f, // 0f..1f for audio visualizer
    val lastCommandAction: String? = null,
    val lastError: String? = null,
    val sessionTimestamp: Long = 0L
)

enum class WakeWordOption(val id: String, val displayName: String, val wakePhrases: List<String>) {
    OK_CLOCK(
        "OK_CLOCK",
        "「OK クロック」/「ねえ クロック」",
        listOf("ok クロック", "オッケークロック", "オーケークロック", "ねえクロック", "ねえ クロック", "hey clock", "ヘイクロック", "クロック")
    ),
    OK_GOOGLE(
        "OK_GOOGLE",
        "「OK Google」/「オッケー Google」",
        listOf("ok google", "オッケーグーグル", "オーケーグーグル", "ok グーグル", "hey google", "ヘイグーグル")
    ),
    HEY_ASSISTANT(
        "HEY_ASSISTANT",
        "「ヘイ アシスタント」",
        listOf("ヘイアシスタント", "ヘイ アシスタント", "アシスタント", "hey assistant")
    ),
    CUSTOM(
        "CUSTOM",
        "カスタムキーワード",
        emptyList()
    )
}

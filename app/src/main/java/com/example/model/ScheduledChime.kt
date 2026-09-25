package com.example.model

import com.example.audio.ChimeSound
import java.util.UUID

enum class ChimeAudioSourceType {
    BUILT_IN,
    CUSTOM_FILE,
    VIDEO_SOUND, // 動画の音楽をそのままチャイム音源にする (動画単体再生)
    NONE         // チャイム音なし (リモコン操作や動画再生のみのルーティン用)
}

enum class ChimeVideoSourceType(val displayName: String, val description: String) {
    NONE("動画なし", "通常の時計背景"),
    CUSTOM_FILE("端末の動画ファイル", "取り込んだMP4/WebM動画を背景で再生"),
    PRESET_AURORA("オーロラ・夜空", "幻想的な緑と紫のオーロラ・アニメーション"),
    PRESET_FIREPLACE("暖炉・キャンドル", "温かみのある揺らぐ炎のアンビエント背景"),
    PRESET_STARRY_NIGHT("満天の星空・流星", "夜空に流れる星と静かな宇宙空間"),
    PRESET_RAIN("癒しの雨滴・リフレッシュ", "ガラスに滴る雨と水面の波紋"),
    PRESET_SUNRISE("朝焼け・サンライズ", "爽快な朝の陽射しとグラデーション")
}

data class CustomAudioItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val dateAdded: Long = System.currentTimeMillis()
)

data class CustomVideoItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val dateAdded: Long = System.currentTimeMillis()
)

data class ActiveBackgroundVideo(
    val chimeId: String? = null,
    val chimeLabel: String = "チャイム",
    val videoSourceType: ChimeVideoSourceType = ChimeVideoSourceType.NONE,
    val customVideoPath: String? = null,
    val customVideoName: String? = null,
    val playVideoAudio: Boolean = false,
    val volume: Float = 0.85f,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 60 // 0 = 停止ボタンを押すまで, 30, 60, 180, 300
)

data class ScheduledChime(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,                // 0..23
    val minute: Int,              // 0..59
    val label: String = "チャイム", // e.g. "朝のチャイム", "お昼休憩", "15時リフレッシュ", "終業"
    val isEnabled: Boolean = true,
    // Days of week in ISO 1..7 (1 = Monday, 7 = Sunday)
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val sourceType: ChimeAudioSourceType = ChimeAudioSourceType.BUILT_IN,
    val builtInSound: ChimeSound = ChimeSound.WESTMINSTER,
    val customAudioId: String? = null,
    val customAudioName: String? = null,
    val customAudioPath: String? = null,
    val volume: Float = 0.85f,
    // Video background settings
    val videoSourceType: ChimeVideoSourceType = ChimeVideoSourceType.NONE,
    val customVideoId: String? = null,
    val customVideoName: String? = null,
    val customVideoPath: String? = null,
    val videoDurationSeconds: Int = 60, // -1 = 動画の長さ(1周再生), 0 = 停止ボタンを押すまで, 30, 60, 180...
    val playVideoAudio: Boolean = false,
    // IR remote routine settings (家電リモコン操作連動)
    val irSendEnabled: Boolean = false,
    val irButtonId: String? = null,
    val irButtonName: String? = null
) {
    companion object {
        const val DURATION_VIDEO_LENGTH = -1
        const val DURATION_MANUAL_STOP = 0
    }

    val isVideoOnlyAudio: Boolean
        get() = sourceType == ChimeAudioSourceType.VIDEO_SOUND || (playVideoAudio && videoSourceType == ChimeVideoSourceType.CUSTOM_FILE)

    val formattedTime: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    val repeatDaysText: String
        get() = when {
            daysOfWeek.size == 7 -> "毎日"
            daysOfWeek == setOf(1, 2, 3, 4, 5) -> "平日 (月〜金)"
            daysOfWeek == setOf(6, 7) -> "週末 (土・日)"
            daysOfWeek.isEmpty() -> "1回のみ"
            else -> {
                val dayNames = mapOf(
                    1 to "月", 2 to "火", 3 to "水", 4 to "木", 5 to "金", 6 to "土", 7 to "日"
                )
                daysOfWeek.sorted().mapNotNull { dayNames[it] }.joinToString("・")
            }
        }

    val soundDisplayName: String
        get() = when (sourceType) {
            ChimeAudioSourceType.BUILT_IN -> builtInSound.displayName
            ChimeAudioSourceType.CUSTOM_FILE -> customAudioName ?: "カスタム音声"
            ChimeAudioSourceType.VIDEO_SOUND -> "🎬 動画の音声 (${videoDisplayName})"
            ChimeAudioSourceType.NONE -> if (irSendEnabled) "🔇 チャイム音なし (リモコンのみ)" else "🔇 音なし"
        }

    val remoteActionDisplayName: String
        get() = if (irSendEnabled && !irButtonName.isNullOrBlank()) {
            "📡 リモコン: $irButtonName"
        } else {
            "リモコン連動なし"
        }

    val videoDisplayName: String
        get() = when (videoSourceType) {
            ChimeVideoSourceType.NONE -> "動画なし"
            ChimeVideoSourceType.CUSTOM_FILE -> customVideoName ?: "カスタム動画"
            else -> videoSourceType.displayName
        }
}

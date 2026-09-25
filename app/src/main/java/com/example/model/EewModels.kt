package com.example.model

enum class EewScaleLevel(val scaleValue: Int, val label: String, val colorHex: String) {
    SCALE_1(10, "震度1", "#8c8c8c"),
    SCALE_2(20, "震度2", "#4c8bff"),
    SCALE_3(30, "震度3", "#2ca02c"),
    SCALE_4(40, "震度4", "#f2cf00"),
    SCALE_5_LOWER(45, "震度5弱", "#ff9a00"),
    SCALE_5_UPPER(50, "震度5強", "#ff6300"),
    SCALE_6_LOWER(55, "震度6弱", "#ff2020"),
    SCALE_6_UPPER(60, "震度6強", "#c2001f"),
    SCALE_7(70, "震度7", "#a800a8")
}

enum class EewTestScenario(val id: String, val title: String, val subtitle: String, val severity: String) {
    HYUGANADA_M71("hyuganada_m71", "日向灘 M7.1（最大震度6弱）", "宮崎県南部平野部・鹿児島・高知", "6弱"),
    NOTO_M76("noto_m76", "能登半島 M7.6（最大震度7）", "石川県能登・富山・新潟・福井", "7"),
    NANKAI_M82("nankai_m82", "南海トラフ M8.2（最大震度7・広域警報）", "和歌山・三重・高知・愛知・徳島・大阪", "7"),
    TOKYO_M65("tokyo_m65", "東京湾 M6.5（最大震度5強・首都直下）", "東京23区・神奈川東部・千葉北西部", "5強"),
    CANCEL_DEMO("cancel_demo", "取消報テスト（速報取消）", "緊急地震速報の取り消しアナウンス", "取消")
}

data class EewLiveState(
    val isActive: Boolean = false,
    val summaryText: String = "",
    val lastUpdatedEpochMs: Long = 0L,
    val isTestMode: Boolean = false
)

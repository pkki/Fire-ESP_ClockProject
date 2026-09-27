package com.example.model

/**
 * Predefined Equalizer Presets
 */
enum class EqualizerPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val bandGainsDb: List<Int>, // 5 frequency bands: ~60Hz, ~230Hz, ~910Hz, ~3600Hz, ~14000Hz [-15dB .. +15dB]
    val bassCutMode: BassCutMode = BassCutMode.OFF
) {
    FLAT(
        "FLAT",
        "フラット (標準)",
        "原音に忠実なバランス",
        listOf(0, 0, 0, 0, 0),
        BassCutMode.OFF
    ),
    BASS_REDUCE(
        "BASS_REDUCE",
        "低音カット (小型スピーカー保護)",
        "低音域を抑えて小型・時計スピーカーの音割れ・ビビリを強力に防止",
        listOf(-12, -7, 0, 2, 3),
        BassCutMode.STRONG
    ),
    BASS_CUT_LIGHT(
        "BASS_CUT_LIGHT",
        "低音マイルドカット (スッキリ)",
        "余分な重低音のみを適度に抑えて聴きやすくクリアに",
        listOf(-6, -3, 0, 1, 2),
        BassCutMode.LIGHT
    ),
    VOCAL(
        "VOCAL",
        "ボーカル・声くっきり",
        "中音域（人の声・ボーカル・時報・アナウンス）を明瞭化",
        listOf(-5, 2, 6, 3, -1),
        BassCutMode.MEDIUM
    ),
    TREBLE_BOOST(
        "TREBLE_BOOST",
        "高音強調・クリスタルトーン",
        "高音域の抜けと繊細さを高める",
        listOf(-4, -2, 1, 5, 7),
        BassCutMode.LIGHT
    ),
    BASS_BOOST(
        "BASS_BOOST",
        "低音強化 (外部・大型スピーカー用)",
        "迫力のある重低音をブースト（※低音に強いスピーカー推奨）",
        listOf(8, 5, 0, -1, -2),
        BassCutMode.OFF
    ),
    NIGHT_RELAX(
        "NIGHT_RELAX",
        "ナイト・リラックス",
        "耳障りな周波数と低音の振動をカットした優しい音質",
        listOf(-8, -4, 0, -2, -5),
        BassCutMode.STRONG
    ),
    POP(
        "POP",
        "ポップス",
        "明るくメリハリのあるリズミカルな音質",
        listOf(3, 1, 2, 4, 3),
        BassCutMode.OFF
    ),
    ROCK(
        "ROCK",
        "ロック",
        "ドンシャリ系のエネルギッシュなサウンド",
        listOf(6, 3, -2, 3, 5),
        BassCutMode.OFF
    ),
    CLASSICAL(
        "CLASSICAL",
        "クラシック・アコースティック",
        "空間の広がりとダイナミックレンジを大切にしたサウンド",
        listOf(4, 2, -1, 3, 4),
        BassCutMode.OFF
    ),
    CUSTOM(
        "CUSTOM",
        "カスタム (手動イコライザー)",
        "5つの周波数バンドとお好みの低音カットを自由に設定",
        listOf(0, 0, 0, 0, 0),
        BassCutMode.OFF
    )
}

/**
 * Bass Cut Filter strength specifically for devices with weak / rattling speakers
 */
enum class BassCutMode(
    val id: String,
    val displayName: String,
    val description: String,
    val lowFreqAttenuationDb: Int
) {
    OFF("OFF", "OFF (低音そのまま)", "低音をカットせずフルレンジで再生", 0),
    LIGHT("LIGHT", "弱カット (-4dB)", "重低音の余分な唸りを軽く除去", -4),
    MEDIUM("MEDIUM", "中カット (-8dB)", "一般的な内蔵スピーカーの音割れを防止", -8),
    STRONG("STRONG", "強カット (-12dB)", "小型スピーカーのビビリ・振動を強力に防止", -12),
    EXTREME("EXTREME", "極カット (-16dB)", "超小型スピーカー・夜間の超低音完全カット", -16)
}

/**
 * Master Equalizer State
 */
data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandGainsDb: List<Int> = listOf(0, 0, 0, 0, 0), // 5 bands in dB [-15 .. +15]
    val bassCutMode: BassCutMode = BassCutMode.OFF,
    val isSupportedOnDevice: Boolean = true,
    val centerFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000)
) {
    /**
     * Compute effective band gains considering the bass cut mode on low bands
     */
    fun getEffectiveBandGains(): List<Int> {
        if (!isEnabled) return listOf(0, 0, 0, 0, 0)
        val cut = bassCutMode.lowFreqAttenuationDb
        return listOf(
            (bandGainsDb.getOrElse(0) { 0 } + cut).coerceIn(-15, 15),
            (bandGainsDb.getOrElse(1) { 0 } + (cut / 2)).coerceIn(-15, 15),
            bandGainsDb.getOrElse(2) { 0 },
            bandGainsDb.getOrElse(3) { 0 },
            bandGainsDb.getOrElse(4) { 0 }
        )
    }
}

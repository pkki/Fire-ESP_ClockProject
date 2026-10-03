package com.example.model

/**
 * Predefined Equalizer Presets for standard music player experience
 */
enum class EqualizerPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val bandGainsDb: List<Int>, // 5 frequency bands: ~60Hz, ~230Hz, ~910Hz, ~3600Hz, ~14000Hz [-15dB .. +15dB]
    val bassBoostStrength: Int = 0, // 0..1000 (0% to 100%)
    val virtualizerStrength: Int = 0, // 0..1000 (0% to 100%)
    val bassCutMode: BassCutMode = BassCutMode.OFF
) {
    FLAT(
        "FLAT",
        "フラット (標準)",
        "原音に忠実なナチュラルバランス",
        listOf(0, 0, 0, 0, 0),
        bassBoostStrength = 0,
        virtualizerStrength = 0,
        bassCutMode = BassCutMode.OFF
    ),
    BASS_BOOST(
        "BASS_BOOST",
        "重低音ブースト (Bass)",
        "迫力のあるディープな重低音とキックを強力に強化",
        listOf(9, 6, 2, 0, -1),
        bassBoostStrength = 800,
        virtualizerStrength = 200,
        bassCutMode = BassCutMode.OFF
    ),
    ROCK(
        "ROCK",
        "ロック (Rock)",
        "パンチのある低音と伸びやかな高音のドンシャリサウンド",
        listOf(6, 3, -1, 3, 5),
        bassBoostStrength = 500,
        virtualizerStrength = 300,
        bassCutMode = BassCutMode.OFF
    ),
    POP(
        "POP",
        "ポップス (Pop)",
        "明るくメリハリのあるリズミカルで心地よいサウンド",
        listOf(3, 1, 2, 4, 3),
        bassBoostStrength = 350,
        virtualizerStrength = 200,
        bassCutMode = BassCutMode.OFF
    ),
    JAZZ(
        "JAZZ",
        "ジャズ (Jazz)",
        "温かみのあるウッドベースと繊細なピアノ・サックス",
        listOf(4, 3, 0, 2, 3),
        bassBoostStrength = 400,
        virtualizerStrength = 300,
        bassCutMode = BassCutMode.OFF
    ),
    CLASSICAL(
        "CLASSICAL",
        "クラシック (Classical)",
        "ホールの残響とダイナミックレンジ豊かな空間表現",
        listOf(5, 3, 0, 3, 4),
        bassBoostStrength = 200,
        virtualizerStrength = 600,
        bassCutMode = BassCutMode.OFF
    ),
    EDM(
        "EDM",
        "ダンス / EDM (Club)",
        "フロアを揺らすサブベースと刺激的なビートサウンド",
        listOf(10, 7, 0, 3, 6),
        bassBoostStrength = 900,
        virtualizerStrength = 450,
        bassCutMode = BassCutMode.OFF
    ),
    VOCAL(
        "VOCAL",
        "ボーカル強調 (Vocal)",
        "歌声・アナウンス・ラジオの人の声を前面にくっきりと",
        listOf(-2, 2, 6, 4, 1),
        bassBoostStrength = 0,
        virtualizerStrength = 150,
        bassCutMode = BassCutMode.OFF
    ),
    TREBLE_BOOST(
        "TREBLE_BOOST",
        "高音強調 (Treble)",
        "クリスタルトーンのような澄んだ透明感と音の抜け",
        listOf(-3, -1, 1, 6, 8),
        bassBoostStrength = 0,
        virtualizerStrength = 250,
        bassCutMode = BassCutMode.OFF
    ),
    NIGHT_RELAX(
        "NIGHT_RELAX",
        "ナイト・リラックス",
        "耳当たりが優しく刺激のない落ち着いた静かな音質",
        listOf(-5, -2, 0, -1, -4),
        bassBoostStrength = 0,
        virtualizerStrength = 200,
        bassCutMode = BassCutMode.OFF
    ),
    BASS_REDUCE(
        "BASS_REDUCE",
        "低音カット (スピーカー保護)",
        "小型スピーカーのビビリ・音割れを抑えるモード",
        listOf(-10, -5, 0, 2, 3),
        bassBoostStrength = 0,
        virtualizerStrength = 0,
        bassCutMode = BassCutMode.STRONG
    ),
    BASS_CUT_LIGHT(
        "BASS_CUT_LIGHT",
        "低音マイルドカット",
        "余分な重低音を適度に抑えてすっきり聴きやすく",
        listOf(-5, -2, 0, 1, 2),
        bassBoostStrength = 0,
        virtualizerStrength = 0,
        bassCutMode = BassCutMode.LIGHT
    ),
    CUSTOM(
        "CUSTOM",
        "カスタム (手動イコライザー)",
        "周波数スライダーや低音ブーストをお好みに調整",
        listOf(0, 0, 0, 0, 0),
        bassBoostStrength = 0,
        virtualizerStrength = 0,
        bassCutMode = BassCutMode.OFF
    )
}

/**
 * Bass Cut Filter strength specifically for devices with weak / rattling speakers (optional protection)
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
    val bassBoostStrength: Int = 0, // 0..1000 (0% to 100% hardware bass boost)
    val virtualizerStrength: Int = 0, // 0..1000 (0% to 100% 3D surround sound)
    val bassCutMode: BassCutMode = BassCutMode.OFF,
    val isSupportedOnDevice: Boolean = true,
    val centerFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val autoVolumeNormalization: Boolean = true, // 自動音量ノーマライズ (最大音量まで自動引き上げ)
    val loudnessBoostGainMb: Int = 400 // ハードウェア最大音量ブースト量 (mB: 0..1200)
) {
    /**
     * Compute effective band gains considering the optional bass cut mode on low bands
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


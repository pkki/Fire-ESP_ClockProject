package com.example.voice

import com.example.model.ChimeVideoSourceType
import com.example.model.VideoDisplayLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of command processing
 */
data class VoiceCommandResult(
    val isHandled: Boolean,
    val spokenResponse: String,
    val displayMessage: String = spokenResponse,
    val actionName: String? = null
)

/**
 * Action callbacks interface for Voice Command execution
 */
interface VoiceCommandCallbacks {
    fun getCurrentTimeText(): String
    fun getCurrentDateText(): String
    fun getWeatherSummary(): String
    fun playMusic()
    fun pauseMusic()
    fun nextMusic()
    fun prevMusic()
    fun setVolume(volume: Float)
    fun adjustVolume(delta: Float)
    fun playVideo(videoType: ChimeVideoSourceType, customPath: String?, customName: String?, layer: VideoDisplayLayer)
    fun stopVideo()
    fun toggleVideoLayer()
    fun startTimer(seconds: Int)
    fun stopAlarm()
    fun toggleNightMode()
    fun setNightMode(enabled: Boolean)
    fun cycleColorTheme()
    fun triggerIrButton(buttonNameOrId: String): Boolean
    fun getRegisteredIrButtons(): List<String>
    fun triggerEewTest()
    fun setEqualizerPreset(preset: com.example.model.EqualizerPreset)
    fun setBassCutMode(mode: com.example.model.BassCutMode)
    fun resetEqualizer()
    fun setAntiNoiseSilence(enabled: Boolean)
    fun startStopwatch()
    fun pauseStopwatch()
    fun resetStopwatch()
    fun recordStopwatchLap()
}

/**
 * Ultra-lightweight on-device regex & keyword voice command parser with zero latency.
 */
object VoiceCommandProcessor {

    fun processCommand(rawText: String, callbacks: VoiceCommandCallbacks): VoiceCommandResult {
        val text = rawText.trim().lowercase(Locale.JAPANESE)
            .replace(" ", "")
            .replace("　", "")
            .replace("、", "")
            .replace("。", "")
            .replace("！", "")
            .replace("？", "")

        if (text.isEmpty()) {
            return VoiceCommandResult(false, "声が聞き取れませんでした。もう一度お話しください。")
        }

        // 1. Time and Date commands (時間・日時)
        if (matchesAny(text, "何時", "いま何時", "今何時", "現在の時間", "時間を教えて", "何時ですか", "何時何分")) {
            val time = callbacks.getCurrentTimeText()
            return VoiceCommandResult(true, "ただ今の時刻は $time です。", "🕒 $time", "TIME")
        }
        if (matchesAny(text, "何日", "今日何日", "今日の日付", "日にち", "何曜日", "今日の曜日")) {
            val date = callbacks.getCurrentDateText()
            return VoiceCommandResult(true, "今日は $date です。", "📅 $date", "DATE")
        }

        // 2. Music Player controls (音楽・BGM)
        if (matchesAny(text, "音楽かけて", "音楽をかけて", "音楽再生", "音楽を再生", "曲をかけて", "曲を流して", "曲再生", "ミュージックスタート", "bgm流して", "音楽スタート", "再生して", "音楽聴きたい")) {
            callbacks.playMusic()
            return VoiceCommandResult(true, "音楽を再生します。", "🎵 音楽を再生中", "MUSIC_PLAY")
        }
        if (matchesAny(text, "音楽止めて", "音楽を止めて", "曲止めて", "曲を止めて", "ストップ", "停止", "一時停止", "ポーズ", "音楽ストップ", "演奏停止", "ミュート解除")) {
            callbacks.pauseMusic()
            return VoiceCommandResult(true, "音楽を一時停止しました。", "⏸ 音楽一時停止", "MUSIC_PAUSE")
        }
        if (matchesAny(text, "次の曲", "次の曲にして", "スキップ", "次のトラック", "曲送って", "次へ")) {
            callbacks.nextMusic()
            return VoiceCommandResult(true, "次の曲へスキップします。", "⏭ 次の曲", "MUSIC_NEXT")
        }
        if (matchesAny(text, "前の曲", "前の曲にして", "最初から", "前のトラック", "曲戻して", "前へ")) {
            callbacks.prevMusic()
            return VoiceCommandResult(true, "前の曲に戻ります。", "⏮ 前の曲", "MUSIC_PREV")
        }

        // 3. Volume Adjustment (音量・ボリューム)
        if (matchesAny(text, "音量上げて", "音量を上げて", "ボリューム上げて", "大きくして", "声大きくして", "音大きくして", "大きく")) {
            callbacks.adjustVolume(0.15f)
            return VoiceCommandResult(true, "音量を上げました。", "🔊 音量UP", "VOLUME_UP")
        }
        if (matchesAny(text, "音量下げて", "音量を下げて", "ボリューム下げて", "小さくして", "声小さくして", "音小さくして", "小さく", "静かにして")) {
            callbacks.adjustVolume(-0.15f)
            return VoiceCommandResult(true, "音量を下げました。", "🔉 音量DOWN", "VOLUME_DOWN")
        }
        if (matchesAny(text, "音量最大", "音量を最大にして", "ボリューム100", "音量100")) {
            callbacks.setVolume(1.0f)
            return VoiceCommandResult(true, "音量を最大に設定しました。", "🔊 音量100%", "VOLUME_MAX")
        }
        if (matchesAny(text, "音量半分", "音量50", "ボリューム半分")) {
            callbacks.setVolume(0.5f)
            return VoiceCommandResult(true, "音量を50%に設定しました。", "🔉 音量50%", "VOLUME_50")
        }
        if (matchesAny(text, "消音", "ミュートにして", "ミュート", "音消して")) {
            callbacks.setVolume(0.0f)
            return VoiceCommandResult(true, "消音にしました。", "🔇 ミュート", "VOLUME_MUTE")
        }

        // 3.5 Equalizer & Bass Protection controls (イコライザー・音質調整・低音保護)
        if (matchesAny(text, "低音カット", "低音カットにして", "スピーカー保護", "低音抑えて", "音割れ防止", "ビビリ防止", "低音減らして", "低音下げて")) {
            callbacks.setBassCutMode(com.example.model.BassCutMode.STRONG)
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.BASS_REDUCE)
            return VoiceCommandResult(true, "低音カットフィルターを有効にしました。小型スピーカーの音割れや振動を防止します。", "🛡️ 低音カット (スピーカー保護)", "EQ_BASS_REDUCE")
        }
        if (matchesAny(text, "ボーカル強調", "声くっきり", "ボーカルモード", "歌声強調", "アナウンス強調", "声を聞きやすく")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.VOCAL)
            return VoiceCommandResult(true, "ボーカル強調モードに設定しました。歌声や会話がクリアになります。", "🎤 ボーカル・声くっきり", "EQ_VOCAL")
        }
        if (matchesAny(text, "高音強調", "クリアトーン", "クリスタルトーン", "高音あげて", "高音上げて", "シャリシャリ")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.TREBLE_BOOST)
            return VoiceCommandResult(true, "高音強調モードに設定しました。", "✨ 高音強調", "EQ_TREBLE")
        }
        if (matchesAny(text, "低音強化", "重低音", "バスブースト", "低音上げて", "低音ブースト")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.BASS_BOOST)
            return VoiceCommandResult(true, "低音強化モードに設定しました。", "🔊 低音ブースト", "EQ_BASS_BOOST")
        }
        if (matchesAny(text, "イコライザーリセット", "フラットにして", "普通の音にして", "標準の音にして", "イコライザー標準", "フラットイコライザー")) {
            callbacks.resetEqualizer()
            return VoiceCommandResult(true, "イコライザーをフラット（標準）にリセットしました。", "🎚️ イコライザー標準", "EQ_FLAT")
        }

        // 3.6 Earphone Jack Anti-Noise Keep-Alive (常時無音再生・イヤホンジャックノイズ防止)
        if (matchesAny(text, "無音再生", "無音流して", "無音再生オン", "ノイズ防止", "ノイズ防止オン", "ノイズ対策オン", "無音を流して", "イヤホンノイズ防止", "無音つけて", "無音流し続けて")) {
            callbacks.setAntiNoiseSilence(true)
            return VoiceCommandResult(true, "常時無音再生を開始しました。イヤホンジャックの待機ノイズやポップ音を防止します。", "🔇 ノイズ防止 (無音再生中)", "ANTI_NOISE_ON")
        }
        if (matchesAny(text, "無音再生止めて", "無音停止", "無音再生オフ", "ノイズ防止オフ", "無音止めて", "無音消して")) {
            callbacks.setAntiNoiseSilence(false)
            return VoiceCommandResult(true, "無音再生を停止しました。", "⚪ ノイズ防止 停止", "ANTI_NOISE_OFF")
        }

        // 4. Video & Ambient Backgrounds (動画・アンビエント映像)
        if (matchesAny(text, "オーロラ", "オーロラ見せて", "オーロラ再生", "オーロラにして")) {
            callbacks.playVideo(ChimeVideoSourceType.PRESET_AURORA, null, "オーロラ夜空", VideoDisplayLayer.BACKGROUND)
            return VoiceCommandResult(true, "オーロラ映像を背景で再生します。", "🌌 オーロラ映像", "VIDEO_AURORA")
        }
        if (matchesAny(text, "焚き火", "焚火", "暖炉", "たき火", "炎見せて", "キャンドル")) {
            callbacks.playVideo(ChimeVideoSourceType.PRESET_FIREPLACE, null, "暖炉と焚き火", VideoDisplayLayer.BACKGROUND)
            return VoiceCommandResult(true, "暖炉の映像を背景で再生します。", "🔥 暖炉映像", "VIDEO_FIREPLACE")
        }
        if (matchesAny(text, "星空", "星座", "プラネタリウム", "星見せて")) {
            callbacks.playVideo(ChimeVideoSourceType.PRESET_STARRY_NIGHT, null, "満天の星空", VideoDisplayLayer.BACKGROUND)
            return VoiceCommandResult(true, "星空の映像を背景で再生します。", "✨ 星空映像", "VIDEO_STARS")
        }
        if (matchesAny(text, "雨の音", "雨見せて", "雨", "雨音")) {
            callbacks.playVideo(ChimeVideoSourceType.PRESET_RAIN, null, "雨のアンビエンス", VideoDisplayLayer.BACKGROUND)
            return VoiceCommandResult(true, "雨のアンビエント映像を再生します。", "🌧 雨映像", "VIDEO_RAIN")
        }
        if (matchesAny(text, "朝焼け", "日の出", "サンライズ")) {
            callbacks.playVideo(ChimeVideoSourceType.PRESET_SUNRISE, null, "朝焼け", VideoDisplayLayer.BACKGROUND)
            return VoiceCommandResult(true, "朝焼けの映像を再生します。", "🌅 朝焼け映像", "VIDEO_SUNRISE")
        }
        if (matchesAny(text, "動画再生", "動画流して", "動画を再生", "ビデオ再生", "動画かけて")) {
            callbacks.playVideo(ChimeVideoSourceType.CUSTOM_FILE, null, "カスタム動画", VideoDisplayLayer.BACKGROUND)
            return VoiceCommandResult(true, "カスタム動画を再生します。", "🎬 動画再生", "VIDEO_CUSTOM")
        }
        if (matchesAny(text, "全画面にして", "前面にして", "大きく動画見せて", "全画面表示", "フルスクリーンにして")) {
            callbacks.toggleVideoLayer()
            return VoiceCommandResult(true, "動画の表示レイヤーを切り替えました。", "📺 全画面/背景切替", "VIDEO_TOGGLE_LAYER")
        }
        if (matchesAny(text, "動画止めて", "動画停止", "映像消して", "背景戻して", "映像止めて")) {
            callbacks.stopVideo()
            return VoiceCommandResult(true, "動画再生を終了しました。", "⏹ 動画終了", "VIDEO_STOP")
        }

        // 5. Timer, Stopwatch, and Alarm (タイマー・ストップウォッチ・アラーム)
        if (matchesAny(text, "ストップウォッチスタート", "ストップウォッチ開始", "ストップウォッチつけて", "ストップウォッチ動かして", "タイム測って", "時間測って", "時間計測開始")) {
            callbacks.startStopwatch()
            return VoiceCommandResult(true, "ストップウォッチを開始しました。", "⏱️ ストップウォッチ開始", "STOPWATCH_START")
        }
        if (matchesAny(text, "ストップウォッチストップ", "ストップウォッチ停止", "ストップウォッチ一時停止", "ストップウォッチ止めて", "タイム止めて", "計測停止")) {
            callbacks.pauseStopwatch()
            return VoiceCommandResult(true, "ストップウォッチを一時停止しました。", "⏸️ ストップウォッチ一時停止", "STOPWATCH_PAUSE")
        }
        if (matchesAny(text, "ストップウォッチリセット", "ストップウォッチクリア", "ストップウォッチ初期化", "タイムリセット")) {
            callbacks.resetStopwatch()
            return VoiceCommandResult(true, "ストップウォッチをリセットしました。", "🔄 ストップウォッチリセット", "STOPWATCH_RESET")
        }
        if (matchesAny(text, "ラップ", "ラップ記録", "ラップタイム", "スプリット")) {
            callbacks.recordStopwatchLap()
            return VoiceCommandResult(true, "ラップタイムを記録しました。", "🚩 ラップ記録", "STOPWATCH_LAP")
        }

        val timerMatch = Regex("(\\d+)(分|秒)(タイマー|測って|計って|カウントダウン)?").find(text)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 3
            val unit = timerMatch.groupValues[2]
            val totalSeconds = if (unit == "分") amount * 60 else amount
            callbacks.startTimer(totalSeconds)
            val msg = "${amount}${unit}のタイマーを開始しました。"
            return VoiceCommandResult(true, msg, "⏱ $msg", "TIMER_START")
        }
        if (matchesAny(text, "タイマー", "3分タイマー", "ラーメンタイマー")) {
            callbacks.startTimer(180)
            return VoiceCommandResult(true, "3分タイマーを開始しました。", "⏱ 3分タイマー開始", "TIMER_START_3M")
        }
        if (matchesAny(text, "アラーム止めて", "アラーム停止", "うるさい", "起きたよ", "起きた", "おはよう止めて", "チャイム止めて", "時報止めて", "音止めて", "ストップ", "チャイム停止", "時報停止")) {
            callbacks.stopAlarm()
            return VoiceCommandResult(true, "アラーム・時報チャイムを停止しました。", "⏰ アラーム・時報停止", "ALARM_STOP")
        }

        // 6. Weather & Temperature (天気・気温)
        if (matchesAny(text, "天気", "今日の天気", "天気を教えて", "雨降る", "傘いる", "気温", "温度", "何度")) {
            val weather = callbacks.getWeatherSummary()
            return VoiceCommandResult(true, weather, "☀️ $weather", "WEATHER")
        }

        // 7. Night Mode & Screen Lighting (夜間モード・テーマ)
        if (matchesAny(text, "夜間モード", "おやすみ", "暗くして", "ナイトモード", "画面暗くして", "寝るよ")) {
            callbacks.setNightMode(true)
            return VoiceCommandResult(true, "夜間モードをオンにしました。おやすみなさい。", "🌙 夜間モードON", "NIGHT_ON")
        }
        if (matchesAny(text, "夜間モード解除", "明るくして", "起きた", "昼間モード", "普通にして")) {
            callbacks.setNightMode(false)
            return VoiceCommandResult(true, "夜間モードを解除しました。", "☀️ 通常モード", "NIGHT_OFF")
        }
        if (matchesAny(text, "テーマ変えて", "色変えて", "カラー変更", "文字色変えて", "デザイン変えて")) {
            callbacks.cycleColorTheme()
            return VoiceCommandResult(true, "時計のカラーテーマを変更しました。", "🎨 カラー変更", "THEME_CYCLE")
        }

        // 8. Infrared (IR) Smart Home Control (家電操作・赤外線)
        if (matchesAny(text, "電気つけて", "照明つけて", "ライトつけて", "明かりつけて", "電気点けて", "照明点けて")) {
            val ok = callbacks.triggerIrButton("照明ON") || callbacks.triggerIrButton("電気") || callbacks.triggerIrButton("LIGHT") || callbacks.triggerIrButton("POWER")
            return if (ok) {
                VoiceCommandResult(true, "照明をつけました。", "💡 照明ON送信", "IR_LIGHT_ON")
            } else {
                VoiceCommandResult(true, "照明のリモコン信号が見つかりませんでした。設定のIRリモコンでボタン名を「照明」や「電気」と登録してください。", "⚠️ リモコン未登録", "IR_NOT_FOUND")
            }
        }
        if (matchesAny(text, "電気消して", "照明消して", "ライト消して", "明かり消して")) {
            val ok = callbacks.triggerIrButton("照明OFF") || callbacks.triggerIrButton("電気") || callbacks.triggerIrButton("消灯")
            return if (ok) {
                VoiceCommandResult(true, "照明を消しました。", "💡 照明OFF送信", "IR_LIGHT_OFF")
            } else {
                VoiceCommandResult(true, "消灯のリモコン信号が見つかりませんでした。IRリモコン登録をご確認ください。", "⚠️ リモコン未登録", "IR_NOT_FOUND")
            }
        }
        if (matchesAny(text, "エアコンつけて", "冷房つけて", "暖房つけて")) {
            val ok = callbacks.triggerIrButton("エアコンON") || callbacks.triggerIrButton("エアコン") || callbacks.triggerIrButton("AIRCON")
            return if (ok) {
                VoiceCommandResult(true, "エアコンをオンにしました。", "❄️ エアコンON送信", "IR_AC_ON")
            } else {
                VoiceCommandResult(true, "エアコンのリモコンが登録されていません。", "⚠️ リモコン未登録", "IR_NOT_FOUND")
            }
        }
        if (matchesAny(text, "エアコン消して", "エアコン止めて")) {
            val ok = callbacks.triggerIrButton("エアコンOFF") || callbacks.triggerIrButton("エアコン停止")
            return if (ok) {
                VoiceCommandResult(true, "エアコンを停止しました。", "❄️ エアコンOFF送信", "IR_AC_OFF")
            } else {
                VoiceCommandResult(true, "エアコンのリモコンが登録されていません。", "⚠️ リモコン未登録", "IR_NOT_FOUND")
            }
        }
        if (matchesAny(text, "テレビつけて", "テレビ点けて")) {
            val ok = callbacks.triggerIrButton("テレビ") || callbacks.triggerIrButton("TV")
            return if (ok) {
                VoiceCommandResult(true, "テレビの電源を送信しました。", "📺 テレビ電源送信", "IR_TV")
            } else {
                VoiceCommandResult(true, "テレビのリモコンが登録されていません。", "⚠️ リモコン未登録", "IR_NOT_FOUND")
            }
        }

        // Generic IR button match (e.g. "扇風機押して", "ボタン1押して")
        val irButtons = callbacks.getRegisteredIrButtons()
        for (btn in irButtons) {
            val cleanBtn = btn.lowercase().replace(" ", "")
            if (cleanBtn.isNotEmpty() && text.contains(cleanBtn)) {
                callbacks.triggerIrButton(btn)
                return VoiceCommandResult(true, "リモコン「$btn」を送信しました。", "📡 $btn 送信", "IR_CUSTOM")
            }
        }

        // 9. Equalizer & Sound Quality (音質・イコライザー・低音カット)
        if (matchesAny(text, "低音カット", "低音下げて", "低音抑えて", "ビビリ音防止", "音割れ防止", "スピーカー保護")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.BASS_REDUCE)
            return VoiceCommandResult(true, "低音カットモードに設定しました。小型スピーカーの音割れやビビリ音を防止します。", "🎚️ 音質: 低音カット (スピーカー保護)", "EQ_BASS_REDUCE")
        }
        if (matchesAny(text, "低音マイルドカット", "低音少しカット", "スッキリ音質")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.BASS_CUT_LIGHT)
            return VoiceCommandResult(true, "低音マイルドカットに設定しました。", "🎚️ 音質: 低音マイルドカット", "EQ_BASS_CUT_LIGHT")
        }
        if (matchesAny(text, "ボーカル強調", "声くっきり", "声聞きやすく", "アナウンス強調", "ボーカルモード")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.VOCAL)
            return VoiceCommandResult(true, "ボーカル・音声くっきりモードに設定しました。", "🎚️ 音質: ボーカル・声くっきり", "EQ_VOCAL")
        }
        if (matchesAny(text, "高音強調", "クリスタルトーン", "高音上げて", "高音クリア")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.TREBLE_BOOST)
            return VoiceCommandResult(true, "高音強調モードに設定しました。", "🎚️ 音質: 高音強調", "EQ_TREBLE")
        }
        if (matchesAny(text, "重低音", "低音ブースト", "低音強調", "低音上げて", "バスブースト")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.BASS_BOOST)
            return VoiceCommandResult(true, "重低音ブーストに設定しました。", "🎚️ 音質: 重低音ブースト", "EQ_BASS_BOOST")
        }
        if (matchesAny(text, "ナイトリラックス", "リラックス音質", "優しい音", "耳に優しい音")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.NIGHT_RELAX)
            return VoiceCommandResult(true, "ナイトリラックス音質に設定しました。", "🎚️ 音質: ナイトリラックス", "EQ_NIGHT_RELAX")
        }
        if (matchesAny(text, "ポップス", "ポップス音質")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.POP)
            return VoiceCommandResult(true, "ポップス音質に設定しました。", "🎚️ 音質: ポップス", "EQ_POP")
        }
        if (matchesAny(text, "ロック", "ロック音質", "ドンシャリ")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.ROCK)
            return VoiceCommandResult(true, "ロック音質に設定しました。", "🎚️ 音質: ロック", "EQ_ROCK")
        }
        if (matchesAny(text, "クラシック", "クラシック音質", "アコースティック")) {
            callbacks.setEqualizerPreset(com.example.model.EqualizerPreset.CLASSICAL)
            return VoiceCommandResult(true, "クラシック音質に設定しました。", "🎚️ 音質: クラシック", "EQ_CLASSICAL")
        }
        if (matchesAny(text, "イコライザーリセット", "フラットにして", "標準の音質", "イコライザー標準", "音質標準", "イコライザーフラット", "音質フラット")) {
            callbacks.resetEqualizer()
            return VoiceCommandResult(true, "イコライザーを標準のフラットに戻しました。", "🎚️ 音質: フラット (標準)", "EQ_FLAT")
        }

        // 10. EEW / Emergency Warning (地震速報テスト)
        if (matchesAny(text, "地震テスト", "緊急地震速報テスト", "地震速報鳴らして")) {
            callbacks.triggerEewTest()
            return VoiceCommandResult(true, "緊急地震速報のテスト表示を開始します。", "🚨 EEWテスト", "EEW_TEST")
        }

        // 11. Help / Greetings (ヘルプ・挨拶)
        if (matchesAny(text, "こんにちは", "ハロー", "hello", "調子はどう")) {
            return VoiceCommandResult(true, "こんにちは！何かお手伝いできることはありますか？時間、天気、音楽再生、音質調整、家電操作などをお答えできます。", "👋 こんにちは！", "GREETING")
        }
        if (matchesAny(text, "何ができる", "ヘルプ", "使い方", "コマンド一覧", "何ができるの")) {
            return VoiceCommandResult(
                true,
                "「音楽かけて」「低音カットにして」「いま何時？」「今日の天気は？」「3分タイマー」「電気つけて」「夜間モードにして」などと話しかけてください。",
                "💡 音声コマンド例:\n• 音楽かけて / 次の曲\n• 低音カットにして / ボーカル強調\n• いま何時？ / 今日の天気\n• 3分タイマー / アラーム停止\n• 電気つけて / エアコン消して\n• 夜間モード / オーロラ見せて",
                "HELP"
            )
        }

        // Fallback for general speech
        return VoiceCommandResult(
            false,
            "「$rawText」ですね。音楽再生、時間、天気、タイマー、家電操作などをお手伝いできます。「何ができる？」と話しかけてみてください。",
            "❓ $rawText"
        )
    }

    private fun matchesAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { kw ->
            val cleanKw = kw.lowercase().replace(" ", "")
            text.contains(cleanKw)
        }
    }
}

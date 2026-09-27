package com.example.camera

/**
 * HTML, CSS and JavaScript components for the Web Dashboard Music Player:
 * - Full-featured Music Player Console for Web Dashboard
 * - Interactive Timeline / Seek Bar, Volume Control with Quick Presets
 * - Repeat Mode (ALL, ONE, OFF, SHUFFLE), Play/Pause, Next/Prev, Stop
 * - Real-time animated audio visualizer & vinyl record rotation
 * - Integrated Playlist with instant play, rename, delete, test chime
 * - Drag-and-drop Audio File Uploader directly inside the Music Player tab
 * - Built-in Chime Soundboard for quick playback testing
 */
object WebDashboardMusic {

    fun getMusicTabNavButtonHtml(): String {
        return """
            <button class="tab-btn" onclick="switchTab('music', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M9 18V5l12-2v13"></path>
                    <circle cx="6" cy="18" r="3"></circle>
                    <circle cx="18" cy="16" r="3"></circle>
                </svg>
                Music Player
            </button>
        """.trimIndent()
    }

    fun getMusicCss(): String {
        return """
        /* --- Music Player Styling --- */
        .music-player-card {
            background: linear-gradient(145deg, #111827 0%, #0f172a 50%, #090d16 100%);
            border: 1px solid rgba(0, 229, 255, 0.25);
            border-radius: var(--radius-lg);
            padding: 24px;
            margin-bottom: 20px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.05);
            position: relative;
            overflow: hidden;
        }
        .music-player-card::before {
            content: '';
            position: absolute;
            top: -50px;
            right: -50px;
            width: 200px;
            height: 200px;
            background: radial-gradient(circle, rgba(0, 229, 255, 0.08) 0%, transparent 70%);
            pointer-events: none;
        }
        .vinyl-disc {
            width: 84px;
            height: 84px;
            border-radius: 50%;
            background: radial-gradient(circle, #222 20%, #111 21%, #111 40%, #000 41%, #181818 70%, #050505 100%);
            border: 3px solid #334155;
            box-shadow: 0 4px 14px rgba(0,0,0,0.6), inset 0 0 8px rgba(0,229,255,0.3);
            display: flex;
            align-items: center;
            justify-content: center;
            position: relative;
            flex-shrink: 0;
            transition: transform 0.3s ease;
        }
        .vinyl-disc.playing {
            animation: spinVinyl 5s linear infinite;
            border-color: var(--primary);
        }
        .vinyl-center {
            width: 28px;
            height: 28px;
            border-radius: 50%;
            background: linear-gradient(135deg, #00e5ff 0%, #3b82f6 100%);
            border: 2px solid #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 0 10px rgba(0,229,255,0.5);
        }
        .vinyl-center span {
            font-size: 10px;
        }
        @keyframes spinVinyl {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }

        /* Animated Spectrum Bars */
        .eq-bars {
            display: inline-flex;
            align-items: flex-end;
            gap: 3px;
            height: 18px;
            margin-left: 8px;
            vertical-align: middle;
        }
        .eq-bar {
            width: 3px;
            background: var(--primary);
            border-radius: 2px;
            height: 4px;
            transition: height 0.15s ease;
        }
        .eq-bars.playing .eq-bar:nth-child(1) { animation: eqJump 0.8s ease infinite alternate; }
        .eq-bars.playing .eq-bar:nth-child(2) { animation: eqJump 0.6s ease infinite 0.2s alternate; }
        .eq-bars.playing .eq-bar:nth-child(3) { animation: eqJump 1.0s ease infinite 0.4s alternate; }
        .eq-bars.playing .eq-bar:nth-child(4) { animation: eqJump 0.7s ease infinite 0.1s alternate; }
        .eq-bars.playing .eq-bar:nth-child(5) { animation: eqJump 0.9s ease infinite 0.3s alternate; }
        @keyframes eqJump {
            0% { height: 4px; }
            100% { height: 18px; }
        }

        /* Seeker Slider */
        .seek-container {
            margin: 16px 0 12px 0;
        }
        .seek-slider {
            width: 100%;
            height: 6px;
            border-radius: 4px;
            background: rgba(255, 255, 255, 0.12);
            outline: none;
            cursor: pointer;
            accent-color: #00e5ff;
            transition: all 0.2s;
        }
        .seek-slider:hover {
            height: 8px;
        }

        /* Preset Volume Buttons */
        .vol-preset-btn {
            background: var(--surface-subtle);
            border: 1px solid var(--border-color);
            color: var(--text-muted);
            padding: 3px 8px;
            border-radius: 12px;
            font-size: 0.72rem;
            cursor: pointer;
            transition: all 0.15s;
        }
        .vol-preset-btn:hover {
            background: var(--surface-hover);
            color: #fff;
            border-color: var(--primary);
        }

        /* Playlist item playing highlight */
        .playlist-row {
            transition: all 0.2s ease;
        }
        .playlist-row.active-track {
            border-color: var(--primary) !important;
            background: rgba(0, 229, 255, 0.08) !important;
            box-shadow: 0 0 15px rgba(0, 229, 255, 0.15);
        }

        /* Floating Mini Player */
        .floating-mini-player {
            position: sticky;
            top: 60px;
            z-index: 95;
            background: rgba(13, 17, 23, 0.92);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid rgba(0, 229, 255, 0.2);
            padding: 8px 16px;
            display: none;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
            animation: slideDown 0.2s ease;
        }
        @keyframes slideDown {
            from { transform: translateY(-10px); opacity: 0; }
            to { transform: translateY(0); opacity: 1; }
        }
        """.trimIndent()
    }

    fun getMusicTabContentHtml(): String {
        return """
        <!-- TAB: MUSIC PLAYER (音楽プレイヤー) -->
        <div id="tab-music" class="tab-content">
            <!-- Hero Music Console Card -->
            <div class="music-player-card">
                <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:16px;">
                    <div style="display:flex; align-items:center; gap:16px;">
                        <!-- Vinyl Record Animation -->
                        <div class="vinyl-disc" id="mpVinylDisc">
                            <div class="vinyl-center">
                                <span>🎵</span>
                            </div>
                        </div>
                        <div>
                            <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                                <span class="tag tag-primary" style="font-size:0.75rem; letter-spacing:0.5px;">DESKCLOCK MUSIC</span>
                                <span id="mpStatusBadge" class="tag" style="background:#1e293b; color:#94a3b8;">IDLE</span>
                                <div id="mpEqBars" class="eq-bars">
                                    <div class="eq-bar"></div>
                                    <div class="eq-bar"></div>
                                    <div class="eq-bar"></div>
                                    <div class="eq-bar"></div>
                                    <div class="eq-bar"></div>
                                </div>
                            </div>
                            <h2 style="font-size:1.25rem; font-weight:700; color:#fff; margin:6px 0 4px 0; word-break:break-all;" id="mpMainTrackTitle">
                                楽曲を選択してください
                            </h2>
                            <div style="font-size:0.8rem; color:var(--text-muted);" id="mpMainTrackSub">
                                デバイス上の音楽ライブラリから連続再生できます
                            </div>
                        </div>
                    </div>

                    <!-- Top Action Right: Device Indicator -->
                    <div style="text-align:right;">
                        <span style="font-size:0.75rem; color:var(--text-muted);">再生先: <strong>Android 本体スピーカー</strong></span>
                        <div style="font-size:0.72rem; color:var(--primary); margin-top:2px;">♪ 高音質オーディオ出力</div>
                    </div>
                </div>

                <!-- Seeker / Progress Bar -->
                <div class="seek-container">
                    <input type="range" id="mpSeekSlider" class="seek-slider" min="0" max="1000" value="0"
                           oninput="onMpSeekInput(this.value)" onchange="onMpSeekChange(this.value)">
                    <div style="display:flex; justify-content:space-between; align-items:center; font-size:0.78rem; font-family:var(--font-mono); color:var(--text-muted); margin-top:4px;">
                        <span id="mpPosText" style="color:var(--primary); font-weight:700;">00:00</span>
                        <span id="mpDurText">00:00</span>
                    </div>
                </div>

                <!-- Main Transport Controls -->
                <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:14px; margin-top:10px;">
                    <!-- Repeat & Shuffle Controls -->
                    <div style="display:flex; align-items:center; gap:8px;">
                        <button class="btn btn-outline btn-sm" id="mpRepeatBtn" onclick="cycleMusicRepeatMode()" title="リピートモード切替">
                            <span id="mpRepeatIcon">🔁</span>
                            <span id="mpRepeatLabel">全曲リピート</span>
                        </button>
                    </div>

                    <!-- Core Playback Buttons -->
                    <div style="display:flex; align-items:center; gap:10px;">
                        <button class="btn btn-outline" style="border-radius:50%; width:42px; height:42px; padding:0; justify-content:center;" onclick="musicAction('prev')" title="前の曲 / 先頭に戻る">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:20px; height:20px;"><polygon points="19 20 9 12 19 4 19 20"/><line x1="5" y1="19" x2="5" y2="5"/></svg>
                        </button>
                        <button class="btn btn-primary" id="mpMainPlayBtn" style="border-radius:50%; width:54px; height:54px; padding:0; justify-content:center; box-shadow:0 0 20px rgba(0,229,255,0.4);" onclick="musicAction('toggle')" title="再生 / 一時停止">
                            <svg id="mpMainPlayIcon" viewBox="0 0 24 24" fill="currentColor" style="width:24px; height:24px;"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                        </button>
                        <button class="btn btn-outline" style="border-radius:50%; width:42px; height:42px; padding:0; justify-content:center;" onclick="musicAction('next')" title="次の曲">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:20px; height:20px;"><polygon points="5 4 15 12 5 20 5 4"/><line x1="19" y1="5" x2="19" y2="19"/></svg>
                        </button>
                        <button class="btn btn-danger btn-sm" style="margin-left:6px;" onclick="musicAction('stop')" title="完全停止">
                            <svg viewBox="0 0 24 24" fill="currentColor" style="width:14px; height:14px;"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
                            停止
                        </button>
                    </div>

                    <!-- Volume Control Bar & Quick Presets -->
                    <div style="display:flex; flex-direction:column; align-items:flex-end; gap:6px;">
                        <div style="display:flex; align-items:center; gap:8px;">
                            <span id="mpVolIcon" style="font-size:1rem; cursor:pointer;" onclick="setMusicVolumePreset(0)">🔊</span>
                            <span style="font-size:0.8rem; font-weight:600; color:#fff;">音量:</span>
                            <input type="range" id="mpVolumeSlider" min="0" max="100" value="85" style="width:110px; accent-color:var(--primary);"
                                   oninput="onMusicVolumeInput(this.value)" onchange="onMusicVolumeChange(this.value)">
                            <span id="mpVolumePercent" style="font-size:0.8rem; font-family:var(--font-mono); color:var(--primary); font-weight:700; min-width:38px;">85%</span>
                        </div>
                        <div style="display:flex; gap:4px;">
                            <button class="vol-preset-btn" onclick="setMusicVolumePreset(0)">消音</button>
                            <button class="vol-preset-btn" onclick="setMusicVolumePreset(25)">25%</button>
                            <button class="vol-preset-btn" onclick="setMusicVolumePreset(50)">50%</button>
                            <button class="vol-preset-btn" onclick="setMusicVolumePreset(75)">75%</button>
                            <button class="vol-preset-btn" onclick="setMusicVolumePreset(100)">MAX</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Upload Zone & Playlist Row -->
            <div class="grid-2">
                <!-- Music Library & Playlist -->
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
                                楽曲プレイリスト (<span id="mpTrackCount">0</span>曲)
                            </div>
                            <div class="card-description">端末にアップロードされた音楽一覧 (クリックで即座に再生)</div>
                        </div>
                        <button class="btn btn-outline btn-sm" onclick="document.getElementById('musicFileInputDirect').click()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                            音楽を追加
                        </button>
                    </div>

                    <!-- Dynamic Playlist Container -->
                    <div id="mpPlaylistContainer" style="max-height: 480px; overflow-y: auto; padding-right: 4px;">
                        <p style="color:var(--text-muted); font-size:0.82rem; padding:12px 0;">読み込み中...</p>
                    </div>
                </div>

                <!-- Direct Audio Uploader & Ambient Soundboard Card -->
                <div>
                    <!-- Upload Dropzone -->
                    <div class="card">
                        <div class="card-header">
                            <div>
                                <div class="card-title">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                                    音楽ファイルのアップロード
                                </div>
                                <div class="card-description">MP3, WAV, AAC, M4A, OGG, FLAC に対応</div>
                            </div>
                        </div>

                        <div class="upload-dropzone" id="musicDropzone" onclick="document.getElementById('musicFileInputDirect').click()"
                             ondragover="event.preventDefault(); this.style.borderColor='var(--primary)';"
                             ondragleave="this.style.borderColor='var(--border-light)';"
                             ondrop="event.preventDefault(); this.style.borderColor='var(--border-light)'; if(event.dataTransfer.files.length) uploadAudio(event.dataTransfer.files[0]);">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:36px; height:36px; stroke:var(--primary); margin-bottom:8px;"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
                            <p style="font-weight: 600; font-size: 0.9rem; color: #fff;">クリックまたはドラッグ＆ドロップで音楽を追加</p>
                            <p style="font-size: 0.78rem; color: var(--text-muted); margin-top: 4px;">端末本体のストレージに安全に保存されます</p>
                            <input type="file" id="musicFileInputDirect" accept="audio/*" style="display:none" onchange="uploadAudio(this.files[0])">
                        </div>
                    </div>

                    <!-- Built-in Clock Sounds Soundboard -->
                    <div class="card">
                        <div class="card-header">
                            <div>
                                <div class="card-title">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
                                    内蔵チャイム・効果音サウンドボード
                                </div>
                                <div class="card-description">時報やアラーム用の内蔵メロディを本体でワンタップ試聴</div>
                            </div>
                        </div>
                        <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap:8px;">
                            <button class="btn btn-outline btn-sm" style="justify-content:flex-start;" onclick="testSoundDirect('WESTMINSTER')">🔔 ウェストミンスター</button>
                            <button class="btn btn-outline btn-sm" style="justify-content:flex-start;" onclick="testSoundDirect('WHITTINGTON')">🔔 ウィッティントン</button>
                            <button class="btn btn-outline btn-sm" style="justify-content:flex-start;" onclick="testSoundDirect('SCHOOL_BELL')">🏫 学校チャイム</button>
                            <button class="btn btn-outline btn-sm" style="justify-content:flex-start;" onclick="testSoundDirect('DIGITAL_SIGNAL')">📟 デジタル時報音</button>
                            <button class="btn btn-outline btn-sm" style="justify-content:flex-start;" onclick="testSoundDirect('PING_PONG')">🏓 ピンポンチャイム</button>
                            <button class="btn btn-outline btn-sm" style="justify-content:flex-start;" onclick="testSoundDirect('TEMPLE_BELL')">⛩ 日本の寺院の鐘</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Master Equalizer & Speaker Bass Protection Console (User Requested) -->
            <div class="card" style="margin-top: 20px;">
                <div class="card-header">
                    <div>
                        <div class="card-title">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/></svg>
                            🎚️ 音楽・音声全般イコライザー & 小型スピーカー低音保護 (Audio Equalizer)
                        </div>
                        <div class="card-description">音楽プレイヤー、時報チャイム、動画音声など端末から流れる全ての音質と低音歪みをリアルタイム調整</div>
                    </div>
                    <div style="display:flex; align-items:center; gap:10px;">
                        <button class="btn btn-outline btn-sm" onclick="resetEqualizerDirect()">標準(FLAT)に戻す</button>
                        <label class="switch">
                            <input type="checkbox" id="webEqMasterToggle" onchange="toggleEqualizerDirect(this.checked)">
                            <span class="slider round"></span>
                        </label>
                    </div>
                </div>

                <!-- 1. Speaker Protection Bass Cut Filter Banner -->
                <div style="background: rgba(250, 84, 28, 0.08); border: 1px solid rgba(250, 84, 28, 0.3); border-radius: 12px; padding: 14px 18px; margin-bottom: 16px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:10px;">
                        <div>
                            <div style="font-weight:700; color:#fa541c; font-size:0.92rem; display:flex; align-items:center; gap:6px;">
                                <span>🛡️ スピーカー保護・低音カットフィルター (Bass Cut)</span>
                            </div>
                            <div style="font-size:0.8rem; color:var(--text-muted); margin-top:2px;">
                                小型スピーカーや時計内蔵スピーカーの音割れ・ビビリ音・低温歪みを周波数カットして強力に防止します
                            </div>
                        </div>
                        <div style="display:flex; gap:6px; flex-wrap:wrap;" id="webBassCutButtonGroup">
                            <button class="btn btn-outline btn-sm" onclick="setEqBassCutDirect('OFF')" data-mode="OFF">OFF (無加工)</button>
                            <button class="btn btn-outline btn-sm" onclick="setEqBassCutDirect('LIGHT')" data-mode="LIGHT">弱 (-4dB)</button>
                            <button class="btn btn-outline btn-sm" onclick="setEqBassCutDirect('MEDIUM')" data-mode="MEDIUM">中 (-8dB)</button>
                            <button class="btn btn-outline btn-sm" onclick="setEqBassCutDirect('STRONG')" data-mode="STRONG">強 (-12dB)</button>
                            <button class="btn btn-outline btn-sm" onclick="setEqBassCutDirect('EXTREME')" data-mode="EXTREME">極 (-16dB)</button>
                        </div>
                    </div>
                </div>

                <!-- 2. Presets Selection Row -->
                <div style="margin-bottom: 18px;">
                    <div style="font-size:0.82rem; font-weight:700; color:#fff; margin-bottom:8px;">🎵 音質プリセット:</div>
                    <div style="display:flex; gap:6px; flex-wrap:wrap;" id="webEqPresetButtonGroup">
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('FLAT')" data-preset="FLAT">フラット (標準)</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('BASS_REDUCE')" data-preset="BASS_REDUCE" style="border-color:#fa541c; color:#fa541c;">低音カット (小型保護)</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('BASS_CUT_LIGHT')" data-preset="BASS_CUT_LIGHT">低音マイルドカット</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('VOCAL')" data-preset="VOCAL">ボーカル・声くっきり</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('TREBLE_BOOST')" data-preset="TREBLE_BOOST">高音強調</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('NIGHT_RELAX')" data-preset="NIGHT_RELAX">ナイト・リラックス</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('POP')" data-preset="POP">ポップス</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('ROCK')" data-preset="ROCK">ロック</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('CLASSICAL')" data-preset="CLASSICAL">クラシック</button>
                        <button class="btn btn-outline btn-sm" onclick="setEqPresetDirect('BASS_BOOST')" data-preset="BASS_BOOST">低音ブースト (大型推奨)</button>
                    </div>
                </div>

                <!-- 3. 5-Band Graphic Sliders Grid -->
                <div>
                    <div style="font-size:0.82rem; font-weight:700; color:#fff; margin-bottom:10px; display:flex; justify-content:space-between;">
                        <span>🎚️ 5バンド周波数調整 (-15dB 〜 +15dB):</span>
                        <span id="webEqActivePresetLabel" style="color:var(--primary); font-family:var(--font-mono); font-size:0.8rem;">FLAT</span>
                    </div>
                    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap:12px;">
                        <!-- Band 0: ~60Hz -->
                        <div style="background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:10px; padding:10px;">
                            <div style="display:flex; justify-content:space-between; font-size:0.75rem; margin-bottom:4px;">
                                <strong style="color:#fff;">低音 (60Hz)</strong>
                                <span id="webEqGain0" style="color:var(--primary); font-family:var(--font-mono);">0 dB</span>
                            </div>
                            <input type="range" class="seek-slider" min="-15" max="15" value="0" id="webEqSlider0"
                                   oninput="onEqSliderInput(0, this.value)" onchange="onEqSliderChange(0, this.value)">
                            <div style="font-size:0.7rem; color:var(--text-muted); margin-top:2px;">重低音・バスドラム</div>
                        </div>

                        <!-- Band 1: ~230Hz -->
                        <div style="background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:10px; padding:10px;">
                            <div style="display:flex; justify-content:space-between; font-size:0.75rem; margin-bottom:4px;">
                                <strong style="color:#fff;">低中音 (230Hz)</strong>
                                <span id="webEqGain1" style="color:var(--primary); font-family:var(--font-mono);">0 dB</span>
                            </div>
                            <input type="range" class="seek-slider" min="-15" max="15" value="0" id="webEqSlider1"
                                   oninput="onEqSliderInput(1, this.value)" onchange="onEqSliderChange(1, this.value)">
                            <div style="font-size:0.7rem; color:var(--text-muted); margin-top:2px;">ベース・低音の厚み</div>
                        </div>

                        <!-- Band 2: ~910Hz -->
                        <div style="background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:10px; padding:10px;">
                            <div style="display:flex; justify-content:space-between; font-size:0.75rem; margin-bottom:4px;">
                                <strong style="color:#fff;">中音 (910Hz)</strong>
                                <span id="webEqGain2" style="color:var(--primary); font-family:var(--font-mono);">0 dB</span>
                            </div>
                            <input type="range" class="seek-slider" min="-15" max="15" value="0" id="webEqSlider2"
                                   oninput="onEqSliderInput(2, this.value)" onchange="onEqSliderChange(2, this.value)">
                            <div style="font-size:0.7rem; color:var(--text-muted); margin-top:2px;">人の声・ボーカル・時報</div>
                        </div>

                        <!-- Band 3: ~3.6kHz -->
                        <div style="background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:10px; padding:10px;">
                            <div style="display:flex; justify-content:space-between; font-size:0.75rem; margin-bottom:4px;">
                                <strong style="color:#fff;">中高音 (3.6kHz)</strong>
                                <span id="webEqGain3" style="color:var(--primary); font-family:var(--font-mono);">0 dB</span>
                            </div>
                            <input type="range" class="seek-slider" min="-15" max="15" value="0" id="webEqSlider3"
                                   oninput="onEqSliderInput(3, this.value)" onchange="onEqSliderChange(3, this.value)">
                            <div style="font-size:0.7rem; color:var(--text-muted); margin-top:2px;">明瞭度・音の輪郭</div>
                        </div>

                        <!-- Band 4: ~14kHz -->
                        <div style="background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:10px; padding:10px;">
                            <div style="display:flex; justify-content:space-between; font-size:0.75rem; margin-bottom:4px;">
                                <strong style="color:#fff;">高音 (14kHz)</strong>
                                <span id="webEqGain4" style="color:var(--primary); font-family:var(--font-mono);">0 dB</span>
                            </div>
                            <input type="range" class="seek-slider" min="-15" max="15" value="0" id="webEqSlider4"
                                   oninput="onEqSliderInput(4, this.value)" onchange="onEqSliderChange(4, this.value)">
                            <div style="font-size:0.7rem; color:var(--text-muted); margin-top:2px;">透明感・空気感・シンバル</div>
                        </div>
                    </div>
                </div>

                <!-- 4. Earphone Jack Anti-Noise Keep-Alive Console (User Requested) -->
                <div style="margin-top: 16px; background: rgba(0, 229, 255, 0.06); border: 1px solid rgba(0, 229, 255, 0.25); border-radius: 12px; padding: 14px 18px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:10px;">
                        <div>
                            <div style="font-weight:700; color:#00e5ff; font-size:0.92rem; display:flex; align-items:center; gap:8px;">
                                <span>🔇 イヤホンジャック・ノイズ防止（常時無音再生）</span>
                                <span id="webSilenceStatusBadge" style="font-size:0.7rem; padding:2px 8px; border-radius:10px; background:#00e5ff; color:#000; font-weight:700;">稼働中</span>
                            </div>
                            <div style="font-size:0.8rem; color:var(--text-muted); margin-top:3px;">
                                タブレットのイヤホン端子や外部アンプが待機状態で発する「ジー」というホワイトノイズや、音が出る瞬間の「プチッ」というポップノイズを防ぐため、超低負荷で無音PCM信号を常時流し続けます（CPU負荷0%）
                            </div>
                        </div>
                        <div style="display:flex; align-items:center; gap:10px;">
                            <label class="switch">
                                <input type="checkbox" id="webAntiNoiseToggle" onchange="toggleAntiNoiseDirect(this.checked)">
                                <span class="slider round"></span>
                            </label>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        """.trimIndent()
    }

    fun getMusicJs(): String {
        return """
        // ==========================================
        // Web Dashboard Music Player Logic (Enhanced)
        // ==========================================
        var isSeeking = false;
        var localPlayTimer = null;
        var lastMpState = null;

        function updateMusicPlayerUi(data) {
            if (!data || !data.musicPlayer) return;
            var mp = data.musicPlayer;
            lastMpState = mp;

            // 1. Vinyl disc & Visualizer
            var vinyl = document.getElementById('mpVinylDisc');
            var eqBars = document.getElementById('mpEqBars');
            if (vinyl) {
                if (mp.isPlaying) {
                    vinyl.classList.add('playing');
                } else {
                    vinyl.classList.remove('playing');
                }
            }
            if (eqBars) {
                if (mp.isPlaying) {
                    eqBars.classList.add('playing');
                } else {
                    eqBars.classList.remove('playing');
                }
            }

            // 2. Status Badge
            var badge = document.getElementById('mpStatusBadge');
            if (badge) {
                if (mp.isPlaying) {
                    badge.innerText = 'PLAYING';
                    badge.style.background = 'rgba(0, 229, 255, 0.2)';
                    badge.style.color = 'var(--primary)';
                } else if (mp.isPaused) {
                    badge.innerText = 'PAUSED';
                    badge.style.background = 'rgba(234, 179, 8, 0.2)';
                    badge.style.color = '#eab308';
                } else {
                    badge.innerText = 'STOPPED';
                    badge.style.background = '#1e293b';
                    badge.style.color = '#94a3b8';
                }
            }

            // 3. Track Titles
            var titleEl = document.getElementById('mpMainTrackTitle');
            var subEl = document.getElementById('mpMainTrackSub');
            if (titleEl) {
                titleEl.innerText = mp.currentTrackName ? mp.currentTrackName : '楽曲を選択してください';
            }
            if (subEl) {
                if (mp.isPlaying) {
                    subEl.innerText = '再生中 • ' + (mp.repeatModeLabel || '全曲リピート');
                } else if (mp.isPaused) {
                    subEl.innerText = '一時停止中';
                } else {
                    subEl.innerText = 'プレイリストから楽曲を選択して再生を開始してください';
                }
            }

            // 4. Seeker & Times (only update slider if user is not currently dragging it)
            var seekSlider = document.getElementById('mpSeekSlider');
            var posText = document.getElementById('mpPosText');
            var durText = document.getElementById('mpDurText');

            if (posText) posText.innerText = mp.positionFormatted || '00:00';
            if (durText) durText.innerText = mp.durationFormatted || '00:00';

            if (seekSlider && !isSeeking) {
                if (mp.durationMs > 0) {
                    var fraction = (mp.currentPositionMs / mp.durationMs) * 1000;
                    seekSlider.value = Math.min(1000, Math.max(0, fraction));
                } else {
                    seekSlider.value = 0;
                }
            }

            // 5. Play / Pause Button Icon
            var playBtn = document.getElementById('mpMainPlayBtn');
            var playIcon = document.getElementById('mpMainPlayIcon');
            if (playIcon) {
                if (mp.isPlaying) {
                    playIcon.innerHTML = '<rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>';
                } else {
                    playIcon.innerHTML = '<polygon points="5 3 19 12 5 21 5 3"/>';
                }
            }

            // 6. Repeat Mode Label
            var repeatLabel = document.getElementById('mpRepeatLabel');
            var repeatIcon = document.getElementById('mpRepeatIcon');
            if (repeatLabel) repeatLabel.innerText = mp.repeatModeLabel || '全曲リピート';
            if (repeatIcon) {
                if (mp.repeatMode === 'ONE') repeatIcon.innerText = '🔂';
                else if (mp.repeatMode === 'SHUFFLE') repeatIcon.innerText = '🔀';
                else if (mp.repeatMode === 'OFF') repeatIcon.innerText = '➡️';
                else repeatIcon.innerText = '🔁';
            }

            // 7. Volume Slider
            var volSlider = document.getElementById('mpVolumeSlider');
            var volPct = document.getElementById('mpVolumePercent');
            var volIcon = document.getElementById('mpVolIcon');
            var vInt = Math.round((mp.volume !== undefined ? mp.volume : 0.85) * 100);
            if (volSlider && !volSlider.matches(':active')) {
                volSlider.value = vInt;
            }
            if (volPct) volPct.innerText = vInt + '%';
            if (volIcon) {
                if (vInt === 0) volIcon.innerText = '🔇';
                else if (vInt < 40) volIcon.innerText = '🔈';
                else if (vInt < 80) volIcon.innerText = '🔉';
                else volIcon.innerText = '🔊';
            }

            // 8. Render Playlist in Music Tab
            renderMusicTabPlaylist(data);

            // 9. Render Equalizer in Music Tab
            updateEqualizerUi(data);
        }

        function updateEqualizerUi(data) {
            if (!data || !data.equalizer) return;
            var eq = data.equalizer;

            // Master Toggle
            var toggle = document.getElementById('webEqMasterToggle');
            if (toggle) toggle.checked = !!eq.isEnabled;

            // Active Preset Label
            var presetLabel = document.getElementById('webEqActivePresetLabel');
            if (presetLabel) {
                var name = eq.currentPresetName || eq.currentPreset || 'FLAT';
                presetLabel.innerText = eq.isEnabled ? (name + (eq.bassCutMode && eq.bassCutMode !== 'OFF' ? ' (低音カット: ' + eq.bassCutModeName + ')' : '')) : '無効 (OFF)';
                presetLabel.style.color = eq.isEnabled ? 'var(--primary)' : 'var(--text-muted)';
            }

            // Bass Cut buttons
            var bcGroup = document.getElementById('webBassCutButtonGroup');
            if (bcGroup) {
                var btns = bcGroup.querySelectorAll('button');
                btns.forEach(function(b) {
                    var mode = b.getAttribute('data-mode');
                    if (mode === eq.bassCutMode) {
                        b.classList.remove('btn-outline');
                        b.classList.add('btn-primary');
                        b.style.background = '#fa541c';
                        b.style.borderColor = '#fa541c';
                    } else {
                        b.classList.remove('btn-primary');
                        b.classList.add('btn-outline');
                        b.style.background = '';
                        b.style.borderColor = '';
                    }
                });
            }

            // Preset buttons
            var pGroup = document.getElementById('webEqPresetButtonGroup');
            if (pGroup) {
                var pBtns = pGroup.querySelectorAll('button');
                pBtns.forEach(function(b) {
                    var preset = b.getAttribute('data-preset');
                    if (preset === eq.currentPreset) {
                        b.classList.remove('btn-outline');
                        b.classList.add('btn-primary');
                    } else {
                        b.classList.remove('btn-primary');
                        b.classList.add('btn-outline');
                    }
                });
            }

            // 5 Band Sliders & Labels
            var gains = eq.bandGainsDb || [0, 0, 0, 0, 0];
            var effGains = eq.effectiveBandGainsDb || gains;
            for (var i = 0; i < 5; i++) {
                var s = document.getElementById('webEqSlider' + i);
                var g = document.getElementById('webEqGain' + i);
                var val = gains[i] !== undefined ? gains[i] : 0;
                var effVal = effGains[i] !== undefined ? effGains[i] : val;

                if (s && !s.matches(':active')) {
                    s.value = val;
                }
                if (g) {
                    var txt = (val > 0 ? ('+' + val) : String(val)) + ' dB';
                    if (effVal !== val) {
                        txt += ' (実効:' + effVal + 'dB)';
                    }
                    g.innerText = txt;
                    if (val > 0) g.style.color = 'var(--primary)';
                    else if (val < 0) g.style.color = '#fa541c';
                    else g.style.color = 'var(--text-muted)';
                }
            }

            // Anti-Noise Silence Keep-Alive UI (User Requested)
            if (data.antiNoiseSilence) {
                var an = data.antiNoiseSilence;
                var anToggle = document.getElementById('webAntiNoiseToggle');
                var anBadge = document.getElementById('webSilenceStatusBadge');
                if (anToggle && !anToggle.matches(':active')) {
                    anToggle.checked = !!an.enabled;
                }
                if (anBadge) {
                    if (an.isPlaying) {
                        anBadge.innerText = '常時無音再生中 (DACアクティブ)';
                        anBadge.style.background = '#00e5ff';
                        anBadge.style.color = '#000';
                    } else {
                        anBadge.innerText = '停止中';
                        anBadge.style.background = 'rgba(255, 255, 255, 0.15)';
                        anBadge.style.color = 'var(--text-muted)';
                    }
                }
            }
        }

        function toggleAntiNoiseDirect(enabled) {
            fetch('/api/audio/anti_noise', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: enabled })
            }).then(function() {
                showToast(enabled ? 'イヤホンノイズ防止（常時無音再生）を開始しました' : '常時無音再生を停止しました');
                refreshData();
            });
        }

        function toggleEqualizerDirect(enabled) {
            fetch('/api/equalizer/enabled', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: enabled })
            }).then(function() {
                showToast(enabled ? 'イコライザーを有効化しました' : 'イコライザーを無効化（スルー）しました');
                refreshData();
            });
        }

        function setEqPresetDirect(preset) {
            fetch('/api/equalizer/preset', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ preset: preset })
            }).then(function() {
                showToast('音質プリセット: ' + preset);
                refreshData();
            });
        }

        function setEqBassCutDirect(mode) {
            fetch('/api/equalizer/basscut', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mode: mode })
            }).then(function() {
                showToast('低音カットフィルター: ' + mode);
                refreshData();
            });
        }

        function onEqSliderInput(index, val) {
            var g = document.getElementById('webEqGain' + index);
            if (g) {
                var v = parseInt(val, 10);
                g.innerText = (v > 0 ? ('+' + v) : String(v)) + ' dB';
                g.style.color = v > 0 ? 'var(--primary)' : (v < 0 ? '#fa541c' : 'var(--text-muted)');
            }
        }

        function onEqSliderChange(index, val) {
            var gain = parseInt(val, 10);
            fetch('/api/equalizer/band', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ index: index, gain: gain })
            }).then(function() {
                refreshData();
            });
        }

        function resetEqualizerDirect() {
            fetch('/api/equalizer/reset', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({})
            }).then(function() {
                showToast('イコライザーを標準 (FLAT) にリセットしました');
                refreshData();
            });
        }

        function renderMusicTabPlaylist(data) {
            var audios = data.customAudios || [];
            var countEl = document.getElementById('mpTrackCount');
            if (countEl) countEl.innerText = audios.length;

            var container = document.getElementById('mpPlaylistContainer');
            if (!container) return;

            if (audios.length === 0) {
                container.innerHTML = '<div style="text-align:center; padding:32px 16px; color:var(--text-muted);">' +
                    '<div style="font-size:2rem; margin-bottom:8px;">🎵</div>' +
                    '<p style="font-weight:600; color:#fff;">音楽ファイルがまだありません</p>' +
                    '<p style="font-size:0.78rem; margin-top:4px;">右側のエリアからMP3/WAV音声を追加すると、ここで連続再生できます</p>' +
                    '</div>';
                return;
            }

            var currentTrackId = data.musicPlayer ? data.musicPlayer.currentTrackId : null;
            var isPlaying = data.musicPlayer ? data.musicPlayer.isPlaying : false;

            var html = '';
            audios.forEach(function(a, idx) {
                var isCurrent = currentTrackId === a.id;
                var isThisPlaying = isCurrent && isPlaying;
                var safePath = encodeURIComponent(a.filePath);
                var safeName = a.name.replace(/'/g, "\\'");
                var safeId = a.id;

                html += '<div class="item-card playlist-row ' + (isCurrent ? 'active-track' : '') + '">' +
                    '<div style="display:flex; align-items:center; gap:12px; flex:1; min-width:180px;">' +
                        '<div style="font-size:0.85rem; font-family:var(--font-mono); color:' + (isCurrent ? 'var(--primary)' : 'var(--text-muted)') + '; font-weight:700; width:22px;">' +
                            (isThisPlaying ? '▶' : (idx + 1)) +
                        '</div>' +
                        '<div style="flex:1;">' +
                            '<div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">' +
                                '<span style="font-size:0.9rem; font-weight:600; color:#fff; word-break:break-all;">' + a.name + '</span>' +
                                (isThisPlaying ? '<span class="tag tag-primary" style="font-size:0.7rem;">再生中</span>' : (isCurrent ? '<span class="tag" style="font-size:0.7rem;">選択中</span>' : '')) +
                            '</div>' +
                            '<div style="margin-top:4px;">' +
                                '<audio src="/media/audio/' + a.id + '" controls style="height:26px; width:100%; max-width:220px;"></audio>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display:flex; gap:6px; flex-wrap:wrap; align-items:center;">' +
                        '<button class="btn ' + (isThisPlaying ? 'btn-primary' : 'btn-outline') + ' btn-sm" onclick="' + (isThisPlaying ? 'musicAction(\'toggle\')' : ('playCustomMusic(\'' + safeId + '\', \'' + safePath + '\')')) + '">' +
                            (isThisPlaying ? '⏸ 一時停止' : '▶ 再生') +
                        '</button>' +
                        '<button class="btn btn-outline btn-sm" onclick="testCustomAudio(\'' + safePath + '\')" title="時報音としてテスト再生">🔔 試聴</button>' +
                        '<button class="btn btn-outline btn-sm" onclick="renameMediaPrompt(\'' + safeId + '\', \'audio\', \'' + safeName + '\')">✏ 改名</button>' +
                        '<button class="btn btn-danger btn-sm" onclick="deleteMedia(\'' + safeId + '\', \'audio\')">🗑 削除</button>' +
                    '</div>' +
                '</div>';
            });

            container.innerHTML = html;
        }

        // Seeker Callbacks
        function onMpSeekInput(val) {
            isSeeking = true;
            if (lastMpState && lastMpState.durationMs > 0) {
                var targetMs = (val / 1000.0) * lastMpState.durationMs;
                var posText = document.getElementById('mpPosText');
                if (posText) {
                    var totalSec = Math.floor(targetMs / 1000);
                    var m = String(Math.floor(totalSec / 60)).padStart(2, '0');
                    var s = String(totalSec % 60).padStart(2, '0');
                    posText.innerText = m + ':' + s;
                }
            }
        }

        function onMpSeekChange(val) {
            isSeeking = false;
            if (lastMpState && lastMpState.durationMs > 0) {
                var targetMs = Math.round((val / 1000.0) * lastMpState.durationMs);
                musicAction('seek', { positionMs: targetMs });
            }
        }

        function cycleMusicRepeatMode() {
            var currentMode = lastMpState ? lastMpState.repeatMode : 'ALL';
            var nextMode = 'ALL';
            if (currentMode === 'ALL') nextMode = 'ONE';
            else if (currentMode === 'ONE') nextMode = 'SHUFFLE';
            else if (currentMode === 'SHUFFLE') nextMode = 'OFF';
            else nextMode = 'ALL';

            musicAction('repeat', { repeatMode: nextMode });
            showToast('リピートモード: ' + nextMode);
        }

        function setMusicVolumePreset(pct) {
            var slider = document.getElementById('mpVolumeSlider');
            if (slider) slider.value = pct;
            onMusicVolumeInput(pct);
            onMusicVolumeChange(pct);
        }

        function testSoundDirect(soundName) {
            fetch('/api/chime/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sound: soundName, volume: 0.85 })
            });
            showToast('本体で ' + soundName + ' を鳴動テスト中...');
        }
        """.trimIndent()
    }
}

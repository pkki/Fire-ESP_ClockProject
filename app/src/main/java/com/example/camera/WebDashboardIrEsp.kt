package com.example.camera

/**
 * HTML, CSS and JavaScript extensions for the Web Dashboard:
 * - Smart IR Remote Control management (learning, scheduling, sending, button customization)
 * - ESP32 Environment Sensor & Telemetry (AHT20 + BMP280, calibration, connection modes, Arduino sketch)
 */
object WebDashboardIrEsp {

    fun getIrEspCss(): String {
        return """
        /* IR Remote & Sensor Extensions */
        .category-pill {
            background: var(--surface-subtle);
            border: 1px solid var(--border-color);
            color: var(--text-muted);
            padding: 5px 12px;
            border-radius: 20px;
            font-size: 0.78rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.15s ease;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .category-pill:hover {
            color: #fff;
            border-color: var(--border-light);
            background: var(--surface-hover);
        }
        .category-pill.active {
            background: var(--primary);
            border-color: var(--primary);
            color: #fff;
        }
        .category-pill .pill-count {
            background: rgba(0, 0, 0, 0.25);
            padding: 1px 6px;
            border-radius: 10px;
            font-size: 0.7rem;
        }

        .pulse-indicator {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--danger);
            display: inline-block;
            box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
            animation: pulse-red 1.6s infinite;
        }
        @keyframes pulse-red {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
        }

        .metric-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 12px;
            margin-bottom: 16px;
        }
        .metric-card {
            background: var(--surface-subtle);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            padding: 14px 16px;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
        }
        .metric-title {
            font-size: 0.76rem;
            font-weight: 500;
            color: var(--text-muted);
            margin-bottom: 6px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .metric-value {
            font-size: 1.6rem;
            font-weight: 800;
            font-family: var(--font-mono);
            color: #fff;
            line-height: 1.1;
        }
        .metric-unit {
            font-size: 0.85rem;
            font-weight: 500;
            color: var(--text-muted);
            margin-left: 4px;
        }
        .metric-subtext {
            font-size: 0.74rem;
            color: var(--text-subtle);
            margin-top: 6px;
        }

        .code-box {
            background: #080a0f;
            border: 1px solid var(--border-color);
            border-radius: var(--radius-sm);
            padding: 10px 12px;
            font-family: var(--font-mono);
            font-size: 0.76rem;
            color: #93c5fd;
            overflow-x: auto;
            white-space: pre-wrap;
            word-break: break-all;
            max-height: 180px;
        }

        .color-swatch-picker {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-top: 6px;
        }
        .color-swatch {
            width: 28px;
            height: 28px;
            border-radius: 50%;
            cursor: pointer;
            border: 2px solid transparent;
            transition: transform 0.12s ease;
        }
        .color-swatch:hover { transform: scale(1.15); }
        .color-swatch.active { border-color: #fff; box-shadow: 0 0 6px rgba(255,255,255,0.4); }
        """
    }

    fun getTabNavButtonsHtml(): String {
        return """
            <button class="tab-btn" onclick="switchTab('ir', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="4" y="2" width="16" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12.01" y2="18"/><line x1="8" y1="6" x2="16" y2="6"/><line x1="10" y1="10" x2="14" y2="10"/></svg>
                IR Remote
            </button>
            <button class="tab-btn" onclick="switchTab('esp', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 14.76V3.5a2.5 2.5 0 0 0-5 0v11.26a4.5 4.5 0 1 0 5 0z"/></svg>
                Sensors & ESP32
            </button>
        """
    }

    fun getTabIrHtml(): String {
        return """
        <!-- TAB 7: SMART IR REMOTES -->
        <div id="tab-ir" class="tab-content">
            <!-- Learning Mode Banner & Controls -->
            <div class="card" style="margin-bottom: 16px;">
                <div class="card-header">
                    <div>
                        <div class="card-title" style="display: flex; align-items: center; gap: 8px;">
                            <span id="irLearnIndicator" class="" style="display:none;"></span>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>
                            赤外線信号 学習モード (IR Signal Learning)
                        </div>
                        <div class="card-description" id="irLearnStatusDesc">
                            ESP32-C3の受光部(GPIO2)にリモコンを向けて学習ボタンを押します
                        </div>
                    </div>
                    <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                        <button id="btnStartLearn" class="btn btn-outline btn-sm" onclick="startIrLearning()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg>
                            学習開始
                        </button>
                        <button id="btnStopLearn" class="btn btn-danger btn-sm" style="display:none;" onclick="stopIrLearning()">
                            停止
                        </button>
                        <button class="btn btn-outline btn-sm" onclick="clearIrLearned()">
                            クリア
                        </button>
                    </div>
                </div>

                <!-- Latest Learned Candidate Card (Hidden when none) -->
                <div id="irCandidateBox" style="display: none; background: rgba(59, 130, 246, 0.08); border: 1px solid rgba(59, 130, 246, 0.3); border-radius: var(--radius-md); padding: 14px 16px; margin-top: 12px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;">
                        <div>
                            <div style="font-weight: 700; color: #60a5fa; font-size: 0.95rem; display: flex; align-items: center; gap: 6px;">
                                <span>🎯 新しい赤外線信号を受信しました！</span>
                            </div>
                            <div id="irCandidateDetails" style="font-family: var(--font-mono); font-size: 0.82rem; color: #fff; margin-top: 4px;">
                                Protocol: -- | Hex: --
                            </div>
                        </div>
                        <div style="display: flex; gap: 8px;">
                            <button class="btn btn-outline btn-sm" onclick="testCandidateSignal()">
                                🚀 テスト送信
                            </button>
                            <button class="btn btn-success btn-sm" onclick="openSaveCandidateModal()">
                                💾 ボタンとして保存
                            </button>
                        </div>
                    </div>
                    <div id="irCandidateRawLog" class="code-box" style="margin-top: 8px; display: none;"></div>
                </div>
            </div>

            <!-- Remote Buttons Management -->
            <div class="card">
                <div class="card-header" style="flex-wrap: wrap; gap: 12px;">
                    <div>
                        <div class="card-title">
                            登録済みリモコンボタン一覧
                        </div>
                        <div class="card-description">
                            タップで即座に送信。朝のアラームや夜間モード、時刻指定タイマーと連動可能
                        </div>
                    </div>
                    <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center;">
                        <button class="btn btn-sm" onclick="openAddIrModal()">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                            新規ボタン手動追加
                        </button>
                    </div>
                </div>

                <!-- Category Filter Pills -->
                <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px;" id="irCategoryFilters">
                    <button class="category-pill active" onclick="filterIrCategory('ALL', this)">すべて <span class="pill-count" id="countAll">0</span></button>
                    <button class="category-pill" onclick="filterIrCategory('LIGHTING', this)">💡 照明 <span class="pill-count" id="countLIGHTING">0</span></button>
                    <button class="category-pill" onclick="filterIrCategory('AIR_CONDITIONER', this)">❄️ エアコン <span class="pill-count" id="countAIR_CONDITIONER">0</span></button>
                    <button class="category-pill" onclick="filterIrCategory('TV', this)">📺 テレビ <span class="pill-count" id="countTV">0</span></button>
                    <button class="category-pill" onclick="filterIrCategory('FAN', this)">🌀 扇風機 <span class="pill-count" id="countFAN">0</span></button>
                    <button class="category-pill" onclick="filterIrCategory('HEATER', this)">🔥 暖房 <span class="pill-count" id="countHEATER">0</span></button>
                    <button class="category-pill" onclick="filterIrCategory('OTHER', this)">🔌 その他 <span class="pill-count" id="countOTHER">0</span></button>
                </div>

                <!-- Buttons List -->
                <div id="irButtonsList">
                    <p style="color:var(--text-muted); font-size:0.85rem; padding:12px 0;">読み込み中...</p>
                </div>
            </div>

            <!-- Direct Custom Signal Sender -->
            <div class="card" style="margin-top: 16px;">
                <div class="card-header">
                    <div>
                        <div class="card-title">赤外線コード 直接手動送信 (Direct IR Sender)</div>
                        <div class="card-description">プロトコルやHEX値、RAWパルスを直接指定してテスト送信</div>
                    </div>
                </div>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px;">
                    <div class="form-group">
                        <label class="form-label">Protocol</label>
                        <select id="directProtocol">
                            <option value="NEC">NEC (32-bit)</option>
                            <option value="SONY">SONY (12/15/20-bit)</option>
                            <option value="PANASONIC">PANASONIC (48-bit)</option>
                            <option value="RC5">PHILIPS RC5</option>
                            <option value="RC6">PHILIPS RC6</option>
                            <option value="UNKNOWN">RAW (Custom Pulses)</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label">HEX Code (例: 0x00FF807F)</label>
                        <input type="text" id="directHex" placeholder="0x00FF807F">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Bits</label>
                        <input type="number" id="directBits" value="32" min="1" max="128">
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">RAW Timing Pulses (カンマ区切りμs、UNKNOWN時のみ使用)</label>
                    <textarea id="directRaw" class="form-control" rows="2" placeholder="3450,1780,420,410,420,1320..."></textarea>
                </div>
                <button class="btn btn-outline btn-sm" onclick="sendDirectIrSignal()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                    今すぐ送信 (Send Signal)
                </button>
            </div>
        </div>
        """
    }

    fun getTabEspHtml(): String {
        return """
        <!-- TAB 8: SENSORS & ESP32 CONFIGURATION -->
        <div id="tab-esp" class="tab-content">
            <!-- Telemetry Cards -->
            <div class="metric-grid">
                <div class="metric-card">
                    <div class="metric-title">
                        <span>室温 (TEMPERATURE)</span>
                        <span id="espAhtBadge" class="tag">AHT20</span>
                    </div>
                    <div>
                        <span class="metric-value" id="espTempVal">--.-</span><span class="metric-unit">°C</span>
                    </div>
                    <div class="metric-subtext" id="espDiscomfortText">不快指数: --</div>
                </div>

                <div class="metric-card">
                    <div class="metric-title">
                        <span>湿度 (HUMIDITY)</span>
                        <span id="espHumConditionTag" class="tag">--</span>
                    </div>
                    <div>
                        <span class="metric-value" id="espHumVal">--.-</span><span class="metric-unit">%</span>
                    </div>
                    <div class="metric-subtext" id="espHeatstrokeText">熱中症リスク: --</div>
                </div>

                <div class="metric-card">
                    <div class="metric-title">
                        <span>気圧 (PRESSURE)</span>
                        <span id="espBmpBadge" class="tag">BMP280</span>
                    </div>
                    <div>
                        <span class="metric-value" id="espPressVal">----.-</span><span class="metric-unit">hPa</span>
                    </div>
                    <div class="metric-subtext" id="espAltText">標高換算: -- m</div>
                </div>

                <div class="metric-card">
                    <div class="metric-title">
                        <span>ESP32 接続ステータス</span>
                        <span id="espRssiTag" class="tag">RSSI: --</span>
                    </div>
                    <div>
                        <span class="metric-value" id="espConnVal" style="font-size:1.15rem;">未接続</span>
                    </div>
                    <div class="metric-subtext" id="espLastUpdateText">最終更新: --</div>
                </div>
            </div>

            <div style="display: flex; gap: 8px; margin-bottom: 16px;">
                <button class="btn btn-outline btn-sm" onclick="refreshEspData()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg>
                    今すぐ更新 (Refresh Telemetry)
                </button>
                <button class="btn btn-outline btn-sm" onclick="retryEspConnection()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
                    再接続 (Reconnect BLE/USB)
                </button>
            </div>

            <!-- ESP Configuration Card -->
            <div class="card">
                <div class="card-header">
                    <div>
                        <div class="card-title">ESP32 連携設定 & センサー補正</div>
                        <div class="card-description">Bluetooth Low Energy (BLE) / USBシリアル / WiFiの通信とオフセット調整</div>
                    </div>
                </div>

                <div class="switch-row">
                    <div>
                        <div class="switch-title">ESP32 連携を有効化</div>
                        <div class="switch-desc">環境センサーの取得および赤外線スマートリモコン機能を使用する</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkEspEnabled"><span class="slider"></span></label>
                </div>

                <div class="switch-row">
                    <div>
                        <div class="switch-title">時計画面に温湿度・気圧を表示</div>
                        <div class="switch-desc">卓上時計のサブ情報欄にESP32から取得したリアルタイム数値を反映</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkShowEspOnClock"><span class="slider"></span></label>
                </div>

                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-top: 16px;">
                    <div class="form-group">
                        <label class="form-label">接続モード (Connection Mode)</label>
                        <select id="selEspMode">
                            <option value="BLE">Bluetooth LE (Nordic UART)</option>
                            <option value="USB_SERIAL">USB シリアル (OTG 115200bps)</option>
                            <option value="WIFI_HTTP">WiFi HTTP / REST</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label class="form-label">BLE アドバタイズ名</label>
                        <input type="text" id="txtEspBleName" value="ESP32C3-Sensor">
                    </div>

                    <div class="form-group">
                        <label class="form-label">USB ボーレート</label>
                        <select id="selEspBaud">
                            <option value="115200">115200 bps (推奨)</option>
                            <option value="57600">57600 bps</option>
                            <option value="38400">38400 bps</option>
                            <option value="9600">9600 bps</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label class="form-label">データ取得間隔 (秒)</label>
                        <input type="number" id="numEspInterval" value="5" min="2" max="300">
                    </div>
                </div>

                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px;">
                    <div class="form-group">
                        <label class="form-label">WiFi ホスト / IP (WiFiモード時)</label>
                        <input type="text" id="txtEspHost" placeholder="192.168.1.100">
                    </div>
                    <div class="form-group">
                        <label class="form-label">WiFi ポート</label>
                        <input type="number" id="numEspPort" value="80" min="1" max="65535">
                    </div>
                </div>

                <h4 style="font-size: 0.88rem; font-weight: 600; color: #fff; margin: 16px 0 8px 0;">センサーキャリブレーション (測定値オフセット)</h4>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px;">
                    <div class="form-group">
                        <label class="form-label">温度補正 (°C)</label>
                        <input type="number" id="numTempOffset" step="0.1" value="0.0">
                    </div>
                    <div class="form-group">
                        <label class="form-label">湿度補正 (%RH)</label>
                        <input type="number" id="numHumOffset" step="0.5" value="0.0">
                    </div>
                    <div class="form-group">
                        <label class="form-label">気圧補正 (hPa)</label>
                        <input type="number" id="numPressOffset" step="0.5" value="0.0">
                    </div>
                </div>

                <div style="margin-top: 12px;">
                    <button class="btn" onclick="saveEspConfig()">
                        設定を保存 (Save ESP Settings)
                    </button>
                </div>
            </div>

            <!-- Arduino Sketch Download Card -->
            <div class="card" style="margin-top: 16px;">
                <div class="card-header">
                    <div>
                        <div class="card-title">ESP32-C3 Arduino スケッチ (Source Code)</div>
                        <div class="card-description">赤外線学習・送受信＆AHT20/BMP280統合ファームウェア</div>
                    </div>
                    <div style="display: flex; gap: 8px;">
                        <a href="/api/esp/sketch?download=true" class="btn btn-outline btn-sm" style="text-decoration:none;">
                            📥 .ino ファイルをダウンロード
                        </a>
                        <button class="btn btn-outline btn-sm" onclick="copyArduinoSketch()">
                            📋 コードをコピー
                        </button>
                    </div>
                </div>
                <div class="code-box" id="arduinoSketchBox" style="max-height: 260px;">
                    スケッチ読み込み中...
                </div>
            </div>
        </div>
        """
    }

    fun getIrModalHtml(): String {
        return """
        <!-- IR Button Edit / Create Modal -->
        <div id="irButtonModal" class="modal-bg">
            <div class="modal-card">
                <h3 style="margin-bottom: 16px; font-size: 1.05rem; font-weight: 600; color: #fff;" id="irModalTitle">
                    リモコンボタン設定
                </h3>
                <input type="hidden" id="editIrId">

                <div class="form-group">
                    <label class="form-label">ボタン表示名 (Name)</label>
                    <input type="text" id="irBtnName" placeholder="例: リビング照明 全灯">
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                    <div class="form-group">
                        <label class="form-label">機器カテゴリ (Category)</label>
                        <select id="irBtnCategory" onchange="onIrCategoryChange()">
                            <option value="LIGHTING">💡 照明 (Lighting)</option>
                            <option value="AIR_CONDITIONER">❄️ エアコン (Air Conditioner)</option>
                            <option value="TV">📺 テレビ (TV)</option>
                            <option value="FAN">🌀 扇風機 (Fan)</option>
                            <option value="HEATER">🔥 暖房 (Heater)</option>
                            <option value="OTHER">🔌 その他 (Other)</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label">アイコン (Icon)</label>
                        <select id="irBtnIcon">
                            <option value="lightbulb">💡 電球 (Lightbulb)</option>
                            <option value="power_settings_new">⏻ 電源 (Power)</option>
                            <option value="ac_unit">❄️ エアコン (Snow)</option>
                            <option value="tv">📺 テレビ (TV)</option>
                            <option value="mode_fan">🌀 扇風機 (Fan)</option>
                            <option value="whatshot">🔥 暖房 (Flame)</option>
                            <option value="volume_up">🔊 音量+ (Vol Up)</option>
                            <option value="volume_down">🔉 音量- (Vol Down)</option>
                            <option value="arrow_upward">⬆ 上 (Up)</option>
                            <option value="arrow_downward">⬇ 下 (Down)</option>
                            <option value="numbers">🔢 番号 (Channel)</option>
                        </select>
                    </div>
                </div>

                <div class="form-group">
                    <label class="form-label">ボタンテーマカラー</label>
                    <div class="color-swatch-picker" id="irColorPicker">
                        <div class="color-swatch" style="background:#3B82F6;" onclick="selectIrColor('#3B82F6')"></div>
                        <div class="color-swatch" style="background:#10B981;" onclick="selectIrColor('#10B981')"></div>
                        <div class="color-swatch" style="background:#F59E0B;" onclick="selectIrColor('#F59E0B')"></div>
                        <div class="color-swatch" style="background:#EF4444;" onclick="selectIrColor('#EF4444')"></div>
                        <div class="color-swatch" style="background:#8B5CF6;" onclick="selectIrColor('#8B5CF6')"></div>
                        <div class="color-swatch" style="background:#06B6D4;" onclick="selectIrColor('#06B6D4')"></div>
                        <div class="color-swatch" style="background:#EC4899;" onclick="selectIrColor('#EC4899')"></div>
                    </div>
                    <input type="hidden" id="irBtnColorHex" value="#3B82F6">
                </div>

                <div style="background: var(--surface-subtle); border: 1px solid var(--border-color); border-radius: var(--radius-sm); padding: 12px; margin-bottom: 16px;">
                    <div style="font-weight: 600; font-size: 0.84rem; color: #fff; margin-bottom: 8px;">赤外線信号パラメータ</div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                        <div class="form-group" style="margin-bottom:8px;">
                            <label class="form-label">プロトコル</label>
                            <input type="text" id="irBtnProtocol" value="NEC">
                        </div>
                        <div class="form-group" style="margin-bottom:8px;">
                            <label class="form-label">ビット数 (Bits)</label>
                            <input type="number" id="irBtnBits" value="32">
                        </div>
                    </div>
                    <div class="form-group" style="margin-bottom:8px;">
                        <label class="form-label">HEX コード</label>
                        <input type="text" id="irBtnHex" placeholder="0x00FF807F">
                    </div>
                    <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label">RAW パルス (UNKNOWN時)</label>
                        <textarea id="irBtnRaw" class="form-control" rows="2" placeholder="カンマ区切りのμsパルス列"></textarea>
                    </div>
                </div>

                <!-- Automations -->
                <div style="font-weight: 600; font-size: 0.84rem; color: #fff; margin-bottom: 8px;">スマート自動化連携</div>
                <div class="switch-row" style="padding: 8px 0;">
                    <div>
                        <div class="switch-title" style="font-size:0.82rem;">朝のアラーム鳴動時に自動送信</div>
                        <div class="switch-desc">目覚ましチャイム・アラームが鳴った瞬間に照明をつけるなど</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkIrTriggerAlarm"><span class="slider"></span></label>
                </div>
                <div class="switch-row" style="padding: 8px 0;">
                    <div>
                        <div class="switch-title" style="font-size:0.82rem;">夜間モード突入時に自動送信</div>
                        <div class="switch-desc">消灯やテレビOFFなど</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkIrTriggerNight"><span class="slider"></span></label>
                </div>
                <div class="switch-row" style="padding: 8px 0;">
                    <div>
                        <div class="switch-title" style="font-size:0.82rem;">夜間モード解除時に自動送信</div>
                        <div class="switch-desc">朝の覚醒時に連動</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkIrTriggerNightExit"><span class="slider"></span></label>
                </div>

                <!-- Schedule -->
                <div class="switch-row" style="padding: 8px 0;">
                    <div>
                        <div class="switch-title" style="font-size:0.82rem;">指定時刻タイマー送信</div>
                        <div class="switch-desc">毎日指定した時刻に自動で赤外線を発信</div>
                    </div>
                    <label class="switch"><input type="checkbox" id="chkIrScheduleEnabled" onchange="toggleIrScheduleFields()"><span class="slider"></span></label>
                </div>

                <div id="irScheduleFields" style="display:none; background: var(--surface-subtle); padding: 12px; border-radius: var(--radius-sm); border: 1px solid var(--border-color); margin-top: 8px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                        <div class="form-group">
                            <label class="form-label">時 (0-23)</label>
                            <input type="number" id="irScheduleHour" min="0" max="23" value="7">
                        </div>
                        <div class="form-group">
                            <label class="form-label">分 (0-59)</label>
                            <input type="number" id="irScheduleMinute" min="0" max="59" value="0">
                        </div>
                    </div>
                    <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label">繰り返し曜日</label>
                        <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="1" checked> 月</label>
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="2" checked> 火</label>
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="3" checked> 水</label>
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="4" checked> 木</label>
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="5" checked> 金</label>
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="6" checked> 土</label>
                            <label style="font-size:0.78rem;"><input type="checkbox" name="irDay" value="7" checked> 日</label>
                        </div>
                    </div>
                </div>

                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 20px; gap: 8px;">
                    <button class="btn btn-outline btn-sm" type="button" onclick="testModalIrSignal()">
                        🚀 テスト送信
                    </button>
                    <div style="display: flex; gap: 8px;">
                        <button class="btn btn-outline btn-sm" type="button" onclick="closeIrModal()">キャンセル</button>
                        <button class="btn btn-sm" type="button" onclick="saveIrModal()">保存する</button>
                    </div>
                </div>
            </div>
        </div>
        """
    }

    fun getIrEspScript(): String {
        return """
        // ==========================================
        // SMART IR REMOTE & ESP32 SENSOR CONTROLLER
        // ==========================================
        var activeIrCategory = 'ALL';
        var cachedCandidate = null;

        function renderIrData(data) {
            var irButtons = data.irButtons || [];
            var learnState = data.irLearnState || {};

            // 1. Update Learning State
            var isLearning = !!learnState.isLearning;
            var indicator = document.getElementById('irLearnIndicator');
            var btnStart = document.getElementById('btnStartLearn');
            var btnStop = document.getElementById('btnStopLearn');
            var desc = document.getElementById('irLearnStatusDesc');

            if (isLearning) {
                indicator.className = 'pulse-indicator';
                indicator.style.display = 'inline-block';
                btnStart.style.display = 'none';
                btnStop.style.display = 'inline-flex';
                desc.innerHTML = '<span style="color:#ef4444;font-weight:600;">学習待機中...</span> リモコンのボタンを押してください';
            } else {
                indicator.className = '';
                indicator.style.display = 'none';
                btnStart.style.display = 'inline-flex';
                btnStop.style.display = 'none';
                desc.innerText = learnState.statusMessage || 'ESP32-C3の受光部にリモコンを向けて学習ボタンを押します';
            }

            // 2. Candidate Signal
            var candBox = document.getElementById('irCandidateBox');
            var candDet = document.getElementById('irCandidateDetails');
            var candRaw = document.getElementById('irCandidateRawLog');
            if (learnState.lastLearnedSignal) {
                cachedCandidate = learnState.lastLearnedSignal;
                candBox.style.display = 'block';
                candDet.innerText = 'Protocol: ' + cachedCandidate.protocol + ' | Hex: ' + cachedCandidate.hexCode + ' (' + cachedCandidate.bits + ' bits)';
                if (cachedCandidate.rawCode) {
                    candRaw.style.display = 'block';
                    candRaw.innerText = 'RAW: ' + cachedCandidate.rawCode;
                } else {
                    candRaw.style.display = 'none';
                }
            } else {
                cachedCandidate = null;
                candBox.style.display = 'none';
            }

            // 3. Category Counts
            var counts = { ALL: irButtons.length, LIGHTING: 0, AIR_CONDITIONER: 0, TV: 0, FAN: 0, HEATER: 0, OTHER: 0 };
            irButtons.forEach(b => {
                if (counts[b.category] !== undefined) counts[b.category]++;
                else counts.OTHER++;
            });
            for (var k in counts) {
                var el = document.getElementById('count' + k);
                if (el) el.innerText = counts[k];
            }

            // 4. Render Buttons
            var filtered = irButtons;
            if (activeIrCategory !== 'ALL') {
                filtered = irButtons.filter(b => b.category === activeIrCategory);
            }

            var listEl = document.getElementById('irButtonsList');
            if (filtered.length === 0) {
                listEl.innerHTML = '<p style="color:var(--text-muted); font-size:0.85rem; padding:12px 0;">該当するリモコンボタンが登録されていません。「新規ボタン手動追加」または赤外線学習で追加してください。</p>';
                return;
            }

            var html = '';
            filtered.forEach(b => {
                var color = b.colorHex || '#3B82F6';
                var tags = '<span class="tag">' + (b.categoryDisplay || b.category) + '</span>';
                if (b.triggerOnAlarm) tags += ' <span class="tag" style="color:#f59e0b;border-color:rgba(245,158,11,0.3);">⏰ 朝アラーム連動</span>';
                if (b.triggerOnNightMode) tags += ' <span class="tag" style="color:#818cf8;border-color:rgba(129,140,248,0.3);">🌙 夜間突入連動</span>';
                if (b.triggerOnNightExit) tags += ' <span class="tag" style="color:#34d399;border-color:rgba(52,211,153,0.3);">☀️ 夜間解除連動</span>';
                if (b.isScheduleEnabled) {
                    var timeStr = String(b.scheduleHour).padStart(2, '0') + ':' + String(b.scheduleMinute).padStart(2, '0');
                    tags += ' <span class="tag" style="color:#60a5fa;border-color:rgba(96,165,250,0.3);">⏱️ ' + timeStr + '</span>';
                }

                var codeLabel = b.hexCode && b.hexCode !== '0x0' && b.hexCode !== '' ?
                    (b.protocol + ' ' + b.hexCode) : ('RAW (' + (b.rawCode ? b.rawCode.split(',').length : 0) + ' pulses)');

                html += '<div class="item-card">' +
                    '<div style="display:flex; align-items:center; gap:12px; flex:1; min-width:220px;">' +
                        '<div style="width:38px; height:38px; border-radius:var(--radius-sm); background:' + color + '22; border:1px solid ' + color + '55; display:flex; align-items:center; justify-content:center; color:' + color + '; font-weight:bold; font-size:1.1rem; flex-shrink:0;">' +
                            getCategoryEmoji(b.category) +
                        '</div>' +
                        '<div>' +
                            '<div style="font-weight:600; font-size:0.92rem; color:#fff;">' + b.name + '</div>' +
                            '<div style="font-family:var(--font-mono); font-size:0.75rem; color:var(--text-muted); margin-top:2px;">' + codeLabel + '</div>' +
                            '<div style="margin-top:4px; display:flex; gap:4px; flex-wrap:wrap;">' + tags + '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display:flex; gap:6px; align-items:center; flex-wrap:wrap;">' +
                        '<button class="btn btn-sm" style="background:' + color + '; border-color:' + color + ';" onclick="sendIrButton(\'' + b.id + '\', this)">' +
                            '🚀 送信' +
                        '</button>' +
                        '<button class="btn btn-outline btn-sm" onclick="editIrButton(\'' + b.id + '\')">編集</button>' +
                        '<button class="btn btn-danger btn-sm" onclick="deleteIrButton(\'' + b.id + '\')">削除</button>' +
                    '</div>' +
                '</div>';
            });
            listEl.innerHTML = html;
        }

        function getCategoryEmoji(cat) {
            switch(cat) {
                case 'LIGHTING': return '💡';
                case 'AIR_CONDITIONER': return '❄️';
                case 'TV': return '📺';
                case 'FAN': return '🌀';
                case 'HEATER': return '🔥';
                default: return '🔌';
            }
        }

        function filterIrCategory(cat, btn) {
            activeIrCategory = cat;
            document.querySelectorAll('#irCategoryFilters .category-pill').forEach(b => b.classList.remove('active'));
            if (btn) btn.classList.add('active');
            if (currentData) renderIrData(currentData);
        }

        function startIrLearning() {
            fetch('/api/ir/learn/start', { method: 'POST' })
                .then(() => {
                    showToast('赤外線学習を開始しました。リモコンのボタンを押してください');
                    fetchStatus();
                });
        }

        function stopIrLearning() {
            fetch('/api/ir/learn/stop', { method: 'POST' })
                .then(() => {
                    showToast('赤外線学習を停止しました');
                    fetchStatus();
                });
        }

        function clearIrLearned() {
            fetch('/api/ir/learn/clear', { method: 'POST' })
                .then(() => {
                    showToast('学習履歴をクリアしました');
                    fetchStatus();
                });
        }

        function sendIrButton(id, btn) {
            if (btn) {
                btn.style.transform = 'scale(0.92)';
                setTimeout(() => btn.style.transform = '', 150);
            }
            fetch('/api/ir/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    showToast('赤外線信号を送信しました 🚀');
                } else {
                    showToast('赤外線送信エラー (ESP32が未接続の可能性があります)');
                }
            })
            .catch(() => showToast('送信リクエストが失敗しました'));
        }

        function testCandidateSignal() {
            if (!cachedCandidate) return;
            fetch('/api/ir/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    protocol: cachedCandidate.protocol,
                    hex: cachedCandidate.hexCode,
                    bits: cachedCandidate.bits,
                    raw: cachedCandidate.rawCode
                })
            }).then(() => showToast('学習シグナルをテスト送信しました'));
        }

        function openSaveCandidateModal() {
            if (!cachedCandidate) return;
            openAddIrModal();
            document.getElementById('irBtnProtocol').value = cachedCandidate.protocol || 'NEC';
            document.getElementById('irBtnHex').value = cachedCandidate.hexCode || '';
            document.getElementById('irBtnBits').value = cachedCandidate.bits || 32;
            document.getElementById('irBtnRaw').value = cachedCandidate.rawCode || '';
        }

        function sendDirectIrSignal() {
            var protocol = document.getElementById('directProtocol').value;
            var hex = document.getElementById('directHex').value.trim();
            var bits = parseInt(document.getElementById('directBits').value) || 32;
            var raw = document.getElementById('directRaw').value.trim();

            fetch('/api/ir/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ protocol: protocol, hex: hex, bits: bits, raw: raw })
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) showToast('直接指定赤外線信号を送信しました');
                else showToast('赤外線送信失敗');
            });
        }

        // Modal Controls
        function openAddIrModal() {
            document.getElementById('irModalTitle').innerText = '新規リモコンボタン追加';
            document.getElementById('editIrId').value = '';
            document.getElementById('irBtnName').value = '';
            document.getElementById('irBtnCategory').value = 'LIGHTING';
            document.getElementById('irBtnIcon').value = 'lightbulb';
            selectIrColor('#3B82F6');
            document.getElementById('irBtnProtocol').value = 'NEC';
            document.getElementById('irBtnHex').value = '';
            document.getElementById('irBtnBits').value = '32';
            document.getElementById('irBtnRaw').value = '';
            document.getElementById('chkIrTriggerAlarm').checked = false;
            document.getElementById('chkIrTriggerNight').checked = false;
            document.getElementById('chkIrTriggerNightExit').checked = false;
            document.getElementById('chkIrScheduleEnabled').checked = false;
            document.getElementById('irScheduleHour').value = 7;
            document.getElementById('irScheduleMinute').value = 0;
            document.querySelectorAll('input[name="irDay"]').forEach(cb => cb.checked = true);
            toggleIrScheduleFields();
            document.getElementById('irButtonModal').classList.add('active');
        }

        function editIrButton(id) {
            if (!currentData || !currentData.irButtons) return;
            var b = currentData.irButtons.find(x => x.id === id);
            if (!b) return;

            document.getElementById('irModalTitle').innerText = 'リモコンボタン編集';
            document.getElementById('editIrId').value = b.id;
            document.getElementById('irBtnName').value = b.name;
            document.getElementById('irBtnCategory').value = b.category;
            document.getElementById('irBtnIcon').value = b.iconName || 'lightbulb';
            selectIrColor(b.colorHex || '#3B82F6');
            document.getElementById('irBtnProtocol').value = b.protocol || 'NEC';
            document.getElementById('irBtnHex').value = b.hexCode || '';
            document.getElementById('irBtnBits').value = b.bits || 32;
            document.getElementById('irBtnRaw').value = b.rawCode || '';
            document.getElementById('chkIrTriggerAlarm').checked = !!b.triggerOnAlarm;
            document.getElementById('chkIrTriggerNight').checked = !!b.triggerOnNightMode;
            document.getElementById('chkIrTriggerNightExit').checked = !!b.triggerOnNightExit;
            document.getElementById('chkIrScheduleEnabled').checked = !!b.isScheduleEnabled;
            document.getElementById('irScheduleHour').value = b.scheduleHour || 7;
            document.getElementById('irScheduleMinute').value = b.scheduleMinute || 0;

            var days = b.scheduleDays || [1,2,3,4,5,6,7];
            document.querySelectorAll('input[name="irDay"]').forEach(cb => {
                cb.checked = days.includes(parseInt(cb.value));
            });
            toggleIrScheduleFields();
            document.getElementById('irButtonModal').classList.add('active');
        }

        function closeIrModal() {
            document.getElementById('irButtonModal').classList.remove('active');
        }

        function selectIrColor(color) {
            document.getElementById('irBtnColorHex').value = color;
            document.querySelectorAll('#irColorPicker .color-swatch').forEach(sw => {
                sw.classList.toggle('active', sw.style.backgroundColor === color || sw.style.background === color);
            });
        }

        function onIrCategoryChange() {
            var cat = document.getElementById('irBtnCategory').value;
            var iconSel = document.getElementById('irBtnIcon');
            switch(cat) {
                case 'LIGHTING': iconSel.value = 'lightbulb'; selectIrColor('#F59E0B'); break;
                case 'AIR_CONDITIONER': iconSel.value = 'ac_unit'; selectIrColor('#06B6D4'); break;
                case 'TV': iconSel.value = 'tv'; selectIrColor('#3B82F6'); break;
                case 'FAN': iconSel.value = 'mode_fan'; selectIrColor('#10B981'); break;
                case 'HEATER': iconSel.value = 'whatshot'; selectIrColor('#EF4444'); break;
                default: iconSel.value = 'power_settings_new'; selectIrColor('#8B5CF6'); break;
            }
        }

        function toggleIrScheduleFields() {
            var chk = document.getElementById('chkIrScheduleEnabled').checked;
            document.getElementById('irScheduleFields').style.display = chk ? 'block' : 'none';
        }

        function testModalIrSignal() {
            var protocol = document.getElementById('irBtnProtocol').value;
            var hex = document.getElementById('irBtnHex').value.trim();
            var bits = parseInt(document.getElementById('irBtnBits').value) || 32;
            var raw = document.getElementById('irBtnRaw').value.trim();

            fetch('/api/ir/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ protocol: protocol, hex: hex, bits: bits, raw: raw })
            }).then(() => showToast('モーダル内の赤外線信号を送信しました'));
        }

        function saveIrModal() {
            var name = document.getElementById('irBtnName').value.trim();
            if (!name) { alert('ボタン表示名を入力してください'); return; }

            var days = [];
            document.querySelectorAll('input[name="irDay"]:checked').forEach(cb => {
                days.push(parseInt(cb.value));
            });

            var payload = {
                id: document.getElementById('editIrId').value,
                name: name,
                category: document.getElementById('irBtnCategory').value,
                iconName: document.getElementById('irBtnIcon').value,
                colorHex: document.getElementById('irBtnColorHex').value,
                protocol: document.getElementById('irBtnProtocol').value,
                hexCode: document.getElementById('irBtnHex').value.trim(),
                bits: parseInt(document.getElementById('irBtnBits').value) || 32,
                rawCode: document.getElementById('irBtnRaw').value.trim(),
                triggerOnAlarm: document.getElementById('chkIrTriggerAlarm').checked,
                triggerOnNightMode: document.getElementById('chkIrTriggerNight').checked,
                triggerOnNightExit: document.getElementById('chkIrTriggerNightExit').checked,
                isScheduleEnabled: document.getElementById('chkIrScheduleEnabled').checked,
                scheduleHour: parseInt(document.getElementById('irScheduleHour').value) || 0,
                scheduleMinute: parseInt(document.getElementById('irScheduleMinute').value) || 0,
                scheduleDays: days
            };

            fetch('/api/ir/button/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(() => {
                showToast('リモコンボタンを保存しました');
                closeIrModal();
                fetchStatus();
            });
        }

        function deleteIrButton(id) {
            if (!confirm('このリモコンボタンを削除してもよろしいですか？')) return;
            fetch('/api/ir/button/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            }).then(() => {
                showToast('リモコンボタンを削除しました');
                fetchStatus();
            });
        }

        // ==========================================
        // ESP32 SENSOR & TELEMETRY CONTROLLER
        // ==========================================
        function renderEspData(data) {
            var esp = data.espSensor;
            if (!esp) return;

            // Telemetry Cards
            if (esp.temperature !== null && esp.temperature !== undefined) {
                document.getElementById('espTempVal').innerText = Number(esp.temperature).toFixed(1);
            } else {
                document.getElementById('espTempVal').innerText = '--.-';
            }

            if (esp.humidity !== null && esp.humidity !== undefined) {
                document.getElementById('espHumVal').innerText = Number(esp.humidity).toFixed(1);
            } else {
                document.getElementById('espHumVal').innerText = '--.-';
            }

            if (esp.pressure !== null && esp.pressure !== undefined) {
                document.getElementById('espPressVal').innerText = Number(esp.pressure).toFixed(1);
            } else {
                document.getElementById('espPressVal').innerText = '----.-';
            }

            if (esp.altitude !== null && esp.altitude !== undefined) {
                document.getElementById('espAltText').innerText = '標高換算: ' + Math.round(esp.altitude) + ' m';
            }

            document.getElementById('espDiscomfortText').innerText = '体感: ' + (esp.discomfortLabel || '--');
            document.getElementById('espHeatstrokeText').innerText = '熱中症リスク: ' + (esp.heatstrokeRiskLabel || '--');

            var connEl = document.getElementById('espConnVal');
            if (esp.isConnected) {
                connEl.innerText = '接続中 (' + esp.connectionType + ')';
                connEl.style.color = 'var(--success)';
            } else if (esp.isConnecting) {
                connEl.innerText = '接続試行中...';
                connEl.style.color = 'var(--warning)';
            } else {
                connEl.innerText = '未接続';
                connEl.style.color = 'var(--danger)';
            }

            if (esp.rssi !== null && esp.rssi !== undefined) {
                document.getElementById('espRssiTag').innerText = 'RSSI: ' + esp.rssi + ' dBm';
            }

            var ahtBadge = document.getElementById('espAhtBadge');
            if (esp.ahtOk) {
                ahtBadge.className = 'tag';
                ahtBadge.innerText = 'AHT20 正常';
                ahtBadge.style.color = 'var(--success)';
            } else {
                ahtBadge.innerText = 'AHT20';
                ahtBadge.style.color = 'var(--text-muted)';
            }

            var bmpBadge = document.getElementById('espBmpBadge');
            if (esp.bmpOk) {
                bmpBadge.className = 'tag';
                bmpBadge.innerText = 'BMP280 正常';
                bmpBadge.style.color = 'var(--success)';
            } else {
                bmpBadge.innerText = 'BMP280';
                bmpBadge.style.color = 'var(--text-muted)';
            }

            if (esp.lastUpdatedEpochMs > 0) {
                var d = new Date(esp.lastUpdatedEpochMs);
                document.getElementById('espLastUpdateText').innerText = '最終更新: ' + d.toLocaleTimeString();
            }

            // Form inputs (only update if not focused)
            if (document.activeElement !== document.getElementById('txtEspBleName')) {
                document.getElementById('chkEspEnabled').checked = !!esp.enabled;
                document.getElementById('chkShowEspOnClock').checked = !!esp.showOnClock;
                document.getElementById('selEspMode').value = esp.mode || 'BLE';
                document.getElementById('txtEspBleName').value = esp.bleDeviceName || 'ESP32C3-Sensor';
                document.getElementById('selEspBaud').value = String(esp.baudRate || 115200);
                document.getElementById('numEspInterval').value = esp.intervalSeconds || 5;
                document.getElementById('txtEspHost').value = esp.host || '192.168.1.100';
                document.getElementById('numEspPort').value = esp.port || 80;
                document.getElementById('numTempOffset').value = esp.tempOffset || 0.0;
                document.getElementById('numHumOffset').value = esp.humOffset || 0.0;
                document.getElementById('numPressOffset').value = esp.pressOffset || 0.0;
            }
        }

        function refreshEspData() {
            fetch('/api/esp/refresh', { method: 'POST' })
                .then(() => {
                    showToast('ESP32 センサーデータを要求しました');
                    setTimeout(fetchStatus, 500);
                });
        }

        function retryEspConnection() {
            fetch('/api/esp/retry', { method: 'POST' })
                .then(() => {
                    showToast('ESP32 への再接続を開始しました');
                    setTimeout(fetchStatus, 800);
                });
        }

        function saveEspConfig() {
            var payload = {
                enabled: document.getElementById('chkEspEnabled').checked,
                showOnClock: document.getElementById('chkShowEspOnClock').checked,
                mode: document.getElementById('selEspMode').value,
                bleDeviceName: document.getElementById('txtEspBleName').value.trim(),
                baudRate: parseInt(document.getElementById('selEspBaud').value) || 115200,
                intervalSeconds: parseInt(document.getElementById('numEspInterval').value) || 5,
                host: document.getElementById('txtEspHost').value.trim(),
                port: parseInt(document.getElementById('numEspPort').value) || 80,
                tempOffset: parseFloat(document.getElementById('numTempOffset').value) || 0.0,
                humOffset: parseFloat(document.getElementById('numHumOffset').value) || 0.0,
                pressOffset: parseFloat(document.getElementById('numPressOffset').value) || 0.0
            };

            fetch('/api/esp/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(() => {
                showToast('ESP32 連携設定を保存しました');
                fetchStatus();
            });
        }

        function loadArduinoSketch() {
            var box = document.getElementById('arduinoSketchBox');
            if (!box || box.dataset.loaded) return;
            fetch('/api/esp/sketch')
                .then(r => r.text())
                .then(t => {
                    box.innerText = t;
                    box.dataset.loaded = 'true';
                })
                .catch(() => {
                    box.innerText = '// スケッチの読み込みに失敗しました';
                });
        }

        function copyArduinoSketch() {
            var box = document.getElementById('arduinoSketchBox');
            if (box && box.innerText) {
                navigator.clipboard.writeText(box.innerText).then(() => {
                    showToast('スケッチコードをクリップボードにコピーしました 📋');
                });
            }
        }
        """
    }
}

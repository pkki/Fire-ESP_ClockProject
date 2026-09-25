package com.example.camera

/**
 * Web Dashboard extensions for Remote Updates & OTA:
 * - Android DeskClock Application Remote Update (.apk upload & installation launch)
 * - ESP8266 / ESP32 Sensor & IR Remote Firmware OTA Update (.bin upload & flashing over Wi-Fi)
 * - Remote URL APK Download & Install
 * - Real-time progress bar, speed, ETA, and OTA console logs
 * - Arduino OTA setup guide with ready-to-use C++ sketches
 */
object WebDashboardUpdates {

    fun getUpdatesNavButtonHtml(): String {
        return """
            <button class="tab-btn" onclick="switchTab('updates', this)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                Remote Updates (APK & ESP)
            </button>
        """
    }

    fun getTabUpdatesHtml(): String {
        return """
        <!-- TAB: REMOTE UPDATES & OTA (APK & ESP) -->
        <div id="tab-updates" class="tab-content">
            <!-- Header Information Banner -->
            <div class="card" style="margin-bottom: 20px; background: linear-gradient(135deg, rgba(59, 130, 246, 0.12), rgba(16, 185, 129, 0.08)); border-color: rgba(59, 130, 246, 0.3);">
                <div style="display: flex; align-items: flex-start; gap: 16px;">
                    <div style="width: 44px; height: 44px; border-radius: 12px; background: rgba(59, 130, 246, 0.2); display: flex; align-items: center; justify-content: center; flex-shrink: 0; color: #3b82f6;">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="width:24px; height:24px;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    </div>
                    <div>
                        <div style="font-size: 1.05rem; font-weight: 700; color: #fff; margin-bottom: 4px;">
                            リモートアップデート & OTA (Over-The-Air) 管理
                        </div>
                        <div style="font-size: 0.82rem; color: #cbd5e1; line-height: 1.5;">
                            PCやスマートフォンのブラウザから、Wi-Fi経由で<strong>本アプリ（DeskClock Android APK）</strong>および<strong>ESP8266 / ESP32 センサーファームウェア（.bin）</strong>を遠隔更新できます。USBケーブルの抜き差しやADBコマンド不要で、常に最新版へワンクリックでアップデート可能です。
                        </div>
                    </div>
                </div>
            </div>

            <div class="grid-2">
                <!-- CARD 1: ANDROID APP UPDATE (APK) -->
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title" style="display: flex; align-items: center; gap: 8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="color: #10b981;"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
                                DeskClock アプリ更新 (APK)
                            </div>
                            <div class="card-description">本機（Androidタブレット）のアプリケーションをリモート更新</div>
                        </div>
                        <span class="badge badge-online" style="font-size: 0.72rem;">Android OTA</span>
                    </div>

                    <!-- Flow summary -->
                    <div style="background: var(--surface-subtle); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 12px; margin-bottom: 16px; font-size: 0.78rem; color: var(--text-muted); line-height: 1.5;">
                        <span style="color:#10b981; font-weight:600;">更新手順:</span><br>
                        1. 新しい <code>.apk</code> ファイルをドラッグ＆ドロップ、またはURLを入力<br>
                        2. 進捗バーで端末へ高速転送（プログレス表示）<br>
                        3. 卓上時計画面にAndroid公式パッケージインストーラーが起動し、「更新」をタップして完了
                    </div>

                    <!-- APK Upload Dropzone -->
                    <div class="upload-dropzone" id="apkDropzone" onclick="document.getElementById('apkFileInput').click()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                        <p style="font-weight: 600; font-size: 0.88rem; color: #fff;">クリックまたはドラッグ＆ドロップで APK を選択</p>
                        <p style="font-size: 0.76rem; color: var(--text-muted); margin-top: 4px;">DeskClock-release.apk または debug.apk に対応</p>
                        <input type="file" id="apkFileInput" accept=".apk,application/vnd.android.package-archive" style="display:none" onchange="uploadApk(this.files[0])">
                    </div>

                    <!-- Inline APK Upload Progress -->
                    <div id="apkUploadInline" style="display:none; margin-top:14px; background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:12px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; font-size:0.8rem;">
                            <span id="apkUploadInlineName" style="color:#fff; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:70%;">DeskClock.apk</span>
                            <span id="apkUploadInlinePercent" style="color:var(--primary); font-family:var(--font-mono); font-weight:700;">0%</span>
                        </div>
                        <div class="progress-track" style="margin:8px 0 6px 0; height:7px;">
                            <div id="apkUploadInlineBar" class="progress-fill" style="width:0%;"></div>
                        </div>
                        <div style="display:flex; justify-content:space-between; font-size:0.72rem; color:var(--text-muted);">
                            <span id="apkUploadInlineBytes">0 / 0 MB</span>
                            <span id="apkUploadInlineSpeed">-- MB/s</span>
                        </div>
                    </div>

                    <!-- Remote URL APK Download Option -->
                    <div style="margin-top: 20px; border-top: 1px solid var(--border-color); padding-top: 16px;">
                        <label class="form-label" style="font-size: 0.8rem; color: #fff; font-weight: 600; margin-bottom: 6px; display: block;">
                            🌐 外部URLまたはローカルサーバーから直接ダウンロード更新
                        </label>
                        <div style="display: flex; gap: 8px;">
                            <input type="url" id="txtApkDownloadUrl" placeholder="https://github.com/.../app-release.apk" style="flex: 1; font-size: 0.8rem;">
                            <button class="btn btn-primary btn-sm" onclick="downloadApkFromUrl()" id="btnDownloadApk">
                                取得＆更新
                            </button>
                        </div>
                    </div>

                    <!-- APK Result / Status Box -->
                    <div id="apkStatusBox" style="display:none; margin-top: 14px; padding: 12px; border-radius: var(--radius-md); font-size: 0.8rem; line-height: 1.4;"></div>
                </div>

                <!-- CARD 2: ESP8266 / ESP32 SENSOR FIRMWARE OTA -->
                <div class="card">
                    <div class="card-header">
                        <div>
                            <div class="card-title" style="display: flex; align-items: center; gap: 8px;">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" style="color: #3b82f6;"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>
                                ESP センサー & 赤外線 OTA更新 (.bin)
                            </div>
                            <div class="card-description">ESP8266 / ESP32-C3 マイコンファームウェアをWi-Fi書き込み</div>
                        </div>
                        <span class="badge badge-beta" style="font-size: 0.72rem;">ESP OTA</span>
                    </div>

                    <!-- ESP Target IP & Port configuration -->
                    <div style="background: var(--surface-subtle); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 12px; margin-bottom: 14px;">
                        <div style="font-size: 0.78rem; font-weight: 600; color: #fff; margin-bottom: 8px;">
                            📡 送信先 ESP デバイス設定
                        </div>
                        <div style="display: grid; grid-template-columns: 2fr 1fr auto; gap: 8px; align-items: end;">
                            <div class="form-group" style="margin-bottom:0;">
                                <label class="form-label" style="font-size: 0.72rem;">ESP IP / ホスト名</label>
                                <input type="text" id="txtEspOtaHost" placeholder="192.168.1.xxx または esp32.local">
                            </div>
                            <div class="form-group" style="margin-bottom:0;">
                                <label class="form-label" style="font-size: 0.72rem;">OTA ポート</label>
                                <input type="number" id="txtEspOtaPort" value="80">
                            </div>
                            <div>
                                <button class="btn btn-outline btn-sm" onclick="syncEspOtaHostFromPrefs()" title="設定中のESP IPを同期" style="height:36px; font-size:0.75rem;">
                                    自動取得
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- ESP Binary Dropzone -->
                    <div class="upload-dropzone" id="espOtaDropzone" onclick="document.getElementById('espOtaFileInput').click()">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                        <p style="font-weight: 600; font-size: 0.88rem; color: #fff;">クリックまたはドラッグ＆ドロップで .bin を選択</p>
                        <p style="font-size: 0.76rem; color: var(--text-muted); margin-top: 4px;">Arduino IDE / PlatformIO でビルドした firmware.bin</p>
                        <input type="file" id="espOtaFileInput" accept=".bin,application/octet-stream" style="display:none" onchange="uploadEspOta(this.files[0])">
                    </div>

                    <!-- Inline ESP Upload Progress -->
                    <div id="espOtaUploadInline" style="display:none; margin-top:14px; background:var(--surface-subtle); border:1px solid var(--border-color); border-radius:var(--radius-md); padding:12px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; font-size:0.8rem;">
                            <span id="espOtaUploadInlineName" style="color:#fff; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:70%;">firmware.bin</span>
                            <span id="espOtaUploadInlinePercent" style="color:var(--primary); font-family:var(--font-mono); font-weight:700;">0%</span>
                        </div>
                        <div class="progress-track" style="margin:8px 0 6px 0; height:7px;">
                            <div id="espOtaUploadInlineBar" class="progress-fill" style="width:0%;"></div>
                        </div>
                        <div style="display:flex; justify-content:space-between; font-size:0.72rem; color:var(--text-muted);">
                            <span id="espOtaUploadInlineBytes">0 / 0 KB</span>
                            <span id="espOtaUploadInlineSpeed">-- KB/s</span>
                        </div>
                    </div>

                    <!-- Real-time OTA Status & Terminal Log Console -->
                    <div style="margin-top: 14px;">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
                            <span style="font-size:0.75rem; color:var(--text-muted); font-weight:600;">OTA 書き込みコンソールログ:</span>
                            <button class="btn btn-outline btn-sm" onclick="clearOtaLog()" style="padding:2px 6px; font-size:0.7rem;">クリア</button>
                        </div>
                        <div id="espOtaConsole" style="background:#090b10; border:1px solid var(--border-color); border-radius:var(--radius-md); padding:10px 12px; height:120px; overflow-y:auto; font-family:var(--font-mono); font-size:0.75rem; color:#94a3b8; line-height:1.4;">
                            <div style="color:var(--text-muted);">待機中: ファームウェアバイナリ(.bin)を選択するとOTA書き込みを開始します</div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- CARD 3: ARDUINO / PLATFORMIO OTA FIRMWARE GUIDE -->
            <div class="card" style="margin-top: 20px;">
                <div class="card-header">
                    <div>
                        <div class="card-title">📖 ESP8266 / ESP32 OTA ファームウェア作成ガイド</div>
                        <div class="card-description">バイナリ(.bin)のエクスポート方法および標準HTTPUpdateServer実装コード</div>
                    </div>
                </div>

                <div class="grid-2">
                    <!-- Export steps -->
                    <div>
                        <h4 style="font-size: 0.85rem; font-weight: 600; color: #fff; margin-bottom: 8px;">
                            【1. バイナリ(.bin)の出力手順】
                        </h4>
                        <ul style="font-size: 0.8rem; color: #cbd5e1; line-height: 1.6; padding-left: 20px;">
                            <li><strong>Arduino IDE:</strong> 上部メニューの <code>スケッチ</code> → <code>コンパイル済みバイナリをエクスポート</code> (または <kbd>Ctrl+Alt+S</kbd> / <kbd>Cmd+Alt+S</kbd>)。スケッチと同じフォルダに <code>.bin</code> ファイルが生成されます。</li>
                            <li><strong>PlatformIO:</strong> 画面下の「Build」実行後、プロジェクトフォルダ内の <code>.pio/build/esp32c3/firmware.bin</code> を使用します。</li>
                            <li>出力された <code>.bin</code> ファイルを上のドラッグ＆ドロップ枠へドロップするだけで、数秒でWi-Fi書き込みが行われます。</li>
                        </ul>
                    </div>

                    <!-- Code snippet -->
                    <div>
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
                            <h4 style="font-size: 0.85rem; font-weight: 600; color: #fff;">
                                【2. ESP側OTA受け入れスケッチ（参考例）】
                            </h4>
                            <button class="btn btn-outline btn-sm" onclick="copyOtaSampleSnippet()" style="padding:2px 8px; font-size:0.72rem;">コピー</button>
                        </div>
                        <div class="code-box" id="otaSampleSnippet" style="max-height: 180px; font-size: 0.72rem;">// --- ESP8266の場合 (ESP8266HTTPUpdateServer) ---
#include &lt;ESP8266WiFi.h&gt;
#include &lt;ESP8266WebServer.h&gt;
#include &lt;ESP8266HTTPUpdateServer.h&gt;

ESP8266WebServer httpServer(80);
ESP8266HTTPUpdateServer httpUpdater;

void setup() {
  WiFi.begin("SSID", "PASS");
  while (WiFi.status() != WL_CONNECTED) delay(500);
  
  // OTAエンドポイント '/update' を登録
  httpUpdater.setup(&amp;httpServer, "/update");
  httpServer.begin();
}
void loop() {
  httpServer.handleClient();
}

// --- ESP32 / ESP32-C3の場合 (WebServer + Update.h) ---
#include &lt;WiFi.h&gt;
#include &lt;WebServer.h&gt;
#include &lt;Update.h&gt;
WebServer server(80);

void setupOta() {
  server.on("/update", HTTP_POST, []() {
    server.send(200, "text/plain", (Update.hasError()) ? "FAIL" : "OK");
    ESP.restart();
  }, []() {
    HTTPUpload&amp; upload = server.upload();
    if (upload.status == UPLOAD_FILE_START) {
      Update.begin(UPDATE_SIZE_UNKNOWN);
    } else if (upload.status == UPLOAD_FILE_WRITE) {
      Update.write(upload.buf, upload.currentSize);
    } else if (upload.status == UPLOAD_FILE_END) {
      Update.end(true);
    }
  });
  server.begin();
}</div>
                    </div>
                </div>
            </div>
        </div>
        """
    }

    fun getUpdatesScript(): String {
        return """
        // =========================================================================
        // REMOTE UPDATES & OTA JAVASCRIPT CONTROLLER
        // =========================================================================

        function uploadApk(file) {
            if (!file) return;
            var box = document.getElementById('apkStatusBox');
            if (box) {
                box.style.display = 'block';
                box.style.background = 'rgba(59, 130, 246, 0.15)';
                box.style.border = '1px solid rgba(59, 130, 246, 0.3)';
                box.style.color = '#93c5fd';
                box.innerHTML = '⏳ <strong>' + escapeHtml(file.name) + '</strong> を卓上時計端末へアップロード中...';
            }

            uploadFileWithProgress({
                url: '/api/system/update_apk',
                file: file,
                fieldName: 'file',
                typeLabel: 'APK更新',
                inlinePrefix: 'apkUploadInline',
                onSuccess: function(resp) {
                    if (box) {
                        box.style.background = 'rgba(16, 185, 129, 0.15)';
                        box.style.border = '1px solid rgba(16, 185, 129, 0.4)';
                        box.style.color = '#6ee7b7';
                        box.innerHTML = '✅ <strong>アップロード完了！</strong><br>' +
                            escapeHtml(resp.message || 'APKの受信に成功しました。') + '<br>' +
                            '<span style="font-size:0.75rem; color:#cbd5e1;">※ 卓上時計端末の画面に公式パッケージインストーラーが表示されています。「更新」をタップしてください。</span>';
                    }
                    document.getElementById('apkFileInput').value = '';
                },
                onError: function(err) {
                    if (box) {
                        box.style.background = 'rgba(239, 68, 68, 0.15)';
                        box.style.border = '1px solid rgba(239, 68, 68, 0.4)';
                        box.style.color = '#fca5a5';
                        box.innerHTML = '❌ <strong>更新失敗:</strong> ' + escapeHtml(err);
                    }
                    document.getElementById('apkFileInput').value = '';
                }
            });
        }

        function downloadApkFromUrl() {
            var input = document.getElementById('txtApkDownloadUrl');
            var url = (input.value || '').trim();
            if (!url) {
                showToast('APKのダウンロードURLを入力してください');
                return;
            }

            var btn = document.getElementById('btnDownloadApk');
            var box = document.getElementById('apkStatusBox');
            btn.disabled = true;
            btn.innerText = 'ダウンロード中...';

            if (box) {
                box.style.display = 'block';
                box.style.background = 'rgba(59, 130, 246, 0.15)';
                box.style.border = '1px solid rgba(59, 130, 246, 0.3)';
                box.style.color = '#93c5fd';
                box.innerHTML = '⏳ 外部URLからAPKをダウンロード中: <code>' + escapeHtml(url) + '</code>';
            }

            fetch('/api/system/download_and_install_apk', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: url })
            })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                btn.disabled = false;
                btn.innerText = '取得＆更新';
                if (resp.success) {
                    if (box) {
                        box.style.background = 'rgba(16, 185, 129, 0.15)';
                        box.style.border = '1px solid rgba(16, 185, 129, 0.4)';
                        box.style.color = '#6ee7b7';
                        box.innerHTML = '✅ <strong>ダウンロード＆転送完了！</strong><br>' +
                            escapeHtml(resp.message || 'インストーラーを起動しました。') + '<br>' +
                            '<span style="font-size:0.75rem; color:#cbd5e1;">※ 卓上時計端末の画面にインストーラーが表示されています。</span>';
                    }
                    showToast('APKダウンロード成功！端末画面を確認してください');
                } else {
                    if (box) {
                        box.style.background = 'rgba(239, 68, 68, 0.15)';
                        box.style.border = '1px solid rgba(239, 68, 68, 0.4)';
                        box.style.color = '#fca5a5';
                        box.innerHTML = '❌ <strong>ダウンロード失敗:</strong> ' + escapeHtml(resp.message || 'エラー');
                    }
                    showToast('エラー: ' + (resp.message || 'ダウンロード失敗'));
                }
            })
            .catch(function(err) {
                btn.disabled = false;
                btn.innerText = '取得＆更新';
                if (box) {
                    box.style.background = 'rgba(239, 68, 68, 0.15)';
                    box.style.border = '1px solid rgba(239, 68, 68, 0.4)';
                    box.style.color = '#fca5a5';
                    box.innerHTML = '❌ <strong>通信エラー:</strong> ' + escapeHtml(err.message || '接続に失敗しました');
                }
                showToast('通信エラーが発生しました');
            });
        }

        function uploadEspOta(file) {
            if (!file) return;
            var host = (document.getElementById('txtEspOtaHost').value || '').trim();
            var port = (document.getElementById('txtEspOtaPort').value || '80').trim();

            if (!host) {
                showToast('ESPのIPアドレスまたはホスト名を入力してください');
                appendOtaLog('エラー: ESPのIPアドレスが指定されていません', 'error');
                return;
            }

            clearOtaLog();
            appendOtaLog('OTA準備: ファイル名 ' + file.name + ' (' + formatBytes(file.size) + ')', 'info');
            appendOtaLog('対象ESP: http://' + host + ':' + port + '/update', 'info');
            appendOtaLog('① 卓上時計サーバーへバイナリをアップロード中...', 'info');

            var headers = {
                'X-ESP-Host': host,
                'X-ESP-Port': port
            };

            uploadFileWithProgress({
                url: '/api/esp/ota_update',
                file: file,
                fieldName: 'file',
                headers: headers,
                typeLabel: 'ESP OTA',
                inlinePrefix: 'espOtaUploadInline',
                onProgress: function(percent, loaded, total, speed) {
                    if (percent === 100) {
                        appendOtaLog('② 卓上時計端末からESP (http://' + host + ':' + port + '/update) へフラッシュ書き込み中...', 'info');
                    }
                },
                onSuccess: function(resp) {
                    appendOtaLog('③ ' + (resp.message || 'ファームウェア書き込み完了！ESPが再起動します。'), 'success');
                    appendOtaLog('✅ ESP OTA更新が完了しました！', 'success');
                    showToast('ESPファームウェアOTA更新が完了しました！');
                    document.getElementById('espOtaFileInput').value = '';
                },
                onError: function(err) {
                    appendOtaLog('❌ OTAエラー: ' + err, 'error');
                    document.getElementById('espOtaFileInput').value = '';
                }
            });
        }

        function appendOtaLog(msg, type) {
            var consoleEl = document.getElementById('espOtaConsole');
            if (!consoleEl) return;
            var now = new Date();
            var timeStr = String(now.getHours()).padStart(2, '0') + ':' +
                          String(now.getMinutes()).padStart(2, '0') + ':' +
                          String(now.getSeconds()).padStart(2, '0');
            var color = '#cbd5e1';
            if (type === 'error') color = '#ef4444';
            else if (type === 'success') color = '#10b981';
            else if (type === 'warn') color = '#f59e0b';
            else if (type === 'info') color = '#38bdf8';

            var line = document.createElement('div');
            line.style.color = color;
            line.style.marginTop = '2px';
            line.innerHTML = '<span style="color:#64748b;">[' + timeStr + ']</span> ' + escapeHtml(msg);
            consoleEl.appendChild(line);
            consoleEl.scrollTop = consoleEl.scrollHeight;
        }

        function clearOtaLog() {
            var consoleEl = document.getElementById('espOtaConsole');
            if (consoleEl) consoleEl.innerHTML = '';
        }

        function syncEspOtaHostFromPrefs() {
            if (latestState && latestState.esp && latestState.esp.host) {
                document.getElementById('txtEspOtaHost').value = latestState.esp.host;
                if (latestState.esp.port) {
                    document.getElementById('txtEspOtaPort').value = latestState.esp.port;
                }
                showToast('設定中のESPホストを反映しました: ' + latestState.esp.host);
                appendOtaLog('設定同期: ' + latestState.esp.host + ':' + (latestState.esp.port || 80), 'info');
            } else {
                showToast('ESP設定が未設定です。手動でIPを入力してください');
            }
        }

        function copyOtaSampleSnippet() {
            var snippet = document.getElementById('otaSampleSnippet');
            if (!snippet) return;
            navigator.clipboard.writeText(snippet.innerText).then(function() {
                showToast('OTAサンプルコードをコピーしました');
            });
        }

        function setupUpdatesDropzones() {
            setupDropzone('apkDropzone', uploadApk);
            setupDropzone('espOtaDropzone', uploadEspOta);
        }

        // Initialize dropzones once DOM is ready
        setTimeout(setupUpdatesDropzones, 100);
        """
    }
}

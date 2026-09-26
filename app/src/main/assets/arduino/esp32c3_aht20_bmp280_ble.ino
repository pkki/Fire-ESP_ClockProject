/* ==============================================================================
 * ESP32-C3 (Xiao / SuperMini / NodeMCU / Wemos 等)
 * AHT20 + BMP280 温湿度・気圧計 ＆ 赤外線スマートリモコン (学習・送受信)
 * ＆ PCF8574P I2C 8ch 物理ボタン (アドレス 0x20)
 * ＆ PCF8574P マルチNTC 10Kサーミスタ温度計 (アドレス 0x21 + GPIO 1 ADC)
 *    ※ 全チャンネルにNTC接続時の漏れ電流対策として、各chに1N4001を
 *      直列追加した構成に対応 (順方向電圧降下Vfを差し引いて抵抗値を逆算)
 * ＆ MQ-2 火災・煙検知 (GPIO 0 ADC)
 * ＆ Bluetooth Low Energy (BLE OTA) ファームウェア遠隔無線アップデート機能
 * 
 * ==============================================================================
 * 【配線図 (実績のある確実に動作するピン構成)】
 * 
 * 1. [I2C バス (共通)]
 *    ESP32-C3 GPIO 8 (SDA) -> AHT20 / BMP280 / PCF8574P (0x20) / PCF8574P (0x21) SDA
 *    ESP32-C3 GPIO 9 (SCL) -> AHT20 / BMP280 / PCF8574P (0x20) / PCF8574P (0x21) SCL
 *    VCC -> 3.3V, GND -> GND
 * 
 * 2. [1個目の PCF8574P (アドレス 0x20: 物理ボタン用)]
 *    Pin 1,2,3 (A0, A1, A2) -> すべて GND
 *    P0: [大ボタン] アラーム停止 / スヌーズ / 鳴動全停止
 *    P1: [ボタン1] ナイトモード切替
 *    P2: [ボタン2] 時計文字盤デザイン切替
 *    P3: [ボタン3] 時報・現在時刻読み上げ
 *    P4: [ボタン4] デスクタイマー開始/停止
 *    P5: [ボタン5] 赤外線 照明ON/OFF
 *    P6: [ボタン6] 画面明るさ切り替え
 *    P7: [ボタン7] 目覚ましアラーム有効/無効
 * 
 * 3. [2個目の PCF8574P (アドレス 0x21: NTCマルチプレクサ用)]
 *    Pin 1 (A0) -> 3.3V, Pin 2,3 (A1, A2) -> GND
 *    [3.3V] と [ESP32 GPIO 1] の間に 10kΩ固定抵抗 (1本だけ)
 *    各サーミスタ片足 -> ESP32 GPIO 1、もう片足 -> 1N4001アノード
 *    1N4001カソード -> PCF8574P (0x21) の P0〜P7
 *    （全ch常時接続時の漏れ電流を逆流防止ダイオードでブロックする構成）
 * 
 * 4. [MQ-2 ガス・煙・火災検知]
 *    VCC -> 5V (VIN), GND -> GND, A0 -> ESP32-C3 GPIO 0 (ADC1_CH0)
 * 
 * 5. [赤外線送受信]
 *    受信モジュール OUT -> ESP32-C3 GPIO 2
 *    送信 2SC1815 Base (1kΩ抵抗経由) -> ESP32-C3 GPIO 3
 * ============================================================================== */

#include <Wire.h>
#include <Adafruit_AHTX0.h>
#include <Adafruit_BMP280.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include <Update.h>
#include <math.h>

#include <IRrecv.h>
#include <IRsend.h>
#include <IRutils.h>

// -------------------------------------------------------------
// Wi-Fi ＆ WebServer OTA 設定 (Wi-Fi経由のファームウェア更新)
// -------------------------------------------------------------
// Wi-Fi接続用のSSIDとパスワード (未設定時はWi-FiをスキップしBLE単独で高速動作)
// ※ BLEコマンド {"cmd":"set_wifi","ssid":"...","pass":"..."} でも保存・切替可能
#define DEFAULT_WIFI_SSID ""
#define DEFAULT_WIFI_PASS ""

WebServer server(80);
Preferences espPrefs;
bool wifiEnabled = false;
bool wifiConnected = false;
String currentWifiSsid = DEFAULT_WIFI_SSID;
String currentWifiPass = DEFAULT_WIFI_PASS;

// -------------------------------------------------------------
// ピン設定 (実績のある元のピン配置そのまま)
// -------------------------------------------------------------
#define I2C_SDA_PIN    8   // I2C SDA (実績ピン GPIO 8)
#define I2C_SCL_PIN    9   // I2C SCL (実績ピン GPIO 9)
#define IR_SEND_PIN    3   // 赤外線送信LED (2SC1815のBaseへ)
#define IR_RECV_PIN    2   // 赤外線受信モジュール OUTピン
#define MQ2_ANALOG_PIN 0   // MQ-2 ガス・煙検知 (GPIO 0)
#define NTC_ANALOG_PIN 1   // NTC サーミスタ共通ADC (GPIO 1)

// PCF8574 アドレス
#define PCF8574_BTN_ADDR 0x20 // 1個目: ボタン用 (A0=0, A1=0, A2=0)
#define PCF8574_NTC_ADDR 0x21 // 2個目: NTC用 (A0=1, A1=0, A2=0)

// NTC サーミスタ定数 (10kΩ, B=3950)
#define NTC_NOMINAL_R     10000.0f
#define NTC_NOMINAL_T     25.0f
#define NTC_B_COEFFICIENT 3950.0f
#define NTC_PULLUP_R      10000.0f

// --- 1N4001 逆流防止ダイオード補正 -----------------------------
#define NTC_DIODE_VF      0.92f
#define ADC_VREF          3.30f
// ----------------------------------------------------------------

// MQ-2 火災検知デフォルト閾値
#define DEFAULT_MQ2_FIRE_THRESHOLD 1200

// BLE デバイス名
#define DEVICE_NAME "ESP32C3-Sensor"

// Nordic UART Service (NUS) UUID
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E" // Android -> ESP32
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // ESP32 -> Android

// 赤外線バッファサイズ
const uint16_t kCaptureBufferSize = 1024;
const uint8_t  kTimeout = 50; // ms

// オブジェクト
Adafruit_AHTX0 aht;
Adafruit_BMP280 bmp;
IRrecv irrecv(IR_RECV_PIN, kCaptureBufferSize, kTimeout, true);
IRsend irsend(IR_SEND_PIN);
decode_results results;

bool ahtDetected = false;
bool bmpDetected = false;
uint8_t bmpAddress = 0x76;

// 1個目 PCF8574P 物理ボタン設定 (0x20)
bool pcfDetected = false;
uint8_t pcfAddress = 0x20;
uint8_t lastPcfState = 0xFF;
unsigned long lastBtnDebounceTime[8] = {0, 0, 0, 0, 0, 0, 0, 0};
const unsigned long BTN_DEBOUNCE_MS = 30;

const char* const BTN_NAMES[8] = {
    "ALARM_STOP",          // P0: アラーム停止・スヌーズ・鳴動停止 (大ボタン)
    "NIGHT_MODE",          // P1: 夜間モード切替
    "NEXT_FACE",           // P2: 時計文字盤切り替え
    "TIME_CHIME",          // P3: 時報・現在時刻読み上げ
    "TIMER_START_STOP",    // P4: デスクタイマー開始/停止
    "LIGHT_TOGGLE",        // P5: 赤外線 照明ON/OFF送信
    "BRIGHTNESS_STEP",     // P6: 画面明るさ切り替え
    "ALARM_ENABLE_TOGGLE"  // P7: 目覚ましアラーム有効/無効
};

// 2個目 PCF8574P NTCサーミスタ用 (0x21)
bool pcfNtcDetected = false;
float ntcTemperatures[8] = {-999.0f, -999.0f, -999.0f, -999.0f, -999.0f, -999.0f, -999.0f, -999.0f};

// MQ-2 火災検知管理
int mq2FireThreshold = DEFAULT_MQ2_FIRE_THRESHOLD;
bool isFireAlertActive = false;
unsigned long lastFireAlertSendTime = 0;
const unsigned long FIRE_ALERT_REPEAT_INTERVAL_MS = 3000;

BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic = NULL;
BLECharacteristic *pRxCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// 受信コマンドキュー用バッファ
String incomingBleBuffer = "";
bool isLearningMode = false;
unsigned long learningStartTime = 0;
const unsigned long LEARNING_TIMEOUT_MS = 25000;

// センサー定期送信制御 (handleCommand より前で定義)
unsigned long lastSensorSendTime = 0;
unsigned long sensorSendIntervalMs = 1000; // 高速1秒更新デフォルト
bool forceSensorSendNow = false;

// -------------------------------------------------------------
// BLE OTA (Over-The-Air) ファームウェア更新管理
// -------------------------------------------------------------
bool isOtaInProgress = false;
size_t otaTotalSize = 0;
size_t otaWrittenBytes = 0;
unsigned long otaStartTime = 0;
unsigned long lastOtaProgressSendTime = 0;

void finalizeOta();
void sendBleJson(const String& json);
void handleCommand(const String& jsonStr);

// -------------------------------------------------------------
// BLE 受信コールバック
// -------------------------------------------------------------
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("[BLE] Tablet Connected!");
    }

    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("[BLE] Tablet Disconnected.");
        if (isOtaInProgress) {
            Serial.println("[BLE OTA] Disconnected during OTA! Aborting...");
            Update.abort();
            isOtaInProgress = false;
        }
    }
};

class MyRxCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        uint8_t* pData = pCharacteristic->getData();
        size_t len = pCharacteristic->getLength();
        if (len == 0 || pData == NULL) return;

        // OTAモード実行中のバイナリ転送
        if (isOtaInProgress) {
            // 文字列コマンド（ota_end, ota_abort等）かチェック
            if (len >= 8 && strncmp((char*)pData, "{\"cmd\":\"", 8) == 0) {
                String cmdStr = String((char*)pData, len);
                cmdStr.trim();
                handleCommand(cmdStr);
                return;
            }

            // 生バイナリチャンクを直接ESP32フラッシュへ書き込み
            size_t written = Update.write(pData, len);
            if (written > 0) {
                otaWrittenBytes += written;
                unsigned long now = millis();
                if (now - lastOtaProgressSendTime >= 250 || otaWrittenBytes >= otaTotalSize) {
                    lastOtaProgressSendTime = now;
                    int percent = (otaTotalSize > 0) ? (int)((float)otaWrittenBytes / (float)otaTotalSize * 100.0f) : 0;
                    char pBuf[128];
                    snprintf(pBuf, sizeof(pBuf), "{\"type\":\"ota_progress\",\"written\":%u,\"total\":%u,\"percent\":%d}",
                             (unsigned int)otaWrittenBytes, (unsigned int)otaTotalSize, percent);
                    sendBleJson(String(pBuf));
                }

                if (otaWrittenBytes >= otaTotalSize) {
                    finalizeOta();
                }
            } else {
                Serial.printf("[BLE OTA] Write error at offset %u\n", (unsigned int)otaWrittenBytes);
                Update.abort();
                isOtaInProgress = false;
                sendBleJson("{\"type\":\"ota_status\",\"status\":\"error\",\"msg\":\"フラッシュ書き込みエラーが発生しました\"}");
            }
            return;
        }

        // 通常のテキスト/JSONコマンド処理
        String rxValue = String((char*)pData, len);
        incomingBleBuffer += rxValue;

        int newlineIndex = incomingBleBuffer.indexOf('\n');
        while (newlineIndex != -1) {
            String cmdLine = incomingBleBuffer.substring(0, newlineIndex);
            cmdLine.trim();
            incomingBleBuffer = incomingBleBuffer.substring(newlineIndex + 1);

            if (cmdLine.length() > 0) {
                handleCommand(cmdLine);
            }
            newlineIndex = incomingBleBuffer.indexOf('\n');
        }
    }
};

// -------------------------------------------------------------
// 超低遅延・即時ボタン通知 (物理ボタン専用)
// -------------------------------------------------------------
void sendButtonEventFast(uint8_t btnId, const char* btnName) {
    char payload[96];
    snprintf(payload, sizeof(payload), "{\"type\":\"btn\",\"id\":%u,\"name\":\"%s\"}\n", btnId, btnName);
    
    Serial.print("[BTN EVENT] ");
    Serial.print(payload);

    if (deviceConnected && pTxCharacteristic != NULL) {
        pTxCharacteristic->setValue((uint8_t*)payload, strlen(payload));
        pTxCharacteristic->notify();
    }
}

// -------------------------------------------------------------
// BLE通知送信ヘルパー
// -------------------------------------------------------------
void sendBleJson(const String& json) {
    String payload = json + "\n";
    if (deviceConnected && pTxCharacteristic != NULL) {
        const uint8_t* data = (const uint8_t*)payload.c_str();
        size_t totalLen = payload.length();
        size_t offset = 0;
        if (totalLen <= 180) {
            pTxCharacteristic->setValue((uint8_t*)data, totalLen);
            pTxCharacteristic->notify();
            return;
        }

        const size_t CHUNK_SIZE = 240;
        while (offset < totalLen) {
            size_t chunk = (totalLen - offset > CHUNK_SIZE) ? CHUNK_SIZE : (totalLen - offset);
            pTxCharacteristic->setValue((uint8_t*)(data + offset), chunk);
            pTxCharacteristic->notify();
            offset += chunk;
            if (offset < totalLen) {
                delay(25);
            }
        }
        delay(10);
    }
}

// -------------------------------------------------------------
// OTA 完了・再起動ヘルパー
// -------------------------------------------------------------
void finalizeOta() {
    if (!isOtaInProgress) return;
    isOtaInProgress = false;
    Serial.printf("[BLE OTA] Finalizing OTA. Total written: %u / %u bytes\n", (unsigned int)otaWrittenBytes, (unsigned int)otaTotalSize);

    if (Update.end(true)) {
        Serial.println("[BLE OTA] Update successful! Rebooting ESP32...");
        sendBleJson("{\"type\":\"ota_status\",\"status\":\"success\",\"msg\":\"ファームウェア書き込み完了！ESP32を再起動します...\"}");
        delay(1000);
        ESP.restart();
    } else {
        Serial.printf("[BLE OTA] Update end failed! Error: %s\n", Update.errorString());
        char errBuf[128];
        snprintf(errBuf, sizeof(errBuf), "{\"type\":\"ota_status\",\"status\":\"error\",\"msg\":\"書き込み検証失敗: %s\"}", Update.errorString());
        sendBleJson(String(errBuf));
    }
}

// -------------------------------------------------------------
// NTC 10K サーミスタ安全読み取り (PCF8574P 0x21 検出時のみ)
// 【1N4001直列ダイオード補正版】
// -------------------------------------------------------------
float readNtcChannel(uint8_t channel, int &outRawAdc) {
    outRawAdc = 4095;
    if (!pcfNtcDetected || channel >= 8) return -999.0f;

    // チャンネル選択: 対象ピンのみLOW、他ピンはHIGH (疑似双方向シンク)
    uint8_t pattern = ~(1 << channel);
    Wire.beginTransmission(PCF8574_NTC_ADDR);
    Wire.write(pattern);
    if (Wire.endTransmission() != 0) {
        return -999.0f;
    }
    delayMicroseconds(800);

    int adcSum = 0;
    for (int i = 0; i < 4; i++) {
        adcSum += analogRead(NTC_ANALOG_PIN);
        delayMicroseconds(20);
    }
    int rawAdc = adcSum / 4;
    outRawAdc = rawAdc;

    Wire.beginTransmission(PCF8574_NTC_ADDR);
    Wire.write(0xFF);
    Wire.endTransmission();

    const int VF_ADC_FLOOR = (int)(NTC_DIODE_VF / ADC_VREF * 4095.0f);
    if (rawAdc >= 4030 || rawAdc <= (VF_ADC_FLOOR + 20)) return -999.0f;

    float vNode = ADC_VREF * (float)rawAdc / 4095.0f;
    float current = (ADC_VREF - vNode) / NTC_PULLUP_R;
    if (current <= 0.0f) return -999.0f;

    float vNtc = vNode - NTC_DIODE_VF;
    if (vNtc <= 0.0f) return -999.0f;

    float resistance = vNtc / current;
    float steinhart = resistance / NTC_NOMINAL_R;
    steinhart = log(steinhart);
    steinhart /= NTC_B_COEFFICIENT;
    steinhart += 1.0f / (NTC_NOMINAL_T + 273.15f);
    steinhart = 1.0f / steinhart;
    float tempC = steinhart - 273.15f;

    if (tempC < -30.0f || tempC > 125.0f) return -999.0f;
    return tempC;
}

// -------------------------------------------------------------
// 赤外線送信ルーチン
// -------------------------------------------------------------
void sendIrSignal(const String& protocol, const String& hexCode, uint16_t bits, const String& rawStr) {
    Serial.printf("[IR SEND] Protocol: %s, Hex: %s, Bits: %d\n", protocol.c_str(), hexCode.c_str(), bits);
    
    irrecv.disableIRIn();
    delay(10);

    bool sent = false;

    if (rawStr.length() > 0) {
        std::vector<uint16_t> rawList;
        int start = 0;
        int comma = rawStr.indexOf(',');
        while (comma != -1) {
            rawList.push_back(rawStr.substring(start, comma).toInt());
            start = comma + 1;
            comma = rawStr.indexOf(',', start);
        }
        if (start < rawStr.length()) {
            rawList.push_back(rawStr.substring(start).toInt());
        }

        if (!rawList.empty()) {
            Serial.printf("[IR SEND] Sending RAW array size: %d\n", rawList.size());
            irsend.sendRaw(rawList.data(), rawList.size(), 38);
            sent = true;
        }
    }

    if (!sent && hexCode.length() > 0) {
        uint64_t data = strtoull(hexCode.c_str(), NULL, 16);
        if (protocol.equalsIgnoreCase("NEC")) {
            irsend.sendNEC(data, bits == 0 ? 32 : bits);
            sent = true;
        } else if (protocol.equalsIgnoreCase("SONY")) {
            irsend.sendSony(data, bits == 0 ? 12 : bits);
            sent = true;
        } else if (protocol.equalsIgnoreCase("RC5")) {
            irsend.sendRC5(data, bits == 0 ? 12 : bits);
            sent = true;
        } else if (protocol.equalsIgnoreCase("RC6")) {
            irsend.sendRC6(data, bits == 0 ? 20 : bits);
            sent = true;
        } else if (protocol.equalsIgnoreCase("PANASONIC")) {
            irsend.sendPanasonic64(data, bits == 0 ? 48 : bits);
            sent = true;
        } else {
            irsend.sendNEC(data, bits == 0 ? 32 : bits);
            sent = true;
        }
    }

    delay(20);
    irrecv.enableIRIn();

    String resp = "{\"type\":\"ir_sent\",\"status\":\"" + String(sent ? "ok" : "failed") + "\"}";
    sendBleJson(resp);
}

// -------------------------------------------------------------
// コマンド解析
// -------------------------------------------------------------
void handleCommand(const String& jsonStr) {
    DynamicJsonDocument doc(2048);
    DeserializationError error = deserializeJson(doc, jsonStr);
    if (error) return;

    const char* cmd = doc["cmd"] | "";

    // 1. BLE OTA コマンド
    if (strcmp(cmd, "ota_start") == 0) {
        size_t size = doc["size"] | 0;
        if (size == 0) {
            sendBleJson("{\"type\":\"ota_status\",\"status\":\"error\",\"msg\":\"無効なファームウェアサイズです\"}");
            return;
        }

        Serial.printf("[BLE OTA] Starting OTA update. Size: %u bytes\n", (unsigned int)size);
        if (!Update.begin(size, U_FLASH)) {
            Update.printError(Serial);
            char errBuf[128];
            snprintf(errBuf, sizeof(errBuf), "{\"type\":\"ota_status\",\"status\":\"error\",\"msg\":\"Update.begin失敗: %s\"}", Update.errorString());
            sendBleJson(String(errBuf));
            isOtaInProgress = false;
            return;
        }

        otaTotalSize = size;
        otaWrittenBytes = 0;
        isOtaInProgress = true;
        otaStartTime = millis();
        lastOtaProgressSendTime = millis();
        sendBleJson("{\"type\":\"ota_status\",\"status\":\"ready\",\"size\":" + String(otaTotalSize) + ",\"msg\":\"OTA書き込み待機中...\"}");
    }
    else if (strcmp(cmd, "ota_end") == 0) {
        if (isOtaInProgress) {
            finalizeOta();
        }
    }
    else if (strcmp(cmd, "ota_abort") == 0) {
        if (isOtaInProgress) {
            Update.abort();
            isOtaInProgress = false;
            sendBleJson("{\"type\":\"ota_status\",\"status\":\"aborted\",\"msg\":\"OTA更新を中断しました\"}");
        }
    }
    // 2. 赤外線学習・送受信コマンド
    else if (strcmp(cmd, "ir_learn_start") == 0 || strcmp(cmd, "start_learn") == 0) {
        isLearningMode = true;
        learningStartTime = millis();
        irrecv.resume();
        sendBleJson("{\"type\":\"ir_status\",\"learning\":true,\"message\":\"リモコンのボタンを押してください...\"}");
    }
    else if (strcmp(cmd, "ir_learn_stop") == 0 || strcmp(cmd, "stop_learn") == 0) {
        isLearningMode = false;
        sendBleJson("{\"type\":\"ir_status\",\"learning\":false,\"message\":\"学習を待機状態に戻しました\"}");
    }
    else if (strcmp(cmd, "ir_send") == 0 || strcmp(cmd, "send_ir") == 0) {
        const char* protocol = doc["protocol"] | "NEC";
        const char* hex = doc["hex"] | "";
        uint16_t bits = doc["bits"] | 32;
        const char* raw = doc["raw"] | "";
        sendIrSignal(protocol, hex, bits, raw);
    }
    // 3. 火災閾値・更新周期
    else if (strcmp(cmd, "set_fire_threshold") == 0) {
        mq2FireThreshold = doc["threshold"] | DEFAULT_MQ2_FIRE_THRESHOLD;
        Serial.printf("[FIRE] Sensitivity threshold updated to: %d\n", mq2FireThreshold);
        sendBleJson("{\"type\":\"fire_status\",\"threshold\":" + String(mq2FireThreshold) + "}");
    }
    else if (strcmp(cmd, "set_sensor_interval") == 0 || strcmp(cmd, "set_interval") == 0) {
        int ms = doc["interval_ms"] | (doc["interval_sec"].as<int>() * 1000);
        if (ms >= 200 && ms <= 60000) {
            sensorSendIntervalMs = ms;
            Serial.printf("[SENSOR] Update interval changed to: %lu ms\n", sensorSendIntervalMs);
            sendBleJson("{\"type\":\"sensor_interval\",\"interval_ms\":" + String(sensorSendIntervalMs) + "}");
        }
    }
    else if (strcmp(cmd, "read_sensor") == 0 || strcmp(cmd, "refresh_now") == 0) {
        forceSensorSendNow = true;
    }
    else if (strcmp(cmd, "restart") == 0 || strcmp(cmd, "reboot") == 0) {
        sendBleJson("{\"type\":\"status\",\"msg\":\"ESP32を再起動します...\"}");
        delay(500);
        ESP.restart();
    }
    // 4. Wi-Fi 設定コマンド (SSID・パスワード設定＆接続)
    else if (strcmp(cmd, "set_wifi") == 0) {
        const char* ssid = doc["ssid"] | "";
        const char* pass = doc["pass"] | "";
        if (strlen(ssid) > 0) {
            espPrefs.begin("esp_sensor", false);
            espPrefs.putString("wifi_ssid", ssid);
            espPrefs.putString("wifi_pass", pass);
            currentWifiSsid = String(ssid);
            currentWifiPass = String(pass);
            wifiEnabled = true;
            Serial.printf("[Wi-Fi] Saved credentials. Connecting to: %s\n", ssid);
            WiFi.disconnect();
            WiFi.mode(WIFI_STA);
            WiFi.begin(ssid, pass);
            sendBleJson("{\"type\":\"wifi_status\",\"status\":\"connecting\",\"ssid\":\"" + String(ssid) + "\"}");
        } else {
            espPrefs.begin("esp_sensor", false);
            espPrefs.remove("wifi_ssid");
            espPrefs.remove("wifi_pass");
            WiFi.disconnect();
            wifiConnected = false;
            wifiEnabled = false;
            sendBleJson("{\"type\":\"wifi_status\",\"status\":\"disconnected\"}");
        }
    }
}

// -------------------------------------------------------------
// Wi-Fi 接続 ＆ WebServer OTA セットアップ
// -------------------------------------------------------------
void setupWifiAndOta() {
    espPrefs.begin("esp_sensor", false);
    String savedSsid = espPrefs.getString("wifi_ssid", DEFAULT_WIFI_SSID);
    String savedPass = espPrefs.getString("wifi_pass", DEFAULT_WIFI_PASS);

    if (savedSsid.length() > 0) {
        currentWifiSsid = savedSsid;
        currentWifiPass = savedPass;
        wifiEnabled = true;
        Serial.printf("[Wi-Fi] Connecting to: %s ...\n", currentWifiSsid.c_str());
        WiFi.mode(WIFI_STA);
        WiFi.begin(currentWifiSsid.c_str(), currentWifiPass.c_str());

        // 最大5秒待機 (BLE等の起動を妨げない)
        int attempts = 0;
        while (WiFi.status() != WL_CONNECTED && attempts < 20) {
            delay(250);
            Serial.print(".");
            attempts++;
        }
        if (WiFi.status() == WL_CONNECTED) {
            wifiConnected = true;
            Serial.printf("\n[Wi-Fi] Connected! IP Address: %s\n", WiFi.localIP().toString().c_str());
        } else {
            Serial.println("\n[Wi-Fi] Connection timeout. BLE mode active.");
        }
    } else {
        Serial.println("[Wi-Fi] No SSID configured. Running in BLE mode.");
    }

    // WebServer OTA エンドポイント '/update' (POST)
    server.on("/update", HTTP_POST, []() {
        server.sendHeader("Connection", "close");
        server.send(200, "text/plain", (Update.hasError()) ? "FAIL" : "OK");
        delay(1000);
        ESP.restart();
    }, []() {
        HTTPUpload& upload = server.upload();
        if (upload.status == UPLOAD_FILE_START) {
            Serial.printf("[Wi-Fi OTA] Update Start: %s\n", upload.filename.c_str());
            if (!Update.begin(UPDATE_SIZE_UNKNOWN, U_FLASH)) {
                Update.printError(Serial);
            }
        } else if (upload.status == UPLOAD_FILE_WRITE) {
            if (Update.write(upload.buf, upload.currentSize) != upload.currentSize) {
                Update.printError(Serial);
            }
        } else if (upload.status == UPLOAD_FILE_END) {
            if (Update.end(true)) {
                Serial.printf("[Wi-Fi OTA] Update Success: %u bytes. Rebooting ESP32...\n", (unsigned int)upload.totalSize);
            } else {
                Update.printError(Serial);
            }
        }
    });

    server.on("/", HTTP_GET, []() {
        server.send(200, "text/html", "<h3>ESP32-C3 Sensor & OTA Server</h3><p>Status: Ready</p><p>OTA endpoint: /update (POST)</p>");
    });

    if (wifiConnected) {
        server.begin();
        Serial.println("[HTTP] WebServer & Wi-Fi OTA ready on port 80");
    }
}

// -------------------------------------------------------------
// I2C スキャン & PCF8574 自動検出
// -------------------------------------------------------------
void scanI2C() {
  Serial.println("[I2C] ==== Scanning I2C bus... ====");
  byte count = 0;
  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    byte error = Wire.endTransmission();
    if (error == 0) {
      Serial.printf("[I2C] Device found at 0x%02X", addr);
      if (addr == PCF8574_BTN_ADDR) {
        pcfAddress = addr;
        pcfDetected = true;
        Serial.printf(" -> [PCF8574 Buttons Detected]");
      } else if (addr == PCF8574_NTC_ADDR) {
        pcfNtcDetected = true;
        Serial.printf(" -> [PCF8574 NTC Multiplexer Detected]");
      }
      Serial.println();
      count++;
    }
  }
  Serial.printf("[I2C] Scan complete. %d device(s) found.\n", count);
  Serial.println("[I2C] ================================");
}

// -------------------------------------------------------------
// PCF8574P 超低遅延ボタンポーリング
// -------------------------------------------------------------
inline void pollPcfButtons() {
  if (!pcfDetected) return;

  Wire.requestFrom((int)pcfAddress, 1);
  if (Wire.available()) {
    uint8_t currentState = Wire.read();
    if (currentState != lastPcfState) {
      unsigned long now = millis();
      for (uint8_t i = 0; i < 8; i++) {
        bool wasPressed = (lastPcfState & (1 << i)) == 0;
        bool isPressed = (currentState & (1 << i)) == 0;
        if (isPressed && !wasPressed) {
          if (now - lastBtnDebounceTime[i] > BTN_DEBOUNCE_MS) {
            lastBtnDebounceTime[i] = now;
            sendButtonEventFast(i, BTN_NAMES[i]);
          }
        }
      }
      lastPcfState = currentState;
    }
  }
}

// -------------------------------------------------------------
// 初期セットアップ
// -------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n\n==================================================");
  Serial.println("ESP32-C3 AHT20 + BMP280 + IR + PCF8574 + BLE OTA");
  Serial.println("==================================================");

  // 1. I2C初期化
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  Wire.setClock(400000);
  delay(200);
  scanI2C();

  // PCF8574P (0x20: ボタン用)
  if (pcfDetected) {
    Wire.beginTransmission(pcfAddress);
    Wire.write(0xFF);
    byte err = Wire.endTransmission();
    if (err == 0) {
      Serial.printf("[OK] PCF8574P physical buttons ready at 0x%02X\n", pcfAddress);
    } else {
      pcfDetected = false;
    }
  }

  // PCF8574P (0x21: NTC用)
  if (pcfNtcDetected) {
    Wire.beginTransmission(PCF8574_NTC_ADDR);
    Wire.write(0xFF);
    Wire.endTransmission();
    Serial.println("[OK] PCF8574P NTC Multiplexer ready at 0x21");
  }

  // AHT20
  if (aht.begin(&Wire, 0, 0x38)) {
    Serial.println("[OK] AHT20 ready (0x38)");
    ahtDetected = true;
  } else {
    Serial.println("[NG] AHT20 not found");
  }

  // BMP280
  if (bmp.begin(0x76)) {
    Serial.println("[OK] BMP280 ready (0x76)");
    bmpDetected = true;
    bmpAddress = 0x76;
  } else if (bmp.begin(0x77)) {
    Serial.println("[OK] BMP280 ready (0x77)");
    bmpDetected = true;
    bmpAddress = 0x77;
  } else {
    Serial.println("[NG] BMP280 not found");
  }

  if (bmpDetected) {
    bmp.setSampling(Adafruit_BMP280::MODE_NORMAL,
                    Adafruit_BMP280::SAMPLING_X2,
                    Adafruit_BMP280::SAMPLING_X16,
                    Adafruit_BMP280::FILTER_X16,
                    Adafruit_BMP280::STANDBY_MS_500);
  }

  // 2. ADCピン初期化
  pinMode(MQ2_ANALOG_PIN, INPUT);
  pinMode(NTC_ANALOG_PIN, INPUT);
  analogReadResolution(12);
  analogSetAttenuation(ADC_11db);
  analogSetPinAttenuation(MQ2_ANALOG_PIN, ADC_11db);
  analogSetPinAttenuation(NTC_ANALOG_PIN, ADC_11db);

  // 3. 赤外線初期化
  irsend.begin();
  irrecv.enableIRIn();
  Serial.printf("[IR] Receiver on GPIO %d, Transmitter on GPIO %d\n", IR_RECV_PIN, IR_SEND_PIN);

  // 4. BLE 初期化 (NUS)
  BLEDevice::init(DEVICE_NAME);
  BLEDevice::setMTU(517);
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  pRxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_RX,
                        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
                      );
  pRxCharacteristic->setCallbacks(new MyRxCallbacks());

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising ready! Connect from Tablet App.");

  // 5. Wi-Fi & WebServer OTA 初期化 (保存済みSSIDがある場合接続)
  setupWifiAndOta();
}

// -------------------------------------------------------------
// メインループ
// -------------------------------------------------------------
void loop() {
  // OTA更新中はセンサーサンプリングを休止し、フラッシュ書き込みに全リソースを集中
  if (isOtaInProgress) {
    delay(5);
    return;
  }

  // 1. PCF8574P 物理ボタンの超低遅延検知 (最優先)
  pollPcfButtons();

  // 2. Wi-Fi WebServer 処理 (OTA更新リクエスト待機)
  if (wifiConnected) {
    server.handleClient();
  }

  // BLE 再アドバタイズ
  if (!deviceConnected && oldDeviceConnected) {
      delay(500);
      pServer->startAdvertising();
      Serial.println("[BLE] Restarted advertising...");
      oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
      oldDeviceConnected = deviceConnected;
  }

  // 2. MQ-2 火災・煙検知
  unsigned long now = millis();
  int mq2Raw = analogRead(MQ2_ANALOG_PIN);
  bool smokeDetected = (mq2Raw >= mq2FireThreshold);

  if (smokeDetected) {
    if (!isFireAlertActive || (now - lastFireAlertSendTime >= FIRE_ALERT_REPEAT_INTERVAL_MS)) {
      isFireAlertActive = true;
      lastFireAlertSendTime = now;
      String fireJson = "{\"type\":\"fire_alert\",\"detected\":true,\"mq2\":" + String(mq2Raw) +
                        ",\"msg\":\"火事です！火災または煙を検知しました\"}";
      sendBleJson(fireJson);
    }
  } else {
    if (isFireAlertActive && mq2Raw < (mq2FireThreshold - 150)) {
      isFireAlertActive = false;
      sendBleJson("{\"type\":\"fire_alert\",\"detected\":false,\"mq2\":" + String(mq2Raw) + ",\"msg\":\"煙濃度が正常値に戻りました\"}");
    }
  }

  // 3. 赤外線受信処理
  if (irrecv.decode(&results)) {
      String protocol = typeToString(results.decode_type);
      String hexStr = uint64ToString(results.value, 16);
      uint16_t bits = results.bits;

      Serial.printf("[IR RECV] Protocol: %s, Hex: 0x%s, Bits: %d, RawLen: %d\n",
                     protocol.c_str(), hexStr.c_str(), bits, results.rawlen);

      String rawCsv = "";
      if (results.rawlen > 1) {
          rawCsv.reserve(results.rawlen * 6);
          for (uint16_t i = 1; i < results.rawlen; i++) {
              uint32_t val = results.rawbuf[i] * kRawTick;
              rawCsv += String(val);
              if (i < results.rawlen - 1) rawCsv += ",";
          }
      }

      String jsonOut = "{\"type\":\"ir_learned\",\"protocol\":\"" + protocol +
                       "\",\"hex\":\"0x" + hexStr +
                       "\",\"bits\":" + String(bits);
      if (rawCsv.length() > 0) {
          jsonOut += ",\"raw\":\"" + rawCsv + "\"";
      }
      jsonOut += "}";

      lastSensorSendTime = millis() + 2500;
      sendBleJson(jsonOut);

      if (isLearningMode) {
          delay(400);
          isLearningMode = false;
          sendBleJson("{\"type\":\"ir_status\",\"learning\":false,\"message\":\"リモコン信号を受信・保存しました！\"}");
      }

      irrecv.resume();
  }

  // USBシリアル入力処理
  if (Serial.available()) {
      String serialLine = Serial.readStringUntil('\n');
      serialLine.trim();
      if (serialLine.length() > 0) {
          handleCommand(serialLine);
      }
  }

  // 学習モードタイムアウト
  if (isLearningMode && (millis() - learningStartTime > LEARNING_TIMEOUT_MS)) {
      isLearningMode = false;
      sendBleJson("{\"type\":\"ir_status\",\"learning\":false,\"message\":\"学習待機タイムアウト\"}");
  }

  // 4. 温湿度・気圧・MQ-2・NTC 定期送信
  if (isLearningMode) {
    lastSensorSendTime = now;
  } else if (forceSensorSendNow || (now - lastSensorSendTime >= sensorSendIntervalMs)) {
    lastSensorSendTime = now;
    forceSensorSendNow = false;

    float tempAHT = -999.0f;
    float humAHT = -999.0f;
    float tempBMP = -999.0f;
    float pressBMP = -999.0f;

    if (ahtDetected) {
      sensors_event_t humidityEvent, tempEvent;
      if (aht.getEvent(&humidityEvent, &tempEvent)) {
        tempAHT = tempEvent.temperature;
        humAHT = humidityEvent.relative_humidity;
      }
    }

    if (bmpDetected) {
      tempBMP = bmp.readTemperature();
      pressBMP = bmp.readPressure() / 100.0F;
    }

    float representativeTemp = (tempAHT > -100.0f) ? tempAHT : tempBMP;

    // NTC サーミスタ 8チャンネル測定 (0x21が存在する場合のみ)
    String ntcJsonArr = "[";
    String ntcRawJsonArr = "[";
    int ntcRaws[8];
    if (pcfNtcDetected) {
      for (uint8_t ch = 0; ch < 8; ch++) {
        int rawVal = 4095;
        float ntcT = readNtcChannel(ch, rawVal);
        ntcTemperatures[ch] = ntcT;
        ntcRaws[ch] = rawVal;

        if (ntcT > -100.0f) {
          ntcJsonArr += String(ntcT, 1);
        } else {
          ntcJsonArr += "null";
        }
        ntcRawJsonArr += String(rawVal);

        if (ch < 7) {
          ntcJsonArr += ",";
          ntcRawJsonArr += ",";
        }
      }
    }
    ntcJsonArr += "]";
    ntcRawJsonArr += "]";

    char buffer[512];
    snprintf(buffer, sizeof(buffer),
      "{\"temperature\":%.1f,\"humidity\":%.1f,\"pressure\":%.1f,\"ir_ready\":true,\"pcf_ready\":%s,\"mq2\":%d,\"smoke\":%s,\"ntc_ready\":%s,\"wifi\":%s,\"wifi_ip\":\"%s\",\"ntc\":%s,\"ntc_raw\":%s}",
      (representativeTemp > -100.0f) ? representativeTemp : 0.0f,
      (humAHT > -100.0f) ? humAHT : 0.0f,
      (pressBMP > 0.0f) ? pressBMP : 0.0f,
      pcfDetected ? "true" : "false",
      mq2Raw,
      smokeDetected ? "true" : "false",
      pcfNtcDetected ? "true" : "false",
      wifiConnected ? "true" : "false",
      wifiConnected ? WiFi.localIP().toString().c_str() : "",
      ntcJsonArr.c_str(),
      ntcRawJsonArr.c_str()
    );

    sendBleJson(String(buffer));

    Serial.printf("[SENSOR] T:%.1fC, H:%.1f%%, P:%.1fhPa, MQ2:%d (Thresh:%d), NTC0:%.1fC(ADC:%d)\n",
      (representativeTemp > -100.0f) ? representativeTemp : 0.0f,
      (humAHT > -100.0f) ? humAHT : 0.0f,
      (pressBMP > 0.0f) ? pressBMP : 0.0f,
      mq2Raw,
      mq2FireThreshold,
      (pcfNtcDetected && ntcTemperatures[0] > -100.0f) ? ntcTemperatures[0] : 0.0f,
      pcfNtcDetected ? ntcRaws[0] : 0
    );
  }

  delay(2);
}

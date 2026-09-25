// ══════════════════════════════════════════════
// 緊急地震速報 (EEW) 受信・配信
//   データソース: P2P地震情報 JSON API v2 (https://www.p2pquake.net/)
//   ※ このAPIは無償の非公式サービスであり、内容・配信品質は無保証です
//     （気象庁の正式な緊急地震速報の代替としては利用できません。配信元の公式見解）。
//
//   サーバー側で常時 WebSocket (wss://api.p2pquake.net/v2/ws) に接続して受信し、
//   受信したイベントをそのままの形で /ws/eew に接続している全クライアントへ即座に
//   ブロードキャストする（ポーリングではなくpush方式。受信から配信までの遅延を
//   最小限にするため。実測で概ね数十〜100ms程度）。
//
//   code=556: 緊急地震速報（警報） → 震源・予測震度・P波/S波伝播アニメーション用の座標
//   code=551/552: 震度速報／震源・震度に関する情報（実測震度） → 都道府県ごとの観測震度
//
//   ※ 本モジュールは "ws" パッケージが必要です: npm install ws
//     （未導入の場合はEEW機能全体を黙って無効化し、他の機能には一切影響しません）
// ══════════════════════════════════════════════

import db from "../db.js";
import { sendPushToUser, sendPushToUsers } from "./push.js";
import sharp from "sharp";
import { renderEewNoticeSvg } from "./eewImage.js";

const EEW_UPSTREAM_WS_URL = "wss://api.p2pquake.net/v2/ws";

// テスト配信・訓練報も配信するか（既定OFF。本番運用で誤報扱いされるのを防ぐため）
const EEW_INCLUDE_TEST = String(process.env.EEW_INCLUDE_TEST || "").toLowerCase() === "true";
// この最大予測震度未満のEEWは左上カード・地図マーカー・実測震度パネルには表示しない
// （取消は震度に関わらず必ず表示する。全員一律のサーバー既定値で、ユーザーごとの変更はできない）。
// push通知・右下バナーの表示可否はこれとは独立に、ユーザーごとの下限震度
// (users.notify_on_eew_min_scale / users.notify_on_eew_banner_min_scale)で判定する
// （下のEEW_PUSH_MIN_SCALEはpush側の既定値）。
// 45=気象庁震度階級の「5弱」に対応するP2P地震情報 JSON API v2 のscale値
// （以前はデフォルト10=震度1だったため、軽微な地震まで大量に表示されてしまっていた）。
const EEW_MIN_SCALE = Number(process.env.EEW_MIN_SCALE ?? 45);
// push通知（Web Push）を送る最小の最大予測震度の既定値。ユーザーは設定画面で
// users.notify_on_eew_min_scale を自分の好みの値（震度1〜7）に変更でき、その場合はここではなく
// 本人が選んだ値が使われる。この環境変数は、まだ値を選んでいない/列が無い場合のフォールバックや
// マイグレーションのデフォルト値としてのみ使う。45=気象庁震度階級の「5弱」に対応するscale値。
// 表示閾値(EEW_MIN_SCALE)とは独立していて、5弱未満でもpushだけ受け取ることができる。
const EEW_PUSH_MIN_SCALE = Number(process.env.EEW_PUSH_MIN_SCALE ?? 45);
// 右下の「地震発生通知」バナーをユーザーごとに絞り込むための下限震度
// (users.notify_on_eew_banner_min_scale)は、routes/notifications.js側の設定APIで
// 読み書きし、判定自体はクライアント側（index.html）で行う。バナーはpushと違い
// デフォルトで全員に表示される機能なので、既定値はこれまでの挙動と揃えて45(5弱)にしてある
// （＝設定を変更しない限り従来と同じ見え方のまま）。
// EEW（予測）カードの表示継続時間（クライアント側の自動非表示までの時間）
const EEW_CARD_DURATION_MS = Number(process.env.EEW_CARD_DURATION_MS || 20000);
// 実測震度パネルの表示継続時間。EEWより長めに、しばらく地図上に残しておく
const INTENSITY_DURATION_MS = Number(process.env.EEW_INTENSITY_DURATION_MS || 10 * 60 * 1000);

// ── デバッグ用: 上流から受信した生のJSONをそのままDBに保存するか ──
// 「震度1が表示されてしまう」「第?報になる」等の不具合調査用に、フィルタリング前の
// 元データを追跡できるようにする（db.js の eew_raw_log テーブル）。既定ON。
// 保存自体が原因で本来の配信処理が止まらないよう、失敗しても握りつぶす（下のsaveRawEewLog内）。
const EEW_RAW_LOG_ENABLED = String(process.env.EEW_RAW_LOG_ENABLED ?? "true").toLowerCase() !== "false";
// 保存件数の上限（これを超えたら古い行から削除し、無限に肥大化しないようにする）
const EEW_RAW_LOG_MAX_ROWS = Number(process.env.EEW_RAW_LOG_MAX_ROWS || 2000);

// 震度コード → 表示文字列。P2P地震情報 JSON API v2 の scaleFrom/scaleTo（10=震度1 ... 70=震度7）を
// 人間向けの表記に変換する。
const EEW_SCALE_TEXT = {
  "-1": "不明", "0": "0", "10": "1", "20": "2", "30": "3", "40": "4",
  "45": "5弱", "50": "5強", "55": "6弱", "60": "6強", "70": "7", "99": "5強以上",
};
// 震度コード → 表示色（16進）。JMA震度階級の配色。
const EEW_SCALE_COLOR = {
  "10": "#8c8c8c", "20": "#4c8bff", "30": "#2ca02c", "40": "#f2cf00",
  "45": "#ff9a00", "50": "#ff6300", "55": "#ff2020", "60": "#c2001f", "70": "#a800a8", "99": "#ff6300",
};
function eewScaleText(v) {
  if (v === null || v === undefined) return "不明";
  const n = Math.round(Number(v));
  if (!Number.isFinite(n)) return "不明";
  return EEW_SCALE_TEXT[String(n)] ?? "不明";
}
// ユーザーが下限震度として選べる値（push通知・Discord Webhook共通のUI用）。
// 99（5強以上）は表示専用の特殊値でscaleTo等には出てこないため下限選択肢には含めない。
const EEW_SELECTABLE_SCALES = [10, 20, 30, 40, 45, 50, 55, 60, 70];
function isValidEewScale(v) {
  const n = Math.round(Number(v));
  return Number.isFinite(n) && EEW_SELECTABLE_SCALES.includes(n);
}
function eewScaleColor(v) {
  const n = Math.round(Number(v));
  return EEW_SCALE_COLOR[String(n)] || "#6b7280";
}

// ── 実測震度(551/552)の「第◯報」表記について ──
// P2P地震情報 JSON API v2 の仕様上、551/552のissueには serial（情報番号）という項目自体が
// 存在しない（issue.required=[time,type]、properties=source/time/type/correct のみ。
// eventId/serialを持つのは556(EEW)だけ）。つまり実測震度側で「第?報」になっていたのは
// バグではなく、そもそも無い値を表示しようとしていたことが原因。
// 代わりに issue.type（発表種類）と issue.correct（訂正内容。Noneなら訂正なし）から、
// 意味のある表示ラベルを組み立てる。
const INTENSITY_ISSUE_TYPE_TEXT = {
  ScalePrompt: "震度速報",
  Destination: "震源に関する情報",
  ScaleAndDestination: "震度・震源に関する情報",
  DetailScale: "各地の震度に関する情報",
  Foreign: "遠地地震に関する情報",
  Other: "その他の情報",
};
const INTENSITY_ISSUE_CORRECT_TEXT = {
  ScaleOnly: "震度の訂正",
  DestinationOnly: "震源の訂正",
  ScaleAndDestination: "震度・震源の訂正",
};
// issueType/issueCorrect/linkedEewEventId から表示用ラベルを組み立てる。
// ・訂正報（issue.correctがNone/Unknown以外）なら、それを優先して表示する。
// ・DetailScale（各地の震度に関する情報＝最も詳しい報）が、直前に出ていたEEW(556)と
//   同じ地震の続報だと判定できる場合は、実務上ほぼ「この地震についての最終報」に
//   相当するため、その旨を添える（P2P地震情報側にも気象庁側にも「最終報」という
//   明示フラグは無いため、あくまで経験則によるヒント表示であり確定情報ではない）。
function buildIntensityReportLabel(issueType, issueCorrect, linkedEewEventId) {
  if (issueCorrect && issueCorrect !== "None" && issueCorrect !== "Unknown") {
    return `訂正（${INTENSITY_ISSUE_CORRECT_TEXT[issueCorrect] || "内容の訂正"}）`;
  }
  const typeText = INTENSITY_ISSUE_TYPE_TEXT[issueType] || "地震情報";
  if (issueType === "DetailScale" && linkedEewEventId) {
    return `${typeText}（最終報の可能性）`;
  }
  return typeText;
}

// 都道府県 → 概略の緯度経度（都道府県庁所在地付近）。
// 実測震度（P2P地震情報 JSON API v2 の code=551/552。各観測点は市区町村単位の名前のみで
// 緯度経度を含まない）を地図上に描画するために使う簡易テーブル。観測点ごとの正確な位置
// ではなく、あくまで「都道府県内の最大震度をその都道府県庁付近に1点だけ表示する」という
// 粗い近似である点に注意（実際には都道府県内でも場所によって震度が異なる）。
const EEW_PREF_COORDS = {
  "北海道": [43.0642, 141.3469], "青森県": [40.8244, 140.7400], "岩手県": [39.7036, 141.1527],
  "宮城県": [38.2688, 140.8721], "秋田県": [39.7186, 140.1024], "山形県": [38.2404, 140.3633],
  "福島県": [37.7500, 140.4678], "茨城県": [36.3418, 140.4468], "栃木県": [36.5658, 139.8836],
  "群馬県": [36.3911, 139.0608], "埼玉県": [35.8570, 139.6489], "千葉県": [35.6047, 140.1233],
  "東京都": [35.6895, 139.6917], "神奈川県": [35.4478, 139.6425], "新潟県": [37.9026, 139.0236],
  "富山県": [36.6953, 137.2113], "石川県": [36.5947, 136.6256], "福井県": [36.0652, 136.2216],
  "山梨県": [35.6642, 138.5684], "長野県": [36.6513, 138.1810], "岐阜県": [35.3912, 136.7223],
  "静岡県": [34.9769, 138.3831], "愛知県": [35.1802, 136.9066], "三重県": [34.7303, 136.5086],
  "滋賀県": [35.0045, 135.8686], "京都府": [35.0212, 135.7556], "大阪府": [34.6863, 135.5200],
  "兵庫県": [34.6913, 135.1830], "奈良県": [34.6851, 135.8330], "和歌山県": [34.2261, 135.1675],
  "鳥取県": [35.5039, 134.2381], "島根県": [35.4723, 133.0505], "岡山県": [34.6618, 133.9350],
  "広島県": [34.3966, 132.4596], "山口県": [34.1859, 131.4714], "徳島県": [34.0658, 134.5593],
  "香川県": [34.3401, 134.0434], "愛媛県": [33.8416, 132.7657], "高知県": [33.5597, 133.5311],
  "福岡県": [33.6064, 130.4181], "佐賀県": [33.2494, 130.2989], "長崎県": [32.7448, 129.8737],
  "熊本県": [32.7898, 130.7417], "大分県": [33.2382, 131.6126], "宮崎県": [31.9111, 131.4239],
  "鹿児島県": [31.5602, 130.5581], "沖縄県": [26.2124, 127.6809],
};

// ── バグ修正: EEW(556)の areas[].pref は末尾の「県/都/府」が省略された短縮形で届く ──
// （例: 実測震度(551/552)の points[].pref は "熊本県" だが、EEW(556)の areas[].pref は "熊本"）。
// これに気づかず生のprefをそのまま使っていたため、lib/eewImage.js の buildPrefColorMap
// （PREFS＝フル名称の都道府県ポリゴンデータとpref名で突き合わせて塗り分ける）が
// EEW通知画像に限って一件もマッチせず、影響範囲の都道府県が塗られない・地図の
// フォーカス範囲(bbox)も正しく絞り込まれない、という不具合になっていた。
// EEW_PREF_COORDS（フル名称）のキーから「短縮形→フル名称」の変換表を作って正規化する
// （北海道だけは末尾を落とすと「北海」になり短縮形として使われないため、自分自身も登録しておく）。
const EEW_PREF_SHORT_TO_FULL = (() => {
  const map = new Map();
  for (const full of Object.keys(EEW_PREF_COORDS)) {
    map.set(full, full);
    map.set(full.slice(0, -1), full); // 末尾1文字（県/都/府/道）を落とした短縮形
  }
  return map;
})();
function normalizeEewPrefName(raw) {
  if (!raw) return raw;
  return EEW_PREF_SHORT_TO_FULL.get(raw) || raw; // 未知の表記が来てもエラーにはせずそのまま返す
}

// P2P地震情報のtime文字列（日本時間、"YYYY/MM/DD HH:MM:SS[.ffffff]"形式）を
// Unixエポック秒（UTC基準の絶対時刻）に変換する。パース失敗時はnull。
function parseJstTimeToEpoch(raw) {
  if (!raw) return null;
  const m = String(raw)
    .trim()
    .match(/^(\d{4})\/(\d{2})\/(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?$/);
  if (!m) return null;
  const [, y, mo, d, h, mi, se, frac] = m;
  const ms = frac ? Number((frac + "000").slice(0, 3)) : 0;
  const utcMs =
    Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi), Number(se), ms) -
    9 * 60 * 60 * 1000; // JST(UTC+9) → UTC
  return utcMs / 1000;
}

// 上流から受信した生のメッセージをそのままDBに残す（デバッグ用）。
// フィルタリング（EEW_MIN_SCALE等）や整形より前の、加工していない生データが対象。
// code=551/552/556以外（津波予報など、このアプリが処理しない情報）も含め全件保存する
// （「本当は何が来ていたのか」を後から確認できるようにするため）。
// あくまで補助的なデバッグ機能なので、失敗しても配信処理自体には影響させない。
function saveRawEewLog(item) {
  if (!EEW_RAW_LOG_ENABLED) return;
  try {
    const issue = item?.issue || {};
    db.prepare(
      `INSERT INTO eew_raw_log (code, event_id, serial, raw_json) VALUES (?, ?, ?, ?)`
    ).run(
      typeof item?.code === "number" ? item.code : null,
      issue.eventId != null ? String(issue.eventId) : null,
      issue.serial != null ? String(issue.serial) : null,
      JSON.stringify(item)
    );
    // 挿入のたびに古い行を間引き、件数上限を超えないようにする
    // （地震関連メッセージはそれほど高頻度ではないため、毎回実行しても軽量）。
    db.prepare(
      `DELETE FROM eew_raw_log WHERE id <= (SELECT MAX(id) FROM eew_raw_log) - ?`
    ).run(EEW_RAW_LOG_MAX_ROWS);
  } catch (err) {
    console.warn("[EEW] デバッグ用の生データ保存に失敗しました（配信処理は継続します）:", err?.message);
  }
}

// ── モジュール内の状態（サーバープロセスのメモリ内のみ。DBには保存しない＝再起動で消えてよい） ──
let wsModule = null; // "ws" パッケージ（動的import。未導入ならnullのままEEW機能は無効）
let wss = null; // 下流（ブラウザ）向け WebSocketServer
let upstreamWs = null; // 上流（P2P地震情報）への接続中WebSocket
let upstreamReconnectTimer = null;
let upstreamReconnectCount = 0;
let stopped = false;

let latestEew = { id: 0 };
let latestIntensity = { id: 0 };
// ── バグ修正: サーバー再起動をまたいだID採番 ──
// eewNextId/intensityNextIdはこれまでプロセスのメモリ上だけで1から採番していた。
// 画像・詳細データがメモリキャッシュ（TTL付き）にしか無かった間はそれで問題なかったが、
// eew_notification_details にDB永続化するようになった今、再起動のたびに1から採番し直すと
// 過去に払い出したlocal_id（＝既にpush通知として端末に届いていて、後からeqDetail=kind:idで
// 開かれ得るID）と衝突する。INSERTがON CONFLICT(kind, local_id) DO UPDATEなので、
// 衝突した場合は過去のデータが新しい（全く別の）地震のデータで黙って上書きされてしまい、
// 古い通知をクリックすると別の地震の詳細が出るという実害が出る。
// これを避けるため、起動時にDB側の最大local_idから採番を再開する。
function nextLocalIdFromDb(kind) {
  try {
    const row = db.prepare(`SELECT MAX(local_id) AS maxId FROM eew_notification_details WHERE kind = ?`).get(kind);
    return (row?.maxId ?? 0) + 1;
  } catch (err) {
    console.warn(`[EEW] ${kind}のID採番再開に失敗しました（1から採番します）:`, err?.message);
    return 1;
  }
}
let eewNextId = nextLocalIdFromDb("eew");
let intensityNextId = nextLocalIdFromDb("intensity");

// ── 起動時クリーンアップ: 過去に誤ってDB永続化されてしまったテスト送信データの削除 ──
// sendTestEewImagePush（設定画面の「地震通知（画像付き）をテスト送信」ボタン）が、
// 以前はrememberEewImageDataを通じて本物の地震と同じ eew_notification_details に
// 書き込んでいたため、履歴一覧（/api/eew/history）に「東京湾（テスト）」の架空データが
// 混ざってしまっていた。今後はpersist:falseでメモリキャッシュのみに留めるよう直したが、
// 既に書き込まれてしまった過去分はこのままでは残り続けるため、起動時に一度だけ掃除する。
// 判定はkind='eew'ならdata.test===true、kind='intensity'は元々testフラグを持たないため
// sendTestEewImagePush固有のreport_label（"テスト配信"）で見分ける。
try {
  const staleTestRows = db
    .prepare(
      `SELECT id, kind, data_json FROM eew_notification_details
       WHERE kind = 'eew' OR kind = 'intensity'`
    )
    .all();
  const staleIds = staleTestRows
    .filter((row) => {
      try {
        const d = JSON.parse(row.data_json);
        return row.kind === "eew" ? d?.test === true : d?.report_label === "テスト配信";
      } catch {
        return false;
      }
    })
    .map((row) => row.id);
  if (staleIds.length > 0) {
    const placeholders = staleIds.map(() => "?").join(",");
    db.prepare(`DELETE FROM eew_notification_details WHERE id IN (${placeholders})`).run(...staleIds);
    console.log(`[EEW] 履歴に混入していたテスト送信データを${staleIds.length}件削除しました`);
  }
} catch (err) {
  console.warn("[EEW] テスト送信データの起動時クリーンアップに失敗しました:", err?.message);
}

// ── 起動時クリーンアップ: 旧スキーマの実測震度通知データを削除 ──
// report_label（issue.type/issue.correctから組み立てるラベル）を導入する前のコードが
// publishIntensityResultした行は、report_labelキー自体を持たない（かつorigin_epochの
// フォールバックも無かったため大抵null）。buildIntensityReportLabelは常に文字列を返すため、
// report_labelキーの有無が「新/旧スキーマ」の確実な判定材料になる。
// このような行は詳細モーダルで「発生時刻不明・地震情報」としてしか出せず、実データが
// eew_raw_logに残っていても自動では直らないため、起動時に削除して次回以降の受信データに
// 任せる（履歴一覧はeew_raw_log由来なので、ここを消しても履歴機能自体には影響しない）。
try {
  const legacyIntensityRows = db
    .prepare(`SELECT id, data_json FROM eew_notification_details WHERE kind = 'intensity'`)
    .all();
  const legacyIds = legacyIntensityRows
    .filter((row) => {
      try {
        const d = JSON.parse(row.data_json);
        return !("report_label" in d);
      } catch {
        return false;
      }
    })
    .map((row) => row.id);
  if (legacyIds.length > 0) {
    const placeholders = legacyIds.map(() => "?").join(",");
    db.prepare(`DELETE FROM eew_notification_details WHERE id IN (${placeholders})`).run(...legacyIds);
    console.log(`[EEW] 旧スキーマ（report_label未対応）の実測震度通知データを${legacyIds.length}件削除しました`);
  }
} catch (err) {
  console.warn("[EEW] 旧スキーマ通知データの起動時クリーンアップに失敗しました:", err?.message);
}
let eewLastEventKey = null; // 直前に処理したEEWイベントの識別キー（eventId/serial/cancelled）
let intensityLastKey = null; // 同上、実測震度用（eventId/serial/code）
// 556(EEW)→551(実測震度)の橋渡し用（下のpublishEewResult/publishIntensityResultで使用）。
// 震源名が一致し、発生時刻もこの範囲内に収まっていれば「同じ地震の続報」とみなす。
let recentEewSession = null;
const EEW_INTENSITY_LINK_MAX_AGE_MS = 15 * 60 * 1000;
// 実測震度(551/552)同士の続報を束ねるためのセッション。
// 当初は issue.eventId をキーに intensityEventState へ束ねる設計だったが、実際のP2P地震情報の
//配信を見るとeventIdは551/552側には一度も入ってこない（556にしか入らない）。そのため
// eventId頼みだと「震度速報→震源判明→詳細震度」のような一連の続報がすべて別イベント扱いに
// なり、pointsが空の報（Destination型など）が「情報が無いので破棄」されてしまい、そこに
// 含まれるはずの震源確定・マグニチュード修正が丸ごと消えるバグがあった。
// 対策として、まずEEW(556)と紐付けられればそのevent_idをキーとして共有し（→intensityEventState
// もEEWと同じキーで束ねられる）、紐付かない場合は直前の実測震度セッションとの震源名・時刻の
// 近さで継続 or 新規発行するようにする（isSameQuakeSession参照）。
let recentIntensitySession = null; // { key, hypocenterName, originEpoch, updatedAtMs }
let intensitySyntheticSeq = 0;
// セッション（EEWまたは実測震度の直近状態）と新しい報が「同じ地震」かどうかを判定する共通ヘルパー。
// 震源名が両方分かっていればその一致（＋発生時刻の近さ）で判定し、どちらかが震源不明なら
// 直近（maxAgeMs以内）であることのみで同一とみなす（ScalePrompt型のように震源情報を
// 含まない報は、名前で照合しようが無いため）。
function isSameQuakeSession(session, reportHypoName, reportOriginEpoch, maxAgeMs) {
  if (!session) return false;
  if (Date.now() - session.updatedAtMs > maxAgeMs) return false;
  if (session.hypocenterName && reportHypoName) {
    if (session.hypocenterName !== reportHypoName) return false;
    return (
      typeof session.originEpoch !== "number" ||
      typeof reportOriginEpoch !== "number" ||
      Math.abs(session.originEpoch - reportOriginEpoch) <= 5 * 60
    );
  }
  return true; // 名前を一方または双方とも照合できない場合は、直近であることのみで同一視する
}
const eewSeenIds = []; // 直近処理済みitem.id（WebSocketの重複配信対策の簡易リングバッファ）
const intensitySeenIds = [];
const eewDetectionSeenIds = []; // 同上、code=554（EEW発表検出）用
const SEEN_IDS_MAX = 50;
// code=554（緊急地震速報の発表を検出した、という速報。556本体より一瞬早く/ほぼ同時に届く）を
// 受けてから、実際に556が来ないまま「検出中」表示をクライアント側に残し続けないための上限。
// 何らかの理由で556が来なかった（フィルタで弾かれた・配信が遅延した等）場合の保険。
const EEW_DETECTION_AUTO_CLEAR_MS = 8000;

// ── push通知の画像（lib/eewImage.js でSVGを描画）用データキャッシュ ──
// Web Push本体には画像そのものではなくURL（/api/eew/image/:kind/:id）を積んでおき、
// 実際の画像生成はブラウザが通知表示のためにそのURLを取りに来たタイミングで行う
// （通知を受け取った全端末分を毎回サーバー側で事前レンダリングするのは無駄なため）。
// そのため、pushした後もしばらくは元データを参照できるようにこのMapに保持しておく
// （kind:id をキーに、最新の一定件数だけ・かつ一定時間で自動的に捨てる）。
const eewImageDataCache = new Map();
const EEW_IMAGE_CACHE_MAX = 30;
const EEW_IMAGE_CACHE_TTL_MS = 60 * 60 * 1000; // メモリキャッシュ自体はホットパス用に短命のままでよい
// push通知クリック時の詳細表示（routes/eewImage.js の /detail/:kind/:id）は、上のメモリキャッシュの
// TTL・件数上限に関係なく引けるよう、DBの eew_notification_details にも永続化する。
// 時間では間引かず、kindごとの件数上限のみで間引く（地震関連の通知はそう高頻度ではないため）。
const EEW_NOTIFICATION_DETAILS_MAX_PER_KIND = 500;
function rememberEewImageData(kind, data, { persist = true } = {}) {
  const key = `${kind}:${data.id}`;
  eewImageDataCache.set(key, { kind, data, storedAtMs: Date.now() });
  // 古いものから間引く（Mapは挿入順を保持するため先頭＝一番古い）
  while (eewImageDataCache.size > EEW_IMAGE_CACHE_MAX) {
    const oldestKey = eewImageDataCache.keys().next().value;
    eewImageDataCache.delete(oldestKey);
  }
  // persist=false（sendTestEewImagePushからの動作確認用サンプルデータ）はメモリキャッシュのみに
  // 留め、DBには書かない。これらは実際の地震ではなくボタン一つで何度でも作れる架空データなので、
  // 恒久的に残る履歴一覧（/api/eew/history）や期限切れ後の詳細取得に混ざり込むと、実際の地震と
  // 見分けがつかなくなってしまうため。push通知が届いた直後に画像を取りに来る分は
  // メモリキャッシュ（TTL内）だけで十分間に合う。
  if (!persist) return;
  try {
    db.prepare(
      `INSERT INTO eew_notification_details (kind, local_id, data_json) VALUES (?, ?, ?)
       ON CONFLICT(kind, local_id) DO UPDATE SET data_json = excluded.data_json`
    ).run(kind, data.id, JSON.stringify(data));
    db.prepare(
      `DELETE FROM eew_notification_details
       WHERE kind = ? AND id <= (SELECT MAX(id) FROM eew_notification_details WHERE kind = ?) - ?`
    ).run(kind, kind, EEW_NOTIFICATION_DETAILS_MAX_PER_KIND);
  } catch (err) {
    console.warn("[EEW] 通知詳細のDB保存に失敗しました（画像・pushの送信自体は継続します）:", err?.message);
  }
}
// routes/eewImage.js から呼ばれる。まずメモリキャッシュ（速い）、無ければDB永続化分（消えない）を見る。
// どちらにも無ければnull。
export { eewScaleText, isValidEewScale, EEW_SELECTABLE_SCALES };

export function getEewImageSourceData(kind, id) {
  const key = `${kind}:${id}`;
  const cached = eewImageDataCache.get(key);
  if (cached && Date.now() - cached.storedAtMs <= EEW_IMAGE_CACHE_TTL_MS) return cached;
  try {
    const row = db
      .prepare(`SELECT data_json FROM eew_notification_details WHERE kind = ? AND local_id = ?`)
      .get(kind, Number(id));
    if (row) return { kind, data: JSON.parse(row.data_json) };
  } catch (err) {
    console.warn("[EEW] 通知詳細のDB取得に失敗しました:", err?.message);
  }
  return null;
}
// ── 過去の地震履歴一覧（routes/eewImage.js の GET /api/eew/history, GET /api/eew/history/:id から呼ばれる） ──
// 以前は eew_notification_details（push通知の詳細復元用に、特定の条件を満たした報のみ書き込む
// テーブル）をデータソースにしていたが、これだと「実際に上流から届いた報のうちどれだけが
// 記録されるか」が条件次第でまばらになり、履歴として見るには取りこぼしが多かった
// （実機のapp.dbで確認したところ、eew_raw_logには551だけで54件あるのに対し、
// eew_notification_details側は1件しか無かった）。
// eew_raw_log は上流から届いたメッセージをフィルタ・加工前にそのまま全件保存しているため、
// より網羅的な履歴ソースとして使える。ただしEEW/実測震度に直接関係しない他コード
// （555=遠地地震、561=長周期地震動、9611=SignalNow Professional運用メッセージ等）まで
// 混ざっているため、履歴として意味のある3種類だけに絞る（ユーザー指定）:
//   551 = 震度速報／各地の震度に関する情報（実測震度）
//   554 = 緊急地震速報の発表を検出（本体である556より先に届く軽量な検知のみの報）
//   556 = 緊急地震速報（警報・本体）
const EEW_HISTORY_CODES = [551, 554, 556];
const EEW_HISTORY_KIND_BY_CODE = { 556: "eew", 551: "intensity", 554: "detected" };

// eew_raw_log 1行分の生JSON（P2P地震情報からそのまま受信したもの）を、既存の
// buildEqNoticeDetailSnapshotFromEew/Intensity（クライアント側）がそのまま食える形の
// data オブジェクトに変換する。publishEewResult/publishIntensityResultと基本同じ
// フィールド構成に合わせているが、こちらは「1件だけを独立に見る」ための純粋な変換のみで、
// DB更新・push配信・複数報をまたいだ継続イベント紐付け（recentEewSession等）は行わない
// （そのため「最終報の可能性」ヒントは出ない・ScalePrompt単体では震源が「不明」のままになる
// 等、ライブ配信時の表示より情報がやや少ないことがあるが、受信した報をそのまま見るという
// 履歴機能の趣旨には合っている）。
function buildHistoryDetailFromRawRow(row) {
  let item;
  try {
    item = JSON.parse(row.raw_json);
  } catch {
    return null;
  }
  const kind = EEW_HISTORY_KIND_BY_CODE[row.code];
  if (!kind) return null;
  // received_atはSQLiteのdatetime('now')＝UTCの"YYYY-MM-DD HH:MM:SS"形式で保存されている。
  const issuedAtMs = Date.parse(`${row.received_at.replace(" ", "T")}Z`);
  const issuedAt = Number.isFinite(issuedAtMs) ? issuedAtMs : null;

  if (kind === "detected") {
    return { kind, data: { id: row.id, issued_at_ms: issuedAt } };
  }

  if (kind === "eew") {
    const eq = item.earthquake || {};
    const hypocenter = eq.hypocenter || {};
    const issue = item.issue || {};
    const cancelled = !!item.cancelled;
    const isTest = !!item.test;
    let magnitude = hypocenter.magnitude;
    if (typeof magnitude !== "number" || magnitude < 0) magnitude = null;
    let lat = hypocenter.latitude;
    let lon = hypocenter.longitude;
    if (typeof lat !== "number" || typeof lon !== "number" || lat <= -180 || lon <= -180) {
      lat = null;
      lon = null;
    }
    // 深さ(km)。P2P地震情報APIの仕様上、「ごく浅い」（具体的な数値なし）は0、
    // 震源情報が存在しない場合は-1になる。以前は「0=震源不明のプレースホルダー」と誤解し
    // lat/lonが不明な場合にdepthもnull化していたが、0は「ごく浅い」を意味する正当な値であり
    // nullにしてはいけない（-1のみ不明として弾く）。0の表示側の扱いはlib/eewImage.js側で
    // 「ごく浅い」という文字列に変換する。
    let depth = hypocenter.depth;
    if (typeof depth !== "number" || depth < 0) depth = null;
    const originEpoch = cancelled ? null : parseJstTimeToEpoch(eq.originTime);
    const scaleToMax = (item.areas || []).reduce(
      (mx, a) => (typeof a.scaleTo === "number" && a.scaleTo > mx ? a.scaleTo : mx),
      -Infinity
    );
    const maxScale = Number.isFinite(scaleToMax) ? scaleToMax : null;
    const areasOut = (item.areas || [])
      .map((a) => ({
        pref: normalizeEewPrefName(a.pref) ?? null,
        name: a.name ?? "",
        scale_to: typeof a.scaleTo === "number" ? a.scaleTo : null,
        scale_text: eewScaleText(a.scaleTo),
        color: eewScaleColor(a.scaleTo),
      }))
      .sort((a, b) => (b.scale_to ?? -1) - (a.scale_to ?? -1))
      .slice(0, 10);
    const rawSerial = issue.serial;
    const serial =
      rawSerial === undefined || rawSerial === null || String(rawSerial).trim() === ""
        ? "?"
        : String(rawSerial).trim();
    return {
      kind,
      data: {
        id: row.id,
        event_id: issue.eventId ?? null,
        test: isTest,
        cancelled,
        serial,
        hypocenter_name: hypocenter.name || "震源不明",
        hypocenter_lat: lat,
        hypocenter_lon: lon,
        magnitude,
        depth,
        max_scale: cancelled ? null : maxScale,
        max_scale_text: cancelled ? null : eewScaleText(maxScale),
        max_scale_color: cancelled ? null : eewScaleColor(maxScale),
        origin_epoch: originEpoch,
        areas: cancelled ? [] : areasOut,
        issued_at_ms: issuedAt,
      },
    };
  }

  // kind === "intensity"（code 551）
  const eq = item.earthquake || {};
  const hypocenter = eq.hypocenter || {};
  const issue = item.issue || {};
  // item.points は市区町村（観測点）単位で、同じ都道府県の点が何十件も並ぶことがある。
  // リアルタイム配信側（processIntensityItem）はここを都道府県ごとの最大震度だけに
  // 集計してから使っており、履歴側もそれと揃える必要がある。集計せず生の観測点を
  // そのまま返すと、「都道府県ごとの震度」欄に同じ県が観測点の数だけ重複して
  // 表示されてしまう（実際に発生していたバグ）。
  const prefMax = new Map();
  // 都道府県ごとの市区町村（観測点）の内訳（「詳しい地域も表示してほしい」への対応）。
  // pref -> Map<addr, scale>
  const areaMaxByPref = new Map();
  for (const p of item.points || []) {
    const pref = normalizeEewPrefName(p?.pref);
    const scale = p?.scale;
    if (!pref || !EEW_PREF_COORDS[pref] || typeof scale !== "number") continue;
    if (!prefMax.has(pref) || scale > prefMax.get(pref)) prefMax.set(pref, scale);

    const addr = typeof p?.addr === "string" && p.addr ? p.addr : null;
    if (!addr) continue; // addrが無い形式の報（古いAPI等）もあり得るので、その場合は内訳無しでよい
    if (!areaMaxByPref.has(pref)) areaMaxByPref.set(pref, new Map());
    const areaMax = areaMaxByPref.get(pref);
    if (!areaMax.has(addr) || scale > areaMax.get(addr)) areaMax.set(addr, scale);
  }
  const pointsOut = [...prefMax.entries()]
    .map(([pref, scale]) => {
      const coords = EEW_PREF_COORDS[pref];
      const areas = [...(areaMaxByPref.get(pref)?.entries() ?? [])]
        .map(([addr, s]) => ({ addr, scale: s, scale_text: eewScaleText(s) }))
        .sort((a, b) => b.scale - a.scale);
      return {
        pref,
        lat: coords ? coords[0] : null,
        lon: coords ? coords[1] : null,
        scale,
        scale_text: eewScaleText(scale),
        color: eewScaleColor(scale),
        areas,
      };
    })
    .sort((a, b) => b.scale - a.scale);
  let hLat = hypocenter.latitude;
  let hLon = hypocenter.longitude;
  if (typeof hLat !== "number" || typeof hLon !== "number" || hLat <= -180 || hLon <= -180) {
    hLat = null;
    hLon = null;
  }
  // 深さ(km)。P2P地震情報APIの仕様上、「ごく浅い」は0、震源情報が存在しない場合は-1になる。
  // 0は正当な値（「ごく浅い」）なのでnullにしない。表示側（lib/eewImage.js）で「ごく浅い」に変換する。
  let hDepth = hypocenter.depth;
  if (typeof hDepth !== "number" || hDepth < 0) hDepth = null;
  let magnitude = hypocenter.magnitude;
  if (typeof magnitude !== "number" || magnitude < 0) magnitude = null;
  const originEpoch = parseJstTimeToEpoch(eq.time ?? eq.originTime);
  const issueType = typeof issue.type === "string" ? issue.type : null;
  const issueCorrect = typeof issue.correct === "string" ? issue.correct : null;
  const reportLabel = buildIntensityReportLabel(issueType, issueCorrect, null);
  return {
    kind,
    data: {
      id: row.id,
      event_id: issue.eventId ?? null,
      serial: null, // 551には本来serial自体が存在しない（report_labelを使う。publishIntensityResultと同じ）
      issue_type: issueType,
      issue_correct: issueCorrect,
      report_label: reportLabel,
      hypocenter_name: hypocenter.name || "",
      hypocenter_lat: hLat,
      hypocenter_lon: hLon,
      hypocenter_depth: hDepth,
      magnitude,
      origin_epoch: originEpoch,
      max_scale: pointsOut[0]?.scale ?? null,
      max_scale_text: pointsOut[0]?.scale_text || "不明",
      points: pointsOut,
      issued_at_ms: issuedAt,
    },
  };
}

// ── 同じ地震の複数報を履歴一覧では1件にまとめる ──
// P2P地震情報には551(実測震度)同士や551↔556(EEW)を「同じ地震」と機械的に紐付けるID
// （event_id相当）が無い（event_idを持つのは556だけ）。実際にapp.dbの中身を調べたところ:
//   ・同じ地震の556→551系続報は、earthquake.time（551側は分単位に丸められる）のズレが
//     最大でも36秒程度だった
//   ・一方、明確に別の地震（数分間隔で発生した別々の余震）同士は、震源名が同じ
//     （例:「熊本県熊本地方」が1日に何度も再発）でも、origin時刻は最低でも2分（120秒）以上
//     離れていた
// という傾向が確認できたため、origin_epoch（earthquake.time/originTimeをUTC秒に変換したもの）
// が90秒以内に収まる報同士を「同じ地震」とみなして束ねる（120秒の下限に対して余裕を持たせた値）。
// 554(検知のみ)はearthquake情報自体を持たないため、代わりに受信時刻(received_at)が直近の
// グループの最終更新から30秒以内なら、そのグループにぶら下げる。
// なお、これはあくまでJMA非公式データから経験的に導いたヒューリスティックであり、
// 気象庁側の公式な「同一地震」判定を保証するものではない点に留意。
const EEW_HISTORY_GROUP_TOLERANCE_SEC = 90;
const EEW_HISTORY_DETECTED_LINK_MS = 30 * 1000;

// 生ログの行（時系列昇順で渡すこと）を「同じ地震」ごとにグループ化する。
// 各グループの代表報（representative）は、そのグループの中で最後に届いた報を採用する
// （ScalePrompt→Destination→DetailScaleの順で情報が充実していく実データの傾向と一致するため、
// 基本的に一番情報量の多い報が採用される）。
function groupHistoryRowsByQuake(rowsAscending) {
  const groups = [];
  for (const row of rowsAscending) {
    const built = buildHistoryDetailFromRawRow(row);
    if (!built) continue;
    const receivedMs = Date.parse(`${row.received_at.replace(" ", "T")}Z`);
    const originEpoch = typeof built.data.origin_epoch === "number" ? built.data.origin_epoch : null;
    let target = null;
    if (built.kind === "detected") {
      for (let i = groups.length - 1; i >= 0; i--) {
        if (
          Number.isFinite(receivedMs) &&
          Number.isFinite(groups[i].lastReceivedMs) &&
          Math.abs(receivedMs - groups[i].lastReceivedMs) <= EEW_HISTORY_DETECTED_LINK_MS
        ) {
          target = groups[i];
          break;
        }
      }
    } else if (originEpoch != null) {
      for (let i = groups.length - 1; i >= 0; i--) {
        if (
          groups[i].originEpoch != null &&
          Math.abs(groups[i].originEpoch - originEpoch) <= EEW_HISTORY_GROUP_TOLERANCE_SEC
        ) {
          target = groups[i];
          break;
        }
      }
    }
    if (!target) {
      target = {
        originEpoch: null,
        lastReceivedMs: null,
        minRowId: row.id,
        maxRowId: row.id,
        reportCount: 0,
        hasEew: false,
        hasIntensity: false,
        representative: null,
      };
      groups.push(target);
    }
    target.minRowId = Math.min(target.minRowId, row.id);
    target.maxRowId = Math.max(target.maxRowId, row.id);
    if (Number.isFinite(receivedMs)) target.lastReceivedMs = receivedMs;
    if (originEpoch != null) target.originEpoch = originEpoch;
    target.reportCount += 1;
    if (built.kind === "eew") target.hasEew = true;
    if (built.kind === "intensity") target.hasIntensity = true;
    target.representative = { id: row.id, kind: built.kind, data: built.data };
  }
  return groups;
}

export function listEewHistory({ kind = "all", limit = 30, beforeCursor = null } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 30, 1), 100);
  // 生ログは複数報が同じ1つの地震に対応することがあるため、まとめた後にlim件確保できるよう、
  // 生の行としては多め（目安10倍、上限500件）に取得してからグルーピングする。
  const fetchWindow = Math.min(lim * 10, 500);
  const conditions = [`code IN (${EEW_HISTORY_CODES.join(",")})`];
  const params = [];
  if (kind === "eew") conditions.push("code = 556");
  else if (kind === "intensity") conditions.push("code = 551");
  else if (kind === "detected") conditions.push("code = 554");
  const cursor = Number(beforeCursor);
  if (beforeCursor != null && Number.isFinite(cursor)) {
    conditions.push("id < ?");
    params.push(cursor);
  }
  let rowsDesc;
  try {
    rowsDesc = db
      .prepare(
        `SELECT id, code, raw_json, received_at FROM eew_raw_log
         WHERE ${conditions.join(" AND ")} ORDER BY id DESC LIMIT ?`
      )
      .all(...params, fetchWindow);
  } catch (err) {
    console.warn("[EEW] 履歴一覧の取得に失敗しました:", err?.message);
    return { items: [], nextCursor: null };
  }
  // グルーピングは時系列順（古い→新しい）で処理する必要があるため昇順に並べ直す。
  const rowsAscending = rowsDesc.slice().reverse();
  const groups = groupHistoryRowsByQuake(rowsAscending).sort((a, b) => b.maxRowId - a.maxRowId);
  const visibleGroups = groups.slice(0, lim);
  const items = visibleGroups.map((g) => {
    const rep = g.representative;
    const d = rep.data;
    const isIntensity = rep.kind === "intensity";
    const isDetected = rep.kind === "detected";
    const maxScaleColor = isDetected ? null : isIntensity ? d.points?.[0]?.color ?? null : d.max_scale_color ?? null;
    return {
      cursor: g.minRowId,
      // kindは常に代表報自身の種別と一致させる（フロント側で/api/eew/history/:idの結果を
      // どちらのビルダーで解釈するか、この値を使って決めるため、ここがズレると詳細表示が壊れる）。
      kind: rep.kind,
      id: rep.id, // 詳細取得(/api/eew/history/:id)には代表報のidをそのまま使う
      event_id: d.event_id ?? null,
      hypocenter_name: isDetected ? null : d.hypocenter_name || "震源不明",
      magnitude: typeof d.magnitude === "number" ? d.magnitude : null,
      origin_epoch: typeof d.origin_epoch === "number" ? d.origin_epoch : null,
      issued_at_ms: typeof d.issued_at_ms === "number" ? d.issued_at_ms : null,
      max_scale: isDetected ? null : d.max_scale ?? null,
      max_scale_text: isDetected ? null : d.max_scale_text ?? null,
      max_scale_color: maxScaleColor,
      cancelled: !!d.cancelled,
      test: !!d.test,
      report_label: isIntensity ? d.report_label ?? null : null,
      serial: rep.kind === "eew" ? d.serial ?? null : null,
      // このグループに何件の生の報（556/551/554）がまとまっているか。フロント側で
      // 「全3件」のように件数バッジを出すのに使う（1件なら単発、非表示でよい）。
      report_count: g.reportCount,
      had_eew_warning: g.hasEew, // 実測震度側が代表報になっていても、元々EEW(警報)が出ていたかどうか
    };
  });
  const hasMoreInWindow = groups.length > lim;
  const windowExhausted = rowsDesc.length < fetchWindow;
  let nextCursor = null;
  if (hasMoreInWindow) {
    // 表示した最後のグループの最も古い行のidを境目にする（グループ同士はid区間が
    // 重ならない前提のため、これより小さいidは次ページに残っている未表示分だけになる）。
    nextCursor = visibleGroups[visibleGroups.length - 1].minRowId;
  } else if (!windowExhausted) {
    // 取得したウィンドウ全体がまとめても既定件数に届かなかった場合（＝密集していた場合）。
    // 「本当に続きがあるか」はここでは断定できないが、安全側に倒してウィンドウの続きから
    // 再取得できるようにしておく（続きが無ければ次回のレスポンスが自然に空になる）。
    nextCursor = rowsDesc[rowsDesc.length - 1].id;
  }
  return { items, nextCursor };
}

// 履歴一覧の1件をクリックした際の詳細取得（routes/eewImage.js の GET /api/eew/history/:id）。
// idはeew_raw_log.id（テーブル全体で一意なので、push通知詳細のようにkindを別途渡す必要がない）。
// push通知クリック時に使う getEewImageSourceData（kind+local_id、eew_notification_details）とは
// 完全に別の経路・別のID体系である点に注意。
export function getEewHistoryDetail(id) {
  const rowId = Number(id);
  if (!Number.isFinite(rowId)) return null;
  let row;
  try {
    row = db
      .prepare(
        `SELECT id, code, raw_json, received_at FROM eew_raw_log
         WHERE id = ? AND code IN (${EEW_HISTORY_CODES.join(",")})`
      )
      .get(rowId);
  } catch (err) {
    console.warn("[EEW] 履歴詳細の取得に失敗しました:", err?.message);
    return null;
  }
  if (!row) return null;
  return buildHistoryDetailFromRawRow(row);
}

// ── アプリを地震発生「後」に初めて開いた場合の取りこぼし対策 ──
// これまで直近の状態（latestEew/latestIntensity）はWebSocketの"hello"にしか使っていなかったが、
// helloは「新規訪問者に古い地震を急に見せない」ためあえてクライアント側で無視する設計にしている。
// 一方、sessionStorageによる復元（index.html側）は同じタブでのリロードにしか効かず、
// push通知クリックはそもそも通知を押した場合にしか効かない。結果として「通知を押さずに
// 普通にアプリを開いた」場合に地震発生直後の状態を拾う経路が一つも無かった。
// これをHTTPで拾えるようにする。ただしlatestEew/latestIntensityは「最後に発表されたもの」を
// 期限なくメモリに保持し続けているだけなので、そのまま返すと何時間・何日経っていても
// 返ってしまう。index.html側のsessionStorage復元の許容時間（EEW_SESSION_RESTORE_MAX_AGE_MS,
// 20分）と揃えて、それより古ければ返さない。
const EEW_CATCHUP_MAX_AGE_MS = 20 * 60 * 1000;
function catchUpIfFresh(latest) {
  if (!latest || typeof latest.issued_at_ms !== "number") return null;
  if (Date.now() - latest.issued_at_ms > EEW_CATCHUP_MAX_AGE_MS) return null;
  return latest;
}
export function getLatestEewForCatchUp() {
  return catchUpIfFresh(latestEew);
}
export function getLatestIntensityForCatchUp() {
  return catchUpIfFresh(latestIntensity);
}
// push通知に積む画像URLを組み立てる。OGP画像と同じ考え方でPUBLIC_BASE_URLを使う
// （こちらはリクエストを経由しないコンテキスト＝WebSocket受信時に呼ぶため、req.get('host')が
// 使えず、絶対URLの組み立てに環境変数が必須になる。未設定の場合は画像なしで通知する）。
// fallbackBase: リクエストを経由する呼び出し元（例: /api/push/test-eew-image）が
// req.protocol + req.get('host') から組み立てたURLを渡せば、PUBLIC_BASE_URL未設定でも
// テスト送信時に限りそれを使う（本番の一斉配信では使わない＝undefinedのまま呼ぶ）。
function buildEewImageUrl(kind, id, fallbackBase = null) {
  const base = (process.env.PUBLIC_BASE_URL || fallbackBase || "").replace(/\/+$/, "");
  if (!base) return null;
  return `${base}/api/eew/image/${kind}/${id}.png`;
}

// push通知の間引き用。ユーザーごとに受け取りたい下限震度(users.notify_on_eew_min_scale)が
// 異なるようになったため、「同じ地震につき1回だけ」という単純なON/OFFの重複排除ではなく、
// 「その地震について、これまでに何スケールまでpush済みか」を覚えておく方式にする。
// 続報で震度が上方修正されるたびに、新たに閾値を超えた（＝まだ通知していない）ユーザーだけに
// 追加で送る。同じ値のまま・下方修正の場合は追加送信しない（すでに通知済みのユーザーへの
// 二重通知を防ぐため）。
const eewPushedMaxScaleByEvent = new Map(); // eventKey -> これまでにpush済みの最大震度
const EEW_PUSHED_MAX_SCALE_MAX_ENTRIES = 200;
function rememberPushedMaxScale(key, scale) {
  if (key === undefined || key === null) return;
  // バグ修正: Mapは既存キーへの.set()では反復順序（挿入順）が更新されない。
  // 同じ地震が続報のたびに何度も更新されるケースで、実際にはまだアクティブなイベントなのに
  // 「最初に追加された順」で古参扱いされ先に削除されてしまっていた（本来のLRUなら
  // 直近に更新したものほど残ってほしい）。一度deleteしてから入れ直すことで、
  // 更新するたびに最新として扱われる正しいLRUにする。
  if (eewPushedMaxScaleByEvent.has(key)) eewPushedMaxScaleByEvent.delete(key);
  eewPushedMaxScaleByEvent.set(key, scale);
  if (eewPushedMaxScaleByEvent.size > EEW_PUSHED_MAX_SCALE_MAX_ENTRIES) {
    const oldestKey = eewPushedMaxScaleByEvent.keys().next().value;
    eewPushedMaxScaleByEvent.delete(oldestKey);
  }
}

// 実測震度(551/552)側の同種の間引き。以前はここに間引きが無く、震度速報→震源判明→各地の震度
// のように同じ地震で複数タイプの続報が届くたびに、最大震度が変わっていなくても毎回pushしていた。
// requireInteraction（urgent:true）を効かせるようになった今、間引きが無いと「震度は変わって
// いないのに通知だけ何個も画面に積み上がり、手動で消すまで残り続ける」実害が出るため、
// EEW側と同じ「このセッションでこれまでにpush済みの最大震度を上回った時だけ再通知」方式にする。
const intensityPushedMaxScaleBySession = new Map(); // stateKey -> これまでにpush済みの最大震度
const INTENSITY_PUSHED_MAX_SCALE_MAX_ENTRIES = 200;
function rememberIntensityPushedMaxScale(key, scale) {
  if (key === undefined || key === null) return;
  if (intensityPushedMaxScaleBySession.has(key)) intensityPushedMaxScaleBySession.delete(key);
  intensityPushedMaxScaleBySession.set(key, scale);
  if (intensityPushedMaxScaleBySession.size > INTENSITY_PUSHED_MAX_SCALE_MAX_ENTRIES) {
    const oldestKey = intensityPushedMaxScaleBySession.keys().next().value;
    intensityPushedMaxScaleBySession.delete(oldestKey);
  }
}

function rememberSeenId(list, id) {
  if (id === undefined || id === null) return;
  list.push(id);
  if (list.length > SEEN_IDS_MAX) list.shift();
}

// "ws" パッケージを遅延読み込みする（未導入でもアプリ全体の起動を止めないため）
async function loadWs() {
  if (wsModule) return wsModule;
  try {
    wsModule = await import("ws");
  } catch (err) {
    console.warn(
      '[EEW] "ws" パッケージが見つからないため、緊急地震速報(EEW)機能は無効です。' +
        " `npm install ws` を実行すると有効になります。",
      err?.message
    );
    wsModule = null;
  }
  return wsModule;
}

// ══════════════════════════════════════════════
// 下流（ブラウザクライアント）向け配信
// ══════════════════════════════════════════════

// index.html 側からの接続を受け付ける。接続直後にサーバーが保持している最新状態(hello)を
// 送り、以後は新しいイベントを受信するたびに broadcast() で即座に全クライアントへ配信する。
export async function attachEewWebSocket(httpServer, path = "/ws/eew") {
  const mod = await loadWs();
  if (!mod) return null;
  const { WebSocketServer } = mod;

  wss = new WebSocketServer({ server: httpServer, path });

  wss.on("connection", (client) => {
    client.isAlive = true;
    client.on("pong", () => {
      client.isAlive = true;
    });
    // 接続直後、既にサーバーが保持している最新状態を送る（表示継続時間内かどうかは
    // issued_at_ms/duration_ms を見てクライアント側で判定する。既存の配信オーバーレイ／
    // 視聴者用マップ（FlightStreamAssistantGUI.py側）と同じ考え方）。
    try {
      client.send(JSON.stringify({ type: "hello", eew: latestEew, intensity: latestIntensity }));
    } catch {
      // 接続直後の切断等で送信に失敗しても無視（次のブロードキャストで復帰を試みる）
    }
  });

  // 応答が無いクライアント（回線切断・スリープ等で片方向だけ死んでいる接続）を定期的に切断する
  const heartbeat = setInterval(() => {
    for (const client of wss.clients) {
      if (client.isAlive === false) {
        client.terminate();
        continue;
      }
      client.isAlive = false;
      try {
        client.ping();
      } catch {
        // ignore
      }
    }
  }, 30000);
  wss.on("close", () => clearInterval(heartbeat));

  console.log(`[EEW] 緊急地震速報の配信用WebSocketを ${path} で待ち受けます`);
  return wss;
}

function broadcast(type, data) {
  if (!wss) return;
  const payload = JSON.stringify({ type, data });
  for (const client of wss.clients) {
    if (client.readyState === 1) {
      // 1 = WebSocket.OPEN
      try {
        client.send(payload);
      } catch {
        // 個別クライアントへの送信失敗は無視し、他のクライアントへの配信は継続する
      }
    }
  }
}

// ══════════════════════════════════════════════
// 上流（P2P地震情報）からの受信
// ══════════════════════════════════════════════

export async function startEewMonitor() {
  const mod = await loadWs();
  if (!mod) return;
  stopped = false;
  connectUpstream(mod);
}

export function stopEewMonitor() {
  stopped = true;
  if (upstreamReconnectTimer) {
    clearTimeout(upstreamReconnectTimer);
    upstreamReconnectTimer = null;
  }
  if (upstreamWs) {
    try {
      upstreamWs.terminate();
    } catch {
      // ignore
    }
    upstreamWs = null;
  }
}

function connectUpstream(mod) {
  if (stopped) return;
  const { default: WebSocketCtor } = mod;
  const ws = new WebSocketCtor(EEW_UPSTREAM_WS_URL);
  upstreamWs = ws;

  ws.on("open", () => {
    upstreamReconnectCount = 0;
    console.log("[EEW] P2P地震情報 WebSocketへ接続しました（リアルタイム受信中）");
  });

  ws.on("message", (raw) => {
    let item;
    try {
      item = JSON.parse(raw.toString());
    } catch (err) {
      console.warn("[EEW] メッセージの解析に失敗しました:", err?.message);
      return;
    }
    saveRawEewLog(item); // デバッグ用: フィルタリング前の生データを丸ごと保存（この後の処理には影響しない）
    try {
      const code = item?.code;
      if (code === 556) {
        processEewItem(item);
      } else if (code === 551 || code === 552) {
        processIntensityItem(item);
      } else if (code === 554) {
        processEewDetectionItem(item);
      }
    } catch (err) {
      console.warn("[EEW] 受信処理中にエラーが発生しました:", err?.message);
    }
  });

  ws.on("error", (err) => {
    console.warn("[EEW] WebSocketでエラーが発生しました:", err?.message);
  });

  ws.on("close", () => {
    upstreamWs = null;
    if (stopped) return;
    upstreamReconnectCount += 1;
    const waitMs = Math.min(3000 * upstreamReconnectCount, 30000);
    console.warn(
      `[EEW] 上流WebSocketが切断されました。${waitMs / 1000}秒後に再接続します（${upstreamReconnectCount}回目）`
    );
    upstreamReconnectTimer = setTimeout(() => connectUpstream(mod), waitMs);
  });
}

// ── EEW発表検出（code=554）処理 ──────────────────────────────
// 「緊急地震速報の発表を検出した」という軽量な速報。実際の震源・震度・エリア等の
// 具体的な内容は一切含まれない（P2P地震情報APIの実データでも code/time/type 程度のみ）。
// 本体である556が続いて届くのが通常だが、554単体でも「地震を検知した」という事実自体は
// ユーザーに伝える価値があるため、以前のように無視はせず、軽量な検出通知として
// クライアントへブロードキャストする（556が届けば、そちらが従来通りの詳細カードとして
// 表示を引き継ぐ。クライアント側は556が一定時間内に来なければ検出表示を自動で消す）。
function processEewDetectionItem(item) {
  // バグ修正: P2P地震情報の実データはメッセージ固有IDを `_id` で持つ（`id` ではない）。
  // item.id を見ていたためこの重複排除が常にno-op（itemIdが常にundefined）になっていた。
  const itemId = item._id ?? item.id;
  if (itemId && eewDetectionSeenIds.includes(itemId)) return;
  rememberSeenId(eewDetectionSeenIds, itemId);

  console.log("[EEW] 🔔 緊急地震速報の発表を検出しました（詳細は本報を待機中）");
  broadcast("eew_detected", {
    detected_at_ms: Date.now(),
    auto_clear_ms: EEW_DETECTION_AUTO_CLEAR_MS,
  });
}

// ── EEW（code=556）処理 ──────────────────────────────
function processEewItem(item) {
  if (item.code !== 556) return; // EEW以外の情報（地震情報・津波予報等）は無視

  // WebSocketは同一イベントを複数回配信することがあるため、item側のidでも重複排除する。
  // バグ修正: 実データのメッセージ固有IDは `_id`（`id` ではない）。item.id を見ていたため
  // このチェックは常にno-opになっていた。
  const itemId = item._id ?? item.id;
  if (itemId && eewSeenIds.includes(itemId)) return;
  rememberSeenId(eewSeenIds, itemId);

  const issue = item.issue || {};
  const cancelled = Boolean(item.cancelled);
  // eventId+serial+取消有無で一意に識別。続報（serial違い）や取消（cancelled違い）は
  // 別イベントとして扱い、再配信する。
  const key = `${issue.eventId ?? ""}/${issue.serial ?? ""}/${cancelled}`;
  if (key === eewLastEventKey) return; // 既に処理済み（前回受信時から変化なし）
  eewLastEventKey = key;

  const isTest = Boolean(item.test);
  if (isTest && !EEW_INCLUDE_TEST) return;

  const areas = Array.isArray(item.areas) ? item.areas : [];
  let scaleToMax = -1;
  for (const a of areas) {
    if (typeof a?.scaleTo === "number" && a.scaleTo > scaleToMax) scaleToMax = a.scaleTo;
  }
  // 以前はここで EEW_MIN_SCALE 未満を即return（配信・push含め完全に無視）していたが、
  // 「5弱未満の地震もpushで受け取りたい」というユーザー向けに、push可否は
  // ユーザーごとの下限震度(users.notify_on_eew_min_scale)で個別に判定する必要があるため、
  // ここでは足切りせず publishEewResult に処理を進める。
  // 左上カード・右下バナーの表示自体を5弱未満で出さないという既存の挙動は、
  // publishEewResult内の shouldDisplay 判定で維持する。

  publishEewResult(item, { isTest, cancelled, scaleToMax });
}

function publishEewResult(item, { isTest, cancelled, scaleToMax }) {
  const eq = item.earthquake || {};
  const hypocenter = eq.hypocenter || {};
  const issue = item.issue || {};

  const name = hypocenter.name || "震源不明";
  let magnitude = hypocenter.magnitude;
  if (typeof magnitude !== "number" || magnitude < 0) magnitude = null;
  // issue.serial は仕様上は必ず文字列で入っているはずだが、`issue.serial || "?"` だと
  // 万一 0 のような falsy 値が来た場合に誤って "?" 扱いになってしまう。
  // undefined/null/空文字のときだけ "?" にフォールバックし、それ以外は文字列化してそのまま使う。
  // それでも "?" になった場合は上流データそのものに serial が無かったということなので、
  // 次回発生時に原因追跡できるよう issue の生データをログに残す。
  const rawSerial = issue.serial;
  const serial =
    rawSerial === undefined || rawSerial === null || String(rawSerial).trim() === ""
      ? "?"
      : String(rawSerial).trim();
  if (serial === "?") {
    console.warn(`[EEW] issue.serialを取得できませんでした。issue生データ: ${JSON.stringify(issue)}`);
  }

  // 震源の緯度経度（P波/S波の伝播アニメーションに使用）。P2P地震情報APIの仕様上、
  // 震源情報が存在しない場合は緯度経度に-200が入るため、その場合はnull扱いにする。
  let lat = hypocenter.latitude;
  let lon = hypocenter.longitude;
  if (typeof lat !== "number" || typeof lon !== "number" || lat <= -180 || lon <= -180) {
    lat = null;
    lon = null;
  }
  // 深さ(km)。P2P地震情報APIの仕様上、「ごく浅い」（具体的な数値なし）は0、震源情報が
  // 存在しない場合は-1になる。0は正当な値（「ごく浅い」）なのでnullにしない。
  // 表示側（lib/eewImage.js）で「ごく浅い」という文字列に変換する。
  let depth = hypocenter.depth;
  if (typeof depth !== "number" || depth < 0) depth = null;

  // 地震発生時刻（P波/S波アニメーションの起点として使用）。取消時はnull。
  const originEpoch = cancelled ? null : parseJstTimeToEpoch(eq.originTime);

  const areasOut = (item.areas || [])
    .map((a) => ({
      pref: normalizeEewPrefName(a.pref) ?? null,
      name: a.name ?? "",
      scale_to: typeof a.scaleTo === "number" ? a.scaleTo : null,
      scale_text: eewScaleText(a.scaleTo),
      color: eewScaleColor(a.scaleTo),
    }))
    .sort((a, b) => (b.scale_to ?? -1) - (a.scale_to ?? -1))
    .slice(0, 10);

  const data = {
    id: eewNextId++,
    // 同じ地震の続報（第2報, 第3報...）や取消を、クライアント側で同一のイベントとして
    // 紐付けるための識別子（P2P地震情報APIのissue.eventId）。複数の地震のEEWが同時に
    // 表示されている状態でも、続報が来たときに新しいカードを増やすのではなく既存の
    // カードを更新できるようにするために必要。無ければnull（＝クライアント側は
    // idベースの単発イベントとして扱う）。
    event_id: issue.eventId ?? null,
    test: isTest,
    cancelled,
    serial,
    hypocenter_name: name,
    hypocenter_lat: lat,
    hypocenter_lon: lon,
    magnitude,
    depth,
    max_scale: cancelled ? null : scaleToMax,
    max_scale_text: cancelled ? null : eewScaleText(scaleToMax),
    max_scale_color: cancelled ? null : eewScaleColor(scaleToMax),
    origin_epoch: originEpoch,
    areas: cancelled ? [] : areasOut,
    duration_ms: EEW_CARD_DURATION_MS,
    issued_at_ms: Date.now(),
  };
  const shouldDisplay = cancelled || scaleToMax >= EEW_MIN_SCALE;
  latestEew = shouldDisplay ? data : latestEew; // 表示閾値未満なら、既存の表示状態（latestEew）は変えない

  // ── 556(EEW/予測)→551(実測震度)の橋渡し用に、直近のEEWセッションを覚えておく ──
  // 実測震度(551)側はP2P地震情報の仕様上 issue.eventId を持たないため、後続の実測震度が
  // 「さっきのEEWと同じ地震か」を機械的に判定できない。そこで震源名・発生時刻が近ければ
  // 同一の地震とみなすヒューリスティックのために、ここで直近のEEW情報を残しておく
  // （publishIntensityResult側で参照し、一致すればクライアントに linked_eew_event_id を
  // 積んで送る＝クライアント側は波紋を継続させたまま表示内容だけ最新化できる）。
  if (!cancelled && !isTest) {
    recentEewSession = { eventId: data.event_id, hypocenterName: name, originEpoch, updatedAtMs: Date.now() };
  }

  // 通知画像・詳細モーダル用データは、push対象ユーザーが1人もいない場合でも常に記録しておく
  // （以前はpushEewToSubscribers内、対象ユーザーが1人以上いる場合のみ記録していたため、
  // 通知設定をしている人が誰もいない環境ではデータ自体が残らず、後から「詳細」を開けなかった）。
  // 取消・テストは元々pushの対象外＝画像を作る意味も薄いため、これまで通り対象外にする。
  if (!cancelled && !isTest) rememberEewImageData("eew", data);

  const label = cancelled ? "取消" : isTest ? "テスト" : "警報";
  const magText = magnitude !== null ? ` M${magnitude}` : "";
  if (shouldDisplay) {
    console.log(
      `[EEW] 🚨 緊急地震速報（${label}）第${serial}報: ${name}${magText} 最大予測震度${data.max_scale_text ?? "不明"}`
    );
    broadcast("eew", data);
  } else {
    // 表示閾値(EEW_MIN_SCALE)未満のため画面には出さないが、下限を低く設定しているユーザーへの
    // push対象にはなり得るので、その旨だけ静かにログしておく（デバッグ用の生データは
    // saveRawEewLog側で別途eew_raw_logに残っている）。
    console.log(
      `[EEW] （表示閾値未満のため非表示）第${serial}報: ${name}${magText} 最大予測震度${data.max_scale_text ?? "不明"}`
    );
  }

  // ── push通知（Web Push） ──
  // 左上のEEWカード・右下の通知バナーの表示可否（shouldDisplay）とは切り離し、pushは
  // ユーザーごとの下限震度(users.notify_on_eew_min_scale)で個別に判定する。
  // 取消・テストは対象外。同じ地震（event_id）について、これまでにpush済みの最大震度より
  // 上回った分だけ、新たに閾値を超えたユーザーに追加で送る（二重通知防止、詳細は
  // eewPushedMaxScaleByEvent のコメント参照）。
  if (!cancelled && !isTest) {
    const pushEventKey = issue.eventId ?? `no-event-id:${data.id}`;
    const prevMaxScale = eewPushedMaxScaleByEvent.has(pushEventKey)
      ? eewPushedMaxScaleByEvent.get(pushEventKey)
      : -Infinity;
    if (scaleToMax > prevMaxScale) {
      rememberPushedMaxScale(pushEventKey, scaleToMax);
      pushEewToSubscribers(data, prevMaxScale).catch((err) =>
        console.warn("[EEW] push通知の送信中にエラーが発生しました:", err?.message)
      );
    }
  }

  // ── Discord Webhook ──
  // push通知と違いユーザー個別の重複排除（eewPushedMaxScaleByEvent）は行わない。
  // 続報のたびに最新の内容でチャンネルへ流れる方が、Discord上で経過を追いやすいため
  // （ブラウザpushのように「同じ端末に何度も通知が鳴る」煩わしさが無い）。
  if (!isTest) {
    dispatchDiscordWebhooks("eew", data).catch((err) =>
      console.warn("[EEW] Discord Webhook送信中にエラーが発生しました:", err?.message)
    );
    // ── Misskey ──
    // Discord Webhookはユーザーごとの設定だが、こちらはサービス全体で1つだけ（.env設定）。
    // 詳しくは下のdispatchMisskeyNote()のコメント参照。
    dispatchMisskeyNote("eew", data).catch((err) =>
      console.warn("[EEW] Misskey投稿中にエラーが発生しました:", err?.message)
    );
  }
}

// ── Discord Webhook通知 ──
// ユーザーごとに1つだけ設定できるDiscord Incoming Webhook URL（users.discord_webhook_url、
// 設定APIはroutes/eewImage.jsの/webhook参照）。push通知と違い、URLを設定したこと自体が
// 「受け取る」という意思表示なので、notify_on_eew（ブラウザpushのON/OFF）とは独立に動作する。
// 下限震度は専用列users.discord_webhook_min_scaleで持つ（push通知用のnotify_on_eew_min_scaleとは
// 別に、Discordだけ震度○○以上にしたいという要望に対応するため分離した）。
const DISCORD_WEBHOOK_TIMEOUT_MS = 8000;

function truncateForDiscord(str, max) {
  if (typeof str !== "string") return str;
  return str.length > max ? `${str.slice(0, Math.max(0, max - 1))}…` : str;
}

// eewScaleColor()が返す"#rrggbb"をDiscord embedのcolor（10進数の24bit整数）に変換する。
function scaleToDiscordColor(scale) {
  const hex = eewScaleColor(scale) || "#6b7280";
  return parseInt(hex.replace("#", ""), 16) || 0x6b7280;
}

// EEW(556)・実測震度(551)共通のDiscord embedを組み立てる。push通知・詳細モーダルと
// 情報量を揃えている（震源名・発生時刻・M・深さ・最大震度・地域ごとの震度一覧・画像）。
function buildDiscordEewEmbed(kind, data) {
  const isEew = kind === "eew";
  const cancelled = !!data.cancelled;
  const title = cancelled
    ? "緊急地震速報 取消"
    : isEew
      ? `緊急地震速報（警報）${data.test ? "（訓練）" : ""}`
      : "地震情報（観測）";
  const areas = isEew ? data.areas : data.points;
  const areasText =
    Array.isArray(areas) && areas.length
      ? areas
          .slice(0, 15)
          .map((a) => `${a.pref ?? a.name ?? "不明"}: 震度${a.scale_text ?? "不明"}`)
          .join("\n")
      : "情報なし";
  const originText =
    typeof data.origin_epoch === "number"
      ? new Date(data.origin_epoch * 1000).toLocaleString("ja-JP", { hour12: false, timeZone: "Asia/Tokyo" })
      : "不明";
  const depth = isEew ? data.depth : data.hypocenter_depth;
  const depthText = depth === null || depth === undefined ? "不明" : depth === 0 ? "ごく浅い" : `${Math.round(depth)}km`;
  const magText = typeof data.magnitude === "number" ? `M${Number(data.magnitude).toFixed(1)}` : "不明";
  const imageUrl = buildEewImageUrl(kind, data.id);

  return {
    title,
    description: cancelled ? "この地震に関する緊急地震速報は取り消されました。" : data.hypocenter_name || "震源不明",
    color: cancelled ? 0x64748b : scaleToDiscordColor(data.max_scale),
    fields: cancelled
      ? []
      : [
          { name: "発生時刻", value: originText, inline: true },
          { name: "マグニチュード", value: magText, inline: true },
          { name: "震源の深さ", value: depthText, inline: true },
          { name: isEew ? "最大予測震度" : "最大震度", value: data.max_scale_text || "不明", inline: true },
          { name: isEew ? "地域ごとの予測震度" : "都道府県ごとの震度", value: truncateForDiscord(areasText, 1024) },
        ],
    timestamp: new Date().toISOString(),
    footer: { text: "気象庁の発表に基づく速報値です。実際の内容と異なる場合があります。" },
    ...(imageUrl ? { image: { url: imageUrl } } : {}),
  };
}

// ── Discord Webhookの送信者名・アイコン ──
// 何も指定しなければDiscord側のWebhook設定画面で登録した名前・アイコン（デフォルトの
// ロボットアイコン等）がそのまま使われるが、execute webhook APIは呼び出し側で
// username/avatar_urlを指定してその回のメッセージだけ上書きできる。地震情報という
// 送信元が一目で分かるよう、毎回明示的に指定する。
// アイコンはpublic/icons/配下の複数解像度のうちicon-512.pngを使う（Discordが表示時に
// 縮小するため、favicon.ico等の小さい画像より512pxの方が綺麗に表示される）。
// 他のアイコン（favicon.ico・apple-touch-icon.png・icon-192.png）に変更したい場合は
// DISCORD_WEBHOOK_AVATAR_PATH だけ書き換えればよい。
const DISCORD_WEBHOOK_USERNAME = "地震速報(fsa.pkkis.com)";
const DISCORD_WEBHOOK_AVATAR_PATH = "/icons/icon-512.png";
// buildEewImageUrl と同じ考え方でPUBLIC_BASE_URLから絶対URLを組み立てる（Discord側が
// 画像を取得しに来るため、相対URLでは表示できない）。PUBLIC_BASE_URL未設定の環境では
// avatar_url自体を省略し、Webhook設定画面のデフォルトアイコンにフォールバックする。
function buildDiscordWebhookAvatarUrl() {
  const base = (process.env.PUBLIC_BASE_URL || "").replace(/\/+$/, "");
  if (!base) return null;
  return `${base}${DISCORD_WEBHOOK_AVATAR_PATH}`;
}

// 設定済みのDiscord Webhookを持つユーザーへ一斉送信する。push通知と同じくfire-and-forgetで、
// 呼び出し側は結果を待たずに次の処理へ進んでよい（catchでログするだけ）。
async function dispatchDiscordWebhooks(kind, data) {
  let users;
  try {
    users = db
      .prepare(
        `SELECT id, discord_webhook_url AS url, discord_webhook_min_scale AS min_scale
         FROM users WHERE discord_webhook_url IS NOT NULL AND discord_webhook_url != ''`
      )
      .all();
  } catch (err) {
    console.warn("[EEW] Discord Webhook対象ユーザーの取得に失敗しました:", err?.message);
    return;
  }
  if (!users.length) return;

  const targets = users.filter((u) => {
    if (typeof data.max_scale !== "number") return true; // 取消等、震度自体が無い報は全員に送る
    const th = Number.isFinite(u.min_scale) ? u.min_scale : EEW_PUSH_MIN_SCALE;
    return data.max_scale >= th;
  });
  if (!targets.length) return;

  const embed = buildDiscordEewEmbed(kind, data);
  const avatarUrl = buildDiscordWebhookAvatarUrl();
  const payload = JSON.stringify({
    username: DISCORD_WEBHOOK_USERNAME,
    ...(avatarUrl ? { avatar_url: avatarUrl } : {}),
    embeds: [embed],
  });

  let sent = 0;
  await Promise.allSettled(
    targets.map(async (u) => {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), DISCORD_WEBHOOK_TIMEOUT_MS);
      try {
        const res = await fetch(u.url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: payload,
          signal: controller.signal,
        });
        if (res.status === 404 || res.status === 401) {
          // Webhookがサーバー（Discord）側で削除・無効化されている。放置すると毎回失敗し
          // 続けるだけなので、本人が再設定できるようこちらで自動的にクリアしておく。
          try {
            db.prepare("UPDATE users SET discord_webhook_url = NULL WHERE id = ?").run(u.id);
            console.warn(`[EEW] Discord Webhookが無効(HTTP ${res.status})だったためuser_id=${u.id}の設定を解除しました`);
          } catch (err) {
            console.warn("[EEW] 無効なDiscord Webhook設定の解除に失敗しました:", err?.message);
          }
          return;
        }
        if (!res.ok) {
          console.warn(`[EEW] Discord Webhook送信失敗（user_id=${u.id}）: HTTP ${res.status}`);
          return;
        }
        sent++;
      } catch (err) {
        console.warn(`[EEW] Discord Webhook送信中にエラー（user_id=${u.id}）:`, err?.message);
      } finally {
        clearTimeout(timer);
      }
    })
  );
  console.log(`[EEW] Discord Webhook: 対象${targets.length}件中${sent}件へ送信しました`);
}

// ── Misskey投稿 ──
// Discord Webhookはユーザーごとに個別設定できるが、こちらはユーザー単位の設定を持たず、
// サービス（このインスタンス）自身のMisskeyアカウントから1箇所へ投稿する仕組み。
// 「地震情報をこのサービスの中だけに閉じておくのはもったいない」という理由で追加した、
// 単純な外部発信用の機能なので、.envだけで完結させる（DBやユーザー設定は増やさない）。
//
// 必要な環境変数:
//   MISSKEY_INSTANCE_URL … 投稿先インスタンスのオリジン（例: https://misskey.io）。
//                           これが無い、またはMISSKEY_ACCESS_TOKENが無い場合は投稿しない
//                           （＝この機能はデフォルトで無効）。
//   MISSKEY_ACCESS_TOKEN … 投稿に使うアカウントのAPIアクセストークン
//                           （Misskeyの「設定 > API」から発行。note-write権限が必要）。
// 任意の環境変数（省略時は下記デフォルト）:
//   MISSKEY_MIN_SCALE     … 投稿する最小震度（P2P地震情報のscale値。10=震度1）。既定値10。
//                           「震度は1以上」という要望に合わせ、EEW_MIN_SCALE等の表示閾値とは
//                           独立してこちらだけで判定する。
//   MISSKEY_VISIBILITY    … ノートの公開範囲。public/home/followers/specified。既定"public"。
//   MISSKEY_CW            … 指定した場合、この文字列をCW（閲覧注意ラベル）として使い、
//                           本文は畳んだ状態で投稿する。地震情報を頻繁に流してTLを埋めたくない
//                           運用の場合に設定する（例: "地震情報"）。未設定ならCW無しで投稿。
const MISSKEY_NOTE_TIMEOUT_MS = 8000;
const MISSKEY_DEFAULT_MIN_SCALE = 10; // 震度1

function misskeyConfig() {
  const instanceUrl = (process.env.MISSKEY_INSTANCE_URL || "").trim().replace(/\/+$/, "");
  const accessToken = (process.env.MISSKEY_ACCESS_TOKEN || "").trim();
  if (!instanceUrl || !accessToken) return null;
  const minScaleRaw = Number(process.env.MISSKEY_MIN_SCALE);
  const minScale = Number.isFinite(minScaleRaw) ? minScaleRaw : MISSKEY_DEFAULT_MIN_SCALE;
  const visibility = ["public", "home", "followers", "specified"].includes(process.env.MISSKEY_VISIBILITY)
    ? process.env.MISSKEY_VISIBILITY
    : "public";
  const cw = process.env.MISSKEY_CW && String(process.env.MISSKEY_CW).trim() ? String(process.env.MISSKEY_CW).trim() : null;
  return { instanceUrl, accessToken, minScale, visibility, cw };
}

function isMisskeyConfigured() {
  return !!misskeyConfig();
}

// Discord embedと同じ情報量（震源・発生時刻・M・深さ・最大震度・地域ごとの震度）を、
// Misskeyのノート本文向けに組み立てる。地域ごとの震度一覧は、index.htmlの色分けバッジと
// 同じ考え方でMFM（Misskey Flavored Markdown）の$[fg.color=...]を使い、各都道府県を
// 気象庁震度階級の色で表示する（震度5弱以上は太字も付けて目立たせる）。
// 画像はファイルアップロードせず、URLをそのまま本文に載せる（Misskey側がリンクプレビューを
// 生成してくれるため。webhook実行の手間・失敗要因を増やしたくないための簡略化）。

// MFMの$[fg.color=RRGGBB ...]はカラーコードから"#"を取り除いた6桁hexを渡す。
function mfmColored(text, colorHex, bold) {
  const hex = (colorHex || "#6b7280").replace("#", "");
  const inner = bold ? `**${text}**` : text;
  return `$[fg.color=${hex} ${inner}]`;
}

// attachImage: 画像をMisskeyのドライブへアップロードしてfileIdsで添付できた場合はtrue。
// その場合、本文中に画像URLを別途貼る必要はない（重複するため）ので行を省く。
// アップロードに失敗した場合（未設定・エラー等）はfalseのままとなり、代わりに画像URLを
// 本文に貼ることでリンクプレビューだけは表示されるようにする（フォールバック）。
function buildMisskeyEewText(kind, data, attachImage = false) {
  const isEew = kind === "eew";
  const cancelled = !!data.cancelled;
  const title = cancelled
    ? "🚨 緊急地震速報 取消"
    : isEew
      ? `🚨 緊急地震速報（警報）${data.test ? "（訓練）" : ""}`
      : "📢 地震情報（観測）";
  const areas = isEew ? data.areas : data.points;
  const areasText =
    Array.isArray(areas) && areas.length
      ? areas
          .slice(0, 15)
          .map((a) => {
            const name = a.pref ?? a.name ?? "不明";
            const scaleText = a.scale_text ?? "不明";
            const scale = a.scale ?? a.scale_to;
            // 震度5弱(45)以上は太字にして強い揺れを目立たせる
            return `・${mfmColored(`${name}: 震度${scaleText}`, a.color, Number(scale) >= 45)}`;
          })
          .join("\n")
      : "情報なし";
  const originText =
    typeof data.origin_epoch === "number"
      ? new Date(data.origin_epoch * 1000).toLocaleString("ja-JP", { hour12: false, timeZone: "Asia/Tokyo" })
      : "不明";
  const depth = isEew ? data.depth : data.hypocenter_depth;
  const depthText = depth === null || depth === undefined ? "不明" : depth === 0 ? "ごく浅い" : `${Math.round(depth)}km`;
  const magText = typeof data.magnitude === "number" ? `M${Number(data.magnitude).toFixed(1)}` : "不明";
  const imageUrl = attachImage ? null : buildEewImageUrl(kind, data.id);

  if (cancelled) {
    return `${title}\n\nこの地震に関する緊急地震速報は取り消されました。\n\n#地震速報`;
  }

  const lines = [
    title,
    "",
    data.hypocenter_name || "震源不明",
    `発生時刻: ${originText}`,
    `マグニチュード: ${magText}`,
    `震源の深さ: ${depthText}`,
    `${isEew ? "最大予測震度" : "最大震度"}: ${mfmColored(data.max_scale_text || "不明", data.max_scale_color, true)}`,
    "",
    isEew ? "【地域ごとの予測震度】" : "【都道府県ごとの震度】",
    areasText,
  ];
  if (imageUrl) lines.push("", imageUrl);
  lines.push("", "#地震速報", "気象庁の発表に基づく速報値です。実際の内容と異なる場合があります。");
  return lines.join("\n");
}

// Web Push通知用の画像（/api/eew/image/:kind/:id.png、routes/eewImage.jsと同じ生成ロジック）を
// HTTPを経由せずサーバー内で直接組み立てる。Misskeyのドライブへアップロードするための
// バイト列が欲しいだけなので、わざわざ自分自身のHTTPエンドポイントを叩く必要はない。
// 元データ（eew_notification_details）が見つからない場合はnullを返す
// （画像なしでもテキストだけは投稿できるよう、呼び出し側でフォールバックする）。
async function buildEewImagePngBuffer(kind, id) {
  const entry = getEewImageSourceData(kind, id);
  if (!entry) return null;
  try {
    const svg = renderEewNoticeSvg(entry.kind, entry.data);
    return await sharp(Buffer.from(svg)).png().toBuffer();
  } catch (err) {
    console.warn("[EEW] Misskey投稿用画像の生成に失敗しました:", err?.message);
    return null;
  }
}

// 生成したPNGをMisskeyのドライブへアップロードし、ノートに添付できるfileIdを返す。
// Misskeyの drive/files/create はmultipart/form-dataでファイル本体を受け取るAPI
// （notes/createにURLを渡す方式は無いため、DiscordのようにURLをそのまま埋め込むだけでは
// 添付画像として表示できない＝実際にアップロードする必要がある）。
// 失敗時はnullを返し、呼び出し側でテキストのみの投稿にフォールバックする。
async function uploadEewImageToMisskey(cfg, buffer, filename) {
  const form = new FormData();
  form.append("i", cfg.accessToken);
  form.append("force", "true"); // 同名ファイルが既にあってもエラーにせず新規アップロードする
  form.append("file", new Blob([buffer], { type: "image/png" }), filename);

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), MISSKEY_NOTE_TIMEOUT_MS);
  try {
    const res = await fetch(`${cfg.instanceUrl}/api/drive/files/create`, {
      method: "POST",
      body: form,
      signal: controller.signal,
    });
    if (!res.ok) {
      const errText = await res.text().catch(() => "");
      console.warn(`[EEW] Misskeyへの画像アップロード失敗: HTTP ${res.status} ${errText.slice(0, 200)}`);
      return null;
    }
    const json = await res.json();
    return json?.id || null;
  } catch (err) {
    console.warn("[EEW] Misskeyへの画像アップロード中にエラーが発生しました:", err?.message);
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// 設定済みの場合のみ、このサービス自身のMisskeyアカウントから1件ノートを投稿する。
// 「震度1以上」の指定どおり、キャンセル報（震度情報自体が無い）は必ず投稿し、それ以外は
// max_scaleがMISSKEY_MIN_SCALE（既定=震度1=10）以上のときだけ投稿する。
// Discord Webhookと違い投稿先はサービス全体で1つなので、ユーザー個別の重複排除は不要。
async function dispatchMisskeyNote(kind, data) {
  const cfg = misskeyConfig();
  if (!cfg) return; // 未設定＝この機能は無効（デフォルトOFF）

  if (typeof data.max_scale === "number" && !data.cancelled && data.max_scale < cfg.minScale) {
    return; // 震度が設定した下限未満なので投稿しない
  }

  // 取消報には元々画像が無いのでアップロードは試みない。
  let fileId = null;
  if (!data.cancelled) {
    const png = await buildEewImagePngBuffer(kind, data.id);
    if (png) {
      fileId = await uploadEewImageToMisskey(cfg, png, `eq-${kind}-${data.id}.png`);
    }
  }

  const text = buildMisskeyEewText(kind, data, !!fileId);
  const body = JSON.stringify({
    i: cfg.accessToken,
    text,
    visibility: cfg.visibility,
    ...(fileId ? { fileIds: [fileId] } : {}),
    ...(cfg.cw ? { cw: cfg.cw } : {}),
  });

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), MISSKEY_NOTE_TIMEOUT_MS);
  try {
    const res = await fetch(`${cfg.instanceUrl}/api/notes/create`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      signal: controller.signal,
    });
    if (!res.ok) {
      const errText = await res.text().catch(() => "");
      console.warn(`[EEW] Misskey投稿失敗: HTTP ${res.status} ${errText.slice(0, 200)}`);
      return;
    }
    console.log("[EEW] Misskeyへ地震情報を投稿しました");
  } catch (err) {
    console.warn("[EEW] Misskey投稿中にエラーが発生しました:", err?.message);
  } finally {
    clearTimeout(timer);
  }
}

// 緊急地震速報のpush通知を、users.notify_on_eew=1 のユーザーへ送る
// （follow/like等の個人間通知と違い、特定の行為者に紐づかない一斉配信のため、
// notifications テーブルへの記録は行わずpush送信のみ行う）。
// prevMaxScale: この地震について、これまでにpush済みの最大震度（未送信ならは -Infinity）。
// ユーザーごとの下限震度(notify_on_eew_min_scale)が (prevMaxScale, data.max_scale] の範囲に
// 入っている人だけを対象にする＝「今回はじめて自分の閾値を超えた」ユーザーのみに送る。
// すでにより低い閾値で通知済みのユーザーへ、続報のたびに重複して送らないための絞り込み。
async function pushEewToSubscribers(data, prevMaxScale = -Infinity) {
  let users;
  try {
    users = db.prepare("SELECT id, notify_on_eew_min_scale AS min_scale FROM users WHERE notify_on_eew = 1").all();
  } catch (err) {
    console.warn("[EEW] push対象ユーザーの取得に失敗しました:", err?.message);
    return;
  }
  if (!users.length) return;

  const targetUserIds = users
    .filter((u) => {
      const th = Number.isFinite(u.min_scale) ? u.min_scale : EEW_PUSH_MIN_SCALE;
      return data.max_scale >= th && th > prevMaxScale;
    })
    .map((u) => u.id);
  if (!targetUserIds.length) return;

  // 通知に添える画像（左上カードを拡大し、影響範囲を都道府県ごとに色分けした地図。
  // NHK等の地震速報テロップを意識したデザイン。詳しくは lib/eewImage.js 参照）。
  // データ自体の記録（rememberEewImageData）はpublishEewResult側で対象ユーザーの有無に
  // 関わらず済ませてある。ここでは実際にpushする画像URLの組み立てだけを行う。
  // PUBLIC_BASE_URL未設定の環境では絶対URLを組み立てられないため、その場合は画像なしで送る。
  const imageUrl = buildEewImageUrl("eew", data.id);

  // 緊急地震速報(556)は「今この瞬間」の警報。Doze/バッテリーセーバー中でも即時配信されるよう
  // urgency: high を指定し、sw.js側でrequireInteraction/強め振動になるよう urgent: true も載せる。
  // TTLは短め（5分）にし、配信できないまま古い予測情報が後から届いて混乱するのを防ぐ
  // （実測震度側に切り替わるまでの短い期間だけ意味を持つ情報のため）。
  const { attempted, sent } = await sendPushToUsers(
    targetUserIds,
    {
      title: "緊急地震速報（警報）",
      body: `${data.hypocenter_name || "震源不明"} 最大予測震度${data.max_scale_text || "不明"}`,
      url: `/?eqDetail=eew:${data.id}`,
      tag: "eew",
      urgent: true,
      ...(imageUrl ? { image: imageUrl } : {}),
    },
    { urgency: "high", ttl: 60 * 5 }
  );
  console.log(
    `[EEW] push通知: 対象${attempted}件中${sent}件へ送信しました（最大予測震度${data.max_scale_text ?? "不明"}、対象ユーザー${targetUserIds.length}人）`
  );
}

// ── 実測震度（code=551/552）処理 ──────────────────────────
// P2P地震情報の実測震度report（551/552）は、issue.typeによって中身が変わる：
//   - ScalePrompt: 観測震度(points)のみ。震源(hypocenter)は含まれない
//   - Destination: 震源(hypocenter)のみ。観測震度(points)は空配列で届く
//   - ScaleAndDestination: 両方含む
// 同じ地震について別々のtypeの報が続けて届くことがあり、例えば「最初にScalePromptが
// 届いて震源不明のまま表示され、後からDestinationで震源が判明しても、その報はpointsが
// 空というだけの理由で捨てられてしまい、震源不明の表示のまま更新されない」というバグが
// あった。これを防ぐため、そのイベント（stateKey。決め方はprocessIntensityItem参照）
// ごとにこれまで届いた観測震度・震源をマージして記憶しておき、新しい報が届くたびに
// 「今分かっている最新の情報」で表示判定する。
const intensityEventState = new Map(); // stateKey -> { points: Map<pref, scale>, areaPoints: Map<"pref\u0001addr", scale>, hypocenter, magnitude, originEpoch }
const INTENSITY_EVENT_STATE_MAX = 50; // 古いイベントから捨てて無限に肥大化しないようにする

function rememberIntensityEventState(stateKey, patch) {
  let state = intensityEventState.get(stateKey);
  if (state) {
    // 簡易LRU: 使ったイベントを一度削除してから入れ直し、Mapの反復順を最近使った順にする
    intensityEventState.delete(stateKey);
  } else {
    state = { points: new Map(), areaPoints: new Map(), hypocenter: null, magnitude: null, originEpoch: null };
  }
  intensityEventState.set(stateKey, state);
  if (intensityEventState.size > INTENSITY_EVENT_STATE_MAX) {
    const oldestKey = intensityEventState.keys().next().value;
    intensityEventState.delete(oldestKey);
  }
  for (const [pref, scale] of patch.points) {
    if (!state.points.has(pref) || scale > state.points.get(pref)) state.points.set(pref, scale);
  }
  // 都道府県ごとの市区町村（観測点）内訳。続報のたびに観測点が増減することがあるため、
  // pref側のマージと同じ考え方で「これまでに届いたどの報でも見えた最大震度」を覚えておく
  // （「詳しい地域も表示してほしい」への対応。都道府県バッジの下に展開表示する）。
  if (patch.areaPoints) {
    for (const [key, scale] of patch.areaPoints) {
      if (!state.areaPoints.has(key) || scale > state.areaPoints.get(key)) state.areaPoints.set(key, scale);
    }
  }
  if (patch.hypocenter) state.hypocenter = patch.hypocenter;
  if (patch.magnitude != null) state.magnitude = patch.magnitude;
  if (patch.originEpoch != null) state.originEpoch = patch.originEpoch;
  return state;
}

function processIntensityItem(item) {
  const code = item.code;
  if (code !== 551 && code !== 552) return;

  // バグ修正: 実データのメッセージ固有IDは `_id`（`id` ではない）。item.id を見ていたため
  // このチェックは常にno-opになっていた。
  const itemId = item._id ?? item.id;
  if (itemId && intensitySeenIds.includes(itemId)) return;
  rememberSeenId(intensitySeenIds, itemId);

  const issue = item.issue || {};
  // ── バグ修正: eventId/serialが両方とも欠けている場合の誤重複判定 ──
  // 551/552にissue.eventIdが入ることは無く、さらに「各地の震度に関する情報」(issue.type=
  // "DetailScale")にはissue.serialも入ってこない（P2P地震情報の実データ・upstreamの
  // jmaxml-seis-parser-goの出力を確認済み）。そのため、これまでは
  // `${eventId ?? ""}/${serial ?? ""}/${code}` が常に同じ文字列（例: "//551"）になり、
  // 「震度速報→震源判明」のように別の（内容の異なる）報が続けて届いても、直前に処理した
  // 報と同じキーだからという理由だけで"重複"とみなされ黙って捨てられてしまっていた
  // （＝震源・発生時刻が判明した詳細な報が破棄され、発生時刻不明・第?報のまま止まる）。
  // eventId/serialのどちらも無い場合は、そのメッセージ固有ID(_id)と本文の内容（震源名・
  // 発生時刻・観測点数・最大震度）から作った簡易フィンガープリントをキーに使うことで、
  // 内容が同じ場合（＝本当に同一メッセージの再送）だけを重複として弾き、内容が異なる
  // 報（＝別の続報・別の地震）は正しく処理されるようにする。
  let key;
  if (issue.eventId || issue.serial) {
    key = `${issue.eventId ?? ""}/${issue.serial ?? ""}/${code}`;
  } else {
    const eq0 = item.earthquake || {};
    const sig = [
      eq0.hypocenter?.name ?? "",
      eq0.time ?? eq0.originTime ?? "",
      Array.isArray(item.points) ? item.points.length : 0,
      Math.max(-1, ...(Array.isArray(item.points) ? item.points.map((p) => p?.scale ?? -1) : [-1])),
      itemId ?? "",
    ].join("|");
    key = `sig:${sig}/${code}`;
  }
  if (key === intensityLastKey) return;
  intensityLastKey = key;

  // 観測点（市区町村単位）は緯度経度を持たないため、都道府県ごとに最大震度だけを集計する
  const prefMax = new Map();
  // 都道府県ごとの市区町村内訳（表示用）。キーは "pref\u0001addr"。
  const areaMax = new Map();
  for (const p of item.points || []) {
    const pref = p?.pref;
    const scale = p?.scale;
    if (!pref || !EEW_PREF_COORDS[pref] || typeof scale !== "number") continue;
    if (!prefMax.has(pref) || scale > prefMax.get(pref)) prefMax.set(pref, scale);

    const addr = typeof p?.addr === "string" && p.addr ? p.addr : null;
    if (!addr) continue;
    const areaKey = `${pref}\u0001${addr}`;
    if (!areaMax.has(areaKey) || scale > areaMax.get(areaKey)) areaMax.set(areaKey, scale);
  }

  // この報自体の震源情報（Destination型のように観測震度を伴わない報でも、ここで拾っておく）。
  // 実測震度側は earthquake.originTime ではなく earthquake.time というフィールド名で発生時刻が
  // 入ってくる（556/EEWとはキー名が異なる）。念のため両対応にしておく。
  const eq = item.earthquake || {};
  const hypocenter = eq.hypocenter && eq.hypocenter.name ? eq.hypocenter : null;
  const reportOriginEpoch = parseJstTimeToEpoch(eq.time ?? eq.originTime);

  // 状態を束ねるキー（stateKey）の決定。
  // P2P地震情報の実データを見ると issue.eventId は551/552には一度も入ってこない
  // （556にしか入らない）ため、issue.eventId頼みだと「震度速報→震源判明→詳細震度」という
  // 一連の続報がすべて別イベント扱いになってしまう。優先順位は次の通り：
  //  1) issue.eventIdがあれば（将来的に付与されるようになった場合や552の一部形式に備えて）それを使う
  //  2) 直近のEEW(556)セッションと同じ地震とみなせれば、そのevent_idを使う
  //     （→ intensityEventStateがEEW側と同じキーで束ねられ、linked_eew_event_idの判定もこれと揃う）
  //  3) 直前の実測震度セッションと同じ地震とみなせれば、それを継続する
  //  4) どれにも当てはまらなければ、新しい地震として新規のキーを発行する
  let linkedEewEventId = null;
  let stateKey = issue.eventId != null && issue.eventId !== "" ? `id:${issue.eventId}` : null;
  if (!stateKey) {
    if (isSameQuakeSession(recentEewSession, hypocenter?.name || "", reportOriginEpoch, EEW_INTENSITY_LINK_MAX_AGE_MS)) {
      linkedEewEventId = recentEewSession.eventId;
      stateKey = `eew:${recentEewSession.eventId}`;
    } else if (isSameQuakeSession(recentIntensitySession, hypocenter?.name || "", reportOriginEpoch, 5 * 60 * 1000)) {
      stateKey = recentIntensitySession.key;
    } else {
      stateKey = `intensity:${Date.now()}:${++intensitySyntheticSeq}`;
    }
  } else if (isSameQuakeSession(recentEewSession, hypocenter?.name || "", reportOriginEpoch, EEW_INTENSITY_LINK_MAX_AGE_MS)) {
    // issue.eventIdがある場合でも、EEWと同じ地震と分かるなら紐付けフラグ自体は立てておく
    linkedEewEventId = recentEewSession.eventId;
  }

  const eventState = rememberIntensityEventState(stateKey, {
    points: prefMax,
    areaPoints: areaMax,
    hypocenter,
    // -1はJMA仕様上「震源情報が存在しない場合の値」（publishIntensityResult側のフィルタと同じ基準）。
    // ここで弾いておかないと、震源不明の震度速報（3・4回目相当）の-1がそのままキャッシュされ、
    // 後続の報でmagnitude<0な実測値が来るたびeventState.magnitude ?? nullのフォールバック先として
    // 「M-1.0」が復活してしまう。
    magnitude:
      typeof eq.hypocenter?.magnitude === "number" && eq.hypocenter.magnitude >= 0
        ? eq.hypocenter.magnitude
        : null,
    originEpoch: reportOriginEpoch,
  });

  // このイベントについて今まで分かっている観測震度（pointsを伴う報を一度でも受け取っていれば
  // それ）を使う。まだ一度も無ければ（Destination単体の報が最初に届いた等）、表示しようが
  // ないのでここで終える。
  const mergedPoints = eventState.points;
  if (mergedPoints.size === 0) return;

  // 次の報がこのセッションと同じ地震かどうか判定できるよう、直近の実測震度セッションとして覚えておく
  // （震源名・発生時刻は、ここまでにマージ済みの最新情報＝eventStateのものを使う）。
  recentIntensitySession = {
    key: stateKey,
    hypocenterName: eventState.hypocenter?.name || "",
    originEpoch: typeof eventState.originEpoch === "number" ? eventState.originEpoch : null,
    updatedAtMs: Date.now(),
  };

  publishIntensityResult(item, mergedPoints, eventState, linkedEewEventId, stateKey);
}

function publishIntensityResult(item, prefMax, eventState = null, linkedEewEventId = null, stateKey = null) {
  // 都道府県ごとの市区町村内訳（「詳しい地域も表示してほしい」への対応）。
  // eventState.areaPoints（"pref\u0001addr" -> scale、これまでの続報をマージ済み）を
  // 都道府県ごとにグルーピングし直す。eventStateが無い（呼び出し側の想定外の使い方）場合は
  // 単に内訳無し＝バッジのみの表示になる。
  const areasByPref = new Map(); // pref -> Array<{addr, scale}>
  if (eventState?.areaPoints) {
    for (const [key, scale] of eventState.areaPoints) {
      const sep = key.indexOf("\u0001");
      if (sep < 0) continue;
      const pref = key.slice(0, sep);
      const addr = key.slice(sep + 1);
      if (!areasByPref.has(pref)) areasByPref.set(pref, []);
      areasByPref.get(pref).push({ addr, scale });
    }
  }

  const pointsOut = [...prefMax.entries()]
    .map(([pref, scale]) => {
      const [lat, lon] = EEW_PREF_COORDS[pref];
      const areas = (areasByPref.get(pref) || [])
        .slice()
        .sort((a, b) => b.scale - a.scale)
        .map((a) => ({ addr: a.addr, scale: a.scale, scale_text: eewScaleText(a.scale) }));
      return { pref, lat, lon, scale, scale_text: eewScaleText(scale), color: eewScaleColor(scale), areas };
    })
    .sort((a, b) => b.scale - a.scale);

  const issue = item.issue || {};
  const eq = item.earthquake || {};
  // 震源情報は、この報自体にあればそれを使い、無ければ同じeventIdについて過去に届いた
  // 報（Destination型など）から分かっている震源情報にフォールバックする（詳しくは
  // processIntensityItem / rememberIntensityEventState 参照。震度速報→震源判明の順で
  // 複数の報が届く実運用に合わせて「震源不明」のまま固定されてしまわないようにするため）。
  const hypocenter = (eq.hypocenter && eq.hypocenter.name ? eq.hypocenter : null) || eventState?.hypocenter || {};

  // code=552（震源・震度に関する情報）やDestination型の報には震源の緯度経度・発生時刻が
  // 含まれるが、ScalePrompt型（観測震度のみ）には含まれないため、その場合は上のフォールバック
  // 経由で得られなければ null のままになる（クライアント側は null の場合、S波の到達計算を
  // せず固定時間で非表示にする）。
  let hLat = hypocenter.latitude;
  let hLon = hypocenter.longitude;
  if (typeof hLat !== "number" || typeof hLon !== "number" || hLat <= -180 || hLon <= -180) {
    hLat = null;
    hLon = null;
  }
  // 震源の深さ（km）。クライアント側で走時表(JMA2001)を使ってS波の到達時刻を正確に
  // 計算するために必要（深さによってP波/S波の伝わる速さが変わるため）。
  // P2P地震情報APIの仕様上、「ごく浅い」（具体的な数値なし）は0、震源情報が存在しない
  // 場合は-1になる。0は正当な値なのでnullにしない。表示側（lib/eewImage.js）で
  // 「ごく浅い」という文字列に変換する。
  let hDepth = hypocenter.depth;
  if (typeof hDepth !== "number" || hDepth < 0) hDepth = null;
  // マグニチュード。この報自体に無ければ、上と同じ理由でeventStateのフォールバックを使う。
  // 通知画像（lib/eewImage.js）のフッター表示に使う。
  let magnitude = hypocenter.magnitude;
  if (typeof magnitude !== "number" || magnitude < 0) magnitude = eventState?.magnitude ?? null;
  // 実測震度側は earthquake.time というフィールド名で発生時刻が入ってくる（556の originTime とは異なる）。
  const originEpoch = parseJstTimeToEpoch(eq.time ?? eq.originTime) ?? eventState?.originEpoch ?? null;

  // ── バグ修正: 実測震度(551/552)には issue.serial という項目自体が存在しない ──
  // （556(EEW)のissueだけがeventId/serialを持つ。詳しくは buildIntensityReportLabel の
  // コメント参照）。そのため以前はここで毎回 serial="?" になった上に、それを異常として
  // warnログを出しており、実質常に発火する無意味なノイズになっていた（修正前の
  // 「発生時刻不明・第?報」の"第?報"の直接の原因）。
  // serial自体はデバッグ用に一応残しつつ、表示に使う値は issue.type / issue.correct から
  // 組み立てた report_label に一本化する（クライアント側の表示もこちらを使うよう変更済み）。
  const rawIntensitySerial = issue.serial;
  const serial =
    rawIntensitySerial === undefined || rawIntensitySerial === null || String(rawIntensitySerial).trim() === ""
      ? null
      : String(rawIntensitySerial).trim();
  const issueType = typeof issue.type === "string" ? issue.type : null;
  const issueCorrect = typeof issue.correct === "string" ? issue.correct : null;
  const reportLabel = buildIntensityReportLabel(issueType, issueCorrect, linkedEewEventId);

  const data = {
    id: intensityNextId++,
    // EEW側と同じ考え方で、続報を同一イベントとして扱うための識別子
    event_id: issue.eventId ?? null,
    serial, // 実測震度には本来存在しない項目。デバッグ用に残すのみで表示には使わない
    issue_type: issueType,
    issue_correct: issueCorrect,
    report_label: reportLabel, // クライアント側の「第◯報」表示はこれを使う
    hypocenter_name: hypocenter.name || "",
    hypocenter_lat: hLat,
    hypocenter_lon: hLon,
    hypocenter_depth: hDepth,
    magnitude,
    origin_epoch: originEpoch,
    max_scale: pointsOut[0]?.scale ?? null, // push要否のしきい値判定に使う（表示にはmax_scale_textを使用）
    max_scale_text: pointsOut[0]?.scale_text || "不明",
    points: pointsOut,
    issued_at_ms: Date.now(),
    duration_ms: INTENSITY_DURATION_MS,
    // 556(EEW)→551(実測震度)の橋渡し用。一致する直近のEEWセッションがあれば、その
    // event_id（クライアント側のactiveEewsのキーと同じ形式）を積む。クライアント側は
    // これが自分の表示中のEEWカードと一致すれば、波紋アニメーションは継続させたまま
    // カードの内容だけをこの実測震度の情報に更新する（無ければ今まで通り、右下の
    // バナー・マーカーとして独立に表示する）。判定自体はprocessIntensityItem側で
    // （stateKeyの決定と共通のisSameQuakeSessionヘルパーで）一度だけ行い、ここでは
    // その結果をそのまま積む（二重に判定すると条件がズレるおそれがあるため）。
    linked_eew_event_id: linkedEewEventId,
  };
  const shouldDisplay = typeof data.max_scale === "number" && data.max_scale >= EEW_MIN_SCALE;
  // 地図左上のEEWカード・地図上のマーカー・実測震度パネルは、これまで通り全員一律の
  // 閾値(EEW_MIN_SCALE)で表示可否を決める。一方、右下の地震発生通知バナーはユーザーごとに
  // 個別の下限震度(notify_on_eew_banner_min_scale)を設定できるようにしたため、この閾値未満でも
  // クライアント側で各自の設定と照らし合わせられるよう、メッセージ自体は常に配信する
  // （以前はここでshouldDisplay=falseの場合に配信自体を止めていたため、バナー側だけ閾値を
  // 下げたいユーザーにも情報が届かなかった）。data.max_scaleを見てクライアント側で
  // 判定できるよう、この閾値判定結果自体もmeets_display_thresholdとして一緒に送る。
  data.meets_display_threshold = shouldDisplay;
  latestIntensity = shouldDisplay ? data : latestIntensity; // 表示閾値未満なら、既存の表示状態は変えない

  // 通知画像・詳細モーダル用データは、push対象ユーザーが1人もいない場合でも常に記録しておく
  // （以前はpushIntensityToSubscribers内、対象ユーザーが1人以上いる場合のみ記録していたため、
  // 通知設定をしている人が誰もいない環境ではデータ自体が残らず、後から「詳細」を開けなかった）。
  if (typeof data.max_scale === "number") rememberEewImageData("intensity", data);

  if (shouldDisplay) {
    console.log(
      `[EEW] 📊 各地の震度情報（${data.report_label}）を受信しました: 最大震度${data.max_scale_text}（${pointsOut.length}都道府県）`
    );
  } else {
    console.log(
      `[EEW] （表示閾値未満のためEEWカード・マーカーは非表示。バナーはユーザー個別設定次第）各地の震度情報（${data.report_label}）: 最大震度${data.max_scale_text}（${pointsOut.length}都道府県）`
    );
  }
  broadcast("intensity", data);

  // ── push通知（Web Push） ──
  // 予測（EEW/556）と違い、こちらは実測に基づく確定情報（震度速報／震源・震度に関する情報）
  // なので、続報のたびに新しい情報として毎回pushしてよい……はずだったが、実際には
  // ScalePrompt（観測震度）→Destination（震源判明。pointsは空）→DetailScale（各地の震度）の
  // ように、震度の値自体は変わらないまま複数タイプの続報が届くことがあり、その都度pushすると
  // 同じ地震について中身がほぼ同じ通知が何個も届いてしまっていた。以前は「消えても気にならない」
  // 程度の実害だったが、urgent:true（requireInteraction）にした今は、震度が変わっていないのに
  // 通知だけ画面に積み上がり手動で消すまで残り続けるという実害になる。そのため、EEW側と同じく
  // 「このセッションでこれまでにpush済みの最大震度を上回った時だけ」再通知する間引きを入れる
  // （呼び出し自体はevent_id+serial+codeで重複排除済みだが、それは「新しい報かどうか」の判定で
  // あって「震度が変わったかどうか」の判定ではないため、これとは別に必要）。
  // 表示閾値(shouldDisplay/EEW_MIN_SCALE)とは切り離し、ユーザーごとの下限震度
  // (users.notify_on_eew_min_scale)で個別に判定する。
  // Discord Webhookは「同じ端末に何度も通知が鳴る」煩わしさが無く、経過を追う用途なので、
  // これまで通り間引かず続報のたびに送る。
  if (typeof data.max_scale === "number") {
    const prevPushedScale = intensityPushedMaxScaleBySession.has(stateKey)
      ? intensityPushedMaxScaleBySession.get(stateKey)
      : -Infinity;
    if (data.max_scale > prevPushedScale) {
      rememberIntensityPushedMaxScale(stateKey, data.max_scale);
      pushIntensityToSubscribers(data).catch((err) =>
        console.warn("[EEW] 実測震度push通知の送信中にエラーが発生しました:", err?.message)
      );
    }
    dispatchDiscordWebhooks("intensity", data).catch((err) =>
      console.warn("[EEW] 実測震度Discord Webhook送信中にエラーが発生しました:", err?.message)
    );
    dispatchMisskeyNote("intensity", data).catch((err) =>
      console.warn("[EEW] 実測震度Misskey投稿中にエラーが発生しました:", err?.message)
    );
  }
}

// 実測震度（確定情報）のpush通知を、users.notify_on_eew=1 かつ本人の下限震度
// (notify_on_eew_min_scale)以下のユーザーへ送る。続報のたびに毎回この判定を行うため、
// EEW側のような「これまでにpush済みの最大震度」の記憶は不要（間引きをしない設計のため）。
async function pushIntensityToSubscribers(data) {
  let users;
  try {
    users = db.prepare("SELECT id, notify_on_eew_min_scale AS min_scale FROM users WHERE notify_on_eew = 1").all();
  } catch (err) {
    console.warn("[EEW] 実測震度push対象ユーザーの取得に失敗しました:", err?.message);
    return;
  }
  if (!users.length) return;

  const userIds = users
    .filter((u) => {
      const th = Number.isFinite(u.min_scale) ? u.min_scale : EEW_PUSH_MIN_SCALE;
      return data.max_scale >= th;
    })
    .map((u) => u.id);
  if (!userIds.length) return;

  const topPref = data.points?.[0]?.pref;
  const bodyPrefix = topPref ? `${topPref}で ` : "";

  // EEW側と同じ考え方で、都道府県ごとの震度を色分けした画像を通知に添える。
  // データ自体の記録（rememberEewImageData）はpublishIntensityResult側で対象ユーザーの
  // 有無に関わらず済ませてある。ここでは実際にpushする画像URLの組み立てだけを行う。
  const imageUrl = buildEewImageUrl("intensity", data.id);

  // 実測震度（確定情報）もEEW同様に人命に関わる情報なので urgency: high にする。
  // ただしこちらは「今この瞬間」の予測ではなく確定した観測結果なので、EEWほど短命ではなく
  // TTLは長め（30分）にしておく（配信が遅れても内容の意味が薄れにくいため）。
  const { attempted, sent } = await sendPushToUsers(
    userIds,
    {
      title: "地震情報（観測）",
      body: `${bodyPrefix}最大震度${data.max_scale_text || "不明"}を観測（${data.hypocenter_name || "震源不明"}）`,
      url: `/?eqDetail=intensity:${data.id}`,
      tag: "eew-intensity",
      urgent: true,
      ...(imageUrl ? { image: imageUrl } : {}),
    },
    { urgency: "high", ttl: 60 * 30 }
  );
  console.log(
    `[EEW] push通知: 対象${attempted}件中${sent}件へ実測震度（最大震度${data.max_scale_text ?? "不明"}、対象ユーザー${userIds.length}人）を送信しました`
  );
}

// ── 画像付きpush通知の動作確認用テスト送信 ──
// routes/push.js の POST /api/push/test-eew-image から呼ばれる。実際の地震を待たずに、
// 架空のサンプルデータ（関東地方で最大震度6弱、を想定）で画像URLを発行し、
// 呼び出したユーザー本人の端末にだけpushする（他のユーザーには一切送らない）。
// notify_on_eewのON/OFF設定は見ない（本人が明示的にテストボタンを押した操作なので）。
export async function sendTestEewImagePush(userId, kind = "eew", fallbackBase = null) {
  const nowSec = Math.floor(Date.now() / 1000);
  const sampleAreas = [
    { pref: "東京都", scale_to: 55, scale_text: "6弱", color: "#ff2020" },
    { pref: "神奈川県", scale_to: 50, scale_text: "5強", color: "#ff6300" },
    { pref: "埼玉県", scale_to: 45, scale_text: "5弱", color: "#ff9a00" },
    { pref: "千葉県", scale_to: 45, scale_text: "5弱", color: "#ff9a00" },
    { pref: "茨城県", scale_to: 40, scale_text: "4", color: "#f2cf00" },
  ];

  let data;
  if (kind === "intensity") {
    data = {
      id: intensityNextId++,
      event_id: null,
      serial: "テスト",
      report_label: "テスト配信",
      hypocenter_name: "東京湾（テスト）",
      hypocenter_lat: 35.5,
      hypocenter_lon: 139.9,
      hypocenter_depth: 30,
      magnitude: 6.5,
      origin_epoch: nowSec,
      max_scale: 55,
      max_scale_text: "6弱",
      points: sampleAreas.map((a) => ({ pref: a.pref, scale: a.scale_to, scale_text: a.scale_text, color: a.color })),
      issued_at_ms: Date.now(),
      duration_ms: INTENSITY_DURATION_MS,
    };
  } else {
    data = {
      id: eewNextId++,
      event_id: null,
      test: true,
      cancelled: false,
      serial: "テスト",
      hypocenter_name: "東京湾（テスト）",
      hypocenter_lat: 35.5,
      hypocenter_lon: 139.9,
      magnitude: 6.5,
      depth: 30,
      max_scale: 55,
      max_scale_text: "6弱",
      max_scale_color: eewScaleColor(55),
      origin_epoch: nowSec,
      areas: sampleAreas,
      duration_ms: EEW_CARD_DURATION_MS,
      issued_at_ms: Date.now(),
    };
  }

  rememberEewImageData(kind, data, { persist: false });
  const imageUrl = buildEewImageUrl(kind, data.id, fallbackBase);

  const title = kind === "intensity" ? "【テスト】地震情報（観測）" : "【テスト】緊急地震速報（警報）";
  const body =
    kind === "intensity"
      ? `${data.hypocenter_name} 最大震度${data.max_scale_text}を観測（テスト配信）`
      : `${data.hypocenter_name} 最大予測震度${data.max_scale_text}（テスト配信）`;

  // テスト送信も本番のEEW/実測震度と同じ見た目（requireInteraction・強め振動）を
  // 確認できるよう、urgency: high / urgent: true を本番と揃えておく。
  const result = await sendPushToUser(
    userId,
    {
      title,
      body,
      url: `/?eqDetail=${kind}:${data.id}`,
      tag: kind === "intensity" ? "eew-intensity-test" : "eew-test",
      urgent: true,
      ...(imageUrl ? { image: imageUrl } : {}),
    },
    { urgency: "high" }
  );

  return { ...result, imageUrl };
}
import express from "express";
import sharp from "sharp";
import {
  getEewImageSourceData,
  getLatestEewForCatchUp,
  getLatestIntensityForCatchUp,
  listEewHistory,
  getEewHistoryDetail,
  eewScaleText,
  isValidEewScale,
  EEW_SELECTABLE_SCALES,
} from "../lib/eew.js";
import { renderEewNoticeSvg } from "../lib/eewImage.js";
import db from "../db.js";
import { requireAuth } from "../middleware/authMiddleware.js";

const router = express.Router();

// 設定できるDiscord Webhook URLの形式（discord.com / discordapp.com の
// /api/webhooks/<id>/<token>のみ許可。SSRFや任意URLへの送信先変更を防ぐバリデーション）。
const DISCORD_WEBHOOK_URL_RE =
  /^https:\/\/(?:ptb\.|canary\.)?(?:discord|discordapp)\.com\/api\/webhooks\/\d+\/[\w-]+\/?$/;

// DBの列既定値（45=5弱）と揃えている。列側のDEFAULTはCREATE TABLE時にしか効かないため、
// アプリ側（レスポンスの補完・リセット時）でも同じ値をここに持っておく。
const DISCORD_WEBHOOK_DEFAULT_MIN_SCALE = 45;

// 表示用にWebhook URLの一部だけ見せる（トークン部分はチャットログ等に絶対に出さない）。
// 例: https://discord.com/api/webhooks/123456789012345678/AbCdEfGh...
//  → https://discord.com/api/webhooks/123456789012345678/••••
function maskDiscordWebhookUrl(url) {
  if (!url) return null;
  const m = url.match(/^(https:\/\/[^/]+\/api\/webhooks\/\d+)\//);
  return m ? `${m[1]}/••••` : "設定済み";
}

// Web Push通知のimageに指定されるURL。ブラウザが通知を表示するタイミングで
// このURLへGETしにくる（≒push受信直後、非同期）。認証不要（通知は本人の端末からしか
// 開かないうえ、内容自体は「どこかで地震があった」という公開情報のため）。
// :kind は 'eew'（予測・556）または 'intensity'（実測震度・551/552）。
//
// 画像は内部的にSVGで組み立てた後、sharpでPNGにラスタライズして返す。
// Web Push通知のimageはOS/ブラウザによってSVGを受け付けない場合がある
// （参考: https://pushpad.xyz/blog/why-the-image-is-not-displayed-in-the-web-push-notification ）ため、
// 実績のあるPNGで統一している。なお、macOSはChrome 59以降ネイティブの通知センターに
// 移行しており、OS側の制約でimage自体が表示されない（アイコンのみになる）。これはサーバー側の
// 対応では回避できない既知の制限（参考: https://developer.chrome.com/blog/native-mac-os-notifications ）。
router.get("/image/:kind/:id.png", async (req, res) => {
  const { kind, id } = req.params;
  if (kind !== "eew" && kind !== "intensity") {
    return res.status(400).json({ error: "kindはeewまたはintensityを指定してください" });
  }
  const entry = getEewImageSourceData(kind, id);
  if (!entry) {
    // 生成元データが見つからない（キャッシュ期限切れ・不正なID等）。
    // 通知の画像取得が失敗するだけで、通知自体（title/body）は問題なく表示される。
    return res.status(404).json({ error: "画像の元データが見つかりません（期限切れの可能性があります）" });
  }
  try {
    const svg = renderEewNoticeSvg(entry.kind, entry.data);
    const png = await sharp(Buffer.from(svg)).png().toBuffer();
    res.set("Content-Type", "image/png");
    // 内容は同一id・同一データなので長めにキャッシュしてよい（ただし作り直しが効くようimmutableにはしない）。
    res.set("Cache-Control", "public, max-age=3600");
    res.send(png);
  } catch (err) {
    console.error("[EEW] 通知画像の生成に失敗しました:", err);
    res.status(500).json({ error: "画像の生成に失敗しました" });
  }
});

// push通知（EEW/実測震度）をクリックして開いた際、その地震の詳細をモーダルで表示するための
// JSON版。画像と同じデータ（lib/eew.js の getEewImageSourceData、DB永続化済み）を返す。
// 認証不要（画像ルートと同じ理由）。
router.get("/detail/:kind/:id", (req, res) => {
  const { kind, id } = req.params;
  if (kind !== "eew" && kind !== "intensity") {
    return res.status(400).json({ error: "kindはeewまたはintensityを指定してください" });
  }
  const entry = getEewImageSourceData(kind, id);
  if (!entry) {
    return res.status(404).json({ error: "詳細データが見つかりません" });
  }
  res.json({ kind: entry.kind, data: entry.data });
});

// 過去の地震履歴一覧（新規: 「過去の地震履歴を見れて詳細が見れる機能」）。
// eew_notification_details にDB永続化されている分（TTL無し・件数上限のみで間引き）を
// そのまま一覧化する。1件クリックした後の詳細取得は既存の /detail/:kind/:id をそのまま使う
// （このエンドポイントでは要約フィールドのみ返す）。認証不要（他のEEWルートと同じ理由。
// 地震があったこと自体・その震度は公開情報のため）。
// クエリ:
//   kind: 'all'（既定）| 'eew' | 'intensity'
//   limit: 1件あたりの取得件数（既定30, 最大100）
//   before: ページング用カーソル（前回レスポンスのnextCursorをそのまま渡す。省略時は最新から）
router.get("/history", (req, res) => {
  const { kind = "all", limit, before } = req.query;
  if (kind !== "all" && kind !== "eew" && kind !== "intensity") {
    return res.status(400).json({ error: "kindはall・eew・intensityのいずれかを指定してください" });
  }
  const result = listEewHistory({ kind, limit, beforeCursor: before });
  res.json(result);
});

// 履歴詳細モーダルに表示する画像。push通知添付画像（/image/:kind/:id.png、local_id・
// eewImageDataCache/eew_notification_details依存）とはid体系が別なので流用できないため、
// こちらは毎回その場でSVG→PNGにレンダリングする（DBやファイルへの保存はしない。
// buildHistoryDetailFromRawRowが作るdataはrenderEewNoticeSvgが期待する形と同じ
// フィールド構成＝publishEewResult/publishIntensityResultと揃えてあるため、そのまま渡せる）。
// kind='detected'（554）は震源等の詳細を持たず地図に描く材料が無いため画像自体を提供しない。
//
// ── 注意: このルートは必ず下の /history/:id より前に定義すること ──
// Express（path-to-regexp）の:idはデフォルトで「/」以外の任意の文字（.も含む）にマッチする。
// そのため /history/:id を先に定義すると、"/history/297.png" もそちらにid="297.png"として
// マッチしてしまい（Number("297.png")はNaNになりgetEewHistoryDetailが404を返す）、
// 下の.png専用ルートには一生到達しない。実際にこの順序ミスで画像が404になっていた。
router.get("/history/:id.png", async (req, res) => {
  const entry = getEewHistoryDetail(req.params.id);
  if (!entry || entry.kind === "detected") {
    return res.status(404).json({ error: "画像の元データが見つかりません" });
  }
  try {
    const svg = renderEewNoticeSvg(entry.kind, entry.data);
    const png = await sharp(Buffer.from(svg)).png().toBuffer();
    res.set("Content-Type", "image/png");
    res.set("Cache-Control", "public, max-age=3600");
    res.send(png);
  } catch (err) {
    console.error("[EEW] 履歴画像の生成に失敗しました:", err);
    res.status(500).json({ error: "画像の生成に失敗しました" });
  }
});

// 履歴一覧の1件をクリックした際の詳細（データソースはeew_raw_log。上の/historyと同じ経路）。
// idは/historyが返すitem.id（=eew_raw_log.id、テーブル全体で一意）をそのまま渡す。
// push通知クリック用の /detail/:kind/:id（eew_notification_details・kind+local_id）とは
// 別のID体系・別のエンドポイントなので混同しないこと。
// kind='detected'（554＝EEW発表検出のみ、震源等の詳細を持たない）の場合は
// data.hypocenter_name等が無い最小限の内容になる。
router.get("/history/:id", (req, res) => {
  const entry = getEewHistoryDetail(req.params.id);
  if (!entry) {
    return res.status(404).json({ error: "履歴データが見つかりません" });
  }
  res.json(entry);
});

// アプリ起動時（地震発生"後"に初めてアプリを開いた場合を含む）に、直近のEEW/実測震度が
// あれば追いつけるようにするための軽量エンドポイント。WSの"hello"は新規訪問者に古い地震を
// 見せないためあえて何も送らない設計だが、それとは別に「直近（EEW_CATCHUP_MAX_AGE_MS以内）
// に発表されたもの」だけはここで拾えるようにする（lib/eew.js側で古ければnullを返す）。
// 認証不要（画像・詳細ルートと同じ理由。地震があったこと自体は公開情報のため）。
router.get("/latest", (_req, res) => {
  res.json({ eew: getLatestEewForCatchUp(), intensity: getLatestIntensityForCatchUp() });
});

// ── Discord Webhook設定（地震情報をDiscordチャンネルへ転送する） ──
// 1アカウントにつき1つだけ（users.discord_webhook_url、単一列で管理）。
// 本人のみ参照・変更できるよう認証必須（他のEEWルートと違い、Webhook URLはトークンを
// 含む機微情報のため、公開情報である地震情報そのものとは扱いを分ける）。

// 現在の設定状況を返す。URLはトークンを含むため、生の値は返さずマスクした表示用文字列のみ返す。
// min_scale・min_scale_text・selectable_scalesはフロント側の下限震度セレクトボックス用。
router.get("/webhook", requireAuth, (req, res) => {
  try {
    const row = db
      .prepare("SELECT discord_webhook_url AS url, discord_webhook_min_scale AS min_scale FROM users WHERE id = ?")
      .get(req.userId);
    const url = row?.url || null;
    res.json({
      configured: !!url,
      url_preview: maskDiscordWebhookUrl(url),
      min_scale: row?.min_scale ?? DISCORD_WEBHOOK_DEFAULT_MIN_SCALE,
      min_scale_text: eewScaleText(row?.min_scale ?? DISCORD_WEBHOOK_DEFAULT_MIN_SCALE),
      selectable_scales: EEW_SELECTABLE_SCALES.map((s) => ({ value: s, text: eewScaleText(s) })),
    });
  } catch (err) {
    console.error("[EEW] Discord Webhook設定の取得に失敗しました:", err);
    res.status(500).json({ error: "設定の取得に失敗しました" });
  }
});

// 設定・上書き。既に設定済みの場合は新しいURLで置き換える（1アカウント1件なのでUPSERT不要）。
// min_scaleは任意。指定が無ければ新規設定時は既定値、既に設定済みなら現在値を維持する。
router.put("/webhook", requireAuth, (req, res) => {
  const url = typeof req.body?.url === "string" ? req.body.url.trim() : "";
  if (!url) {
    return res.status(400).json({ error: "urlを指定してください" });
  }
  if (!DISCORD_WEBHOOK_URL_RE.test(url)) {
    return res.status(400).json({
      error: "DiscordのWebhook URL（https://discord.com/api/webhooks/<id>/<token>）を指定してください",
    });
  }
  let minScale;
  if (req.body?.min_scale !== undefined && req.body?.min_scale !== null && req.body?.min_scale !== "") {
    if (!isValidEewScale(req.body.min_scale)) {
      return res.status(400).json({ error: "min_scaleが不正です（震度1〜7に対応する値を指定してください）" });
    }
    minScale = Math.round(Number(req.body.min_scale));
  }
  try {
    if (minScale === undefined) {
      db.prepare("UPDATE users SET discord_webhook_url = ? WHERE id = ?").run(url, req.userId);
    } else {
      db.prepare("UPDATE users SET discord_webhook_url = ?, discord_webhook_min_scale = ? WHERE id = ?").run(
        url,
        minScale,
        req.userId
      );
    }
    const row = db
      .prepare("SELECT discord_webhook_min_scale AS min_scale FROM users WHERE id = ?")
      .get(req.userId);
    res.json({
      configured: true,
      url_preview: maskDiscordWebhookUrl(url),
      min_scale: row?.min_scale ?? DISCORD_WEBHOOK_DEFAULT_MIN_SCALE,
      min_scale_text: eewScaleText(row?.min_scale ?? DISCORD_WEBHOOK_DEFAULT_MIN_SCALE),
    });
  } catch (err) {
    console.error("[EEW] Discord Webhook設定の保存に失敗しました:", err);
    res.status(500).json({ error: "設定の保存に失敗しました" });
  }
});

// 下限震度だけを変更する（URLはそのまま）。URL未設定の状態で叩かれても意味が無いので400にする。
router.patch("/webhook/min-scale", requireAuth, (req, res) => {
  if (!isValidEewScale(req.body?.min_scale)) {
    return res.status(400).json({ error: "min_scaleが不正です（震度1〜7に対応する値を指定してください）" });
  }
  const minScale = Math.round(Number(req.body.min_scale));
  try {
    const current = db.prepare("SELECT discord_webhook_url AS url FROM users WHERE id = ?").get(req.userId);
    if (!current?.url) {
      return res.status(400).json({ error: "先にDiscord Webhook URLを設定してください" });
    }
    db.prepare("UPDATE users SET discord_webhook_min_scale = ? WHERE id = ?").run(minScale, req.userId);
    res.json({ min_scale: minScale, min_scale_text: eewScaleText(minScale) });
  } catch (err) {
    console.error("[EEW] Discord Webhook下限震度の更新に失敗しました:", err);
    res.status(500).json({ error: "設定の更新に失敗しました" });
  }
});

// 削除（地震情報の転送を停止する）。下限震度の設定は次回設定時に既定値へ戻す
// （URLと紐づく設定という位置づけのため、URL削除時にリセットする）。
router.delete("/webhook", requireAuth, (req, res) => {
  try {
    db.prepare(
      "UPDATE users SET discord_webhook_url = NULL, discord_webhook_min_scale = ? WHERE id = ?"
    ).run(DISCORD_WEBHOOK_DEFAULT_MIN_SCALE, req.userId);
    res.json({ configured: false, url_preview: null });
  } catch (err) {
    console.error("[EEW] Discord Webhook設定の削除に失敗しました:", err);
    res.status(500).json({ error: "設定の削除に失敗しました" });
  }
});

export default router;
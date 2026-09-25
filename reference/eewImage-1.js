// ══════════════════════════════════════════════
// 地震通知（EEW/実測震度）用のプレビュー画像生成
//   Web Push通知の image に使う画像を、都道府県ポリゴンを塗り分けたSVGとして
//   その場で（DBやファイルへの保存はせず）動的に生成する。
//   「NHK等の地震速報テロップ」を意識し、画面右のブラウザ左上カードと同じ情報
//   （震源名・最大震度バッジ・M・深さ）を上部の帯パネルに、影響範囲を都道府県ごとの
//   色分けとして地図全体に重ねて表示する構成にしている。
//
//   都道府県の輪郭データは lib/data/japanPrefectures.json
//   （国土数値情報ベースの行政界データを、サムネイル用途に大幅間引き・最大の島のみ抽出して
//   軽量化したもの。詳細な海岸線の正確さより「ぱっと見て日本地図と分かる」ことを優先）。
//
//   画像形式はPNGではなくSVG（Content-Type: image/svg+xml）で返す。
//   ラスタライズ用のネイティブ依存（sharp/canvas等）を増やしたくないための選択。
//   主要ブラウザ（Chrome/Edge）はWeb Push通知のimageにSVGを指定しても表示できるが、
//   環境によってはSVGでの表示に対応しない場合もあるので、その場合は本ファイルの
//   renderEewNoticeSvg() の出力を sharp 等でPNG化する処理を呼び出し側に足してほしい。
// ══════════════════════════════════════════════

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// [{ name: '北海道', ring: [[lon,lat], ...] }, ...] × 47都道府県
const PREFS = JSON.parse(
  fs.readFileSync(path.join(__dirname, "data", "japanPrefectures.json"), "utf8")
);

// [{ name: '熊本市中央区', pref: '熊本県', code: '43101', rings: [[[lon,lat],...], ...] }, ...]
// 国土数値情報 行政区域データ（N03、政令指定都市の区まで含む）を元に、地震情報のaddr表記
// （例:「熊本市中央区」「益城町」）と一致する名前・都道府県ごとの実際の境界ポリゴンを持つ。
// 都道府県内で震度にばらつきがある場合、この実際の形で塗り分けるために使う
// （「実際の市区町村の位置に対応した塗り分けをしてほしい」への対応。以前試した「件数に応じた
// 帯」による簡易表現はやめて、本物の境界データに置き換えた）。
const MUNIS = JSON.parse(
  fs.readFileSync(path.join(__dirname, "data", "japanMunicipalities.json"), "utf8")
);
// pref名 → (市区町村名 → rings) のMap。同じ市区町村名が違う都道府県にもあり得るため
// 都道府県で一段絞り込んでから引く。
const MUNIS_BY_PREF = new Map();
for (const m of MUNIS) {
  if (!MUNIS_BY_PREF.has(m.pref)) MUNIS_BY_PREF.set(m.pref, new Map());
  // ごく稀に同名重複がある（データの境界誤差等）。先勝ちでよい。
  const byName = MUNIS_BY_PREF.get(m.pref);
  if (!byName.has(m.name)) byName.set(m.name, m.rings);
}
// 政令指定都市の区は「〇〇市△△区」だが、地震情報側のaddr/地域名は「市」を省いた
// 「〇〇△△区」表記で来ることが多い（例:「熊本西区春日」）。マッチできるよう別名でも登録しておく。
for (const byName of MUNIS_BY_PREF.values()) {
  for (const name of [...byName.keys()]) {
    const m = name.match(/^(.+市)(.+区)$/);
    if (!m) continue;
    const alias = m[1].replace(/市$/, "") + m[2];
    if (!byName.has(alias)) byName.set(alias, byName.get(name));
  }
}

// 実際に来る地震情報のaddr/地域名（例:「宇土市浦田町」「熊本西区春日」「熊本県天草・芦北」）は
// 市区町村そのものの名前ではなく、市区町村名＋町字名（さらに細かい単位）や、複数市区町村を
// まとめた気象庁の地域名（＝地方公共団体の境界とは一致しない）であることがほとんど。
// そのため単純な完全一致（Map#get）ではまず当たらない（実際にapp.dbの実測震度データで検証した
// ところ、完全一致では市区町村の一致率はほぼ0%だった）。ここでは「町字名まで含んだ文字列の
// 先頭が市区町村名と一致するか」で一番長くマッチする市区町村を探す。pref名（フル/末尾の
// 都道府県を除いた短縮形）が頭についている場合はそれを取り除いてから同様に探す。
// なお「天草・芦北」「阿蘇」等、複数市区町村にまたがる広域の地域名はどのみち単一の市区町村と
// 対応しないため、その場合はnullを返す（呼び出し側で都道府県代表色へフォールバックする）。
// マッチした市区町村名も一緒に返す（＝呼び出し側で「別の観測点addrだが同じ市区町村」を
// 束ねて重複描画・上書きを防ぐために必要）。
function findMunicipalityMatch(pref, addr) {
  const byName = MUNIS_BY_PREF.get(pref);
  if (!byName || !addr) return null;
  const tryMatch = (candidate) => {
    let best = null;
    for (const name of byName.keys()) {
      if (candidate.startsWith(name) && (!best || name.length > best.length)) best = name;
    }
    return best;
  };
  let hit = tryMatch(addr);
  if (!hit) {
    const prefShort = pref.replace(/[都道府県]$/, "");
    if (addr.startsWith(pref)) hit = tryMatch(addr.slice(pref.length));
    else if (addr.startsWith(prefShort)) hit = tryMatch(addr.slice(prefShort.length));
  }
  return hit ? { name: hit, rings: byName.get(hit) } : null;
}

// 震度コード → 表示文字列／表示色。eew.js内のテーブルと同じ内容（循環import回避のため複製）。
const SCALE_TEXT = {
  "-1": "不明", "0": "0", "10": "1", "20": "2", "30": "3", "40": "4",
  "45": "5弱", "50": "5強", "55": "6弱", "60": "6強", "70": "7", "99": "5強以上",
};
const SCALE_COLOR = {
  "10": "#8c8c8c", "20": "#4c8bff", "30": "#2ca02c", "40": "#f2cf00",
  "45": "#ff9a00", "50": "#ff6300", "55": "#ff2020", "60": "#c2001f", "70": "#a800a8", "99": "#ff6300",
};
function scaleText(v) {
  if (v === null || v === undefined) return "不明";
  const n = Math.round(Number(v));
  return SCALE_TEXT[String(n)] ?? "不明";
}
function scaleColor(v) {
  const n = Math.round(Number(v));
  return SCALE_COLOR[String(n)] || "#6b7280";
}
// 文字色は背景色の明るさに応じて黒/白を切り替える（EEW_SCALE系の色は暗色が多いため白固定で概ね問題ないが、
// 40=黄色（#f2cf00）だけ明るいので黒文字にする）。
function scaleTextColor(v) {
  const n = Math.round(Number(v));
  return n === 40 ? "#1a1a1a" : "#ffffff";
}

// NHKの地震速報画面のような、小さな四角の中に震度を短く収めるための表記。
// index.html側の EEW_SCALE_SHORT_LABEL / eewScaleTextColor と同じ内容（都道府県バッジ用）。
const SCALE_SHORT_LABEL = { 10: "1", 20: "2", 30: "3", 40: "4", 45: "5-", 50: "5+", 55: "6-", 60: "6+", 70: "7", 99: "5+" };
function scaleShortLabel(v, fallback) {
  const n = Math.round(Number(v));
  return SCALE_SHORT_LABEL[n] ?? (fallback || "?");
}
// バッジの文字色。震度5弱(45)以上は背景色が濃くなるため白文字、それ未満は黒文字にする
// （index.htmlのマーカーと合わせている。ヘッダーの大きな震度バッジとはルールが異なる点に注意）。
function badgeTextColorForScale(v) {
  const n = Math.round(Number(v));
  return n >= 45 ? "#ffffff" : "#101820";
}

const CANVAS_W = 640;
const CANVAS_H = 480;
const MAP_PAD = 26;

// 全都道府県ポリゴンのバウンディングボックス（モジュール読み込み時に1回だけ計算）。
let BBOX = null;
function getBbox() {
  if (BBOX) return BBOX;
  let minLon = Infinity, maxLon = -Infinity, minLat = Infinity, maxLat = -Infinity;
  for (const f of PREFS) {
    for (const ring of getPrefRings(f)) {
      for (const [lon, lat] of ring) {
        if (lon < minLon) minLon = lon;
        if (lon > maxLon) maxLon = lon;
        if (lat < minLat) minLat = lat;
        if (lat > maxLat) maxLat = lat;
      }
    }
  }
  BBOX = { minLon, maxLon, minLat, maxLat };
  return BBOX;
}

// 経度1度あたりの物理的な横幅は緯度によって変わる（cos(緯度)倍）ため、それを加味した
// 簡易的な正距円筒図法もどきで投影する。全国規模のサムネイル用途としては十分な精度。
//
// focusBbox を渡すと、全国ではなくその範囲（影響のあった都道府県＋震源）にズームする。
// 「NHKの地震速報で見るような、影響範囲を拡大した地図」という要望に応えるための挙動。
function makeProjector(focusBbox) {
  const { minLon, maxLon, minLat, maxLat } = focusBbox || getBbox();
  const avgLatRad = ((minLat + maxLat) / 2) * (Math.PI / 180);
  const lonScale = Math.cos(avgLatRad);
  const lonSpanPhys = (maxLon - minLon) * lonScale;
  const latSpan = maxLat - minLat;
  const drawW = CANVAS_W - MAP_PAD * 2;
  const drawH = CANVAS_H - MAP_PAD * 2;
  const scale = Math.min(drawW / lonSpanPhys, drawH / latSpan);
  const offsetX = MAP_PAD + (drawW - lonSpanPhys * scale) / 2;
  const offsetY = MAP_PAD + (drawH - latSpan * scale) / 2;
  return (lon, lat) => [
    offsetX + (lon - minLon) * lonScale * scale,
    offsetY + (maxLat - lat) * scale,
  ];
}

// 影響のあった都道府県（＋震源）を囲む範囲を計算し、程よい余白を持たせてズームする範囲を決める。
// ・対象都道府県が1つも無い（=points/areasが空）場合は全国表示にフォールバックする。
// ・震源が対象都道府県から離れている場合（内陸で沿岸部だけ揺れた等）も震源が入るようにする。
// ・広範囲（複数地方にまたがる）大地震ではズームしすぎない・逆に1都道府県だけの小さな揺れでは
//   寄りすぎない、の両方を防ぐため、余白は「対象範囲の広さに応じた割合」と「最低限の絶対値」の
//   大きい方を取り、かつ全国表示より広くはならないようクランプする。
function computeFocusBbox(prefNames, hypoLat, hypoLon) {
  const country = getBbox();
  if (!prefNames.length) return country;

  let minLon = Infinity, maxLon = -Infinity, minLat = Infinity, maxLat = -Infinity;
  const nameSet = new Set(prefNames);
  for (const f of PREFS) {
    if (!nameSet.has(f.name)) continue;
    for (const ring of getPrefRings(f)) {
      for (const [lon, lat] of ring) {
        if (lon < minLon) minLon = lon;
        if (lon > maxLon) maxLon = lon;
        if (lat < minLat) minLat = lat;
        if (lat > maxLat) maxLat = lat;
      }
    }
  }
  if (typeof hypoLat === "number" && typeof hypoLon === "number") {
    if (hypoLon < minLon) minLon = hypoLon;
    if (hypoLon > maxLon) maxLon = hypoLon;
    if (hypoLat < minLat) minLat = hypoLat;
    if (hypoLat > maxLat) maxLat = hypoLat;
  }
  if (!Number.isFinite(minLon)) return country; // 該当都道府県が見つからなかった場合の保険

  const lonSpan = maxLon - minLon;
  const latSpan = maxLat - minLat;
  // 余白：対象範囲の40%分、ただし最低0.6度は確保する（1都道府県だけでも隣接県が見える程度に）
  const padLon = Math.max(lonSpan * 0.4, 0.6);
  const padLat = Math.max(latSpan * 0.4, 0.6);
  let fMinLon = minLon - padLon, fMaxLon = maxLon + padLon;
  let fMinLat = minLat - padLat, fMaxLat = maxLat + padLat;

  // 全国表示より広くズームアウトしない（＝この関数は「寄る」ためだけに使う）
  fMinLon = Math.max(fMinLon, country.minLon);
  fMaxLon = Math.min(fMaxLon, country.maxLon);
  fMinLat = Math.max(fMinLat, country.minLat);
  fMaxLat = Math.min(fMaxLat, country.maxLat);

  return { minLon: fMinLon, maxLon: fMaxLon, minLat: fMinLat, maxLat: fMaxLat };
}

// 都道府県ごとのバッジ配置位置（その都道府県ポリゴンのバウンディングボックス中心）。
// 正確な重心ではないが、バッジを置く目安としては十分。モジュール読み込み時に1回だけ計算する。
let PREF_CENTROIDS = null;
function getPrefCentroids() {
  if (PREF_CENTROIDS) return PREF_CENTROIDS;
  PREF_CENTROIDS = new Map();
  for (const f of PREFS) {
    let minLon = Infinity, maxLon = -Infinity, minLat = Infinity, maxLat = -Infinity;
    for (const ring of getPrefRings(f)) {
      for (const [lon, lat] of ring) {
        if (lon < minLon) minLon = lon;
        if (lon > maxLon) maxLon = lon;
        if (lat < minLat) minLat = lat;
        if (lat > maxLat) maxLat = lat;
      }
    }
    PREF_CENTROIDS.set(f.name, [(minLon + maxLon) / 2, (minLat + maxLat) / 2]);
  }
  return PREF_CENTROIDS;
}

function escXml(s) {
  return String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

// data.areas（EEW/556。{pref,scale_text,color}の配列）または data.points（実測震度/551,552。
// {pref,scale,scale_text,color,areas}の配列。areasは市区町村内訳。lib/eew.jsのpublishIntensityResult/
// buildHistoryDetailFromRawRowが都道府県ごとに集計する際、一緒に積んでいる）から、
// 都道府県名 → {scaleText,color,areas} のMapを作る。
function buildPrefColorMap(kind, data) {
  const map = new Map();
  if (kind === "eew") {
    // EEW(556)のdata.areasは都道府県ごとに1件とは限らない。同じ都道府県が複数の地域
    // （例:「熊本県熊本」「熊本県天草・芦北」「熊本県球磨」）に分かれて複数件出てくることがあり、
    // それぞれ震度が異なる場合がある。
    // 以前はここで震度が最大の1件だけを残し、残りを握りつぶしていたため、同じ都道府県内で
    // 予測震度にばらつきがあっても常に「県全体が最大震度」であるかのように塗られてしまっていた
    // （実際にapp.dbの実データで、熊本県内に45と40が混在する報でも常に45一色になるバグを確認した）。
    // まず同じ都道府県のエントリを全て集約し、代表色（＝最大震度）とばらつき判定・描き分け用の
    // 一覧（areas）の両方を保持するようにする。
    const byPref = new Map(); // pref -> [{addr, scale, scale_text, color}, ...]
    for (const a of data.areas || []) {
      if (!a.pref) continue;
      if (!byPref.has(a.pref)) byPref.set(a.pref, []);
      byPref.get(a.pref).push({ addr: a.name || a.pref, scale: a.scale_to, scale_text: a.scale_text, color: a.color });
    }
    for (const [pref, areas] of byPref) {
      let best = areas[0];
      let bestRank = SCALE_ORDER.indexOf(String(Math.round(best.scale ?? -1)));
      for (const ar of areas) {
        const rank = SCALE_ORDER.indexOf(String(Math.round(ar.scale ?? -1)));
        if (rank > bestRank) {
          best = ar;
          bestRank = rank;
        }
      }
      map.set(pref, { text: best.scale_text, color: best.color, rank: bestRank, areas });
    }
    return map;
  }
  // kind === "intensity"（551/552）。data.pointsは都道府県ごとに集計済みの1件で、
  // その中のareasが市区町村（観測点）単位の内訳を持つ。
  for (const a of data.points || []) {
    if (!a.pref) continue;
    const rank = SCALE_ORDER.indexOf(String(Math.round(a.scale ?? -1)));
    map.set(a.pref, { text: a.scale_text, color: a.color, rank, areas: Array.isArray(a.areas) ? a.areas : null });
  }
  return map;
}
const SCALE_ORDER = ["-1", "0", "10", "20", "30", "40", "45", "50", "55", "60", "70", "99"];

// PREFS の1件（都道府県）から、そのポリゴンの外周リング配列を取り出す。
// 単一の島で構成される都道府県は { ring: [[lon,lat],...] }（従来の形）だが、
// 沖縄・長崎・鹿児島・東京都のように離島を多く抱える都道府県は複数リング
// { rings: [[[lon,lat],...], ...] } で持たれている場合があるため、両対応にする
// （このガードが無いと、ringが無い都道府県で "f.ring is not iterable" になる）。
function getPrefRings(f) {
  if (Array.isArray(f?.rings)) return f.rings;
  if (Array.isArray(f?.ring)) return [f.ring];
  return [];
}

// 市区町村ポリゴン（rings）の重心代わりの目安点（バウンディングボックス中心）を求める。
// 小さな震度バッジをその市区町村の位置に置くために使う。
function centroidOfRings(rings) {
  let minLon = Infinity, maxLon = -Infinity, minLat = Infinity, maxLat = -Infinity;
  for (const ring of rings) {
    for (const [lon, lat] of ring) {
      if (lon < minLon) minLon = lon;
      if (lon > maxLon) maxLon = lon;
      if (lat < minLat) minLat = lat;
      if (lat > maxLat) maxLat = lat;
    }
  }
  if (!Number.isFinite(minLon)) return null;
  return [(minLon + maxLon) / 2, (minLat + maxLat) / 2];
}

// 都道府県ポリゴンを塗り分けた地図本体（<g>）を組み立てる
function renderMapLayer(kind, data) {
  const prefColors = buildPrefColorMap(kind, data);
  const focusBbox = computeFocusBbox([...prefColors.keys()], data.hypocenter_lat, data.hypocenter_lon);
  const project = makeProjector(focusBbox);
  const parts = [];
  const allMuniBadges = [];
  const prefsWithMuniDetail = new Set();
  for (const f of PREFS) {
    // 複数リング（離島の多い都道府県）の場合、各リングを独立したサブパス（"M...Z"を複数連結）
    // として1つのdにまとめる。fill-ruleは既定のnonzeroのままでよい（リング同士が重ならない）。
    const d = getPrefRings(f)
      .map((ring) => {
        const pts = ring.map(([lon, lat]) => project(lon, lat));
        return "M " + pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" L ") + " Z";
      })
      .join(" ");
    const hit = prefColors.get(f.name);
    // 市区町村単位の内訳（hit.areas）があるかどうか。あるのに県全体を代表色（＝県内の
    // 最大震度）でベース塗りしてしまうと、実際には報告の無い・震度の低い市区町村まで
    // 最大震度の色に見えてしまい誤解を招く（「県内のどこか一部でも震度1があれば
    // 県全体が震度1の色に塗られる」という指摘）。そのため、内訳がある場合は県全体の
    // ベース塗りは行わず、下のループで実際に観測された市区町村だけを個別に塗る。
    // 内訳が全く無い場合（気象庁が「〇〇地方」等の広域地名でしか発表しておらず、
    // 単一の市区町村に対応づけられないケース）に限り、従来通り県代表色でフォールバック
    // 表示する（＝それしか手掛かりが無いため）。
    const hasMuniBreakdown = Boolean(hit?.areas?.length);
    const fill = hasMuniBreakdown ? "#1c2230" : hit ? hit.color : "#1c2230";
    const stroke = hit ? "rgba(255,255,255,.55)" : "#333c50";
    const strokeWidth = hit ? 1.1 : 0.6;
    parts.push(`<path d="${d}" fill="${fill}" stroke="${stroke}" stroke-width="${strokeWidth}"/>`);

    if (!hasMuniBreakdown) continue;

    let drewAny = false;
    const muniBadges = [];
    // 同じ市区町村が、観測点の地名違い（「八代市千丁町」「八代市鏡町」等）で複数のaddrとして
    // hit.areasに入っていることがある。市区町村単位に束ね、その中の最大震度だけを残してから
    // 描画する（束ねないと、同じ市区町村のポリゴン・バッジが観測点の数だけ重なって表示され
        // 非常に見にくくなるうえ、areasが震度の高い順にソートされているため、後から重ね描きされる
    // 震度の低い観測点で本来の最大震度が覆い隠されてしまい、実際より低い震度に見えてしまう
    // バグがあった）。
    const byMuni = new Map(); // muniName -> { rings, scale, scale_text }
    for (const area of hit.areas) {
      const match = findMunicipalityMatch(f.name, area.addr);
      if (!match) continue; // 「〜地方」等、複数市区町村にまたがる広域地名は単一市区町村に対応しないため無理に描かない
      const existing = byMuni.get(match.name);
      if (!existing || (area.scale ?? -1) > existing.scale) {
        byMuni.set(match.name, { rings: match.rings, scale: area.scale, scale_text: area.scale_text });
      }
    }
    // ── 描画順: 震度が高いほど最前面に ──
    // byMuniはhit.areas（震度降順でソート済み）の出現順のまま=震度が高い方から先に
    // 挿入されている。SVGは後に描画した要素ほど手前（前面）に来るため、このまま
    // Map挿入順で描くと震度の低いバッジが後から描かれて震度の高いバッジの上に
    // 重なってしまい、本来一番目立つべき最大震度が隠れてしまう
    // （実際に「震度5弱のはずが2しか見えない」という報告があった）。
    // ここで震度の昇順に並べ替えてから描くことで、震度が高いものほど最後
    // ＝最前面に描かれるようにする。
    const sortedMunis = [...byMuni.values()].sort((a, b) => (a.scale ?? -1) - (b.scale ?? -1));
    for (const info of sortedMunis) {
      const areaColor = scaleColor(info.scale);
      for (const ring of info.rings) {
        const mpts = ring.map(([lon, lat]) => project(lon, lat));
        const md = "M " + mpts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" L ") + " Z";
        parts.push(`<path d="${md}" fill="${areaColor}" stroke="rgba(255,255,255,.35)" stroke-width="0.5"/>`);
        drewAny = true;
      }
      // 市区町村ごとに小さな丸バッジ（震度を短く収めた表記）を、その市区町村の位置に置く。
      // 塗り分けの色だけだと差が分かりにくいため、数字でもはっきり示す
      // （ユーザー提供の参考画像のような「小さな丸の中に震度」の見た目に合わせる）。
      //
      // 以前は震度1のバッジを間引いていた（広域地震では震度1の市区町村だけで数十件に
      // なり、バッジ同士が重なって震度3・4のバッジまで埋もれてしまっていたため）。
      // しかし今は描画順を震度昇順にして震度が高いバッジを必ず最前面に描くよう
      // 直したので、震度1のバッジがあっても高い震度のバッジが隠れることはなくなった。
      // そのため間引きはやめ、震度1も含めて全市区町村分バッジを表示する。
      const centroid = centroidOfRings(info.rings);
      if (centroid) {
        const [mx, my] = project(centroid[0], centroid[1]);
        const label = scaleShortLabel(info.scale, info.scale_text);
        const textColor = badgeTextColorForScale(info.scale);
        const r = 9;
        muniBadges.push({
          scale: info.scale ?? -1,
          svg:
            `<g>` +
              `<circle cx="${mx.toFixed(1)}" cy="${my.toFixed(1)}" r="${r}" fill="${areaColor}" stroke="rgba(255,255,255,.85)" stroke-width="1"/>` +
              `<text x="${mx.toFixed(1)}" y="${(my + 3.5).toFixed(1)}" text-anchor="middle" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="10" font-weight="800" fill="${textColor}">${escXml(label)}</text>` +
            `</g>`,
        });
      }
    }
    // 市区町村ポリゴンで都道府県の外周付近が塗り重ねられていることがあるため、
    // 都道府県境の縁だけをもう一度くっきり描き直す。
    if (drewAny) {
      parts.push(`<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${strokeWidth}"/>`);
      allMuniBadges.push(...muniBadges);
      prefsWithMuniDetail.add(f.name);
    }
  }
  // 都道府県ごとの震度バッジ（index.htmlの地図マーカーと同じ「5-」「6+」等の短縮表記）。
  // 塗り分けだけだと色の微妙な違いが分かりにくいため、実際の震度も文字で添える。
  // ただし市区町村ごとの小さな丸バッジ（上のprefsWithMuniDetail）で内訳を出している都道府県は、
  // 県中央に大きな四角バッジを重ねると逆に見づらくなるためスキップする。
  const centroids = getPrefCentroids();
  for (const [prefName, hit] of prefColors) {
    if (prefsWithMuniDetail.has(prefName)) continue;
    const centroid = centroids.get(prefName);
    if (!centroid) continue;
    const [cx, cy] = project(centroid[0], centroid[1]);
    const label = scaleShortLabel(hit.rank >= 0 ? SCALE_ORDER[hit.rank] : null, hit.text);
    const textColor = badgeTextColorForScale(SCALE_ORDER[hit.rank]);
    const half = 15;
    parts.push(
      `<g>` +
        `<rect x="${(cx - half).toFixed(1)}" y="${(cy - half).toFixed(1)}" width="${half * 2}" height="${half * 2}" rx="6" fill="${hit.color}" stroke="rgba(255,255,255,.9)" stroke-width="1.5"/>` +
        `<text x="${cx.toFixed(1)}" y="${(cy + 6).toFixed(1)}" text-anchor="middle" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="16" font-weight="800" fill="${textColor}">${escXml(label)}</text>` +
      `</g>`
    );
  }
  // 市区町村ごとの小さな丸バッジは、都道府県の縁取り等より後（手前）に描いて隠れないようにする。
  // 都道府県をまたいで隣接する市区町村同士のバッジが重なるケースにも対応できるよう、
  // 全都道府県分をまとめた上でもう一度震度昇順に並べ替えてから描く（＝震度が高いほど
  // 最前面）。都道府県ごとのループの都合上はすでに昇順で積んであるが、複数の都道府県の
  // バッジが混ざった状態では県境をまたいだ前後関係までは保証されないため、ここで
  // 全体を通してもう一度ソートする。
  parts.push(...allMuniBadges.sort((a, b) => a.scale - b.scale).map((b) => b.svg));

  // 震源マーカー（分かる場合のみ）。以前は二重丸+十字（照準/クロスヘア風）だったが、
  // FPSのエイムのように見えると不評だったため、テレビの地震速報でおなじみの赤い×印に変更。
  // 太めの白フチを乗せてから赤線を重ねることで、地図の色（震度の塗り分け）に紛れず
  // どの背景色の上でも視認できるようにしている。
  if (typeof data.hypocenter_lat === "number" && typeof data.hypocenter_lon === "number") {
    const [hx, hy] = project(data.hypocenter_lon, data.hypocenter_lat);
    const r = 12; // ×の腕の長さ（中心からの距離）
    const x1 = (hx - r).toFixed(1), y1 = (hy - r).toFixed(1);
    const x2 = (hx + r).toFixed(1), y2 = (hy + r).toFixed(1);
    const x3 = (hx - r).toFixed(1), y3 = (hy + r).toFixed(1);
    const x4 = (hx + r).toFixed(1), y4 = (hy - r).toFixed(1);
    parts.push(
      `<g stroke-linecap="round">` +
        // 白フチ（下地の色に関わらず視認性を確保）
        `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#ffffff" stroke-width="6"/>` +
        `<line x1="${x3}" y1="${y3}" x2="${x4}" y2="${y4}" stroke="#ffffff" stroke-width="6"/>` +
        // 赤い×本体（NHK等の地震速報でおなじみの表現）
        `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#e60012" stroke-width="3.5"/>` +
        `<line x1="${x3}" y1="${y3}" x2="${x4}" y2="${y4}" stroke="#e60012" stroke-width="3.5"/>` +
      `</g>`
    );
  }
  return parts.join("");
}

// 上部の帯パネル（左上カードの拡大版に相当。タイトル・震源・M/深さ・最大震度バッジ）
function renderHeaderPanel(kind, data) {
  const isEew = kind === "eew";
  const cancelled = Boolean(data.cancelled);
  const mainTitle = cancelled
    ? "緊急地震速報 取消"
    : isEew
      ? (data.test ? "緊急地震速報（訓練）" : "緊急地震速報（警報）")
      : "地震情報（観測）";
  const subTitle = cancelled
    ? "この地震に関する緊急地震速報は取り消されました"
    : `${data.hypocenter_name || "震源不明"}`;
  const magText = (data.magnitude !== null && data.magnitude !== undefined) ? `M${Number(data.magnitude).toFixed(1)}` : "M不明";
  // 深さ(km)。P2P地震情報APIの仕様上、0は「ごく浅い」（具体的な数値なし）を意味する正当な値であり、
  // 「深さ0km」という数値表示にしてしまうとバグ・プレースホルダーのように見えてしまうため、
  // 気象庁の発表文言に合わせて「ごく浅い」という文字列で表示する。
  const rawDepth = data.depth ?? data.hypocenter_depth;
  const depthText = rawDepth == null ? "深さ不明" : rawDepth === 0 ? "深さごく浅い" : `深さ${Math.round(rawDepth)}km`;
  const badgeLabel = isEew ? "最大予測震度" : "最大震度";
  const badgeText = cancelled ? "中止" : (data.max_scale_text || "不明");
  const badgeColor = cancelled ? "#64748b" : scaleColor(data.max_scale);
  // このバッジは背景が常に暗いカード（rgba(255,255,255,.06)、badgeColorは枠線のみに使用）のため、
  // 震度色（黄色等）を背景と想定して文字色を切り替えるscaleTextColor()は使わない。
  // これを使うと震度4のときだけ文字色が黒(#1a1a1a)になり、暗い背景に黒文字でほぼ見えなくなるバグがあった。
  const badgeTextColor = "#ffffff";
  const titleColor = cancelled ? "#94a3b8" : "#ffffff";

  const badgeFontSize = badgeText.length <= 2 ? 40 : badgeText.length === 3 ? 30 : 22;

  return `
    <rect x="0" y="0" width="${CANVAS_W}" height="128" fill="rgba(8,10,16,.86)"/>
    <rect x="0" y="126" width="${CANVAS_W}" height="2" fill="rgba(255,255,255,.15)"/>
    <text x="24" y="40" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="26" font-weight="700" fill="${titleColor}">${escXml(mainTitle)}</text>
    <text x="24" y="68" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="17" fill="#cbd5e1">${escXml(subTitle)}</text>
    ${!cancelled ? `<text x="24" y="96" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="15" fill="#94a3b8">${escXml(magText)} / ${escXml(depthText)}</text>` : ""}
    <g>
      <rect x="${CANVAS_W - 168}" y="18" width="144" height="92" rx="10" fill="rgba(255,255,255,.06)" stroke="${badgeColor}" stroke-width="2"/>
      <text x="${CANVAS_W - 96}" y="40" text-anchor="middle" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="13" fill="#cbd5e1">${escXml(badgeLabel)}</text>
      <text x="${CANVAS_W - 96}" y="88" text-anchor="middle" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="${badgeFontSize}" font-weight="800" fill="${badgeTextColor}">${escXml(badgeText)}</text>
    </g>
  `;
}

function renderFooter(data) {
  const originText = (typeof data.origin_epoch === "number")
    ? new Date(data.origin_epoch * 1000).toLocaleString("ja-JP", { hour12: false, timeZone: "Asia/Tokyo" })
    : "";
  return `
    <rect x="0" y="${CANVAS_H - 34}" width="${CANVAS_W}" height="34" fill="rgba(8,10,16,.78)"/>
    <text x="16" y="${CANVAS_H - 12}" font-family="'Hiragino Sans','Noto Sans JP',sans-serif" font-size="12" fill="#94a3b8">${escXml(originText ? `${originText} 発生` : "")}　気象庁発表に基づく速報値です</text>
  `;
}

// kind: 'eew' | 'intensity'、data: eew.jsのpublishEewResult/publishIntensityResultが作るオブジェクト
export function renderEewNoticeSvg(kind, data) {
  const mapLayer = renderMapLayer(kind, data);
  const header = renderHeaderPanel(kind, data);
  const footer = renderFooter(data);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${CANVAS_W} ${CANVAS_H}" width="${CANVAS_W}" height="${CANVAS_H}">
    <rect x="0" y="0" width="${CANVAS_W}" height="${CANVAS_H}" fill="#10131a"/>
    <g>${mapLayer}</g>
    ${header}
    ${footer}
  </svg>`;
}
package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MunicipalityItem(
    val name: String,             // e.g. "新宿区", "横浜市", "つくば市"
    val prefecture: String,       // e.g. "東京都", "神奈川県"
    val fullName: String,         // e.g. "東京都 新宿区"
    val latitude: Double,
    val longitude: Double
)

object JapanMunicipalities {

    val ALL_47_PREFECTURES = listOf(
        "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
        "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
        "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県",
        "静岡県", "愛知県", "三重県", "滋賀県", "京都府", "大阪府", "兵庫県",
        "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県",
        "徳島県", "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県",
        "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"
    )

    // JMA 6-digit area code for all 47 prefectures
    val JMA_PREFECTURE_CODES = mapOf(
        "北海道" to "016000",
        "青森県" to "020000",
        "岩手県" to "030000",
        "宮城県" to "040000",
        "秋田県" to "050000",
        "山形県" to "060000",
        "福島県" to "070000",
        "茨城県" to "080000",
        "栃木県" to "090000",
        "群馬県" to "100000",
        "埼玉県" to "110000",
        "千葉県" to "120000",
        "東京都" to "130000",
        "神奈川県" to "140000",
        "新潟県" to "150000",
        "富山県" to "160000",
        "石川県" to "170000",
        "福井県" to "180000",
        "山梨県" to "190000",
        "長野県" to "200000",
        "岐阜県" to "210000",
        "静岡県" to "220000",
        "愛知県" to "230000",
        "三重県" to "240000",
        "滋賀県" to "250000",
        "京都府" to "260000",
        "大阪府" to "270000",
        "兵庫県" to "280000",
        "奈良県" to "290000",
        "和歌山県" to "300000",
        "鳥取県" to "310000",
        "島根県" to "320000",
        "岡山県" to "330000",
        "広島県" to "340000",
        "山口県" to "350000",
        "徳島県" to "360000",
        "香川県" to "370000",
        "愛媛県" to "380000",
        "高知県" to "390000",
        "福岡県" to "400000",
        "佐賀県" to "410000",
        "長崎県" to "420000",
        "熊本県" to "430000",
        "大分県" to "440000",
        "宮崎県" to "450000",
        "鹿児島県" to "460100",
        "沖縄県" to "471000"
    )

    // Capital coordinates for each prefecture
    val PREFECTURE_COORDS = mapOf(
        "北海道" to Pair(43.0642, 141.3469),
        "青森県" to Pair(40.8244, 140.7400),
        "岩手県" to Pair(39.7036, 141.1527),
        "宮城県" to Pair(38.2682, 140.8694),
        "秋田県" to Pair(39.7186, 140.1024),
        "山形県" to Pair(38.2404, 140.3633),
        "福島県" to Pair(37.7500, 140.4678),
        "茨城県" to Pair(36.3418, 140.4468),
        "栃木県" to Pair(36.5658, 139.8836),
        "群馬県" to Pair(36.3911, 139.0608),
        "埼玉県" to Pair(35.8617, 139.6455),
        "千葉県" to Pair(35.6074, 140.1065),
        "東京都" to Pair(35.6895, 139.6917),
        "神奈川県" to Pair(35.4437, 139.6380),
        "新潟県" to Pair(37.9022, 139.0236),
        "富山県" to Pair(36.6953, 137.2113),
        "石川県" to Pair(36.5947, 136.6256),
        "福井県" to Pair(36.0652, 136.2216),
        "山梨県" to Pair(35.6639, 138.5683),
        "長野県" to Pair(36.6513, 138.1812),
        "岐阜県" to Pair(35.3912, 136.7223),
        "静岡県" to Pair(34.9756, 138.3828),
        "愛知県" to Pair(35.1815, 136.9066),
        "三重県" to Pair(34.7303, 136.5086),
        "滋賀県" to Pair(35.0045, 135.8686),
        "京都府" to Pair(35.0116, 135.7681),
        "大阪府" to Pair(34.6937, 135.5023),
        "兵庫県" to Pair(34.6913, 135.1830),
        "奈良県" to Pair(34.6851, 135.8048),
        "和歌山県" to Pair(34.2260, 135.1675),
        "鳥取県" to Pair(35.5036, 134.2383),
        "島根県" to Pair(35.4723, 133.0505),
        "岡山県" to Pair(34.6618, 133.9344),
        "広島県" to Pair(34.3963, 132.4594),
        "山口県" to Pair(34.1858, 131.4705),
        "徳島県" to Pair(34.0658, 134.5594),
        "香川県" to Pair(34.3402, 134.0433),
        "愛媛県" to Pair(33.8417, 132.7661),
        "高知県" to Pair(33.5597, 133.5311),
        "福岡県" to Pair(33.5904, 130.4017),
        "佐賀県" to Pair(33.2494, 130.2988),
        "長崎県" to Pair(32.7448, 129.8737),
        "熊本県" to Pair(32.7898, 130.7417),
        "大分県" to Pair(33.2382, 131.6126),
        "宮崎県" to Pair(31.9111, 131.4239),
        "鹿児島県" to Pair(31.5602, 130.5581),
        "沖縄県" to Pair(26.2124, 127.6809)
    )

    // Search any municipality across Japan via Open-Meteo Geocoding API
    suspend fun searchMunicipalities(query: String): List<MunicipalityItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=ja&country=JP"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val list = mutableListOf<MunicipalityItem>()
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val name = item.optString("name")
                        val admin1 = item.optString("admin1", "") // Prefecture name
                        val lat = item.optDouble("latitude")
                        val lon = item.optDouble("longitude")

                        // Normalize prefecture
                        val pref = if (admin1.endsWith("都") || admin1.endsWith("道") || admin1.endsWith("府") || admin1.endsWith("県")) {
                            admin1
                        } else if (admin1.isNotEmpty()) {
                            "$admin1"
                        } else {
                            findPrefectureForName(name)
                        }

                        val fullName = if (pref.isNotEmpty() && !name.contains(pref)) "$pref $name" else name
                        list.add(
                            MunicipalityItem(
                                name = name,
                                prefecture = pref.ifEmpty { "東京都" },
                                fullName = fullName,
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            }
        } catch (_: Exception) {}

        // Local match fallback
        ALL_47_PREFECTURES.filter { it.contains(trimmed) }.map { pref ->
            val coords = PREFECTURE_COORDS[pref] ?: Pair(35.6895, 139.6917)
            MunicipalityItem(
                name = pref,
                prefecture = pref,
                fullName = pref,
                latitude = coords.first,
                longitude = coords.second
            )
        }
    }

    private fun findPrefectureForName(cityName: String): String {
        for (pref in ALL_47_PREFECTURES) {
            if (cityName.startsWith(pref)) return pref
        }
        return "東京都"
    }

    fun getJmaAreaCode(prefecture: String): String {
        for ((key, code) in JMA_PREFECTURE_CODES) {
            if (prefecture.contains(key) || key.contains(prefecture)) {
                return code
            }
        }
        return "130000"
    }
}

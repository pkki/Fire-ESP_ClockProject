package com.example.data

import com.example.model.WarningSeverity
import com.example.model.WeatherCondition
import com.example.model.WeatherState
import com.example.model.WeatherWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class WeatherRepository {

    private val JMA_CODE_MAP = mapOf(
        "02" to Pair("暴風雪特別警報", WarningSeverity.SPECIAL_WARNING),
        "03" to Pair("大雨特別警報", WarningSeverity.SPECIAL_WARNING),
        "04" to Pair("暴風特別警報", WarningSeverity.SPECIAL_WARNING),
        "05" to Pair("大雪特別警報", WarningSeverity.SPECIAL_WARNING),
        "06" to Pair("波浪特別警報", WarningSeverity.SPECIAL_WARNING),
        "07" to Pair("高潮特別警報", WarningSeverity.SPECIAL_WARNING),
        "10" to Pair("大雨警報", WarningSeverity.WARNING),
        "12" to Pair("大雪警報", WarningSeverity.WARNING),
        "13" to Pair("風雪注意報", WarningSeverity.ADVISORY),
        "14" to Pair("雷注意報", WarningSeverity.ADVISORY),
        "15" to Pair("強風注意報", WarningSeverity.ADVISORY),
        "16" to Pair("波浪注意報", WarningSeverity.ADVISORY),
        "17" to Pair("融雪注意報", WarningSeverity.ADVISORY),
        "18" to Pair("洪水注意報", WarningSeverity.ADVISORY),
        "19" to Pair("高潮注意報", WarningSeverity.ADVISORY),
        "20" to Pair("濃霧注意報", WarningSeverity.ADVISORY),
        "21" to Pair("乾燥注意報", WarningSeverity.ADVISORY),
        "22" to Pair("なだれ注意報", WarningSeverity.ADVISORY),
        "23" to Pair("低温注意報", WarningSeverity.ADVISORY),
        "24" to Pair("霜注意報", WarningSeverity.ADVISORY),
        "25" to Pair("着氷注意報", WarningSeverity.ADVISORY),
        "26" to Pair("着雪注意報", WarningSeverity.ADVISORY),
        "30" to Pair("暴風雪警報", WarningSeverity.WARNING),
        "32" to Pair("暴風警報", WarningSeverity.WARNING),
        "33" to Pair("波浪警報", WarningSeverity.WARNING),
        "34" to Pair("高潮警報", WarningSeverity.WARNING),
        "35" to Pair("洪水警報", WarningSeverity.WARNING)
    )

    suspend fun fetchWeather(
        prefecture: String = "東京都",
        cityName: String = "東京都",
        customLat: Double? = null,
        customLon: Double? = null,
        demoWarnings: Boolean = false
    ): WeatherState = withContext(Dispatchers.IO) {
        val coords = if (customLat != null && customLon != null) {
            Pair(customLat, customLon)
        } else {
            JapanMunicipalities.PREFECTURE_COORDS[prefecture] ?: Pair(35.6895, 139.6917)
        }

        try {
            // Fetch live weather data from Open-Meteo
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=${coords.first}&longitude=${coords.second}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=Asia%2FTokyo"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val root = JSONObject(response)
                val current = root.getJSONObject("current")
                val daily = root.optJSONObject("daily")

                val temp = current.getDouble("temperature_2m").toInt()
                val humidity = current.optInt("relative_humidity_2m", 50)
                val windSpeed = current.optDouble("wind_speed_10m", 8.0).toInt()
                val weatherCode = current.getInt("weather_code")

                val maxTemp = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.toInt() ?: (temp + 3)
                val minTemp = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.toInt() ?: (temp - 4)

                val (condition, desc) = parseWeatherCode(weatherCode)

                // Fetch real JMA warnings
                val warnings = if (demoWarnings) {
                    getDemoWarnings()
                } else {
                    fetchRealJmaWarnings(prefecture, cityName)
                }

                return@withContext WeatherState(
                    cityName = cityName,
                    regionName = prefecture,
                    condition = condition,
                    temperatureCelsius = temp,
                    conditionText = desc,
                    highTemp = maxTemp,
                    lowTemp = minTemp,
                    humidityPercent = humidity,
                    windSpeedKmH = windSpeed,
                    warnings = warnings
                )
            }
        } catch (_: Exception) {}

        // Fallback when network is offline
        getOfflineWeather(prefecture, cityName, demoWarnings)
    }

    private fun fetchRealJmaWarnings(prefecture: String, cityName: String): List<WeatherWarning> {
        val areaCode = JapanMunicipalities.getJmaAreaCode(prefecture)
        try {
            val url = URL("https://www.jma.go.jp/bosai/warning/data/warning/$areaCode.json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()

                val root = JSONObject(body)
                val areaTypes = root.optJSONArray("areaTypes")
                val resultList = mutableListOf<WeatherWarning>()
                val seenTitles = mutableSetOf<String>()

                if (areaTypes != null) {
                    // Traverse municipality level (index 1) or regional level (index 0)
                    for (t in 0 until areaTypes.length()) {
                        val areaTypeObj = areaTypes.getJSONObject(t)
                        val areas = areaTypeObj.optJSONArray("areas") ?: continue

                        for (a in 0 until areas.length()) {
                            val areaObj = areas.getJSONObject(a)
                            val warningsJson = areaObj.optJSONArray("warnings") ?: continue

                            for (w in 0 until warningsJson.length()) {
                                val item = warningsJson.getJSONObject(w)
                                val code = item.optString("code")
                                val status = item.optString("status")

                                // Only keep active warnings (発表, 継続, etc.)
                                if (status != "解除" && status != "発表警報・注意報はなし" && status.isNotEmpty()) {
                                    val mapped = JMA_CODE_MAP[code]
                                    if (mapped != null && !seenTitles.contains(mapped.first)) {
                                        seenTitles.add(mapped.first)
                                        // Format as standard level if applicable
                                        val formattedTitle = when (mapped.second) {
                                            WarningSeverity.ADVISORY -> {
                                                if (mapped.first.contains("大雨") || mapped.first.contains("洪水")) "レベル2 ${mapped.first}" else mapped.first
                                            }
                                            WarningSeverity.WARNING -> {
                                                if (mapped.first.contains("大雨") || mapped.first.contains("洪水")) "レベル3 ${mapped.first}" else mapped.first
                                            }
                                            WarningSeverity.SPECIAL_WARNING -> "レベル5 ${mapped.first}"
                                        }
                                        resultList.add(WeatherWarning(title = formattedTitle, severity = mapped.second))
                                    }
                                }
                            }
                        }
                    }
                }

                // If real JMA has active warnings, return them!
                // If there are no active warnings in JMA, return emptyList()! (no false positives)
                return resultList
            }
        } catch (_: Exception) {}

        return emptyList()
    }

    private fun getDemoWarnings(): List<WeatherWarning> {
        return listOf(
            WeatherWarning("レベル2 大雨注意報", WarningSeverity.ADVISORY),
            WeatherWarning("レベル2 土砂災害注意報", WarningSeverity.ADVISORY),
            WeatherWarning("雷注意報", WarningSeverity.ADVISORY)
        )
    }

    private fun parseWeatherCode(code: Int): Pair<WeatherCondition, String> {
        return when (code) {
            0 -> Pair(WeatherCondition.CLEAR, "快晴")
            1, 2 -> Pair(WeatherCondition.PARTLY_CLOUDY, "晴れ時々曇")
            3 -> Pair(WeatherCondition.CLOUDY, "曇り")
            45, 48 -> Pair(WeatherCondition.CLOUDY, "濃霧")
            51, 53, 55, 61, 63, 65 -> Pair(WeatherCondition.RAIN, "雨")
            71, 73, 75, 77, 85, 86 -> Pair(WeatherCondition.SNOW, "雪")
            80, 81, 82 -> Pair(WeatherCondition.RAIN, "にわか雨")
            95, 96, 99 -> Pair(WeatherCondition.THUNDERSTORM, "雷雨")
            else -> Pair(WeatherCondition.CLEAR, "晴れ")
        }
    }

    private fun getOfflineWeather(prefecture: String, cityName: String, demoWarnings: Boolean): WeatherState {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val temp = when (hour) {
            in 0..5 -> 17
            in 6..10 -> 20
            in 11..15 -> 24
            in 16..19 -> 22
            else -> 19
        }

        return WeatherState(
            cityName = cityName,
            regionName = prefecture,
            condition = if (hour in 6..17) WeatherCondition.CLEAR else WeatherCondition.PARTLY_CLOUDY,
            temperatureCelsius = temp,
            conditionText = if (hour in 6..17) "快晴" else "晴れ",
            highTemp = temp + 3,
            lowTemp = temp - 4,
            humidityPercent = 48,
            windSpeedKmH = 7,
            warnings = if (demoWarnings) getDemoWarnings() else emptyList()
        )
    }
}

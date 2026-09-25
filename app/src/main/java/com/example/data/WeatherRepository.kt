package com.example.data

import com.example.model.DailyForecast
import com.example.model.HourlyForecast
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
            // Fetch live detailed weather data from Open-Meteo (including precipitation mm and precipitation_sum)
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=${coords.first}&longitude=${coords.second}&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m&hourly=temperature_2m,precipitation_probability,precipitation,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max&timezone=Asia%2FTokyo&forecast_days=7"
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
                val hourly = root.optJSONObject("hourly")
                val daily = root.optJSONObject("daily")

                val temp = current.getDouble("temperature_2m").toInt()
                val humidity = current.optInt("relative_humidity_2m", 50)
                val apparentTemp = current.optDouble("apparent_temperature", temp.toDouble()).toInt()
                val precipitation = current.optDouble("precipitation", 0.0)
                val windSpeed = current.optDouble("wind_speed_10m", 8.0).toInt()
                val windDirDeg = current.optInt("wind_direction_10m", 0)
                val pressure = current.optDouble("surface_pressure", 1013.0).toInt()
                val weatherCode = current.getInt("weather_code")

                val maxTemp = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.toInt() ?: (temp + 3)
                val minTemp = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.toInt() ?: (temp - 4)

                val (condition, desc) = resolveConditionAndDesc(weatherCode, daily?.optJSONArray("precipitation_probability_max")?.optInt(0, 0) ?: 0, precipitation)
                val windDirText = parseWindDirection(windDirDeg)

                // Parse Sunrise & Sunset
                val rawSunrise = daily?.optJSONArray("sunrise")?.optString(0) ?: ""
                val rawSunset = daily?.optJSONArray("sunset")?.optString(0) ?: ""
                val sunriseStr = formatIsoTime(rawSunrise, "05:25")
                val sunsetStr = formatIsoTime(rawSunset, "17:50")

                val uvIndex = daily?.optJSONArray("uv_index_max")?.optDouble(0) ?: 4.5

                // Parse Hourly Forecasts (Next 24 hours)
                val hourlyList = parseHourlyForecasts(hourly)

                // Parse Daily Forecasts (7 days)
                val dailyList = parseDailyForecasts(daily)

                // Fetch real JMA warnings
                val warnings = if (demoWarnings) {
                    getDemoWarnings()
                } else {
                    fetchRealJmaWarnings(prefecture, cityName)
                }

                val nowTimeStr = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date()) + " 更新"

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
                    windDirectionText = windDirText,
                    apparentTempCelsius = apparentTemp,
                    pressureHpa = pressure,
                    precipitationMm = precipitation,
                    uvIndexMax = uvIndex,
                    sunriseTime = sunriseStr,
                    sunsetTime = sunsetStr,
                    lastUpdatedTime = nowTimeStr,
                    hourlyForecasts = hourlyList,
                    dailyForecasts = dailyList,
                    warnings = warnings
                )
            }
        } catch (_: Exception) {}

        // Fallback when network is offline
        getOfflineWeather(prefecture, cityName, demoWarnings)
    }

    private fun parseWindDirection(degrees: Int): String {
        val directions = arrayOf("北", "北北東", "北東", "東北東", "東", "東南東", "南東", "南南東", "南", "南南西", "南西", "西南西", "西", "西北西", "北西", "北北西")
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    private fun formatIsoTime(isoString: String, fallback: String): String {
        return try {
            if (isoString.contains("T")) {
                isoString.substringAfter("T").take(5)
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseHourlyForecasts(hourly: JSONObject?): List<HourlyForecast> {
        if (hourly == null) return emptyList()
        val result = mutableListOf<HourlyForecast>()
        try {
            val times = hourly.optJSONArray("time") ?: return emptyList()
            val temps = hourly.optJSONArray("temperature_2m")
            val probs = hourly.optJSONArray("precipitation_probability")
            val precips = hourly.optJSONArray("precipitation")
            val codes = hourly.optJSONArray("weather_code")

            val nowCalendar = Calendar.getInstance()
            val currentHour = nowCalendar.get(Calendar.HOUR_OF_DAY)
            val todayDatePrefix = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(nowCalendar.time)

            var startIndex = 0
            for (i in 0 until times.length()) {
                val t = times.optString(i)
                if (t.startsWith(todayDatePrefix)) {
                    val hourInT = t.substringAfter("T").take(2).toIntOrNull() ?: 0
                    if (hourInT >= currentHour) {
                        startIndex = i
                        break
                    }
                }
            }

            // Take 24 hours
            val count = minOf(24, times.length() - startIndex)
            for (i in 0 until count) {
                val idx = startIndex + i
                val rawTime = times.optString(idx)
                val hourLabel = if (i == 0) "今" else formatIsoTime(rawTime, "")
                val temp = temps?.optDouble(idx)?.toInt() ?: 20
                val prob = probs?.optInt(idx, 0) ?: 0
                val precipMm = precips?.optDouble(idx, 0.0) ?: 0.0
                val code = codes?.optInt(idx, 0) ?: 0
                val (cond, desc) = resolveConditionAndDesc(code, prob, precipMm)

                result.add(
                    HourlyForecast(
                        time = hourLabel,
                        temperatureCelsius = temp,
                        condition = cond,
                        conditionText = desc,
                        precipitationProbability = prob,
                        precipitationMm = precipMm
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    private fun parseDailyForecasts(daily: JSONObject?): List<DailyForecast> {
        if (daily == null) return emptyList()
        val result = mutableListOf<DailyForecast>()
        try {
            val times = daily.optJSONArray("time") ?: return emptyList()
            val codes = daily.optJSONArray("weather_code")
            val maxTemps = daily.optJSONArray("temperature_2m_max")
            val minTemps = daily.optJSONArray("temperature_2m_min")
            val probs = daily.optJSONArray("precipitation_probability_max")
            val precipSums = daily.optJSONArray("precipitation_sum")
            val sunrises = daily.optJSONArray("sunrise")
            val sunsets = daily.optJSONArray("sunset")
            val uvs = daily.optJSONArray("uv_index_max")

            val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
            val outFormat = SimpleDateFormat("M/d (E)", Locale.JAPAN)

            for (i in 0 until times.length()) {
                val rawDate = times.optString(i)
                val label = try {
                    val d = inFormat.parse(rawDate)
                    if (i == 0) "今日" else if (i == 1) "明日" else if (d != null) outFormat.format(d) else rawDate
                } catch (_: Exception) {
                    rawDate
                }

                val code = codes?.optInt(i, 0) ?: 0
                val maxT = maxTemps?.optDouble(i)?.toInt() ?: 24
                val minT = minTemps?.optDouble(i)?.toInt() ?: 16
                val prob = probs?.optInt(i, 0) ?: 0
                val precipSum = precipSums?.optDouble(i, 0.0) ?: 0.0
                val (cond, desc) = resolveConditionAndDesc(code, prob, precipSum)
                val sr = formatIsoTime(sunrises?.optString(i) ?: "", "05:25")
                val ss = formatIsoTime(sunsets?.optString(i) ?: "", "17:50")
                val uv = uvs?.optDouble(i, 4.0) ?: 4.0

                result.add(
                    DailyForecast(
                        date = label,
                        condition = cond,
                        conditionText = desc,
                        highTemp = maxT,
                        lowTemp = minT,
                        precipitationProbability = prob,
                        precipitationSumMm = precipSum,
                        sunrise = sr,
                        sunset = ss,
                        uvIndexMax = uv
                    )
                )
            }
        } catch (_: Exception) {}
        return result
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

    private fun resolveConditionAndDesc(code: Int, probability: Int, precipitationMm: Double = 0.0): Pair<WeatherCondition, String> {
        var (cond, desc) = parseWeatherCode(code)

        // 降水量・降水確率に基づいた整合性補正 (tenki.jp準拠)
        when {
            precipitationMm >= 3.0 -> {
                cond = WeatherCondition.RAIN
                desc = if (code in 95..99) "雷雨" else "本降りの雨 (${String.format(Locale.JAPAN, "%.1f", precipitationMm)}mm)"
            }
            precipitationMm >= 0.5 -> {
                cond = WeatherCondition.RAIN
                desc = if (desc.contains("雨") || desc.contains("雷")) desc else "雨 (${String.format(Locale.JAPAN, "%.1f", precipitationMm)}mm)"
            }
            precipitationMm > 0.0 -> {
                cond = WeatherCondition.RAIN
                desc = if (desc.contains("雪")) desc else "小雨 (${String.format(Locale.JAPAN, "%.1f", precipitationMm)}mm)"
            }
            probability >= 60 -> {
                if (cond == WeatherCondition.CLEAR || cond == WeatherCondition.PARTLY_CLOUDY) {
                    cond = WeatherCondition.RAIN
                    desc = "雨のち晴れ/雨"
                } else if (cond == WeatherCondition.CLOUDY) {
                    cond = WeatherCondition.RAIN
                    desc = "曇り一時雨"
                }
            }
            probability in 30..59 -> {
                if (cond == WeatherCondition.CLEAR) {
                    cond = WeatherCondition.PARTLY_CLOUDY
                    desc = "晴れ時々曇"
                }
            }
        }

        return Pair(cond, desc)
    }

    private fun parseWeatherCode(code: Int): Pair<WeatherCondition, String> {
        return when (code) {
            0 -> Pair(WeatherCondition.CLEAR, "快晴")
            1 -> Pair(WeatherCondition.CLEAR, "ほぼ快晴")
            2 -> Pair(WeatherCondition.PARTLY_CLOUDY, "晴れ時々曇")
            3 -> Pair(WeatherCondition.CLOUDY, "曇り")
            45, 48 -> Pair(WeatherCondition.CLOUDY, "霧・濃霧")
            51, 53, 55 -> Pair(WeatherCondition.RAIN, "小雨・霧雨")
            56, 57 -> Pair(WeatherCondition.RAIN, "着氷性の霧雨")
            61 -> Pair(WeatherCondition.RAIN, "弱雨")
            63 -> Pair(WeatherCondition.RAIN, "雨")
            65 -> Pair(WeatherCondition.RAIN, "強い雨")
            66, 67 -> Pair(WeatherCondition.RAIN, "着氷性の雨")
            71, 73, 75 -> Pair(WeatherCondition.SNOW, "雪")
            77 -> Pair(WeatherCondition.SNOW, "細氷・雪粒")
            80, 81, 82 -> Pair(WeatherCondition.RAIN, "にわか雨")
            85, 86 -> Pair(WeatherCondition.SNOW, "にわか雪")
            95 -> Pair(WeatherCondition.THUNDERSTORM, "雷雨")
            96, 99 -> Pair(WeatherCondition.THUNDERSTORM, "雹を伴う雷雨")
            else -> Pair(WeatherCondition.CLEAR, "晴れ")
        }
    }

    private fun getOfflineWeather(prefecture: String, cityName: String, demoWarnings: Boolean): WeatherState {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val temp = when (hour) {
            in 0..5 -> 17
            in 6..10 -> 20
            in 11..15 -> 24
            in 16..19 -> 22
            else -> 19
        }

        val hourlyList = (0..23).map { hOffset ->
            val h = (hour + hOffset) % 24
            val t = when (h) {
                in 0..5 -> 17
                in 6..10 -> 20
                in 11..15 -> 24
                in 16..19 -> 22
                else -> 19
            }
            val cond = if (h in 6..17) WeatherCondition.CLEAR else WeatherCondition.PARTLY_CLOUDY
            HourlyForecast(
                time = if (hOffset == 0) "今" else String.format(Locale.JAPAN, "%02d:00", h),
                temperatureCelsius = t,
                condition = cond,
                conditionText = if (cond == WeatherCondition.CLEAR) "快晴" else "晴れ",
                precipitationProbability = if (hOffset in 4..8) 20 else 0
            )
        }

        val outFormat = SimpleDateFormat("M/d (E)", Locale.JAPAN)
        val dailyList = (0..6).map { dOffset ->
            val dCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dOffset) }
            val label = if (dOffset == 0) "今日" else if (dOffset == 1) "明日" else outFormat.format(dCal.time)
            val cond = when (dOffset % 3) {
                0 -> WeatherCondition.CLEAR
                1 -> WeatherCondition.PARTLY_CLOUDY
                else -> WeatherCondition.RAIN
            }
            DailyForecast(
                date = label,
                condition = cond,
                conditionText = when (cond) {
                    WeatherCondition.CLEAR -> "快晴"
                    WeatherCondition.PARTLY_CLOUDY -> "晴れ時々曇"
                    else -> "雨"
                },
                highTemp = 25 - dOffset % 3,
                lowTemp = 16 - dOffset % 2,
                precipitationProbability = if (cond == WeatherCondition.RAIN) 70 else 10,
                sunrise = "05:25",
                sunset = "17:50",
                uvIndexMax = 5.2
            )
        }

        val nowTimeStr = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date()) + " (オフライン)"

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
            windDirectionText = "北東",
            apparentTempCelsius = temp,
            pressureHpa = 1013,
            precipitationMm = 0.0,
            uvIndexMax = 5.2,
            sunriseTime = "05:25",
            sunsetTime = "17:50",
            lastUpdatedTime = nowTimeStr,
            hourlyForecasts = hourlyList,
            dailyForecasts = dailyList,
            warnings = if (demoWarnings) getDemoWarnings() else emptyList()
        )
    }
}


package com.example.data

import com.example.model.WeatherCondition
import com.example.model.WeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class WeatherRepository {

    suspend fun fetchWeather(cityName: String = "Tokyo"): WeatherState = withContext(Dispatchers.IO) {
        try {
            // Free public Open-Meteo API (Tokyo Coordinates, no API key required)
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=35.6895&longitude=139.6917&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=Asia%2FTokyo"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
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

                return@withContext WeatherState(
                    cityName = cityName,
                    condition = condition,
                    temperatureCelsius = temp,
                    conditionText = desc,
                    highTemp = maxTemp,
                    lowTemp = minTemp,
                    humidityPercent = humidity,
                    windSpeedKmH = windSpeed
                )
            }
        } catch (_: Exception) {
            // Offline fallback
        }

        // Graceful offline fallback based on current hour
        getFallbackWeather(cityName)
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

    private fun getFallbackWeather(cityName: String): WeatherState {
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
            condition = if (hour in 6..17) WeatherCondition.CLEAR else WeatherCondition.PARTLY_CLOUDY,
            temperatureCelsius = temp,
            conditionText = if (hour in 6..17) "快晴" else "晴れ",
            highTemp = temp + 3,
            lowTemp = temp - 4,
            humidityPercent = 48,
            windSpeedKmH = 7
        )
    }
}

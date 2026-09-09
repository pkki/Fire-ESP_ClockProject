package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class DetectedLocation(
    val prefecture: String,
    val cityName: String,
    val latitude: Double,
    val longitude: Double
)

object DeviceLocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): DetectedLocation? = withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        var location: Location? = null
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (location == null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
        } catch (_: SecurityException) {
            return@withContext null
        } catch (_: Exception) {
            return@withContext null
        }

        if (location == null) {
            return@withContext null
        }

        val lat = location.latitude
        val lon = location.longitude

        // Try standard Geocoder first
        try {
            val geocoder = Geocoder(context, Locale.JAPAN)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val adminArea = address.adminArea ?: "" // e.g. 東京都, 神奈川県
                val locality = address.locality ?: address.subAdminArea ?: address.subLocality ?: ""
                if (adminArea.isNotEmpty()) {
                    val cityDisplay = if (locality.isNotEmpty()) "$adminArea $locality" else adminArea
                    return@withContext DetectedLocation(
                        prefecture = adminArea,
                        cityName = cityDisplay,
                        latitude = lat,
                        longitude = lon
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback to online reverse geocode
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=10&accept-language=ja"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "SmartDeskClock/1.0")
            }
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()
                val address = json.optJSONObject("address")
                if (address != null) {
                    val province = address.optString("province", address.optString("state", "東京都"))
                    val city = address.optString("city", address.optString("town", address.optString("village", address.optString("suburb", ""))))
                    val display = if (city.isNotEmpty()) "$province $city" else province
                    return@withContext DetectedLocation(
                        prefecture = province,
                        cityName = display,
                        latitude = lat,
                        longitude = lon
                    )
                }
            }
        } catch (_: Exception) {}

        DetectedLocation(
            prefecture = "東京都",
            cityName = "東京都",
            latitude = lat,
            longitude = lon
        )
    }
}

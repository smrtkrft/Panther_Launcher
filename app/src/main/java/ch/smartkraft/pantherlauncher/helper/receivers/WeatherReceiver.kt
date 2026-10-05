package ch.smartkraft.pantherlauncher.helper.receivers

import android.net.Uri
import ch.smartkraft.common.AppLogger
import java.util.Locale
import android.content.Context
import ch.smartkraft.pantherlauncher.data.Prefs
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** A coordinate with two decimals (about 1 km), written with a dot whatever the device language is. */
internal fun coarseCoordinate(value: Double): String = String.format(Locale.US, "%.2f", value)

class WeatherReceiver(context: Context) {

    // Create Moshi instance once
    private val moshi = Moshi.Builder().build()

    // Load saved location from SharedPreferences
    private val prefs = Prefs(context)

    // Adapters
    private val weatherAdapter = moshi.adapter(WeatherResponse::class.java)
    private val geocodingAdapter = moshi.adapter(GeocodingResponse::class.java)

    // Simple in-memory cache
    private var cachedWeather: WeatherResponse? = null

    /** 🔹 Fetch weather for given lat/lon */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val tempUnit = prefs.tempUnit.toString().lowercase() // "celsius" or "fahrenheit"
                // About a kilometre is plenty for a forecast; the exact position stays on the device
                val urlStr =
                    "https://api.open-meteo.com/v1/forecast?latitude=${coarseCoordinate(latitude)}&longitude=${coarseCoordinate(longitude)}&current_weather=true&temperature_unit=${tempUnit}"
                val response = URL(urlStr).readText()
                val weather = weatherAdapter.fromJson(response)
                cachedWeather = weather // update cache
            } catch (e: Exception) {
                // Not the stack trace: an HTTP error message contains the URL, and with it the coordinates
                AppLogger.w("WeatherReceiver", "Weather request failed: ${e.javaClass.simpleName}")
            }
            cachedWeather
        }
    }

    /** 🔹 Map weather codes to emoji */
    fun getWeatherEmoji(weatherCode: Int): String {
        return when (weatherCode) {
            0 -> "☀️"
            1 -> "🌤️"
            2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55, 56, 57 -> "🌦️"
            61, 63, 65, 66, 67 -> "🌧️"
            80, 81, 82 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "❄️"
            95, 96, 99 -> "⛈️"
            else -> "❔"
        }
    }

    /** 🔹 Search location using Open-Meteo Geocoding API */
    suspend fun searchLocation(query: String): List<LocationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(query)}"
                val response = URL(urlStr).readText()
                val geocodingResponse = geocodingAdapter.fromJson(response)
                geocodingResponse?.results ?: emptyList()
            } catch (e: Exception) {
                AppLogger.w("WeatherReceiver", "Location search failed: ${e.javaClass.simpleName}")
                emptyList()
            }
        }
    }

    /** 🔹 Fetch weather directly for saved location */
    suspend fun loadWeatherForSavedLocation(latitude: Double, longitude: Double): WeatherResponse? {
        val coords = prefs.loadLocation()
        return if (coords != null) {
            val (lat, lon) = coords
            getCurrentWeather(lat, lon)
        } else {
            getCurrentWeather(latitude, longitude)
        }
    }
}

// --- 📦 Data Classes ---
@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @field:Json(name = "current_weather")
    val currentWeather: CurrentWeather,

    @field:Json(name = "current_weather_units")
    val currentUnits: CurrentUnits,

    @field:Json(name = "timezone")
    val timezone: String
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    val results: List<LocationResult>?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @field:Json(name = "temperature")
    val temperature: Double,

    @field:Json(name = "weathercode")
    val weatherCode: Int,
)

@JsonClass(generateAdapter = true)
data class CurrentUnits(
    @field:Json(name = "temperature")
    val temperatureUnit: String,

    @field:Json(name = "weathercode")
    val weatherCodeUnit: String,
)

@JsonClass(generateAdapter = true)
data class LocationResult(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String
) {
    var displayName = listOfNotNull(
        name,
        if (admin1.isNotBlank() &&
            !country.equals(admin1, ignoreCase = true) &&
            !country.contains(admin1, ignoreCase = true)
        ) admin1 else null,
        country
    ).joinToString(", ")

    var region = listOfNotNull(name, country).joinToString(", ")
}

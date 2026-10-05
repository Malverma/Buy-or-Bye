package com.buyorbye.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.buyorbye.app.domain.Decision
import com.buyorbye.app.domain.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class Settings(
    val mpg: Double = 25.0,
    val valueOfTimePerHour: Double = 15.0,
    val minSavings: Double = 3.0,
    val radiusMiles: Double = 10.0,
    val includeOnline: Boolean = true,
    val wearEnabled: Boolean = false,
    val wearPerMile: Double = 0.10,
    /** Used when live gas prices are unavailable, or always when [gasOverride] is on. */
    val manualGasPrice: Double = 3.20,
    val gasOverride: Boolean = false,
    val home: LatLng? = null,
)

@Serializable
data class HistoryItem(
    val query: String,
    val upc: String? = null,
    val priceHere: Double,
    val decision: Decision,
    val bestRetailer: String? = null,
    val netSavings: Double? = null,
    val timestamp: Long,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val mpg = doublePreferencesKey("mpg")
        val vot = doublePreferencesKey("value_of_time")
        val minSavings = doublePreferencesKey("min_savings")
        val radius = doublePreferencesKey("radius_miles")
        val online = booleanPreferencesKey("include_online")
        val wearOn = booleanPreferencesKey("wear_enabled")
        val wear = doublePreferencesKey("wear_per_mile")
        val gas = doublePreferencesKey("manual_gas")
        val gasOverride = booleanPreferencesKey("gas_override")
        val home = stringPreferencesKey("home")
        val history = stringPreferencesKey("history")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val historySerializer = ListSerializer(HistoryItem.serializer())

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            mpg = p[K.mpg] ?: d.mpg,
            valueOfTimePerHour = p[K.vot] ?: d.valueOfTimePerHour,
            minSavings = p[K.minSavings] ?: d.minSavings,
            radiusMiles = p[K.radius] ?: d.radiusMiles,
            includeOnline = p[K.online] ?: d.includeOnline,
            wearEnabled = p[K.wearOn] ?: d.wearEnabled,
            wearPerMile = p[K.wear] ?: d.wearPerMile,
            manualGasPrice = p[K.gas] ?: d.manualGasPrice,
            gasOverride = p[K.gasOverride] ?: d.gasOverride,
            home = p[K.home]?.let { runCatching { json.decodeFromString(LatLng.serializer(), it) }.getOrNull() },
        )
    }

    val history: Flow<List<HistoryItem>> = context.dataStore.data.map { decodeHistory(it) }

    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[K.mpg] = s.mpg
            p[K.vot] = s.valueOfTimePerHour
            p[K.minSavings] = s.minSavings
            p[K.radius] = s.radiusMiles
            p[K.online] = s.includeOnline
            p[K.wearOn] = s.wearEnabled
            p[K.wear] = s.wearPerMile
            p[K.gas] = s.manualGasPrice
            p[K.gasOverride] = s.gasOverride
            if (s.home != null) p[K.home] = json.encodeToString(LatLng.serializer(), s.home) else p.remove(K.home)
        }
    }

    suspend fun addHistory(item: HistoryItem) {
        context.dataStore.edit { p ->
            val list = (listOf(item) + decodeHistory(p)).take(MAX_HISTORY)
            p[K.history] = json.encodeToString(historySerializer, list)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(K.history) }
    }

    private fun decodeHistory(p: Preferences): List<HistoryItem> =
        p[K.history]?.let { runCatching { json.decodeFromString(historySerializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val MAX_HISTORY = 50
    }
}

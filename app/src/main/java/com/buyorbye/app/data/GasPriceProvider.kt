package com.buyorbye.app.data

import com.buyorbye.app.domain.LatLng
import kotlinx.serialization.json.JsonPrimitive

data class GasPrice(val pricePerGallon: Double, val source: String, val estimated: Boolean)

/** Median regular-unleaded price from nearby stations via Google Places API (New) `fuelOptions`. */
class GasPriceProvider(private val http: Http, private val mapsKey: String) {

    suspend fun nearby(here: LatLng): Double? {
        if (mapsKey.isBlank()) return null
        val body = """
            {"includedTypes":["gas_station"],"maxResultCount":15,
             "locationRestriction":{"circle":{"center":{"latitude":${here.lat},"longitude":${here.lng}},"radius":8000}}}
        """.trimIndent()
        val resp = http.postJson(
            "https://places.googleapis.com/v1/places:searchNearby",
            body,
            mapOf("X-Goog-Api-Key" to mapsKey, "X-Goog-FieldMask" to "places.fuelOptions"),
        )
        val prices = resp.obj()?.get("places").arr().orEmpty().flatMap { place ->
            place.obj()?.get("fuelOptions").obj()?.get("fuelPrices").arr().orEmpty().mapNotNull { fp ->
                val o = fp.obj() ?: return@mapNotNull null
                if (o.str("type") != "REGULAR_UNLEADED") return@mapNotNull null
                val price = o["price"].obj() ?: return@mapNotNull null
                // Money: units is an int64 encoded as a string, nanos an int.
                val units = (price["units"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                val nanos = price.int("nanos") ?: 0
                (units + nanos / 1e9).takeIf { it > 0.5 }
            }
        }.sorted()
        return if (prices.isEmpty()) null else prices[prices.size / 2]
    }
}

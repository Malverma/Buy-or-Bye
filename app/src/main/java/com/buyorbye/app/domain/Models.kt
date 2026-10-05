package com.buyorbye.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class LatLng(val lat: Double, val lng: Double)

data class Product(
    val query: String,
    val upc: String? = null,
    val brand: String? = null,
    val imageUrl: String? = null,
)

enum class Channel { IN_STORE, ONLINE }

data class StoreLocation(
    val name: String,
    val address: String?,
    val location: LatLng,
)

data class PriceResult(
    val retailer: String,
    val title: String,
    val channel: Channel,
    val price: Double,
    /** Online only. Null when the listing doesn't say. */
    val shipping: Double? = null,
    val deliveryText: String? = null,
    val store: StoreLocation? = null,
    val link: String? = null,
    val thumbnail: String? = null,
)

/** Drive distance/time for one leg. */
data class Leg(val miles: Double, val minutes: Double)

/** Legs needed to cost a detour to [PriceResult.store]. */
data class Detour(
    val hereToStore: Leg,
    /** Null when no home location is set; the engine then assumes a round trip. */
    val storeToHome: Leg? = null,
    val hereToHome: Leg? = null,
)

data class TripCost(
    val extraMiles: Double,
    val extraMinutes: Double,
    val fuelCost: Double,
    val timeCost: Double,
    val wearCost: Double,
) {
    val total: Double get() = fuelCost + timeCost + wearCost
}

data class Option(
    val result: PriceResult,
    val trip: TripCost?,
    val netSavings: Double,
    val driveMiles: Double?,
    val driveMinutes: Double?,
)

enum class Decision { BUY, BYE }

data class Verdict(
    val decision: Decision,
    val best: Option?,
    /** All options, best net savings first. */
    val options: List<Option>,
)

data class VerdictParams(
    val priceHere: Double,
    val quantity: Int,
    val gasPrice: Double,
    val mpg: Double,
    val valueOfTimePerHour: Double,
    val wearPerMile: Double,
    val minSavings: Double,
    val includeOnline: Boolean,
)

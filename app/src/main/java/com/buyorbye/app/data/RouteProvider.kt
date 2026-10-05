package com.buyorbye.app.data

import com.buyorbye.app.domain.Detour
import com.buyorbye.app.domain.Geo
import com.buyorbye.app.domain.LatLng
import com.buyorbye.app.domain.Leg

/**
 * Drive distance/time via Google Routes API, falling back to a straight-line estimate
 * when no Maps key is configured or the request fails.
 */
class RouteProvider(private val http: Http, private val mapsKey: String) {

    data class Detours(val byStore: Map<LatLng, Detour>, val estimated: Boolean)

    suspend fun detours(here: LatLng, home: LatLng?, stores: List<LatLng>): Detours {
        if (stores.isEmpty()) return Detours(emptyMap(), estimated = false)
        val routed = if (mapsKey.isNotBlank()) runCatching { routeMatrix(here, home, stores) }.getOrNull() else null
        return routed?.let { Detours(it, estimated = false) } ?: Detours(estimate(here, home, stores), estimated = true)
    }

    private fun estimate(here: LatLng, home: LatLng?, stores: List<LatLng>): Map<LatLng, Detour> =
        stores.associateWith { s ->
            Detour(
                hereToStore = Geo.estimateLeg(here, s),
                storeToHome = home?.let { Geo.estimateLeg(s, it) },
                hereToHome = home?.let { Geo.estimateLeg(here, it) },
            )
        }

    /** Origins: [here, store…]; destinations: [store…, home?]. */
    private suspend fun routeMatrix(here: LatLng, home: LatLng?, stores: List<LatLng>): Map<LatLng, Detour>? {
        val origins = listOf(here) + stores
        val destinations = stores + listOfNotNull(home)
        fun wp(p: LatLng) = """{"waypoint":{"location":{"latLng":{"latitude":${p.lat},"longitude":${p.lng}}}}}"""
        val body = """{"origins":[${origins.joinToString(",") { wp(it) }}],""" +
            """"destinations":[${destinations.joinToString(",") { wp(it) }}],"travelMode":"DRIVE"}"""

        val elements = http.postJson(
            "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix",
            body,
            mapOf(
                "X-Goog-Api-Key" to mapsKey,
                "X-Goog-FieldMask" to "originIndex,destinationIndex,distanceMeters,duration,condition",
            ),
        ).arr() ?: return null

        val legs = HashMap<Pair<Int, Int>, Leg>()
        for (e in elements) {
            val o = e.obj() ?: continue
            if (o.str("condition") != "ROUTE_EXISTS") continue
            // proto3 JSON omits zero values, so a missing index means 0.
            val oi = o.int("originIndex") ?: 0
            val di = o.int("destinationIndex") ?: 0
            val meters = o.dbl("distanceMeters") ?: 0.0
            val seconds = o.str("duration")?.removeSuffix("s")?.toDoubleOrNull() ?: 0.0
            legs[oi to di] = Leg(meters / METERS_PER_MILE, seconds / 60.0)
        }

        val homeIdx = stores.size
        val hereToHome = home?.let { legs[0 to homeIdx] ?: Geo.estimateLeg(here, it) }
        return stores.withIndex().associate { (i, s) ->
            s to Detour(
                hereToStore = legs[0 to i] ?: Geo.estimateLeg(here, s),
                storeToHome = home?.let { legs[(i + 1) to homeIdx] ?: Geo.estimateLeg(s, it) },
                hereToHome = hereToHome,
            )
        }
    }

    private companion object {
        const val METERS_PER_MILE = 1609.344
    }
}

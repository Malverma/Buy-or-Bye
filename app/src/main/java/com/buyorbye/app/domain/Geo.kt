package com.buyorbye.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_MILES = 3958.8

    /** Straight roads are rare; scale crow-flies distance to approximate driving. */
    private const val ROAD_FACTOR = 1.3
    private const val AVG_TOWN_MPH = 25.0

    fun haversineMiles(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_MILES * asin(sqrt(h))
    }

    fun estimateLeg(a: LatLng, b: LatLng): Leg {
        val miles = haversineMiles(a, b) * ROAD_FACTOR
        return Leg(miles, miles / AVG_TOWN_MPH * 60)
    }
}

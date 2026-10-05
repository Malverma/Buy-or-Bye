package com.buyorbye.app.domain

/**
 * Decides BUY (stay) or BYE (go elsewhere). Pure function of its inputs so the
 * verdict can be recomputed instantly when quantity or settings change.
 */
object VerdictEngine {

    fun tripCost(detour: Detour, p: VerdictParams): TripCost {
        val (miles, minutes) = if (detour.storeToHome != null && detour.hereToHome != null) {
            // Extra driving compared to going straight home from here.
            val m = detour.hereToStore.miles + detour.storeToHome.miles - detour.hereToHome.miles
            val t = detour.hereToStore.minutes + detour.storeToHome.minutes - detour.hereToHome.minutes
            m.coerceAtLeast(0.0) to t.coerceAtLeast(0.0)
        } else {
            2 * detour.hereToStore.miles to 2 * detour.hereToStore.minutes
        }
        val fuel = if (p.mpg > 0) miles / p.mpg * p.gasPrice else 0.0
        return TripCost(
            extraMiles = miles,
            extraMinutes = minutes,
            fuelCost = fuel,
            timeCost = minutes / 60.0 * p.valueOfTimePerHour,
            wearCost = miles * p.wearPerMile,
        )
    }

    fun decide(
        results: List<PriceResult>,
        detours: Map<PriceResult, Detour>,
        p: VerdictParams,
    ): Verdict {
        val qty = p.quantity.coerceAtLeast(1)
        val options = results.mapNotNull { r ->
            when (r.channel) {
                Channel.IN_STORE -> {
                    val detour = detours[r] ?: return@mapNotNull null
                    val trip = tripCost(detour, p)
                    Option(
                        result = r,
                        trip = trip,
                        netSavings = (p.priceHere - r.price) * qty - trip.total,
                        driveMiles = detour.hereToStore.miles,
                        driveMinutes = detour.hereToStore.minutes,
                    )
                }
                Channel.ONLINE -> Option(
                    result = r,
                    trip = null,
                    // Shipping is charged once per order, not per item.
                    netSavings = (p.priceHere - r.price) * qty - (r.shipping ?: 0.0),
                    driveMiles = null,
                    driveMinutes = null,
                )
            }
        }.sortedWith(
            // Likely-different items sink to the bottom; they're shown for reference only.
            compareBy<Option> { it.result.match == MatchLevel.DIFFERENT }.thenByDescending { it.netSavings },
        )

        val best = options.firstOrNull {
            it.result.match != MatchLevel.DIFFERENT && (p.includeOnline || it.result.channel == Channel.IN_STORE)
        }
        val decision = if (best != null && best.netSavings >= p.minSavings) Decision.BYE else Decision.BUY
        return Verdict(decision, best, options)
    }
}

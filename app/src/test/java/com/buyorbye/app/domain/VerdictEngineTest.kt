package com.buyorbye.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerdictEngineTest {
    private val params = VerdictParams(
        priceHere = 20.0,
        quantity = 1,
        gasPrice = 3.0,
        mpg = 30.0,
        valueOfTimePerHour = 15.0,
        wearPerMile = 0.0,
        minSavings = 3.0,
        includeOnline = true,
    )
    private val store = StoreLocation("Walmart", null, LatLng(0.0, 0.0))

    private fun inStore(price: Double) = PriceResult("Walmart", "x", Channel.IN_STORE, price, store = store)
    private fun online(price: Double, shipping: Double?) = PriceResult("Amazon", "x", Channel.ONLINE, price, shipping = shipping)

    @Test
    fun roundTripWhenNoHome() {
        val trip = VerdictEngine.tripCost(Detour(Leg(5.0, 10.0)), params)
        assertEquals(10.0, trip.extraMiles, 1e-9)
        assertEquals(20.0, trip.extraMinutes, 1e-9)
        assertEquals(1.0, trip.fuelCost, 1e-9) // 10 mi / 30 mpg * $3
        assertEquals(5.0, trip.timeCost, 1e-9) // 20 min @ $15/hr
    }

    @Test
    fun detourMeasuredAgainstDriveHome() {
        // Here→store 3, store→home 4, here→home 5: only 2 extra miles.
        val trip = VerdictEngine.tripCost(Detour(Leg(3.0, 6.0), Leg(4.0, 8.0), Leg(5.0, 10.0)), params)
        assertEquals(2.0, trip.extraMiles, 1e-9)
        assertEquals(4.0, trip.extraMinutes, 1e-9)
    }

    @Test
    fun storeOnTheWayHomeCostsNothingExtraNotNegative() {
        val trip = VerdictEngine.tripCost(Detour(Leg(2.0, 4.0), Leg(2.0, 4.0), Leg(5.0, 10.0)), params)
        assertEquals(0.0, trip.extraMiles, 1e-9)
        assertEquals(0.0, trip.total, 1e-9)
    }

    @Test
    fun byeWhenSavingsBeatTripCostAndMinimum() {
        val r = inStore(10.0)
        val v = VerdictEngine.decide(listOf(r), mapOf(r to Detour(Leg(5.0, 10.0))), params)
        assertEquals(Decision.BYE, v.decision)
        assertEquals(4.0, v.best!!.netSavings, 1e-9) // 10 saved - 1 gas - 5 time
    }

    @Test
    fun buyWhenTripEatsTheSavings() {
        val r = inStore(15.0)
        val v = VerdictEngine.decide(listOf(r), mapOf(r to Detour(Leg(5.0, 10.0))), params)
        assertEquals(Decision.BUY, v.decision)
        assertEquals(-1.0, v.best!!.netSavings, 1e-9)
    }

    @Test
    fun quantityMultipliesSavingsNotTripCost() {
        val r = inStore(15.0)
        val v = VerdictEngine.decide(listOf(r), mapOf(r to Detour(Leg(5.0, 10.0))), params.copy(quantity = 3))
        assertEquals(Decision.BYE, v.decision)
        assertEquals(9.0, v.best!!.netSavings, 1e-9) // 15 - 6
    }

    @Test
    fun onlineShippingChargedOnce() {
        val v = VerdictEngine.decide(listOf(online(15.0, 2.0)), emptyMap(), params.copy(quantity = 2))
        assertEquals(8.0, v.best!!.netSavings, 1e-9)
    }

    @Test
    fun onlineIgnoredForVerdictWhenDisabled() {
        val v = VerdictEngine.decide(listOf(online(5.0, 0.0)), emptyMap(), params.copy(includeOnline = false))
        assertEquals(Decision.BUY, v.decision)
        assertNull(v.best)
        assertEquals(1, v.options.size)
    }

    @Test
    fun inStoreWithoutRouteIsSkipped() {
        val v = VerdictEngine.decide(listOf(inStore(1.0)), emptyMap(), params)
        assertEquals(Decision.BUY, v.decision)
        assertNull(v.best)
    }

    @Test
    fun zeroMpgDoesNotDivideByZero() {
        val trip = VerdictEngine.tripCost(Detour(Leg(5.0, 10.0)), params.copy(mpg = 0.0))
        assertEquals(0.0, trip.fuelCost, 1e-9)
    }
}

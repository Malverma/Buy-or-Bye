package com.buyorbye.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParsingTest {
    @Test
    fun picksShelfPriceOverUnitPrice() {
        val lines = listOf("PRINGLES ORIGINAL 5.2OZ", "$2.48", "47.7¢ per oz", "Unit price $0.48/oz")
        assertEquals(2.48, PriceParsing.guessShelfPrice(lines)!!, 1e-9)
    }

    @Test
    fun handlesTagsWithoutDollarSign() {
        assertEquals(3.99, PriceParsing.guessShelfPrice(listOf("SALE", "3.99"))!!, 1e-9)
    }

    @Test
    fun noPrice() {
        assertNull(PriceParsing.guessShelfPrice(listOf("Great Value", "Paper Towels")))
    }

    @Test
    fun shipping() {
        assertEquals(0.0, PriceParsing.parseShipping("Free delivery by Thu")!!, 1e-9)
        assertEquals(5.99, PriceParsing.parseShipping("$5.99 delivery")!!, 1e-9)
        assertNull(PriceParsing.parseShipping(null))
        assertNull(PriceParsing.parseShipping("Delivery by Thu"))
    }
}

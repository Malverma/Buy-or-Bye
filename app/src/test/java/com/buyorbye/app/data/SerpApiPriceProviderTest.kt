package com.buyorbye.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerpApiPriceProviderTest {
    @Test
    fun normalizesRetailerNames() {
        assertEquals("Walmart", SerpApiPriceProvider.normalizeRetailer("Walmart - Seller Inc"))
        assertEquals("Amazon", SerpApiPriceProvider.normalizeRetailer("Amazon.com"))
        assertEquals("Target", SerpApiPriceProvider.normalizeRetailer("Target"))
    }

    @Test
    fun classifiesPhysicalChains() {
        assertTrue(SerpApiPriceProvider.isPhysicalChain("Walmart"))
        assertTrue(SerpApiPriceProvider.isPhysicalChain("Best Buy"))
        assertFalse(SerpApiPriceProvider.isPhysicalChain("Amazon"))
        assertFalse(SerpApiPriceProvider.isPhysicalChain("Targeted Deals LLC"))
    }
}

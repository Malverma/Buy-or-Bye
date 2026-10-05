package com.buyorbye.app.domain

import com.buyorbye.app.domain.MatchLevel.DIFFERENT
import com.buyorbye.app.domain.MatchLevel.LIKELY
import org.junit.Assert.assertEquals
import org.junit.Test

/** Titles below are real Google Shopping results for "Pringles Crisps Original 5.2oz". */
class ProductMatcherTest {
    private val ref = "Pringles Crisps Original 5.2oz"
    private fun level(title: String, brand: String? = "Pringles") = ProductMatcher.classify(ref, brand, title)

    @Test
    fun sameProductListings() {
        assertEquals(LIKELY, level("Pringles Original Potato Crisps Chips"))
        assertEquals(LIKELY, level("Pringles 5.2 oz Original Flavored Potato Crisps Chips |"))
        assertEquals(LIKELY, level("Pringles The Original Potato Chips, 148g/5.2 oz, Size: 1 count"))
        assertEquals(LIKELY, level("Potato Crisps Pringles"))
        assertEquals(LIKELY, level("Pack of 1 Pringles Potato Crisps Chips Lunch Snacks"))
        assertEquals(LIKELY, level("Pringles Original Potato Crisps Chips, Lunch Snacks, 5.2 oz Canister"))
    }

    @Test
    fun differentFlavors() {
        assertEquals(DIFFERENT, level("Pringles Sour Cream & Onion"))
        assertEquals(DIFFERENT, level("Pringles Cheddar Cheese Potato Crisps Chips"))
        assertEquals(DIFFERENT, level("Pringles Lightly Salted Original Potato Crisps Chips"))
    }

    @Test
    fun differentSizeOrPack() {
        assertEquals(DIFFERENT, level("Pringles Original 2.36 oz"))
        assertEquals(DIFFERENT, level("Pringles Grab & Go Original 1.3oz"))
        assertEquals(DIFFERENT, level("2x Pringles Original Flavored Potato Chips Snack Crisps"))
        assertEquals(DIFFERENT, level("Pringles Original Potato Crisps, 12 Pack"))
    }

    @Test
    fun moreVariantsFromRealResults() {
        assertEquals(DIFFERENT, level("Pringles All Dressed"))
        assertEquals(DIFFERENT, level("Pringles Original Snack Stacks Potato Crisps"))
        assertEquals(DIFFERENT, level("Pringles Original 185g"))
    }

    @Test
    fun sizeAgreement() {
        assertEquals(true, ProductMatcher.sizeAgrees(ref, "Pringles Original, 5.2 oz Canister"))
        assertEquals(true, ProductMatcher.sizeAgrees(ref, "Pringles The Original, 148g"))
        assertEquals(false, ProductMatcher.sizeAgrees(ref, "Pringles Potato Crisps Chips - 6.8 oz"))
        assertEquals(null, ProductMatcher.sizeAgrees(ref, "Pringles Potato Crisps Chips Original At Hy-Vee"))
        assertEquals(null, ProductMatcher.sizeAgrees("Pringles Original", "Pringles Original 5.2 oz"))
    }

    @Test
    fun differentBrand() {
        assertEquals(DIFFERENT, level("Lay's Classic Potato Chips 5.2 oz"))
    }

    @Test
    fun missingInfoIsNotAConflict() {
        // No size and no brand given by the user: only conflicts that are actually stated count.
        assertEquals(LIKELY, ProductMatcher.classify("pringles original", null, "Pringles Original Potato Crisps Chips"))
    }
}

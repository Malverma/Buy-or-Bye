package com.buyorbye.app.domain

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTitleNamerTest {
    @Test
    fun picksConsensusNameFromRealResults() {
        // Real Google results for the barcode 038000138416.
        val name = WebTitleNamer.pick(
            listOf(
                "seasonskosher.com",
                "https://www-qa3.vons.com/shop/pd/pringles-potato-c...",
                "Pringles Potato Crisps Chips, Lunch Snacks, On-The-Go ...",
                "Pringles Assorted Potato Chips (each) Delivery or Pickup ...",
                "PRINGLES ORIGINAL 5.26OZ 38000138416",
                "Brandclub - Get rewarded to shop",
                "Pringles Original Potato Crisp, 5.2 Ounces Per Pack",
                "Pringles Potato Crisps Chips, Lunch Snacks, On-The-Go ...",
            ),
        )!!
        assertTrue(name, name.startsWith("Pringles"))
        assertTrue(name, "Get rewarded" !in name && ".com" !in name && "Delivery" !in name)
    }

    @Test
    fun nothingUsable() {
        assertNull(WebTitleNamer.pick(listOf("example.com", "https://x.com/abc")))
    }
}

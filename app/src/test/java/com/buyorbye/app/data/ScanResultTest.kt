package com.buyorbye.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultTest {
    private fun guess(vararg lines: Pair<String, Int>) =
        ScanResult(null, null, lines.map { OcrLine(it.first, it.second) }).queryGuess()

    @Test
    fun prefersLargestPrintOverLongestLine() {
        assertEquals(
            "Pringles Original",
            guess(
                "INGREDIENTS: DRIED POTATOES, VEGETABLE OIL, CORN FLOUR" to 12,
                "Pringles" to 90,
                "Original" to 60,
                "Net Wt 5.2 oz (149g)" to 20,
            ),
        )
    }

    @Test
    fun skipsSymbolNoiseAndPrices() {
        assertEquals("Paper Towels", guess("$4.97" to 80, "#7Qx/3%:1" to 70, "Paper Towels" to 50))
    }

    @Test
    fun emptyWhenNothingLooksLikeWords() {
        assertEquals("", guess("0 38000 13841 6" to 40, "x7/Q:" to 30))
    }
}

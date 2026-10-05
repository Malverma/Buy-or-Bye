package com.buyorbye.app.domain

object PriceParsing {
    private val dollarPrice = Regex("""\$\s?(\d{1,4}(?:,\d{3})*)(?:[.,](\d{2}))?""")
    private val shelfCents = Regex("""(?<![\d.])(\d{1,3})[.,](\d{2})(?![\d])""")

    /**
     * Best guess at the shelf price in OCR text. Prefers amounts with a "$";
     * among candidates picks the largest, since unit prices ("12.5¢/oz") are smaller.
     */
    fun guessShelfPrice(lines: List<String>): Double? {
        val text = lines.joinToString("\n")
        val withSign = dollarPrice.findAll(text).mapNotNull { toDouble(it.groupValues[1], it.groupValues[2]) }.toList()
        val candidates = withSign.ifEmpty {
            shelfCents.findAll(text).mapNotNull { toDouble(it.groupValues[1], it.groupValues[2]) }.toList()
        }
        return candidates.filter { it in 0.25..5000.0 }.maxOrNull()
    }

    /** Parses shipping from Google Shopping delivery text. Null when unknown. */
    fun parseShipping(delivery: String?): Double? {
        if (delivery.isNullOrBlank()) return null
        val d = delivery.lowercase()
        if ("free" in d) return 0.0
        val m = dollarPrice.find(delivery) ?: return null
        return toDouble(m.groupValues[1], m.groupValues[2])
    }

    private fun toDouble(whole: String, cents: String): Double? {
        val w = whole.replace(",", "").toDoubleOrNull() ?: return null
        val c = cents.toIntOrNull() ?: 0
        return w + c / 100.0
    }
}

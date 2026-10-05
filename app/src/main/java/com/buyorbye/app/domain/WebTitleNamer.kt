package com.buyorbye.app.domain

/**
 * Picks a product name from web search result titles for a barcode. Result titles are
 * noisy (site names, URLs, "Delivery or Pickup"), so it splits them into segments and
 * picks the one whose words the most results agree on.
 */
object WebTitleNamer {
    private val separators = Regex("""\s+[|\-–—]\s+""")
    private val noise = Regex(
        """(?i)\b(delivery or pickup( near me)?|near me|free shipping|buy online|upc|ean|gtin|barcode)\b|\(each\)|\.\.\.|…""",
    )
    private val longDigits = Regex("""\b\d{6,}\b""")

    fun pick(titles: List<String>): String? {
        val segments = titles.flatMap { title ->
            title.split(separators)
                .map { it.replace(noise, " ").replace(longDigits, " ").replace(Regex("""\s+"""), " ").trim(' ', ',', ':', ';') }
                .filter { seg ->
                    "http" !in seg && "www" !in seg && ".com" !in seg &&
                        seg.split(' ').count { w -> w.count(Char::isLetter) >= 3 } >= 2
                }
                .distinct()
        }
        if (segments.isEmpty()) return null

        // Document frequency: how many result titles each word appears in.
        val df = HashMap<String, Int>()
        titles.forEach { title -> ProductMatcher.tokens(title.lowercase()).toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }

        val best = segments.maxBy { seg ->
            ProductMatcher.tokens(seg.lowercase()).toSet().take(12).sumOf { tok -> (df[tok] ?: 0).takeIf { it >= 2 } ?: 0 }
        }
        val score = ProductMatcher.tokens(best.lowercase()).toSet().sumOf { tok -> (df[tok] ?: 0).takeIf { it >= 2 } ?: 0 }
        return best.takeIf { score >= 2 }
    }
}

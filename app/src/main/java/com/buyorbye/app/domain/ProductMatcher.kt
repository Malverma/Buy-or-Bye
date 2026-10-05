package com.buyorbye.app.domain

import java.util.Locale
import kotlin.math.abs

/** How confident we are that a listing is the product the user scanned. */
enum class MatchLevel {
    /** Listed on Google's product page for this exact item. */
    EXACT,

    /** Title is consistent with the product (brand present, no conflicting size/flavor/pack). */
    LIKELY,

    /** Title conflicts on brand, size, pack count, or flavor/variant. Excluded from the verdict. */
    DIFFERENT,
}

/**
 * Compares a listing title against the product the user confirmed. Deliberately
 * conservative: it only says DIFFERENT on a concrete conflict, never on missing info.
 */
object ProductMatcher {

    /** Flavor/variant words: if a listing has one the product doesn't, it's a different item. */
    private val VARIANT_WORDS = setOf(
        "sour", "cream", "onion", "cheddar", "cheese", "bbq", "barbecue", "barbeque", "ranch", "pizza",
        "jalapeno", "spicy", "hot", "buffalo", "salt", "vinegar", "lightly", "salted", "unsalted", "reduced",
        "fat", "light", "lite", "diet", "zero", "sugar", "decaf", "caffeine", "honey", "mustard", "garlic",
        "herb", "lime", "lemon", "orange", "cherry", "grape", "strawberry", "raspberry", "blueberry",
        "vanilla", "chocolate", "mint", "peppermint", "cinnamon", "caramel", "peanut", "dill", "pickle",
        "sweet", "smoky", "smoked", "chili", "chile", "wasabi", "sriracha", "teriyaki", "keto", "organic",
        "gluten", "unscented", "scented", "mini", "family", "party", "variety", "assorted", "travel",
        "jumbo", "king", "dressed", "ketchup", "pepper", "salsa", "nacho", "taco", "cajun", "chipotle",
        "habanero", "parmesan", "truffle", "seaweed", "flamin", "loaded", "baked", "kettle", "stack",
    )

    private const val SIZE_TOLERANCE = 0.06

    private val sizePattern = Regex(
        """(\d+(?:\.\d+)?)\s*(fl\.?\s?oz|oz|ounces?|lbs?|pounds?|kg|g|grams?|ml|l|liters?|litres?|ct|count)\b""",
    )
    private val packPatterns = listOf(
        Regex("""\bpack of (\d+)\b"""),
        Regex("""\bcase of (\d+)\b"""),
        Regex("""\b(\d+)[\s-]?(?:pack|pk)\b"""),
        Regex("""(?:^|\s)(\d+)\s?x\s"""),
    )

    fun classify(reference: String, brand: String?, title: String): MatchLevel {
        val ref = reference.lowercase(Locale.US)
        val t = title.lowercase(Locale.US)
        val refTokens = tokens(ref)
        val titleTokens = tokens(t)
        if (refTokens.isEmpty() || titleTokens.isEmpty()) return MatchLevel.LIKELY

        // Brand: the listing must mention the brand (or the product's first word).
        val brandTokens = (brand?.let { tokens(it.lowercase(Locale.US)) }.orEmpty() + refTokens.first()).toSet()
        if (brandTokens.none { it in titleTokens }) return MatchLevel.DIFFERENT

        if (sizesConflict(sizes(ref), sizes(t))) return MatchLevel.DIFFERENT
        if (packCount(t) > packCount(ref)) return MatchLevel.DIFFERENT
        if (titleTokens.any { it in VARIANT_WORDS && it !in refTokens }) return MatchLevel.DIFFERENT

        return MatchLevel.LIKELY
    }

    /**
     * Whether the title states the same size as the reference: true if they agree, false if
     * they conflict, null if either side doesn't state a size.
     */
    fun sizeAgrees(reference: String, title: String): Boolean? {
        val r = sizes(reference.lowercase(Locale.US))
        val t = sizes(title.lowercase(Locale.US))
        if (r.isEmpty() || t.isEmpty() || r.none { a -> t.any { it.dim == a.dim } }) return null
        return !sizesConflict(r, t)
    }

    private enum class Dim { OZ, COUNT }
    private data class Size(val amount: Double, val dim: Dim)

    /** Weight and volume are compared together in ounces (close enough to tell 5.2 oz from 2.5 oz). */
    private fun sizes(text: String): List<Size> = sizePattern.findAll(text).mapNotNull { m ->
        val n = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
        val unit = m.groupValues[2].replace(".", "").replace(" ", "")
        when {
            unit.startsWith("floz") || unit == "oz" || unit.startsWith("ounce") -> Size(n, Dim.OZ)
            unit.startsWith("lb") || unit.startsWith("pound") -> Size(n * 16, Dim.OZ)
            unit == "kg" -> Size(n * 35.274, Dim.OZ)
            unit == "g" || unit.startsWith("gram") -> Size(n / 28.3495, Dim.OZ)
            unit == "ml" -> Size(n / 29.5735, Dim.OZ)
            unit == "l" || unit.startsWith("lit") -> Size(n * 33.814, Dim.OZ)
            else -> Size(n, Dim.COUNT)
        }
    }.toList()

    /** Conflict only when both sides state a size of the same kind and none of them agree. */
    private fun sizesConflict(ref: List<Size>, title: List<Size>): Boolean = Dim.entries.any { dim ->
        val r = ref.filter { it.dim == dim }
        val t = title.filter { it.dim == dim }
        r.isNotEmpty() && t.isNotEmpty() && r.none { a -> t.any { b -> abs(a.amount - b.amount) <= SIZE_TOLERANCE * a.amount } }
    }

    private fun packCount(text: String): Int =
        packPatterns.flatMap { p -> p.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList() }.maxOrNull() ?: 1

    /** Lowercase word tokens with apostrophes dropped and simple plurals folded ("crisps" → "crisp"). */
    internal fun tokens(text: String): List<String> = text
        .replace("'", "").replace("’", "")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() && it.any(Char::isLetter) }
        .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
}

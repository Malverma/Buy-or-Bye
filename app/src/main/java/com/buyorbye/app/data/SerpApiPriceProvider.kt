package com.buyorbye.app.data

import com.buyorbye.app.domain.Channel
import com.buyorbye.app.domain.Geo
import com.buyorbye.app.domain.LatLng
import com.buyorbye.app.domain.MatchLevel
import com.buyorbye.app.domain.PriceParsing
import com.buyorbye.app.domain.PriceResult
import com.buyorbye.app.domain.Product
import com.buyorbye.app.domain.ProductMatcher
import com.buyorbye.app.domain.StoreLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Prices from Google Shopping (including Google's exact-product pages), and nearest store
 * branches from Google Maps, all fetched through SerpApi (https://serpapi.com), which
 * returns Google results as JSON.
 */
class SerpApiPriceProvider(
    private val http: Http,
    private val apiKey: String,
) : PriceProvider {

    /** Nearest branch per retailer, cached for the session (keyed by retailer + rounded location). */
    private val storeCache = ConcurrentHashMap<String, StoreLocation?>()

    override suspend fun search(product: Product, here: LatLng?, cityName: String?, radiusMiles: Double): PriceSearch {
        require(apiKey.isNotBlank()) { "No SerpApi key. Add SERPAPI_KEY to local.properties and rebuild." }
        val warnings = mutableListOf<String>()

        val shopping = try {
            shopping(product.query, cityName)
        } catch (e: HttpException) {
            if (cityName == null) throw e
            // SerpApi rejects locations it doesn't recognize; retry without one.
            warnings += "Results not localized to $cityName"
            shopping(product.query, null)
        }

        val listings = shopping["shopping_results"].arr().orEmpty().mapNotNull { parseListing(it.obj(), product) }

        // Google groups identical products (same barcode) onto one product page that lists
        // every store selling that exact item. Open the page for the best-matching listing.
        // Prefer a listing whose title confirms the size, since one without a size can belong to
        // another size's page (e.g. the 6.8 oz can when the user scanned the 5.2 oz one).
        val pageListing = listings
            .filter { it.result.match != MatchLevel.DIFFERENT && it.pageToken != null }
            .sortedWith(
                compareByDescending<Listing> { ProductMatcher.sizeAgrees(product.query, it.result.title) == true }
                    .thenByDescending { it.multipleSources },
            )
            .firstOrNull()
        val exact = pageListing?.let { runCatching { productPageOffers(it.pageToken!!, product) }.getOrNull() }.orEmpty()
        if (exact.none { it.match == MatchLevel.EXACT }) {
            warnings += "Couldn't confirm Google's page for this exact product, so listings are matched by name. Check the photos."
        }

        val offers = (exact + listings.map { it.result })
            .filterNot { it.retailer.lowercase(Locale.US) in SECOND_HAND }
            // One offer per retailer, plus at most one from its marketplace sellers; prefer the
            // surest match, then the lowest price.
            .groupBy { it.retailer.lowercase(Locale.US) + if (it.seller != null) "|marketplace" else "" }
            .map { (_, same) -> same.minWith(compareBy<PriceResult> { it.match.ordinal }.thenBy { it.price }) }
            .sortedBy { it.price }

        if (here == null) return PriceSearch(offers, warnings + "Location off: showing online prices only")

        // Look up branches for the cheapest physical chains only, to limit API usage. Marketplace
        // sellers ship from elsewhere, and different items aren't worth a trip.
        val inStoreCandidates = offers.filter {
            it.seller == null && it.match != MatchLevel.DIFFERENT && isPhysicalChain(it.retailer)
        }.take(MAX_STORE_LOOKUPS)
        val stores = coroutineScope {
            inStoreCandidates.associate { offer ->
                offer.retailer to async { runCatching { nearestStore(offer.retailer, here) }.getOrNull() }
            }.mapValues { it.value.await() }
        }

        val results = offers.mapNotNull { offer ->
            val store = stores[offer.retailer]?.takeIf { offer.seller == null && offer.match != MatchLevel.DIFFERENT }
                ?: return@mapNotNull offer
            val miles = Geo.haversineMiles(here, store.location)
            when {
                // The user is probably standing in this store; it's not an alternative.
                miles < HERE_RADIUS_MILES -> null
                miles <= radiusMiles -> offer.copy(channel = Channel.IN_STORE, store = store)
                else -> offer
            }
        }
        return PriceSearch(results, warnings)
    }

    private class Listing(val result: PriceResult, val pageToken: String?, val multipleSources: Boolean)

    private suspend fun shopping(query: String, cityName: String?): JsonObject {
        val url = base("google_shopping")
            .addQueryParameter("q", query)
            .apply { if (cityName != null) addQueryParameter("location", cityName) }
            .build()
        return http.getJson(url).obj() ?: JsonObject(emptyMap())
    }

    private fun parseListing(o: JsonObject?, product: Product): Listing? {
        o ?: return null
        val price = o.dbl("extracted_price") ?: return null
        val source = o.str("source") ?: return null
        val title = o.str("title").orEmpty()
        val delivery = o.str("delivery")
        val result = PriceResult(
            retailer = normalizeRetailer(source),
            seller = marketplaceSeller(source),
            title = title,
            channel = Channel.ONLINE,
            price = price,
            shipping = PriceParsing.parseShipping(delivery),
            deliveryText = delivery,
            link = o.str("link") ?: o.str("product_link"),
            thumbnail = o.str("thumbnail") ?: o.str("serpapi_thumbnail"),
            match = ProductMatcher.classify(product.query, product.brand, title),
        )
        val multiple = (o["multiple_sources"] as? JsonPrimitive)?.booleanOrNull ?: false
        return Listing(result, o.str("immersive_product_page_token"), multiple)
    }

    /** Stores on Google's product page: all selling this exact item. */
    private suspend fun productPageOffers(token: String, product: Product): List<PriceResult> {
        val url = base("google_immersive_product").addQueryParameter("page_token", token).build()
        val page = http.getJson(url).obj()?.get("product_results").obj() ?: return emptyList()
        val thumbnail = page["thumbnails"].arr()?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content }
        val stores = page["stores"].arr().orEmpty().mapNotNull { it.obj() }

        // Only call the page an exact match when it confirms the size the user is looking for.
        // Otherwise its offers are just "likely", each still checked on its own title.
        val titles = stores.mapNotNull { it.str("title") } + listOfNotNull(page.str("title"))
        val sizeChecks = titles.mapNotNull { ProductMatcher.sizeAgrees(product.query, it) }
        val referenceHasSize = ProductMatcher.sizeAgrees(product.query, product.query) != null
        val confirmed = !referenceHasSize || (sizeChecks.any { it } && sizeChecks.count { !it } <= sizeChecks.count { it } / 2)

        return stores.mapNotNull { o ->
            val price = o.dbl("extracted_price") ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val title = o.str("title") ?: page.str("title").orEmpty()
            val details = o["details_and_offers"].arr()?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            val delivery = details.firstOrNull { d -> listOf("deliver", "shipping").any { it in d.lowercase(Locale.US) } }
            PriceResult(
                retailer = normalizeRetailer(name),
                seller = marketplaceSeller(name),
                title = title,
                channel = Channel.ONLINE,
                price = price,
                shipping = o.dbl("shipping_extracted") ?: PriceParsing.parseShipping(delivery),
                deliveryText = delivery ?: details.firstOrNull(),
                link = o.str("link"),
                thumbnail = thumbnail,
                // Sellers occasionally list a multipack on the page; keep the title check as a guard.
                match = when {
                    ProductMatcher.classify(product.query, product.brand, title) == MatchLevel.DIFFERENT -> MatchLevel.DIFFERENT
                    confirmed -> MatchLevel.EXACT
                    else -> MatchLevel.LIKELY
                },
            )
        }
    }

    private suspend fun nearestStore(retailer: String, here: LatLng): StoreLocation? {
        val key = "%s|%.2f|%.2f".format(Locale.US, retailer.lowercase(Locale.US), here.lat, here.lng)
        if (storeCache.containsKey(key)) return storeCache[key]

        val url = base("google_maps")
            .addQueryParameter("q", retailer)
            .addQueryParameter("type", "search")
            .addQueryParameter("ll", "@%.6f,%.6f,13z".format(Locale.US, here.lat, here.lng))
            .build()
        val resp = http.getJson(url).obj()
        val places = resp?.get("local_results").arr()?.mapNotNull { it.obj() }
            ?: listOfNotNull(resp?.get("place_results").obj())

        val brand = retailer.lowercase(Locale.US).substringBefore(" ")
        val store = places
            .filter { it.str("title")?.lowercase(Locale.US)?.contains(brand) == true }
            .mapNotNull { p ->
                val gps = p["gps_coordinates"].obj() ?: return@mapNotNull null
                val lat = gps.dbl("latitude") ?: return@mapNotNull null
                val lng = gps.dbl("longitude") ?: return@mapNotNull null
                StoreLocation(p.str("title").orEmpty(), p.str("address"), LatLng(lat, lng))
            }
            .minByOrNull { Geo.haversineMiles(here, it.location) }

        storeCache[key] = store
        return store
    }

    private fun base(engine: String): HttpUrl.Builder =
        "https://serpapi.com/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("engine", engine)
            .addQueryParameter("gl", "us")
            .addQueryParameter("hl", "en")
            .addQueryParameter("api_key", apiKey)

    companion object {
        private const val MAX_STORE_LOOKUPS = 4
        private const val HERE_RADIUS_MILES = 0.3

        /** Marketplaces whose listings are often used items, which would skew a new-item comparison. */
        private val SECOND_HAND = setOf("ebay", "mercari", "poshmark", "facebook marketplace", "offerup")

        private val PHYSICAL_CHAINS = listOf(
            "walmart", "target", "costco", "sam's club", "kroger", "best buy", "the home depot", "home depot",
            "lowe's", "cvs", "walgreens", "dollar general", "safeway", "meijer", "h-e-b", "heb", "publix",
            "albertsons", "aldi", "whole foods", "staples", "petco", "petsmart", "ulta", "dick's sporting goods",
            "bj's", "rite aid", "family dollar", "dollar tree", "fred meyer", "hy-vee", "wegmans", "food lion",
            "giant", "stop & shop", "harris teeter", "sprouts", "trader joe's", "kohl's", "macy's", "jcpenney",
            "academy sports", "tractor supply", "michaels", "joann", "barnes & noble", "gamestop", "ikea",
            "marshalls", "tj maxx", "ross", "big lots", "five below", "ace hardware", "menards", "office depot",
            "shoprite", "vons", "ralphs", "jewel-osco", "winco", "smart & final", "bevmo!", "bevmo",
        )

        fun isPhysicalChain(retailer: String): Boolean {
            val r = retailer.lowercase(Locale.US)
            return PHYSICAL_CHAINS.any { r == it || r.startsWith("$it ") }
        }

        /** "Walmart - Seller Co" → "Walmart", "Amazon.com" → "Amazon". */
        fun normalizeRetailer(source: String): String =
            source.substringBefore(" - ").removeSuffix(".com").removeSuffix(".Com").trim()

        /** "Walmart - Seller Co" → "Seller Co"; null when the retailer sells it directly. */
        fun marketplaceSeller(source: String): String? =
            source.substringAfter(" - ", "").trim().takeIf { it.isNotEmpty() }
    }
}

package com.buyorbye.app.data

import com.buyorbye.app.domain.Channel
import com.buyorbye.app.domain.Geo
import com.buyorbye.app.domain.LatLng
import com.buyorbye.app.domain.PriceParsing
import com.buyorbye.app.domain.PriceResult
import com.buyorbye.app.domain.StoreLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Prices from Google Shopping, and nearest store branches from Google Maps, both
 * fetched through SerpApi (https://serpapi.com), which returns Google results as JSON.
 */
class SerpApiPriceProvider(
    private val http: Http,
    private val apiKey: String,
) : PriceProvider {

    /** Nearest branch per retailer, cached for the session (keyed by retailer + rounded location). */
    private val storeCache = ConcurrentHashMap<String, StoreLocation?>()

    override suspend fun search(query: String, here: LatLng?, cityName: String?, radiusMiles: Double): PriceSearch {
        require(apiKey.isNotBlank()) { "No SerpApi key. Add SERPAPI_KEY to local.properties and rebuild." }
        val warnings = mutableListOf<String>()

        val shopping = try {
            shopping(query, cityName)
        } catch (e: HttpException) {
            if (cityName == null) throw e
            // SerpApi rejects locations it doesn't recognize; retry without one.
            warnings += "Results not localized to $cityName"
            shopping(query, null)
        }

        val offers = shopping["shopping_results"].arr().orEmpty().mapNotNull { parseOffer(it.obj()) }
            .filterNot { it.retailer.lowercase(Locale.US) in SECOND_HAND }
            .groupBy { it.retailer.lowercase(Locale.US) }
            .map { (_, sameRetailer) -> sameRetailer.minBy { it.price } }
            .sortedBy { it.price }

        if (here == null) return PriceSearch(offers, warnings + "Location off: showing online prices only")

        // Look up branches for the cheapest physical chains only, to limit API usage.
        val chainsToLocate = offers.filter { isPhysicalChain(it.retailer) }.take(MAX_STORE_LOOKUPS)
        val stores = coroutineScope {
            chainsToLocate.associate { offer ->
                offer.retailer to async { runCatching { nearestStore(offer.retailer, here) }.getOrNull() }
            }.mapValues { it.value.await() }
        }

        val results = offers.mapNotNull { offer ->
            val store = stores[offer.retailer] ?: return@mapNotNull offer
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

    private suspend fun shopping(query: String, cityName: String?): JsonObject {
        val url = base("google_shopping")
            .addQueryParameter("q", query)
            .apply { if (cityName != null) addQueryParameter("location", cityName) }
            .build()
        return http.getJson(url).obj() ?: JsonObject(emptyMap())
    }

    private fun parseOffer(o: JsonObject?): PriceResult? {
        o ?: return null
        val price = o.dbl("extracted_price") ?: return null
        val source = o.str("source") ?: return null
        val delivery = o.str("delivery")
        return PriceResult(
            retailer = normalizeRetailer(source),
            title = o.str("title").orEmpty(),
            channel = Channel.ONLINE,
            price = price,
            shipping = PriceParsing.parseShipping(delivery),
            deliveryText = delivery,
            link = o.str("link") ?: o.str("product_link"),
            thumbnail = o.str("thumbnail"),
        )
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
        )

        fun isPhysicalChain(retailer: String): Boolean {
            val r = retailer.lowercase(Locale.US)
            return PHYSICAL_CHAINS.any { r == it || r.startsWith("$it ") }
        }

        /** "Walmart - Seller Co" → "Walmart", "Amazon.com" → "Amazon". */
        fun normalizeRetailer(source: String): String =
            source.substringBefore(" - ").removeSuffix(".com").removeSuffix(".Com").trim()
    }
}

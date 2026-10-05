package com.buyorbye.app.data

import com.buyorbye.app.domain.Product
import com.buyorbye.app.domain.WebTitleNamer
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Where a barcode's product name came from. */
enum class NameFrom { PRODUCT_DATABASE, WEB_SEARCH }

data class Identified(val product: Product, val from: NameFrom)

/**
 * Barcode → product name. Tries UPCitemdb's free trial endpoint (no key, ~100 lookups/day)
 * first, then a Google web search for the code via SerpApi when the database doesn't know it.
 */
class ProductLookup(private val http: Http, private val serpApiKey: String) {

    suspend fun byUpc(upc: String): Identified? =
        upcItemDb(upc)?.let { Identified(it, NameFrom.PRODUCT_DATABASE) }
            ?: webSearch(upc)?.let { Identified(it, NameFrom.WEB_SEARCH) }

    private suspend fun upcItemDb(upc: String): Product? {
        val url = "https://api.upcitemdb.com/prod/trial/lookup".toHttpUrl().newBuilder()
            .addQueryParameter("upc", upc)
            .build()
        val item = runCatching { http.getJson(url) }.getOrNull()
            .obj()?.get("items").arr()?.firstOrNull().obj()
            ?: return null
        val title = item.str("title")?.takeIf { it.isNotBlank() } ?: return null
        return Product(
            query = title,
            upc = upc,
            brand = item.str("brand")?.takeIf { it.isNotBlank() },
            imageUrl = item["images"].arr()?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content },
        )
    }

    private suspend fun webSearch(upc: String): Product? {
        if (serpApiKey.isBlank()) return null
        val url = "https://serpapi.com/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("engine", "google")
            .addQueryParameter("q", upc)
            .addQueryParameter("gl", "us")
            .addQueryParameter("hl", "en")
            .addQueryParameter("api_key", serpApiKey)
            .build()
        val titles = runCatching { http.getJson(url) }.getOrNull()
            .obj()?.get("organic_results").arr()
            ?.mapNotNull { it.obj()?.str("title") }
            .orEmpty()
        val name = WebTitleNamer.pick(titles) ?: return null
        return Product(query = name, upc = upc)
    }
}

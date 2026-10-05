package com.buyorbye.app.data

import com.buyorbye.app.domain.Product
import okhttp3.HttpUrl.Companion.toHttpUrl

/** UPC → product name via UPCitemdb's free trial endpoint (no key, ~100 lookups/day). */
class ProductLookup(private val http: Http) {

    suspend fun byUpc(upc: String): Product? {
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
            brand = item.str("brand"),
            imageUrl = item["images"].arr()?.firstNotNullOfOrNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
        )
    }
}

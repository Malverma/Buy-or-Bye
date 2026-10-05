package com.buyorbye.app.data

import com.buyorbye.app.domain.LatLng
import com.buyorbye.app.domain.PriceResult
import com.buyorbye.app.domain.Product

data class PriceSearch(
    /** Best offer per retailer, already classified as in-store or online and by match level. */
    val results: List<PriceResult>,
    /** Sources or steps that failed but didn't stop the search. */
    val warnings: List<String> = emptyList(),
)

interface PriceProvider {
    /**
     * @param product the confirmed product; its name (and brand, when known) is used both to
     *   search and to check that each listing is the same item.
     * @param here user's location, or null if unavailable (results are then online-only).
     * @param cityName e.g. "Austin, Texas, United States", to localize results.
     */
    suspend fun search(product: Product, here: LatLng?, cityName: String?, radiusMiles: Double): PriceSearch
}

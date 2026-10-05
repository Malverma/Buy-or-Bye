package com.buyorbye.app.data

import com.buyorbye.app.domain.LatLng
import com.buyorbye.app.domain.PriceResult

data class PriceSearch(
    /** Cheapest offer per retailer, already classified as in-store or online. */
    val results: List<PriceResult>,
    /** Sources or steps that failed but didn't stop the search. */
    val warnings: List<String> = emptyList(),
)

interface PriceProvider {
    /**
     * @param here user's location, or null if unavailable (results are then online-only).
     * @param cityName e.g. "Austin, Texas, United States", to localize results.
     */
    suspend fun search(query: String, here: LatLng?, cityName: String?, radiusMiles: Double): PriceSearch
}

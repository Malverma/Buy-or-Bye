package com.buyorbye.app.data

import android.content.Context
import com.buyorbye.app.BuildConfig

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {
    private val http = Http()
    val settings = SettingsStore(context)
    val location = LocationProvider(context)
    val scanner = Scanner()
    val products = ProductLookup(http, BuildConfig.SERPAPI_KEY)
    val prices: PriceProvider = SerpApiPriceProvider(http, BuildConfig.SERPAPI_KEY)
    val gas = GasPriceProvider(http, BuildConfig.MAPS_API_KEY)
    val routes = RouteProvider(http, BuildConfig.MAPS_API_KEY)
}

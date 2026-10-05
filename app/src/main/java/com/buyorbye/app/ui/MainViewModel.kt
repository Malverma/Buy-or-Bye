package com.buyorbye.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buyorbye.app.BuyOrByeApp
import com.buyorbye.app.data.GasPrice
import com.buyorbye.app.data.HistoryItem
import com.buyorbye.app.data.PriceSearch
import com.buyorbye.app.data.ScanResult
import com.buyorbye.app.data.Settings
import com.buyorbye.app.domain.Channel
import com.buyorbye.app.domain.Detour
import com.buyorbye.app.domain.PriceResult
import com.buyorbye.app.domain.Product
import com.buyorbye.app.domain.Verdict
import com.buyorbye.app.domain.VerdictEngine
import com.buyorbye.app.domain.VerdictParams
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { Scan, Confirm, Verdict, Settings, History }

data class ConfirmState(
    val query: String,
    val upc: String? = null,
    val priceText: String = "",
    val imageUrl: String? = null,
    val identifying: Boolean = false,
    /** The user's own photo from the scan; null for manual search and history re-runs. */
    val photo: Bitmap? = null,
)

data class CheckData(
    val product: Product,
    val priceHere: Double,
    val search: PriceSearch,
    val detours: Map<PriceResult, Detour>,
    val gas: GasPrice,
    val routesEstimated: Boolean,
)

sealed interface CheckState {
    data object Idle : CheckState
    data class Loading(val step: String) : CheckState
    data class Ready(val data: CheckData) : CheckState
    data class Failed(val message: String) : CheckState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as BuyOrByeApp).container

    private val _screen = MutableStateFlow(Screen.Scan)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _confirm = MutableStateFlow<ConfirmState?>(null)
    val confirm: StateFlow<ConfirmState?> = _confirm.asStateFlow()

    private val _check = MutableStateFlow<CheckState>(CheckState.Idle)
    val check: StateFlow<CheckState> = _check.asStateFlow()

    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity.asStateFlow()

    val settings: StateFlow<Settings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val history: StateFlow<List<HistoryItem>> = c.settings.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scanner get() = c.scanner

    private var lookupJob: Job? = null
    private var checkJob: Job? = null

    fun navigate(to: Screen) {
        _screen.value = to
    }

    fun back() {
        _screen.value = when (_screen.value) {
            Screen.Verdict -> Screen.Confirm
            else -> Screen.Scan
        }
        if (_screen.value == Screen.Scan) {
            checkJob?.cancel()
            lookupJob?.cancel()
        }
    }

    fun onScanned(result: ScanResult) {
        _confirm.value = ConfirmState(
            query = result.queryGuess().ifBlank { result.upc.orEmpty() },
            upc = result.upc,
            priceText = result.shelfPrice?.let { "%.2f".format(it) }.orEmpty(),
            identifying = result.upc != null,
            photo = result.photo,
        )
        _screen.value = Screen.Confirm
        val upc = result.upc ?: return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val product = c.products.byUpc(upc)
            _confirm.update { cur ->
                cur?.copy(
                    query = product?.query ?: cur.query,
                    imageUrl = product?.imageUrl,
                    identifying = false,
                )
            }
        }
    }

    /** Manual search without a photo. */
    fun startManual() {
        _confirm.value = ConfirmState(query = "")
        _screen.value = Screen.Confirm
    }

    fun editConfirm(query: String? = null, priceText: String? = null) {
        _confirm.update { it?.copy(query = query ?: it.query, priceText = priceText ?: it.priceText) }
    }

    fun setQuantity(q: Int) {
        _quantity.value = q.coerceIn(1, 99)
    }

    fun runCheck() {
        val cs = _confirm.value ?: return
        val priceHere = cs.priceText.replace("$", "").trim().toDoubleOrNull() ?: return
        val query = cs.query.trim().ifEmpty { return }
        lookupJob?.cancel()
        _quantity.value = 1
        _screen.value = Screen.Verdict

        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            if (!isOnline()) {
                _check.value = CheckState.Failed("You're offline. Connect to Wi-Fi or mobile data to check prices.")
                return@launch
            }
            try {
                val s = settings.value
                _check.value = CheckState.Loading("Finding your location…")
                val here = c.location.current()
                val city = here?.let { c.location.cityName(it) }

                _check.value = CheckState.Loading("Checking prices…")
                val gasJob = async { resolveGas(s, here) }
                val search = c.prices.search(query, here, city, s.radiusMiles)

                val inStore = search.results.filter { it.channel == Channel.IN_STORE && it.store != null }
                val detours = if (here != null && inStore.isNotEmpty()) {
                    _check.value = CheckState.Loading("Working out the drive…")
                    c.routes.detours(here, s.home, inStore.map { it.store!!.location })
                } else null
                val byResult = inStore.mapNotNull { r -> detours?.byStore?.get(r.store!!.location)?.let { r to it } }.toMap()

                val data = CheckData(
                    product = Product(query = query, upc = cs.upc, imageUrl = cs.imageUrl),
                    priceHere = priceHere,
                    search = search,
                    detours = byResult,
                    gas = gasJob.await(),
                    routesEstimated = detours?.estimated ?: false,
                )
                _check.value = CheckState.Ready(data)
                recordHistory(data)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _check.value = CheckState.Failed(e.message ?: "Something went wrong checking prices.")
            }
        }
    }

    fun verdict(data: CheckData, s: Settings, qty: Int): Verdict =
        VerdictEngine.decide(data.search.results, data.detours, params(data, s, qty))

    fun params(data: CheckData, s: Settings, qty: Int) = VerdictParams(
        priceHere = data.priceHere,
        quantity = qty,
        gasPrice = data.gas.pricePerGallon,
        mpg = s.mpg,
        valueOfTimePerHour = s.valueOfTimePerHour,
        wearPerMile = if (s.wearEnabled) s.wearPerMile else 0.0,
        minSavings = s.minSavings,
        includeOnline = s.includeOnline,
    )

    fun rerun(item: HistoryItem) {
        _confirm.value = ConfirmState(query = item.query, upc = item.upc, priceText = "%.2f".format(item.priceHere))
        runCheck()
    }

    fun saveSettings(s: Settings) {
        viewModelScope.launch { c.settings.save(s) }
    }

    /** Returns false when location is unavailable. */
    fun setHomeToCurrent(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val here = c.location.current()
            if (here != null) c.settings.save(settings.value.copy(home = here))
            onDone(here != null)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { c.settings.clearHistory() }
    }

    private suspend fun resolveGas(s: Settings, here: com.buyorbye.app.domain.LatLng?): GasPrice {
        if (!s.gasOverride && here != null) {
            runCatching { c.gas.nearby(here) }.getOrNull()?.let {
                return GasPrice(it, "nearby stations", estimated = false)
            }
        }
        return GasPrice(s.manualGasPrice, "your setting", estimated = !s.gasOverride)
    }

    private suspend fun recordHistory(data: CheckData) {
        val s = settings.first()
        val v = verdict(data, s, 1)
        c.settings.addHistory(
            HistoryItem(
                query = data.product.query,
                upc = data.product.upc,
                priceHere = data.priceHere,
                decision = v.decision,
                bestRetailer = v.best?.result?.retailer,
                netSavings = v.best?.netSavings,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

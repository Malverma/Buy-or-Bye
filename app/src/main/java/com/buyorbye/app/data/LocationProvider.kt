package com.buyorbye.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.buyorbye.app.domain.LatLng
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLng? {
        if (!hasPermission()) return null
        val fresh = withTimeoutOrNull(6_000) {
            val cts = CancellationTokenSource()
            runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await() }
                .getOrNull()
        }
        val loc = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
        return loc?.let { LatLng(it.latitude, it.longitude) }
    }

    /** "City, State, Country" in the form SerpApi's `location` parameter expects. */
    suspend fun cityName(p: LatLng): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.US)
        val address = if (Build.VERSION.SDK_INT >= 33) {
            // The listener never fires on some geocoder errors, so bound the wait.
            withTimeoutOrNull(4_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(p.lat, p.lng, 1) { cont.resume(it.firstOrNull()) }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(p.lat, p.lng, 1)?.firstOrNull() }.getOrNull()
        } ?: return@withContext null
        listOfNotNull(address.locality, address.adminArea, address.countryName)
            .takeIf { it.size == 3 }
            ?.joinToString(", ")
    }
}

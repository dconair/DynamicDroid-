package com.dynamicdroid.routing

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.dynamicdroid.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Aktuelle Position als Routenstart: zuletzt bekannte Position, sonst ein frischer Fix (GPS, dann Netzwerk). */
object CurrentLocation {
    private const val TIMEOUT_MS = 12_000L

    @SuppressLint("MissingPermission")
    suspend fun get(context: Context): LatLng? {
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
        }
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < 2 * 60_000 }
            ?.let { return LatLng(it.latitude, it.longitude) }
        for (provider in providers) {
            val fix = withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                }
            }
            if (fix != null) return LatLng(fix.latitude, fix.longitude)
        }
        return null
    }
}

package com.dynamicdroid.routing

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.LatLng
import com.dynamicdroid.state.NavigationState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * Verfolgt echte GPS-Fixes (Plattform-[LocationManager], keine Play-Services) und mappt sie auf eine Route.
 * Voraussetzung: ACCESS_FINE_LOCATION ist erteilt.
 */
class LocationRouteTracker(
    context: Context,
    private val navigationState: NavigationState,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    fun locations(): Flow<Location> = callbackFlow {
        val listener = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper(),
        )
        awaitClose { locationManager.removeUpdates(listener) }
    }

    /** Wartet auf den ersten Fix, baut daraus [routeFactory] und verfolgt danach laufend den Fortschritt. */
    suspend fun trackFromFirstFix(routeFactory: (LatLng) -> ActiveRoute) {
        val flow = locations()
        val first = flow.first()
        val route = routeFactory(LatLng(first.latitude, first.longitude))
        val cumulative = RouteGeometry.cumulativeMeters(route.geometry)
        navigationState.start(route, RouteProgressCalculator.fromTraveled(route, 0.0, clock()))
        flow.collect { location -> onLocation(route, cumulative, location) }
    }

    /** Verfolgt eine vorhandene Route. */
    suspend fun track(route: ActiveRoute) {
        val cumulative = RouteGeometry.cumulativeMeters(route.geometry)
        navigationState.start(route, RouteProgressCalculator.fromTraveled(route, 0.0, clock()))
        locations().collect { onLocation(route, cumulative, it) }
    }

    private fun onLocation(route: ActiveRoute, cumulative: DoubleArray, location: Location) {
        val snap = RouteGeometry.snap(route.geometry, cumulative, LatLng(location.latitude, location.longitude))
        val offRoute = snap.distanceFromRouteMeters > OFF_ROUTE_THRESHOLD_M
        navigationState.updateProgress(
            RouteProgressCalculator.fromTraveled(route, snap.traveledMeters, clock(), offRoute),
        )
    }

    companion object {
        const val MIN_TIME_MS = 1_000L
        const val MIN_DISTANCE_M = 5f
        const val OFF_ROUTE_THRESHOLD_M = 50.0
    }
}

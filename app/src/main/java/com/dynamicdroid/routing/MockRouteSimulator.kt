package com.dynamicdroid.routing

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.state.NavigationState
import kotlinx.coroutines.delay

/**
 * Simuliert eine Fahrt entlang einer Route und schreibt den Fortschritt in [NavigationState].
 * Kehrt zurück, sobald das Ziel erreicht ist. [speedMultiplier] erlaubt Zeitraffer zum Testen.
 */
class MockRouteSimulator(
    private val navigationState: NavigationState,
    private val clock: () -> Long = System::currentTimeMillis,
    private val speedMps: Double = 13.9,
    private val tickMs: Long = 1_000,
) {
    suspend fun run(route: ActiveRoute, speedMultiplier: Double = 1.0) {
        var traveled = 0.0
        navigationState.start(route, RouteProgressCalculator.fromTraveled(route, traveled, clock()))
        while (traveled < route.totalDistanceMeters) {
            delay(tickMs)
            traveled += speedMps * speedMultiplier * tickMs / 1000.0
            navigationState.updateProgress(RouteProgressCalculator.fromTraveled(route, traveled, clock()))
        }
    }
}

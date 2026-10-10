package com.dynamicdroid.routing

import com.dynamicdroid.testRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressCalculatorTest {
    private val route = testRoute()

    @Test fun startOfRoute_nextStepIsFirstRealManeuver() {
        val p = RouteProgressCalculator.fromTraveled(route, 0.0, 1_000_000L)
        // Schritt 0 (DEPART) liegt bei 0 m und gilt als erreicht.
        assertEquals(1, p.nextStepIndex)
        assertEquals(1000, p.distanceToNextStepMeters)
        assertEquals(5000, p.remainingMeters)
        assertEquals(600, p.remainingSeconds)
        assertEquals(1_000_000L + 600_000L, p.etaEpochMillis)
    }

    @Test fun midRoute_advancesAfterPassingManeuver() {
        val p = RouteProgressCalculator.fromTraveled(route, 1500.0, 0L)
        assertEquals(2, p.nextStepIndex)
        assertEquals(1500, p.distanceToNextStepMeters)
        assertEquals(3500, p.remainingMeters)
        assertEquals(420, p.remainingSeconds)
    }

    @Test fun endOfRoute_staysOnArriveStepAndClamps() {
        val p = RouteProgressCalculator.fromTraveled(route, 99_999.0, 0L)
        assertEquals(3, p.nextStepIndex)
        assertEquals(0, p.distanceToNextStepMeters)
        assertEquals(0, p.remainingMeters)
        assertEquals(5000, p.traveledMeters)
    }

    @Test fun offRouteFlagIsCarried() {
        assertTrue(RouteProgressCalculator.fromTraveled(route, 10.0, 0L, offRoute = true).offRoute)
        assertFalse(RouteProgressCalculator.fromTraveled(route, 10.0, 0L).offRoute)
    }
}

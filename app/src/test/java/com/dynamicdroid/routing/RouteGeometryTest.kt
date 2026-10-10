package com.dynamicdroid.routing

import com.dynamicdroid.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {
    private val route = DemoRoute.create(LatLng(52.0, 13.0))
    private val cumulative = RouteGeometry.cumulativeMeters(route.geometry)

    @Test fun demoRouteGeometryMatchesDeclaredLength() {
        assertEquals(route.totalDistanceMeters.toDouble(), cumulative.last(), 5.0)
        route.steps.forEach { step ->
            // Jede Abbiegung liegt auf einem Geometriepunkt.
            assertTrue(cumulative.any { kotlin.math.abs(it - step.distanceFromStartMeters) < 5.0 })
        }
    }

    @Test fun snapOnRoutePointReturnsItsPosition() {
        val p = RouteGeometry.pointAt(route.geometry, cumulative, 700.0)
        val snap = RouteGeometry.snap(route.geometry, cumulative, p)
        assertEquals(700.0, snap.traveledMeters, 1.0)
        assertTrue(snap.distanceFromRouteMeters < 1.0)
    }

    @Test fun snapOfOffsetPointReportsDistanceFromRoute() {
        val onRoute = RouteGeometry.pointAt(route.geometry, cumulative, 200.0)
        // ~100 m östlich des (nach Norden verlaufenden) ersten Abschnitts
        val offset = LatLng(onRoute.lat, onRoute.lon + 100.0 / (111_320.0 * Math.cos(Math.toRadians(52.0))))
        val snap = RouteGeometry.snap(route.geometry, cumulative, offset)
        assertEquals(200.0, snap.traveledMeters, 2.0)
        assertEquals(100.0, snap.distanceFromRouteMeters, 2.0)
    }
}

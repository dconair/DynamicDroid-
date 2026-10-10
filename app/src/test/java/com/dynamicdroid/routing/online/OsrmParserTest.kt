package com.dynamicdroid.routing.online

import com.dynamicdroid.model.ManeuverType
import com.dynamicdroid.routing.RouteGeometry
import com.dynamicdroid.routing.RouteProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmParserTest {
    private val json = javaClass.classLoader!!.getResource("osrm_sample.json")!!.readText()
    private val route = OsrmParser.parse(json, "Alexanderplatz")

    @Test fun parsesRealOsrmResponse() {
        assertEquals("Alexanderplatz", route.destinationName)
        assertEquals(2754, route.totalDistanceMeters)
        assertEquals(308, route.totalDurationSeconds)
        assertEquals(8, route.steps.size)
        assertEquals(ManeuverType.DEPART, route.steps.first().type)
        assertEquals(ManeuverType.ARRIVE, route.steps.last().type)
        assertEquals(route.totalDistanceMeters, route.steps.last().distanceFromStartMeters)
    }

    @Test fun stepPositionsAreCumulativeStartsOfSteps() {
        // Distanzen der OSRM-Schritte (gerundet): 18, 86, 1326, 295, 852, 152, 25; Summen aus den ungerundeten Werten -> Aktion am Anfang jedes Schritts
        assertEquals(listOf(0, 18, 104, 1430, 1726, 2578, 2730, 2754), route.steps.map { it.distanceFromStartMeters })
    }

    @Test fun mapsManeuversAndStreets() {
        val byIndex = route.steps
        assertEquals(ManeuverType.LEFT, byIndex[1].type)          // "continue", modifier left
        assertEquals(ManeuverType.STRAIGHT, byIndex[2].type)      // "new name", straight
        assertEquals("Unter den Linden", byIndex[2].streetName)
        assertEquals(ManeuverType.RIGHT, byIndex[5].type)
        assertEquals("Alexanderstraße", byIndex[5].streetName)
        assertEquals("", byIndex[6].streetName)                   // unbenannt, keine Referenz
    }

    @Test fun geometryMatchesReportedLength() {
        val length = RouteGeometry.cumulativeMeters(route.geometry).last()
        assertEquals(route.totalDistanceMeters.toDouble(), length, 60.0) // Polyline vs. OSRM-Distanz
    }

    @Test fun routeWorksWithProgressCalculator() {
        val p = RouteProgressCalculator.fromTraveled(route, 100.0, 0L)
        assertEquals(2, p.nextStepIndex) // Schritt 1 (bei 18 m) ist erledigt, Schritt 2 liegt bei 104 m
        assertEquals(4, p.distanceToNextStepMeters)
    }

    @Test(expected = RoutingException::class)
    fun errorCodeBecomesRoutingException() {
        OsrmParser.parse("""{"code":"NoRoute","message":"Impossible route"}""", "x")
    }

    @Test fun maneuverMapping() {
        assertEquals(ManeuverType.ROUNDABOUT, OsrmParser.mapManeuver("roundabout", "right"))
        assertEquals(ManeuverType.U_TURN, OsrmParser.mapManeuver("turn", "uturn"))
        assertEquals(ManeuverType.SHARP_LEFT, OsrmParser.mapManeuver("turn", "sharp left"))
        assertEquals(ManeuverType.STRAIGHT, OsrmParser.mapManeuver("merge", null))
        assertEquals(ManeuverType.SLIGHT_RIGHT, OsrmParser.mapManeuver("fork", "slight right"))
    }
}

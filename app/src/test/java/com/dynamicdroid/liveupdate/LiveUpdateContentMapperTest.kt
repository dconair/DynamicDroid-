package com.dynamicdroid.liveupdate

import com.dynamicdroid.model.ManeuverType
import com.dynamicdroid.routing.RouteProgressCalculator
import com.dynamicdroid.testFormatters
import com.dynamicdroid.testMapper
import com.dynamicdroid.testRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateContentMapperTest {
    private val route = testRoute()
    private fun content(traveled: Double, offRoute: Boolean = false, now: Long = 0L) =
        testMapper.map(route, RouteProgressCalculator.fromTraveled(route, traveled, now, offRoute))

    @Test fun titleCombinesDistanceAndInstruction() {
        val c = content(750.0)
        assertEquals("250 m · right B-Straße", c.title)
        assertEquals(ManeuverType.RIGHT, c.maneuverType)
        assertEquals(1, c.maneuverKey)
    }

    @Test fun textShowsDurationDistanceAndEta() {
        val c = content(750.0, now = 0L)
        // Rest: 4250 m, 510 s -> 9 Min; ETA = 510 s nach Mitternacht UTC -> 00:08 (auf die Minute abgerundet)
        assertEquals("9 Min | 4,3 km | 00:08", c.text)
    }

    @Test fun chipShowsDistanceWhenManeuverIsNear() {
        assertEquals("250 m", content(750.0).shortCriticalText)
    }

    @Test fun chipShowsMinutesWhenManeuverIsFar() {
        // nächste Abbiegung 4000 m entfernt (> 2000 m) -> Restzeit statt Distanz
        val far = route.copy(steps = listOf(
            com.dynamicdroid.model.ManeuverStep(0, ManeuverType.DEPART, "A", 0),
            com.dynamicdroid.model.ManeuverStep(1, ManeuverType.LEFT, "B", 4000),
            com.dynamicdroid.model.ManeuverStep(2, ManeuverType.ARRIVE, "Ziel", 5000),
        ))
        val c = testMapper.map(far, RouteProgressCalculator.fromTraveled(far, 0.0, 0L))
        assertEquals("10 Min", c.shortCriticalText)
    }

    @Test fun chipFitsWithinSixCharacters() {
        listOf(0.0, 500.0, 750.0, 995.0, 1200.0, 2900.0, 4000.0, 4990.0).forEach {
            assertTrue("Chip '${content(it).shortCriticalText}' zu lang", content(it).shortCriticalText.length <= 6)
        }
    }

    @Test fun progressBarSegmentsSumToRouteLength() {
        val c = content(750.0)
        assertEquals(5000, c.segments.sumOf { it.lengthMeters })
        assertEquals(5000, c.progressMaxMeters)
        assertEquals(750, c.progressMeters) // Schrittweite 25 m
        // anstehende Abbiegungen bei 1000 und 3000 (Ankunft bei 5000 ist Ende der Leiste, kein Punkt)
        assertEquals(listOf(1000, 3000), c.points.map { it.positionMeters })
        assertEquals(listOf(1000, 2000, 2000), c.segments.map { it.lengthMeters })
    }

    @Test fun passedManeuversDisappearFromPoints() {
        val c = content(1500.0)
        assertEquals(listOf(3000), c.points.map { it.positionMeters })
        assertEquals(listOf(3000, 2000), c.segments.map { it.lengthMeters })
    }

    @Test fun arrivalOverridesTitleAndChip() {
        val c = content(4990.0)
        assertTrue(c.arrived)
        assertEquals("Arrived", c.title)
        assertEquals("Ziel", c.shortCriticalText)
    }

    @Test fun offRouteOverridesTitleAndChip() {
        val c = content(750.0, offRoute = true)
        assertTrue(c.offRoute)
        assertEquals("Off route", c.title)
        assertEquals("Route?", c.shortCriticalText)
    }

    @Test fun smallMovementsWithinRoundingBucketYieldEqualContent() {
        // 10-m-Rundung: 2 m weiter ändert nichts Sichtbares
        assertEquals(content(760.0), content(762.0))
        assertFalse(content(760.0) == content(800.0))
    }

    @Test fun maxPointsIsCapped() {
        val steps = (1..20).map { com.dynamicdroid.model.ManeuverStep(it, ManeuverType.LEFT, "S$it", it * 200) } +
            com.dynamicdroid.model.ManeuverStep(21, ManeuverType.ARRIVE, "Ziel", 5000)
        val long = route.copy(steps = steps)
        val c = testMapper.map(long, RouteProgressCalculator.fromTraveled(long, 0.0, 0L))
        assertEquals(LiveUpdateContentMapper.MAX_POINTS, c.points.size)
        assertEquals(5000, c.segments.sumOf { it.lengthMeters })
    }

    @Test fun distanceFormatting() {
        val f = testFormatters
        assertEquals("10 m", f.distance(3))
        assertEquals("130 m", f.distance(134))
        assertEquals("250 m", f.distance(240))
        assertEquals("1,0 km", f.distance(990))
        assertEquals("1,2 km", f.distance(1230))
        assertEquals("12 km", f.distance(12_345))
    }

    @Test fun durationFormatting() {
        val f = testFormatters
        assertEquals("1 Min", f.duration(0))
        assertEquals("1 Min", f.duration(30))
        assertEquals("2 Min", f.duration(61))
        assertEquals("1 h 5 Min", f.duration(3900))
    }
}

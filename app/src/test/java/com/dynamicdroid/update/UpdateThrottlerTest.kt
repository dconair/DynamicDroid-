package com.dynamicdroid.update

import com.dynamicdroid.routing.RouteProgressCalculator
import com.dynamicdroid.testMapper
import com.dynamicdroid.testRoute
import com.dynamicdroid.update.UpdateThrottler.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateThrottlerTest {
    private val route = testRoute()
    private fun content(traveled: Double, offRoute: Boolean = false) =
        testMapper.map(route, RouteProgressCalculator.fromTraveled(route, traveled, 0L, offRoute))

    @Test fun firstContentIsAlwaysPosted() {
        assertEquals(Decision.Post, UpdateThrottler().evaluate(content(0.0), 0L))
    }

    @Test fun identicalContentIsSkipped() {
        val t = UpdateThrottler()
        t.markPosted(content(760.0), 0L)
        assertEquals(Decision.Skip, t.evaluate(content(762.0), 10_000L))
    }

    @Test fun minorChangeInsideIntervalIsDeferredWithRemainingTime() {
        val t = UpdateThrottler(minIntervalMs = 2_000)
        t.markPosted(content(700.0), 1_000L)
        assertEquals(Decision.Defer(1_500L), t.evaluate(content(790.0), 1_500L))
    }

    @Test fun minorChangeAfterIntervalIsPosted() {
        val t = UpdateThrottler(minIntervalMs = 2_000)
        t.markPosted(content(700.0), 1_000L)
        assertEquals(Decision.Post, t.evaluate(content(790.0), 3_000L))
    }

    @Test fun newManeuverBypassesInterval() {
        val t = UpdateThrottler(minIntervalMs = 2_000)
        t.markPosted(content(990.0), 1_000L)
        // 1010 m: nächste Abbiegung ist jetzt die zweite (Schritt-ID wechselt)
        assertEquals(Decision.Post, t.evaluate(content(1010.0), 1_100L))
    }

    @Test fun arrivalAndOffRouteBypassInterval() {
        val t = UpdateThrottler(minIntervalMs = 2_000)
        t.markPosted(content(700.0), 1_000L)
        assertEquals(Decision.Post, t.evaluate(content(700.0, offRoute = true), 1_001L))
        t.markPosted(content(4900.0), 2_000L)
        assertEquals(Decision.Post, t.evaluate(content(4995.0), 2_001L))
    }

    @Test fun gpsFixStreamIsReducedToFewPosts() {
        // 1 Fix pro Sekunde mit 14 m/s über 60 s -> deutlich weniger Posts als Fixes.
        val t = UpdateThrottler(minIntervalMs = 2_000)
        var posts = 0
        var now = 0L
        var traveled = 0.0
        repeat(60) {
            val c = content(traveled)
            when (val d = t.evaluate(c, now)) {
                Decision.Post -> { t.markPosted(c, now); posts++ }
                is Decision.Defer, Decision.Skip -> Unit
            }
            now += 1_000
            traveled += 14.0
        }
        assert(posts in 1..30) { "unerwartet viele Posts: $posts" }
    }
}

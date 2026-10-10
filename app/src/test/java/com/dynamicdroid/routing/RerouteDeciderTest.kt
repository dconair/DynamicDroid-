package com.dynamicdroid.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteDeciderTest {
    @Test fun singleOutlierDoesNotTrigger() {
        val d = RerouteDecider(minOffRouteFixes = 3, minIntervalMs = 15_000)
        assertFalse(d.onFix(true, 0)); assertFalse(d.onFix(false, 1_000)); assertFalse(d.onFix(true, 2_000))
        assertFalse(d.onFix(true, 3_000))
    }

    @Test fun triggersAfterConsecutiveOffRouteFixes() {
        val d = RerouteDecider(3, 15_000)
        assertFalse(d.onFix(true, 0)); assertFalse(d.onFix(true, 1_000))
        assertTrue(d.onFix(true, 2_000))
    }

    @Test fun respectsMinimumInterval() {
        val d = RerouteDecider(2, 15_000)
        d.onFix(true, 0); assertTrue(d.onFix(true, 1_000))
        d.onFix(true, 2_000); assertFalse(d.onFix(true, 3_000))      // zu früh
        assertTrue(d.onFix(true, 16_500))                             // Intervall abgelaufen
    }
}

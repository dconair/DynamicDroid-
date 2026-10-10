package com.dynamicdroid.routing

import com.dynamicdroid.state.NavigationState
import com.dynamicdroid.state.NavigationUiState
import com.dynamicdroid.testRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockRouteSimulatorTest {
    @Test fun runsToArrivalAndEmitsMonotonicProgress() = runTest {
        val state = NavigationState()
        val sim = MockRouteSimulator(state, clock = { currentTime }, speedMps = 100.0, tickMs = 1_000)
        val seen = mutableListOf<Int>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            state.state.collect { if (it is NavigationUiState.Active) seen += it.progress.traveledMeters }
        }
        sim.run(testRoute(), speedMultiplier = 1.0)
        collector.cancel()

        assertEquals(50_000L, currentTime) // 5000 m bei 100 m/s = 50 Ticks
        assertEquals(seen, seen.sorted())
        assertEquals(5000, seen.last())
        assertTrue(seen.size > 1)
    }

    @Test fun speedMultiplierShortensSimulation() = runTest {
        val state = NavigationState()
        MockRouteSimulator(state, clock = { currentTime }, speedMps = 100.0, tickMs = 1_000).run(testRoute(), 5.0)
        assertEquals(10_000L, currentTime)
    }

    @Test fun updatesAfterStopAreIgnored() = runTest {
        val state = NavigationState()
        state.start(testRoute(), RouteProgressCalculator.fromTraveled(testRoute(), 0.0, 0L))
        state.stop()
        state.updateProgress(RouteProgressCalculator.fromTraveled(testRoute(), 100.0, 0L))
        assertEquals(NavigationUiState.Idle, state.state.value)
    }
}

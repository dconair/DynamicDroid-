package com.dynamicdroid.routing

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.RouteProgress
import kotlin.math.roundToInt

/** Berechnet [RouteProgress] aus einer Streckenposition. Rein, ohne Android-Abhängigkeiten. */
object RouteProgressCalculator {

    fun fromTraveled(
        route: ActiveRoute,
        traveledMeters: Double,
        nowEpochMillis: Long,
        offRoute: Boolean = false,
    ): RouteProgress {
        val traveled = traveledMeters.coerceIn(0.0, route.totalDistanceMeters.toDouble())
        val remaining = route.totalDistanceMeters - traveled
        // Schritte, deren Position schon (fast) erreicht ist, gelten als ausgeführt – außer dem letzten (ARRIVE).
        val lastIndex = route.steps.lastIndex
        var next = route.steps.indexOfFirst { it.distanceFromStartMeters > traveled + REACHED_TOLERANCE_M }
        if (next == -1) next = lastIndex
        val toNext = (route.steps[next].distanceFromStartMeters - traveled).coerceAtLeast(0.0)
        val remainingSeconds = (route.totalDurationSeconds * (remaining / route.totalDistanceMeters)).roundToInt()
        return RouteProgress(
            traveledMeters = traveled.roundToInt(),
            nextStepIndex = next,
            distanceToNextStepMeters = toNext.roundToInt(),
            remainingMeters = remaining.roundToInt(),
            remainingSeconds = remainingSeconds,
            etaEpochMillis = nowEpochMillis + remainingSeconds * 1000L,
            offRoute = offRoute,
        )
    }

    private const val REACHED_TOLERANCE_M = 1.0
}

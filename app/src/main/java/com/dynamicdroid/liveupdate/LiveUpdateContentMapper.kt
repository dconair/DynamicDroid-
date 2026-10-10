package com.dynamicdroid.liveupdate

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.RouteProgress
import com.dynamicdroid.state.NavigationUiState

/** Reine Abbildung `NavigationUiState.Active -> LiveUpdateContent`. Keine Android-Klassen. */
class LiveUpdateContentMapper(
    private val texts: NavigationTexts,
    private val formatters: Formatters = Formatters(),
) {
    fun map(state: NavigationUiState.Active): LiveUpdateContent = map(state.route, state.progress)

    fun map(route: ActiveRoute, progress: RouteProgress): LiveUpdateContent {
        val step = route.steps[progress.nextStepIndex]
        val arrived = progress.remainingMeters <= ARRIVED_THRESHOLD_M
        val distance = formatters.distance(progress.distanceToNextStepMeters)
        val duration = formatters.duration(progress.remainingSeconds)
        val remaining = formatters.distance(progress.remainingMeters)
        val eta = formatters.clock(formatters.floorToMinute(progress.etaEpochMillis))
        val instruction = texts.instruction(step.type, step.streetName)
        val summary = texts.summary(duration, remaining, eta)

        val title: String
        val text: String
        val chip: String
        when {
            arrived -> {
                title = texts.arrived(route.destinationName)
                text = route.destinationName
                chip = texts.arrivedShort()
            }
            progress.offRoute -> {
                title = texts.offRoute()
                text = summary
                chip = texts.offRouteShort()
            }
            else -> {
                title = "$distance · $instruction"
                text = summary
                chip = if (progress.distanceToNextStepMeters <= CHIP_DISTANCE_LIMIT_M) distance else duration
            }
        }

        val (segments, points) = buildProgressBar(route, progress)
        return LiveUpdateContent(
            title = title,
            text = text,
            shortCriticalText = chip,
            distanceToManeuver = distance,
            instruction = instruction,
            maneuverType = step.type,
            maneuverKey = step.id,
            etaText = eta,
            remainingText = "$duration · $remaining",
            progressMeters = quantizeProgress(progress.traveledMeters, route.totalDistanceMeters),
            progressMaxMeters = route.totalDistanceMeters,
            segments = segments,
            points = points,
            arrived = arrived,
            offRoute = progress.offRoute,
        )
    }

    /** Fortschrittsleiste nur in ~0,5-%-Schritten ändern: kleinere Sprünge sind unsichtbar und würden nur Updates auslösen. */
    private fun quantizeProgress(traveled: Int, total: Int): Int {
        val step = (total / PROGRESS_STEPS).coerceAtLeast(MIN_PROGRESS_STEP_M)
        return if (total - traveled <= 0) total else traveled / step * step
    }

    /**
     * Segmente: ein Abschnitt je anstehender Abbiegung (höchstens [MAX_POINTS]); alles davor
     * (bereits gefahren) ist im ersten Abschnitt enthalten, der Rest der Route im letzten.
     * Die Längen summieren sich immer exakt auf die Gesamtdistanz.
     */
    private fun buildProgressBar(route: ActiveRoute, progress: RouteProgress): Pair<List<SegmentSpec>, List<PointSpec>> {
        val total = route.totalDistanceMeters
        val boundaries = route.steps
            .drop(progress.nextStepIndex)
            .map { it.distanceFromStartMeters }
            .filter { it in 1 until total }
            .distinct()
            .take(MAX_POINTS)
        val segments = ArrayList<SegmentSpec>(boundaries.size + 1)
        var previous = 0
        for (b in boundaries) {
            segments += SegmentSpec(b - previous)
            previous = b
        }
        segments += SegmentSpec(total - previous)
        return segments to boundaries.map(::PointSpec)
    }

    companion object {
        const val MAX_POINTS = 8
        private const val PROGRESS_STEPS = 200
        private const val MIN_PROGRESS_STEP_M = 10
        const val ARRIVED_THRESHOLD_M = 20
        const val CHIP_DISTANCE_LIMIT_M = 2000
    }
}

package com.dynamicdroid.model

/** WGS84-Koordinate. */
data class LatLng(val lat: Double, val lon: Double)

enum class ManeuverType {
    DEPART,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ROUNDABOUT,
    ARRIVE,
}

/**
 * Eine Fahranweisung. [distanceFromStartMeters] ist die Streckenposition (entlang der Route),
 * an der die Aktion ausgeführt wird.
 */
data class ManeuverStep(
    val id: Int,
    val type: ManeuverType,
    val streetName: String,
    val distanceFromStartMeters: Int,
)

/**
 * Eine aktive Route. [geometry] ist die Polyline (mindestens 2 Punkte); [steps] sind nach
 * [ManeuverStep.distanceFromStartMeters] aufsteigend sortiert, der letzte Schritt ist [ManeuverType.ARRIVE].
 */
data class ActiveRoute(
    val id: String,
    val destinationName: String,
    val geometry: List<LatLng>,
    val steps: List<ManeuverStep>,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
) {
    init {
        require(geometry.size >= 2) { "Route braucht mindestens 2 Geometriepunkte" }
        require(steps.isNotEmpty()) { "Route braucht mindestens einen Schritt" }
        require(totalDistanceMeters > 0 && totalDurationSeconds > 0) { "Distanz und Dauer müssen > 0 sein" }
        require(steps.zipWithNext().all { (a, b) -> a.distanceFromStartMeters <= b.distanceFromStartMeters }) {
            "Schritte müssen nach Streckenposition sortiert sein"
        }
    }
}

/**
 * Fahrfortschritt auf einer [ActiveRoute]. Alle Distanzen in Metern.
 *
 * @property nextStepIndex Index des nächsten noch nicht ausgeführten Schritts in [ActiveRoute.steps].
 */
data class RouteProgress(
    val traveledMeters: Int,
    val nextStepIndex: Int,
    val distanceToNextStepMeters: Int,
    val remainingMeters: Int,
    val remainingSeconds: Int,
    val etaEpochMillis: Long,
    val offRoute: Boolean = false,
)

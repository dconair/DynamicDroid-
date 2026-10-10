package com.dynamicdroid.routing

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.LatLng
import com.dynamicdroid.model.ManeuverStep
import com.dynamicdroid.model.ManeuverType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetische Test-Route. Es gibt in diesem Projekt (noch) keine Routing-Engine; die Demo-Route wird per
 * Koppelnavigation aus Teilstrecken und Kurswechseln erzeugt, damit Geometrie und Abbiegepositionen konsistent sind.
 */
object DemoRoute {
    val BERLIN_MITTE = LatLng(52.5200, 13.4050)

    private data class Leg(val street: String, val lengthMeters: Int, val turnAtEnd: ManeuverType, val headingChangeDeg: Double)

    private val legs = listOf(
        Leg("Hauptstraße", 450, ManeuverType.RIGHT, 90.0),
        Leg("Bahnhofstraße", 900, ManeuverType.SLIGHT_LEFT, -30.0),
        Leg("Ringstraße", 350, ManeuverType.LEFT, -90.0),
        Leg("Gartenweg", 1200, ManeuverType.ROUNDABOUT, 20.0),
        Leg("Am Markt", 600, ManeuverType.SHARP_RIGHT, 135.0),
        Leg("Uferallee", 300, ManeuverType.ARRIVE, 0.0),
    )

    /** ~3,8 km, Start nach Norden, mit [origin] als Startpunkt. */
    fun create(origin: LatLng = BERLIN_MITTE, averageSpeedMps: Double = 13.9): ActiveRoute {
        val geometry = ArrayList<LatLng>()
        val steps = ArrayList<ManeuverStep>()
        var position = origin
        var headingDeg = 0.0
        var traveled = 0
        geometry += position
        steps += ManeuverStep(0, ManeuverType.DEPART, legs.first().street, 0)
        legs.forEachIndexed { index, leg ->
            position = advance(position, headingDeg, leg.lengthMeters.toDouble())
            geometry += position
            traveled += leg.lengthMeters
            val nextStreet = legs.getOrNull(index + 1)?.street ?: "Ziel"
            steps += ManeuverStep(index + 1, leg.turnAtEnd, nextStreet, traveled)
            headingDeg += leg.headingChangeDeg
        }
        return ActiveRoute(
            id = "demo",
            destinationName = "Demo-Ziel",
            geometry = geometry,
            steps = steps,
            totalDistanceMeters = traveled,
            totalDurationSeconds = (traveled / averageSpeedMps).toInt(),
        )
    }

    /** Geht [meters] vom Punkt [from] in Richtung [headingDeg] (0 = Nord, im Uhrzeigersinn). */
    private fun advance(from: LatLng, headingDeg: Double, meters: Double): LatLng {
        val rad = Math.toRadians(headingDeg)
        val dNorth = cos(rad) * meters
        val dEast = sin(rad) * meters
        val dLat = Math.toDegrees(dNorth / EARTH_RADIUS_M)
        val dLon = Math.toDegrees(dEast / (EARTH_RADIUS_M * cos(Math.toRadians(from.lat))))
        return LatLng(from.lat + dLat, from.lon + dLon)
    }

    private const val EARTH_RADIUS_M = 6_371_000.0
}

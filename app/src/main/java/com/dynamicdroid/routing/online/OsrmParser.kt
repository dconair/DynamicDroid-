package com.dynamicdroid.routing.online

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.ManeuverStep
import com.dynamicdroid.model.ManeuverType
import org.json.JSONObject
import kotlin.math.roundToInt

class RoutingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Wandelt eine OSRM-`/route/v1`-Antwort (`steps=true`, `geometries=polyline6`) in eine [ActiveRoute]. */
object OsrmParser {

    fun parse(json: String, destinationName: String, routeId: String = "osrm"): ActiveRoute {
        val root = JSONObject(json)
        val code = root.optString("code")
        if (code != "Ok") throw RoutingException("Routing fehlgeschlagen: ${root.optString("message", code)}")
        val route = root.getJSONArray("routes").getJSONObject(0)
        val geometry = Polyline.decode(route.getString("geometry"))
        if (geometry.size < 2) throw RoutingException("Route enthält keine Geometrie")

        val totalMeters = route.getDouble("distance").roundToInt().coerceAtLeast(1)
        val totalSeconds = route.getDouble("duration").roundToInt().coerceAtLeast(1)

        val steps = ArrayList<ManeuverStep>()
        var traveled = 0.0
        val osrmSteps = route.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        for (i in 0 until osrmSteps.length()) {
            val step = osrmSteps.getJSONObject(i)
            val maneuver = step.getJSONObject("maneuver")
            val type = mapManeuver(maneuver.getString("type"), maneuver.optString("modifier").ifEmpty { null })
            // Die Aktion findet am *Anfang* des OSRM-Schritts statt; "arrive" liegt am Ende der Route.
            val position = if (type == ManeuverType.ARRIVE) totalMeters else traveled.roundToInt().coerceIn(0, totalMeters)
            steps += ManeuverStep(i, type, streetName(step), position)
            traveled += step.getDouble("distance")
        }
        if (steps.last().type != ManeuverType.ARRIVE) {
            steps += ManeuverStep(steps.size, ManeuverType.ARRIVE, "", totalMeters)
        }
        return ActiveRoute(routeId, destinationName, geometry, steps, totalMeters, totalSeconds)
    }

    internal fun mapManeuver(type: String, modifier: String?): ManeuverType = when (type) {
        "arrive" -> ManeuverType.ARRIVE
        "depart" -> ManeuverType.DEPART
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> ManeuverType.ROUNDABOUT
        else -> when (modifier) {
            "uturn" -> ManeuverType.U_TURN
            "sharp right" -> ManeuverType.SHARP_RIGHT
            "right" -> ManeuverType.RIGHT
            "slight right" -> ManeuverType.SLIGHT_RIGHT
            "slight left" -> ManeuverType.SLIGHT_LEFT
            "left" -> ManeuverType.LEFT
            "sharp left" -> ManeuverType.SHARP_LEFT
            else -> ManeuverType.STRAIGHT
        }
    }

    /** Straßenname, sonst erste Referenz ("B 2; B 5" -> "B 2"), sonst leer (die UI setzt dann einen Platzhalter). */
    private fun streetName(step: JSONObject): String =
        step.optString("name").ifBlank { step.optString("ref").substringBefore(';').trim() }
}

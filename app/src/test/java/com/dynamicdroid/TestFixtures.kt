package com.dynamicdroid

import com.dynamicdroid.liveupdate.Formatters
import com.dynamicdroid.liveupdate.LiveUpdateContentMapper
import com.dynamicdroid.liveupdate.NavigationTexts
import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.LatLng
import com.dynamicdroid.model.ManeuverStep
import com.dynamicdroid.model.ManeuverType
import java.time.ZoneId
import java.util.Locale

object FakeTexts : NavigationTexts {
    override fun instruction(type: ManeuverType, street: String) = "${type.name.lowercase()} $street"
    override fun arrived(destination: String) = "Arrived"
    override fun arrivedShort() = "Ziel"
    override fun offRoute() = "Off route"
    override fun offRouteShort() = "Route?"
    override fun summary(duration: String, distance: String, eta: String) = "$duration | $distance | $eta"
}

val testFormatters = Formatters(Locale.GERMANY, ZoneId.of("UTC"))
val testMapper = LiveUpdateContentMapper(FakeTexts, testFormatters)

/** 5 km, 600 s; Abbiegungen bei 1000 m (rechts), 3000 m (links), Ziel bei 5000 m. */
fun testRoute() = ActiveRoute(
    id = "t",
    destinationName = "Ziel",
    geometry = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.05)),
    steps = listOf(
        ManeuverStep(0, ManeuverType.DEPART, "A-Straße", 0),
        ManeuverStep(1, ManeuverType.RIGHT, "B-Straße", 1000),
        ManeuverStep(2, ManeuverType.LEFT, "C-Straße", 3000),
        ManeuverStep(3, ManeuverType.ARRIVE, "Ziel", 5000),
    ),
    totalDistanceMeters = 5000,
    totalDurationSeconds = 600,
)

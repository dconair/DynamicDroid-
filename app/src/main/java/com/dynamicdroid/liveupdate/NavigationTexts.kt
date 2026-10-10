package com.dynamicdroid.liveupdate

import com.dynamicdroid.model.ManeuverType

/** Lokalisierte Texte; auf Android über Resources implementiert ([AndroidNavigationTexts]), in Tests gefaket. */
interface NavigationTexts {
    fun instruction(type: ManeuverType, street: String): String
    fun arrived(destination: String): String
    fun arrivedShort(): String
    fun offRoute(): String
    fun offRouteShort(): String

    /** Zusammenfassungszeile, z. B. "12 Min · 5,3 km · Ankunft 14:32". */
    fun summary(duration: String, distance: String, eta: String): String
}

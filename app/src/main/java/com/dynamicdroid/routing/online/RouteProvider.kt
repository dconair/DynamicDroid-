package com.dynamicdroid.routing.online

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.LatLng
import java.util.Locale

/** Routenberechnung. Austauschbar (z. B. gegen Google Routes API oder eine eigene OSRM/Valhalla-Instanz). */
interface RouteProvider {
    suspend fun route(from: LatLng, to: LatLng, destinationName: String): ActiveRoute
}

/**
 * OSRM über HTTP, ohne API-Schlüssel. Der Standard-Server `router.project-osrm.org` ist ein **Demo-Server** ohne
 * Verfügbarkeitszusage und nicht für Dauerbetrieb gedacht – für eine Veröffentlichung eigene Instanz oder einen
 * kommerziellen Anbieter verwenden. Straßendaten © OpenStreetMap-Mitwirkende.
 */
class OsrmRouteProvider(
    private val baseUrl: String = "https://router.project-osrm.org",
    private val profile: String = "driving",
) : RouteProvider {
    override suspend fun route(from: LatLng, to: LatLng, destinationName: String): ActiveRoute {
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val url = "$baseUrl/route/v1/$profile/$coords?overview=full&geometries=polyline6&steps=true"
        val body = try {
            httpGet(url)
        } catch (e: java.io.IOException) {
            throw RoutingException("Routing-Server nicht erreichbar (${e.message})", e)
        }
        return OsrmParser.parse(body, destinationName)
    }
}

package com.dynamicdroid.routing

import com.dynamicdroid.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Ergebnis des Projizierens einer Position auf die Route. */
data class Snap(val traveledMeters: Double, val distanceFromRouteMeters: Double)

/**
 * Hilfsfunktionen für Polylines. Verwendet eine lokale equirektanguläre Näherung pro Segment –
 * für Segmentlängen im Navigationsbereich (< einige km) genau genug und ohne Abhängigkeiten.
 */
object RouteGeometry {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Kumulative Streckenlänge am Beginn jedes Geometriepunkts (Index 0 = 0.0). */
    fun cumulativeMeters(geometry: List<LatLng>): DoubleArray {
        val out = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) {
            out[i] = out[i - 1] + haversineMeters(geometry[i - 1], geometry[i])
        }
        return out
    }

    /** Punkt auf der Route bei [meters] Streckenposition (geklemmt auf [0, Gesamtlänge]). */
    fun pointAt(geometry: List<LatLng>, cumulative: DoubleArray, meters: Double): LatLng {
        if (meters <= 0.0) return geometry.first()
        if (meters >= cumulative.last()) return geometry.last()
        var i = 1
        while (cumulative[i] < meters) i++
        val segLen = cumulative[i] - cumulative[i - 1]
        val t = if (segLen == 0.0) 0.0 else (meters - cumulative[i - 1]) / segLen
        val a = geometry[i - 1]
        val b = geometry[i]
        return LatLng(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Projiziert [p] auf die nächstgelegene Stelle der Polyline. */
    fun snap(geometry: List<LatLng>, cumulative: DoubleArray, p: LatLng): Snap {
        var bestDist = Double.MAX_VALUE
        var bestTraveled = 0.0
        val cosLat = cos(Math.toRadians(p.lat))
        for (i in 1 until geometry.size) {
            val a = geometry[i - 1]
            val b = geometry[i]
            // lokale Meterkoordinaten mit p als Ursprung
            val ax = Math.toRadians(a.lon - p.lon) * cosLat * EARTH_RADIUS_M
            val ay = Math.toRadians(a.lat - p.lat) * EARTH_RADIUS_M
            val bx = Math.toRadians(b.lon - p.lon) * cosLat * EARTH_RADIUS_M
            val by = Math.toRadians(b.lat - p.lat) * EARTH_RADIUS_M
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy).div(len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val dist = sqrt(px * px + py * py)
            if (dist < bestDist) {
                bestDist = dist
                bestTraveled = cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1])
            }
        }
        return Snap(bestTraveled, bestDist)
    }
}

package com.dynamicdroid.routing.online

import com.dynamicdroid.model.LatLng

/** Decoder für Google-Encoded-Polylines (OSRM liefert `polyline6`, also Präzision 1e-6). */
object Polyline {
    fun decode(encoded: String, precision: Int = 6): List<LatLng> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            val (dLat, afterLat) = readValue(encoded, index)
            val (dLon, afterLon) = readValue(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            out += LatLng(lat / factor, lon / factor)
        }
        return out
    }

    private fun readValue(s: String, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var i = start
        while (true) {
            require(i < s.length) { "Polyline endet mitten in einem Wert" }
            val b = s[i++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return (if (result and 1 != 0) (result shr 1).inv() else result shr 1) to i
    }
}

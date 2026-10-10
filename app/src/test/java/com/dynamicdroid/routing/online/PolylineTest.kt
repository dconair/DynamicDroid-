package com.dynamicdroid.routing.online

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {
    @Test fun decodesGoogleReferenceExample() {
        // Referenzbeispiel aus der Google-Polyline-Doku (Präzision 5)
        val pts = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-9); assertEquals(-120.2, pts[0].lon, 1e-9)
        assertEquals(40.7, pts[1].lat, 1e-9); assertEquals(-120.95, pts[1].lon, 1e-9)
        assertEquals(43.252, pts[2].lat, 1e-9); assertEquals(-126.453, pts[2].lon, 1e-9)
    }
}

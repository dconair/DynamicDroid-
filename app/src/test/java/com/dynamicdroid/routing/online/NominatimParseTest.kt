package com.dynamicdroid.routing.online

import org.junit.Assert.assertEquals
import org.junit.Test

class NominatimParseTest {
    @Test fun parsesSearchResult() {
        val json = """[{"place_id":1,"lat":"52.5162699","lon":"13.3777034","display_name":"Brandenburger Tor, Pariser Platz, Mitte, Berlin"}]"""
        val places = NominatimGeocoder().parse(json)
        assertEquals(1, places.size)
        assertEquals("Brandenburger Tor, Pariser Platz, Mitte, Berlin", places[0].name)
        assertEquals(52.5162699, places[0].location.lat, 1e-9)
        assertEquals(13.3777034, places[0].location.lon, 1e-9)
    }
}

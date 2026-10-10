package com.dynamicdroid.routing.online

import com.dynamicdroid.model.LatLng
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val location: LatLng)

interface Geocoder {
    suspend fun search(query: String): List<Place>
}

/**
 * Adresssuche über Nominatim (OpenStreetMap). Nutzungsrichtlinie: eigener User-Agent, höchstens 1 Anfrage/s und
 * keine Autovervollständigung – deshalb wird nur auf ausdrückliche Suche hin angefragt. Daten © OpenStreetMap-Mitwirkende.
 */
class NominatimGeocoder(private val baseUrl: String = "https://nominatim.openstreetmap.org") : Geocoder {
    override suspend fun search(query: String): List<Place> {
        val q = query.trim()
        if (q.length < 3) return emptyList()
        val lang = Locale.getDefault().language
        val url = "$baseUrl/search?q=${URLEncoder.encode(q, "UTF-8")}&format=jsonv2&limit=5&accept-language=$lang"
        val body = try {
            httpGet(url)
        } catch (e: java.io.IOException) {
            throw RoutingException("Adresssuche nicht erreichbar (${e.message})", e)
        }
        return parse(body)
    }

    internal fun parse(json: String): List<Place> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Place(o.getString("display_name"), LatLng(o.getString("lat").toDouble(), o.getString("lon").toDouble()))
        }
    }
}

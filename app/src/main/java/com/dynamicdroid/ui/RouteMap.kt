package com.dynamicdroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.routing.RouteGeometry
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Schematische Routenansicht ohne Kartenkacheln (keine Karten-API, kein Schlüssel nötig):
 * Linie der Route, Start, Ziel und – falls [traveledMeters] gesetzt ist – die aktuelle Position.
 */
@Composable
fun RouteMap(route: ActiveRoute, modifier: Modifier = Modifier, traveledMeters: Int? = null) {
    val line = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val end = MaterialTheme.colorScheme.error
    Canvas(modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp))) {
        drawRect(surface)
        val pts = route.geometry
        val minLat = pts.minOf { it.lat }
        val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }
        val maxLon = pts.maxOf { it.lon }
        val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val w = max((maxLon - minLon) * lonScale, 1e-9)
        val h = max(maxLat - minLat, 1e-9)
        val pad = 20.dp.toPx()
        val scale = min((size.width - 2 * pad) / w, (size.height - 2 * pad) / h).toFloat()
        val offX = (size.width - (w * scale).toFloat()) / 2f
        val offY = (size.height - (h * scale).toFloat()) / 2f
        fun project(lat: Double, lon: Double) = Offset(
            offX + ((lon - minLon) * lonScale * scale).toFloat(),
            offY + ((maxLat - lat) * scale).toFloat(),
        )
        val path = Path()
        pts.forEachIndexed { i, p ->
            val o = project(p.lat, p.lon)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, track, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val first = project(pts.first().lat, pts.first().lon)
        val last = project(pts.last().lat, pts.last().lon)
        drawCircle(line, 6.dp.toPx(), first)
        drawCircle(end, 6.dp.toPx(), last)
        if (traveledMeters != null) {
            val cumulative = RouteGeometry.cumulativeMeters(pts)
            val here = RouteGeometry.pointAt(pts, cumulative, traveledMeters.toDouble())
            val o = project(here.lat, here.lon)
            drawCircle(surface, 9.dp.toPx(), o)
            drawCircle(line, 6.dp.toPx(), o)
        }
    }
}

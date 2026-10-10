package com.dynamicdroid.liveupdate

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formatiert Distanzen und Zeiten (metrisch). Rundung ist Absicht: Sie bestimmt, wie oft sich der
 * Anzeigetext – und damit die Benachrichtigung – überhaupt ändert.
 */
class Formatters(
    private val locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** <200 m: 10-m-Schritte, <1 km: 50-m-Schritte, <10 km: 0,1 km, sonst ganze km. */
    fun distance(meters: Int): String {
        val m = meters.coerceAtLeast(0)
        val rounded = when {
            m < 200 -> ((m / 10.0).roundToInt() * 10).coerceAtLeast(10)
            m < 1000 -> (m / 50.0).roundToInt() * 50
            else -> m
        }
        return when {
            rounded < 1000 -> "$rounded m"
            m < 10_000 -> String.format(locale, "%.1f km", (rounded / 100.0).roundToInt() / 10.0)
            else -> "${(m / 1000.0).roundToInt()} km"
        }
    }

    /** Dauer in ganzen Minuten (aufgerundet, min. 1): "7 Min", "1 h 5 Min". */
    fun duration(seconds: Int): String {
        val minutes = ((seconds.coerceAtLeast(0) + 59) / 60).coerceAtLeast(1)
        return if (minutes < 60) "$minutes Min" else "${minutes / 60} h ${minutes % 60} Min"
    }

    /** Ankunftszeit auf die Minute genau. */
    fun clock(epochMillis: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))

    /** Auf die Minute abgerundete Ankunftszeit; stabil innerhalb einer Minute. */
    fun floorToMinute(epochMillis: Long): Long = epochMillis - Math.floorMod(epochMillis, 60_000L)
}

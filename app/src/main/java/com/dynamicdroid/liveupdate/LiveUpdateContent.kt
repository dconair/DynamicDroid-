package com.dynamicdroid.liveupdate

import com.dynamicdroid.model.ManeuverType

/** Ein Abschnitt der Routen-Fortschrittsleiste (Länge in Metern). */
data class SegmentSpec(val lengthMeters: Int)

/** Eine Markierung (Abbiegung) auf der Fortschrittsleiste, Position in Metern ab Routenstart. */
data class PointSpec(val positionMeters: Int)

/**
 * Plattformunabhängiger Anzeigeinhalt für Live-Update-Benachrichtigung und Overlay-Pille.
 * Alle Texte sind bereits formatiert und gerundet. Dadurch ist `equals` genau die Frage
 * "sieht der Nutzer etwas anderes?" – das nutzt der [UpdateThrottler] zum Deduplizieren.
 */
data class LiveUpdateContent(
    val title: String,
    val text: String,
    /** Kurztext für den Status-Chip (Android 16). Höchstens 6 Zeichen, damit er komplett angezeigt wird. */
    val shortCriticalText: String,
    /** Distanz bis zur nächsten Aktion, formatiert (z. B. "250 m"). */
    val distanceToManeuver: String,
    val instruction: String,
    val maneuverType: ManeuverType,
    /** [com.dynamicdroid.model.ManeuverStep.id] der Anweisung; ändert sich nur bei einer neuen Abbiegung. */
    val maneuverKey: Int,
    val etaText: String,
    val remainingText: String,
    val progressMeters: Int,
    val progressMaxMeters: Int,
    val segments: List<SegmentSpec>,
    val points: List<PointSpec>,
    val arrived: Boolean,
    val offRoute: Boolean,
)

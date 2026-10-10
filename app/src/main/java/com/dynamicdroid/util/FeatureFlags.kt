package com.dynamicdroid.util

import android.content.Context
import androidx.core.content.edit

/** Einfache lokale Feature-Flags. Das Overlay ist standardmäßig AUS. */
class FeatureFlags(context: Context) {
    private val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)

    var islandOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_ISLAND_OVERLAY, false)
        set(value) = prefs.edit { putBoolean(KEY_ISLAND_OVERLAY, value) }

    /**
     * Standard (false): Pille hängt unter der Statusleiste und überdeckt keine Systemicons/den Live-Update-Chip.
     * true: Pille sitzt direkt um den Kamera-Cutout und liegt damit über der Statusleiste.
     */
    var islandAroundCutout: Boolean
        get() = prefs.getBoolean(KEY_AROUND_CUTOUT, false)
        set(value) = prefs.edit { putBoolean(KEY_AROUND_CUTOUT, value) }

    private companion object {
        const val KEY_ISLAND_OVERLAY = "island_overlay"
        const val KEY_AROUND_CUTOUT = "island_around_cutout"
    }
}

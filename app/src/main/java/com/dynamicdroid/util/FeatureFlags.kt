package com.dynamicdroid.util

import android.content.Context
import androidx.core.content.edit

/** Einfache lokale Feature-Flags. Das Overlay ist standardmäßig AUS. */
class FeatureFlags(context: Context) {
    private val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)

    var islandOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_ISLAND_OVERLAY, false)
        set(value) = prefs.edit { putBoolean(KEY_ISLAND_OVERLAY, value) }

    private companion object {
        const val KEY_ISLAND_OVERLAY = "island_overlay"
    }
}

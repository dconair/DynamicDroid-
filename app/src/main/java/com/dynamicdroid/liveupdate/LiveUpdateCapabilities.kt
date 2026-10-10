package com.dynamicdroid.liveupdate

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Capability-Checks für Live Updates. Entscheidet über API-Level, nie über Gerätenamen/Hersteller.
 *
 *  - [supportsProgressStyle]: `Notification.ProgressStyle` existiert ab Android 16 (API 36).
 *  - [hasRequestPromotedOngoingApi]: `Notification.Builder#setRequestPromotedOngoing` ist erst ab
 *    Android 16 QPR2 (API 36.1) öffentlich; auf 36.0 wird stattdessen der Extra-Key gesetzt.
 *  - [canPostPromoted]: ob der Nutzer Live Updates für die App erlaubt hat (Systemeinstellung).
 */
class LiveUpdateCapabilities(context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    val supportsProgressStyle: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    val hasRequestPromotedOngoingApi: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1

    fun canPostPromoted(): Boolean =
        supportsProgressStyle && notificationManager.canPostPromotedNotifications()
}

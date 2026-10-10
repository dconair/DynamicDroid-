package com.dynamicdroid.liveupdate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.dynamicdroid.MainActivity
import com.dynamicdroid.R
import com.dynamicdroid.service.NavigationService

/**
 * Baut aus [LiveUpdateContent] die Benachrichtigung:
 *  - Android 16+ ([LiveUpdateCapabilities.supportsProgressStyle]): `Notification.ProgressStyle` als Live Update,
 *  - darunter: klassische ongoing Navigation-Benachrichtigung mit Custom-Layout (RemoteViews).
 */
class LiveUpdatePresenter(
    private val context: Context,
    private val capabilities: LiveUpdateCapabilities = LiveUpdateCapabilities(context),
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_navigation_name),
            // Nicht IMPORTANCE_MIN (Voraussetzung für Promotion); DEFAULT ohne Ton/Vibration, damit kein Heads-up spammt.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_navigation_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun post(content: LiveUpdateContent) {
        notificationManager.notify(NOTIFICATION_ID, build(content))
    }

    fun build(content: LiveUpdateContent): Notification =
        if (capabilities.supportsProgressStyle) buildLiveUpdate(content) else buildClassic(content)

    /** Platzhalter für den Moment zwischen startForeground() und dem ersten Fix. */
    fun buildStarting(): Notification =
        baseBuilder()
            .setContentTitle(context.getString(R.string.notification_starting))
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .build()

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveUpdate(content: LiveUpdateContent): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(content.progressMeters)
            .setProgressSegments(content.segments.map {
                Notification.ProgressStyle.Segment(it.lengthMeters).setColor(ACCENT_COLOR)
            })
            .setProgressPoints(content.points.map {
                Notification.ProgressStyle.Point(it.positionMeters).setColor(POINT_COLOR)
            })
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_nav_tracker))

        val builder = baseBuilder()
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setLargeIcon(Icon.createWithResource(context, ManeuverIcons.forType(content.maneuverType)))
            .setStyle(style)
            .setShortCriticalText(content.shortCriticalText)

        // Ab API 36.1 öffentliche API; auf 36.0 setzen wir denselben Schlüssel direkt als Extra.
        if (capabilities.hasRequestPromotedOngoingApi) {
            builder.setRequestPromotedOngoing(true)
        } else {
            builder.addExtras(Bundle().apply { putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        return builder.build()
    }

    private fun buildClassic(content: LiveUpdateContent): Notification {
        val views = RemoteViews(context.packageName, R.layout.notification_navigation).apply {
            setImageViewResource(R.id.nav_icon, ManeuverIcons.forType(content.maneuverType))
            setTextViewText(R.id.nav_title, content.title)
            setTextViewText(R.id.nav_text, content.text)
            setProgressBar(R.id.nav_progress, content.progressMaxMeters, content.progressMeters, false)
        }
        return baseBuilder()
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            // Titel/Text zusätzlich setzen: Lockscreen-Redaktion, Wear/Auto-Fallbacks und Accessibility lesen sie.
            .setContentTitle(content.title)
            .setContentText(content.text)
            .build()
    }

    private fun baseBuilder(): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(ACCENT_COLOR)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(servicePendingIntent(NavigationService.ACTION_NOTIFICATION_DISMISSED, 2))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stop),
                    context.getString(R.string.action_stop_navigation),
                    servicePendingIntent(NavigationService.ACTION_STOP, 1),
                ).build(),
            )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context, requestCode,
        Intent(context, NavigationService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "navigation"
        const val NOTIFICATION_ID = 1001
        private const val ACCENT_COLOR = 0xFF1A73E8.toInt()
        private const val POINT_COLOR = 0xFFFFFFFF.toInt()
    }
}

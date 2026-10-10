package com.dynamicdroid.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.dynamicdroid.DynamicDroidApp
import com.dynamicdroid.liveupdate.LiveUpdateContent
import com.dynamicdroid.liveupdate.LiveUpdatePresenter
import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.overlay.IslandOverlayPresenter
import com.dynamicdroid.overlay.OverlayPresenter
import com.dynamicdroid.routing.DemoRoute
import com.dynamicdroid.routing.LocationRouteTracker
import com.dynamicdroid.routing.MockRouteSimulator
import com.dynamicdroid.routing.RouteProgressCalculator
import com.dynamicdroid.state.NavigationUiState
import com.dynamicdroid.update.UpdateThrottler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service (Typ `location`) für die Dauer einer aktiven Route.
 *
 * Lebenszyklus: `ACTION_START` -> [startForeground] -> Fortschrittsquelle (Simulation oder GPS) schreibt in den
 * [com.dynamicdroid.state.NavigationState] -> Service beobachtet ihn und aktualisiert Benachrichtigung (gedrosselt)
 * und optional das Overlay. `ACTION_STOP`, Ankunft oder `NavigationState.stop()` beenden den Service wieder.
 */
class NavigationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as DynamicDroidApp
    private val throttler = UpdateThrottler()
    private lateinit var presenter: LiveUpdatePresenter
    private var overlay: OverlayPresenter? = null
    private var sourceJob: Job? = null
    private var observeJob: Job? = null
    private var running = false
    private var notificationDismissedByUser = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        presenter = LiveUpdatePresenter(this).also { it.ensureChannel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startNavigation(intent)
            ACTION_STOP -> stopNavigation()
            ACTION_NOTIFICATION_DISMISSED -> notificationDismissedByUser = true
            else -> if (!running) stopSelf()
        }
        // Die Route lebt nur im Speicher; ein Neustart durch das System hätte nichts zum Anzeigen.
        return START_NOT_STICKY
    }

    private fun startNavigation(intent: Intent) {
        if (running) return
        val useGps = intent.getStringExtra(EXTRA_SOURCE) == SOURCE_GPS
        val speed = intent.getDoubleExtra(EXTRA_SPEED_MULTIPLIER, 1.0)
        val state = app.navigationState

        // Mock: erst Zustand setzen, damit schon die erste Benachrichtigung echten Inhalt hat.
        val demoRoute = DemoRoute.create()
        if (!useGps) {
            state.start(demoRoute, RouteProgressCalculator.fromTraveled(demoRoute, 0.0, System.currentTimeMillis()))
        }
        val first = (state.state.value as? NavigationUiState.Active)?.let { app.contentMapper.map(it) }
        val notification = if (first != null) presenter.build(first) else presenter.buildStarting()

        try {
            startForeground(
                LiveUpdatePresenter.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: SecurityException) {
            // Fehlende Standortberechtigung (Android 14+ prüft sie beim Start eines location-FGS).
            Log.e(TAG, "startForeground abgelehnt – Standortberechtigung fehlt?", e)
            state.stop()
            stopSelf()
            return
        }
        running = true
        notificationDismissedByUser = false
        first?.let { throttler.markPosted(it, System.currentTimeMillis()) }

        if (app.featureFlags.islandOverlayEnabled && Settings.canDrawOverlays(this)) {
            overlay = IslandOverlayPresenter(this, onOpenApp = ::openApp, onStop = ::stopNavigation).also { it.show() }
        }

        observeJob = scope.launch { observeState() }
        sourceJob = scope.launch {
            if (useGps) {
                LocationRouteTracker(this@NavigationService, state).trackFromFirstFix { origin -> DemoRoute.create(origin) }
            } else {
                MockRouteSimulator(state).run(demoRoute, speed)
                // Ziel erreicht: Ankunftsanzeige kurz stehen lassen, dann beenden.
                delay(ARRIVAL_LINGER_MS)
                stopNavigation()
            }
        }
    }

    private suspend fun observeState() {
        var sawActive = false
        app.navigationState.state.collectLatest { ui ->
            when (ui) {
                is NavigationUiState.Active -> {
                    sawActive = true
                    val content = app.contentMapper.map(ui)
                    overlay?.render(content)
                    postThrottled(content)
                }
                NavigationUiState.Idle -> if (sawActive) stopNavigation()
            }
        }
    }

    /** Wird von `collectLatest` abgebrochen, sobald ein neuerer Zustand eintrifft – ein wartendes Update verfällt dann. */
    private suspend fun postThrottled(content: LiveUpdateContent) {
        while (true) {
            when (val decision = throttler.evaluate(content, System.currentTimeMillis())) {
                UpdateThrottler.Decision.Skip -> return
                UpdateThrottler.Decision.Post -> {
                    // Hat der Nutzer die Benachrichtigung weggewischt, posten wir sie nicht erneut (Plattformvorgabe).
                    if (!notificationDismissedByUser) presenter.post(content)
                    throttler.markPosted(content, System.currentTimeMillis())
                    return
                }
                is UpdateThrottler.Decision.Defer -> delay(decision.delayMs)
            }
        }
    }

    private fun stopNavigation() {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        observeJob?.cancel()
        sourceJob?.cancel()
        overlay?.hide()
        overlay = null
        app.navigationState.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openApp() {
        startActivity(
            Intent(this, com.dynamicdroid.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    override fun onDestroy() {
        overlay?.hide()
        scope.cancel()
        if (running) app.navigationState.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NavigationService"
        const val ACTION_START = "com.dynamicdroid.action.START"
        const val ACTION_STOP = "com.dynamicdroid.action.STOP"
        const val ACTION_NOTIFICATION_DISMISSED = "com.dynamicdroid.action.NOTIFICATION_DISMISSED"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SPEED_MULTIPLIER = "speed_multiplier"
        const val SOURCE_MOCK = "mock"
        const val SOURCE_GPS = "gps"
        private const val ARRIVAL_LINGER_MS = 10_000L

        /** Muss aus dem Vordergrund (sichtbare Activity) aufgerufen werden – Voraussetzung für location-FGS. */
        fun start(context: Context, source: String = SOURCE_MOCK, speedMultiplier: Double = 1.0) {
            val intent = Intent(context, NavigationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_SPEED_MULTIPLIER, speedMultiplier)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, NavigationService::class.java).setAction(ACTION_STOP))
        }
    }
}

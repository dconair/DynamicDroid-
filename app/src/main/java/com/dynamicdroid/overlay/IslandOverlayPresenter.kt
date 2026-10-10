package com.dynamicdroid.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowMetrics
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dynamicdroid.liveupdate.LiveUpdateContent

/**
 * Hängt eine Compose-Pille (TYPE_APPLICATION_OVERLAY) unter die Statusleiste oder (optional, [aroundCutout]) um den
 * Kamera-Cutout. Benötigt SYSTEM_ALERT_WINDOW.
 *
 * Grenzen: Ein Anwendungs-Overlay erscheint nie über dem Sperrbildschirm (Keyguard) und auch nicht über
 * manchen System-UIs; dort übernimmt die Live-Update-Benachrichtigung. Während unsere eigene App im Vordergrund
 * ist, blenden wir die Pille aus. Muss auf dem Main-Thread benutzt werden.
 */
class IslandOverlayPresenter(
    private val context: Context,
    private val onOpenApp: () -> Unit,
    private val onStop: () -> Unit,
    private val aroundCutout: Boolean = false,
) : OverlayPresenter {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val contentState = mutableStateOf<LiveUpdateContent?>(null)
    private val shownState = mutableStateOf(false)
    private val appForegroundState = mutableStateOf(false)
    private val lifecycleOwner = OverlayLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var cutout = CutoutGeometry(0f, 0f, 0, 0)

    // Die Pille blendet per Spring aus, wenn die eigene App sichtbar wird, und wieder ein, wenn sie verschwindet.
    private val appVisibilityObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { appForegroundState.value = true }
        override fun onStop(owner: LifecycleOwner) { appForegroundState.value = false }
    }

    override fun show() {
        if (view != null) return
        cutout = CutoutGeometry.read(windowManager.currentWindowMetrics, density, aroundCutout)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                IslandPill(
                    content = contentState.value,
                    visible = shownState.value && !appForegroundState.value,
                    cutoutWidthDp = cutout.widthDp,
                    cutoutHeightDp = cutout.heightDp,
                    onOpenApp = onOpenApp,
                    onStop = onStop,
                    onWindowExpanded = ::resizeWindow,
                )
            }
        }
        lifecycleOwner.attachTo(composeView)
        lifecycleOwner.start()

        // Festes Fenster (kein WRAP_CONTENT): die Animation läuft komplett in Compose, das Fenster wird nur
        // vor dem Aufklappen vergrößert und nach dem Zuklappen verkleinert – so gibt es keine Layout-Ruckler und
        // das Overlay fängt im eingeklappten Zustand kaum Touches ab.
        val lp = WindowManager.LayoutParams(
            (IslandDimens.windowWidth(false, cutout.widthDp) * density).toInt(),
            (IslandDimens.windowHeight(false, cutout.heightDp) * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = cutout.offsetXPx
            y = cutout.topPx
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "DynamicDroid Island"
        }
        windowManager.addView(composeView, lp)
        view = composeView
        params = lp

        ProcessLifecycleOwner.get().lifecycle.addObserver(appVisibilityObserver)
        shownState.value = true
    }

    private fun resizeWindow(expanded: Boolean) {
        val v = view ?: return
        val lp = params ?: return
        lp.width = (IslandDimens.windowWidth(expanded, cutout.widthDp) * density).toInt()
        lp.height = (IslandDimens.windowHeight(expanded, cutout.heightDp) * density).toInt()
        runCatching { windowManager.updateViewLayout(v, lp) }
    }

    override fun render(content: LiveUpdateContent) {
        contentState.value = content
    }

    /** Blendet per Spring aus und entfernt das Fenster erst danach, damit die Pille nicht abrupt verschwindet. */
    override fun hide() {
        val v = view ?: return
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appVisibilityObserver)
        shownState.value = false
        view = null
        handler.postDelayed({
            runCatching { windowManager.removeViewImmediate(v) }
            lifecycleOwner.stop()
        }, EXIT_ANIMATION_MS)
    }

    private companion object {
        const val EXIT_ANIMATION_MS = 600L
    }
}

/** Position/Größe der Kameraaussparung, aus `DisplayCutout` gelesen. */
internal data class CutoutGeometry(
    val widthDp: Float,
    val heightDp: Float,
    /** Horizontaler Versatz der Aussparungsmitte relativ zur Bildschirmmitte (Pixel). */
    val offsetXPx: Int,
    /** Y-Position der Pille (Pixel). */
    val topPx: Int,
) {
    companion object {
        private const val FALLBACK_TOP_MARGIN_DP = 6f

        fun read(metrics: WindowMetrics, density: Float, aroundCutout: Boolean): CutoutGeometry {
            val insets = metrics.windowInsets
            val cutout = insets.displayCutout
            val bounds = if (aroundCutout) cutout?.boundingRects?.firstOrNull { it.top <= 0 && !it.isEmpty } else null
            return if (bounds != null) {
                CutoutGeometry(
                    widthDp = bounds.width() / density,
                    heightDp = bounds.height() / density,
                    offsetXPx = bounds.centerX() - metrics.bounds.width() / 2,
                    topPx = 0,
                )
            } else {
                // Standard bzw. kein Cutout: Pille mittig UNTER der Statusleiste, damit sie keine Systemicons überdeckt.
                val statusBar = insets.getInsets(WindowInsets.Type.statusBars()).top
                CutoutGeometry(0f, 0f, 0, statusBar + (FALLBACK_TOP_MARGIN_DP * density).toInt())
            }
        }
    }
}

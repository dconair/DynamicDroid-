package com.dynamicdroid.overlay

import android.content.Context
import android.graphics.PixelFormat
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
 * Hängt eine Compose-Pille (TYPE_APPLICATION_OVERLAY) um den Kamera-Cutout. Benötigt SYSTEM_ALERT_WINDOW.
 *
 * Grenzen: Ein Anwendungs-Overlay erscheint nie über dem Sperrbildschirm (Keyguard) und auch nicht über
 * manchen System-UIs; dort übernimmt die Live-Update-Benachrichtigung. Während unsere eigene App im Vordergrund
 * ist, blenden wir die Pille aus. Muss auf dem Main-Thread benutzt werden.
 */
class IslandOverlayPresenter(
    private val context: Context,
    private val onOpenApp: () -> Unit,
    private val onStop: () -> Unit,
) : OverlayPresenter {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val contentState = mutableStateOf<LiveUpdateContent?>(null)
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var view: ComposeView? = null

    private val appVisibilityObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { view?.visibility = android.view.View.GONE }
        override fun onStop(owner: LifecycleOwner) { view?.visibility = android.view.View.VISIBLE }
    }

    override fun show() {
        if (view != null) return
        val cutout = CutoutGeometry.read(windowManager.currentWindowMetrics, context.resources.displayMetrics.density)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                IslandPill(
                    content = contentState.value,
                    cutoutWidthDp = cutout.widthDp,
                    cutoutHeightDp = cutout.heightDp,
                    onOpenApp = onOpenApp,
                    onStop = onStop,
                )
            }
        }
        lifecycleOwner.attachTo(composeView)
        lifecycleOwner.start()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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
        windowManager.addView(composeView, params)
        view = composeView

        val process = ProcessLifecycleOwner.get().lifecycle
        process.addObserver(appVisibilityObserver)
        if (process.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            composeView.visibility = android.view.View.GONE
        }
    }

    override fun render(content: LiveUpdateContent) {
        contentState.value = content
    }

    override fun hide() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appVisibilityObserver)
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
        lifecycleOwner.stop()
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

        fun read(metrics: WindowMetrics, density: Float): CutoutGeometry {
            val insets = metrics.windowInsets
            val cutout = insets.displayCutout
            val bounds = cutout?.boundingRects?.firstOrNull { it.top <= 0 && !it.isEmpty }
            return if (bounds != null) {
                CutoutGeometry(
                    widthDp = bounds.width() / density,
                    heightDp = bounds.height() / density,
                    offsetXPx = bounds.centerX() - metrics.bounds.width() / 2,
                    topPx = 0,
                )
            } else {
                // Kein Cutout: Pille mittig unter der Statusleiste.
                val statusBar = insets.getInsets(WindowInsets.Type.statusBars()).top
                CutoutGeometry(0f, 0f, 0, statusBar + (FALLBACK_TOP_MARGIN_DP * density).toInt())
            }
        }
    }
}

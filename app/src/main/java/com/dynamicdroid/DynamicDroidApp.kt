package com.dynamicdroid

import android.app.Application
import com.dynamicdroid.liveupdate.AndroidNavigationTexts
import com.dynamicdroid.liveupdate.LiveUpdateContentMapper
import com.dynamicdroid.state.NavigationState
import com.dynamicdroid.util.FeatureFlags

class DynamicDroidApp : Application() {
    /** Vom Nutzer geplante Route (Suche + Routing); der Service liest sie beim Start. */
    val plannedRoute = kotlinx.coroutines.flow.MutableStateFlow<com.dynamicdroid.model.ActiveRoute?>(null)

    /** Prozessweite Single Source of Truth (Service, UI und Overlay teilen sich dieselbe Instanz). */
    val navigationState = NavigationState()
    val featureFlags by lazy { FeatureFlags(this) }
    val contentMapper by lazy { LiveUpdateContentMapper(AndroidNavigationTexts(this)) }
}

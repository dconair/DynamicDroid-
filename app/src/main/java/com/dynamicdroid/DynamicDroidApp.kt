package com.dynamicdroid

import android.app.Application
import com.dynamicdroid.liveupdate.AndroidNavigationTexts
import com.dynamicdroid.liveupdate.LiveUpdateContentMapper
import com.dynamicdroid.state.NavigationState
import com.dynamicdroid.util.FeatureFlags

class DynamicDroidApp : Application() {
    /** Prozessweite Single Source of Truth (Service, UI und Overlay teilen sich dieselbe Instanz). */
    val navigationState = NavigationState()
    val featureFlags by lazy { FeatureFlags(this) }
    val contentMapper by lazy { LiveUpdateContentMapper(AndroidNavigationTexts(this)) }
}

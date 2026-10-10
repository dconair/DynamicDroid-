package com.dynamicdroid.overlay

import com.dynamicdroid.liveupdate.LiveUpdateContent

/** Dritter Abnehmer des [com.dynamicdroid.state.NavigationState]: die Dynamic-Island-artige Pille. */
interface OverlayPresenter {
    fun show()
    fun render(content: LiveUpdateContent)
    fun hide()
}

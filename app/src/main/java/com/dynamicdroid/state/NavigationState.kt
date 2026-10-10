package com.dynamicdroid.state

import com.dynamicdroid.model.ActiveRoute
import com.dynamicdroid.model.RouteProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NavigationUiState {
    data object Idle : NavigationUiState

    data class Active(
        val route: ActiveRoute,
        val progress: RouteProgress,
    ) : NavigationUiState
}

/**
 * Single Source of Truth für die laufende Navigation. Alle Presenter (Notification, Overlay, UI)
 * beobachten nur [state]; geschrieben wird ausschließlich über [start], [updateProgress] und [stop].
 */
class NavigationState {
    private val _state = MutableStateFlow<NavigationUiState>(NavigationUiState.Idle)
    val state: StateFlow<NavigationUiState> = _state.asStateFlow()

    fun start(route: ActiveRoute, initial: RouteProgress) {
        _state.value = NavigationUiState.Active(route, initial)
    }

    /** Ignoriert Updates, wenn keine Route aktiv ist (z. B. ein spätes GPS-Fix nach dem Beenden). */
    fun updateProgress(progress: RouteProgress) {
        _state.value = when (val current = _state.value) {
            is NavigationUiState.Active -> current.copy(progress = progress)
            NavigationUiState.Idle -> current
        }
    }

    fun stop() {
        _state.value = NavigationUiState.Idle
    }
}

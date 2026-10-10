package com.dynamicdroid.update

import com.dynamicdroid.liveupdate.LiveUpdateContent

/**
 * Entscheidet, ob ein neuer [LiveUpdateContent] tatsächlich als Benachrichtigungs-Update gepostet wird.
 *
 *  - identisch mit dem zuletzt geposteten Inhalt            -> [Decision.Skip]
 *  - neue Abbiegung / Ankunft / Off-Route-Wechsel          -> [Decision.Post] sofort
 *  - nur Distanz-, Restzeit- oder Fortschrittsänderung     -> höchstens alle [minIntervalMs] Millisekunden,
 *    sonst [Decision.Defer] mit der verbleibenden Wartezeit
 *
 * Der Aufrufer ruft nach dem tatsächlichen Posten [markPosted] auf. Nicht threadsafe (Main-Thread / eine Coroutine).
 */
class UpdateThrottler(private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS) {

    sealed interface Decision {
        data object Post : Decision
        data object Skip : Decision
        data class Defer(val delayMs: Long) : Decision
    }

    private var last: LiveUpdateContent? = null
    private var lastPostAtMs: Long = 0

    fun evaluate(content: LiveUpdateContent, nowMs: Long): Decision {
        val previous = last ?: return Decision.Post
        if (content == previous) return Decision.Skip
        if (isSignificant(previous, content)) return Decision.Post
        val wait = lastPostAtMs + minIntervalMs - nowMs
        return if (wait <= 0) Decision.Post else Decision.Defer(wait)
    }

    fun markPosted(content: LiveUpdateContent, nowMs: Long) {
        last = content
        lastPostAtMs = nowMs
    }

    fun reset() {
        last = null
        lastPostAtMs = 0
    }

    private fun isSignificant(old: LiveUpdateContent, new: LiveUpdateContent): Boolean =
        old.maneuverKey != new.maneuverKey ||
            old.maneuverType != new.maneuverType ||
            old.instruction != new.instruction ||
            old.arrived != new.arrived ||
            old.offRoute != new.offRoute

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 2_000L
    }
}

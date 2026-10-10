package com.dynamicdroid.routing

/**
 * Entscheidet, wann nach einer Abweichung neu geroutet wird: erst nach [minOffRouteFixes] aufeinanderfolgenden
 * Fixes abseits der Route (ein einzelner GPS-Ausreißer zählt nicht) und höchstens alle [minIntervalMs].
 */
class RerouteDecider(
    private val minOffRouteFixes: Int = 3,
    private val minIntervalMs: Long = 15_000,
) {
    private var offRouteFixes = 0
    private var lastRerouteAtMs = Long.MIN_VALUE / 2

    fun onFix(offRoute: Boolean, nowMs: Long): Boolean {
        offRouteFixes = if (offRoute) offRouteFixes + 1 else 0
        if (offRouteFixes < minOffRouteFixes || nowMs - lastRerouteAtMs < minIntervalMs) return false
        lastRerouteAtMs = nowMs
        offRouteFixes = 0
        return true
    }

    /** Nach einer neuen Route zurücksetzen (neue Route startet "sauber"). */
    fun reset() {
        offRouteFixes = 0
    }
}

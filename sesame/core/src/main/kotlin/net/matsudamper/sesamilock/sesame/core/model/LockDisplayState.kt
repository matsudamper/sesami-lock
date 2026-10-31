package net.matsudamper.sesamilock.sesame.core.model

import kotlin.time.Duration

public enum class LockDisplayState {
    LOCKED_VERIFIED,
    UNLOCKED_VERIFIED,
    NOT_LOCKED_OR_MOVING,
    STALE,
    UNKNOWN,
    DISCONNECTED,
}

public fun LockSnapshot.toDisplayState(nowElapsedMillis: Long, freshness: Duration): LockDisplayState {
    val current = observation
    return when (connection) {
        ConnectionState.DISCONNECTED,
        ConnectionState.CONNECTING,
        -> if (current == null) LockDisplayState.DISCONNECTED else LockDisplayState.STALE

        ConnectionState.AUTHENTICATED -> when {
            current == null -> LockDisplayState.UNKNOWN

            current.connectionGeneration != connectionGeneration -> LockDisplayState.STALE

            !pushesUpdatesWhileConnected && nowElapsedMillis - current.observedAtElapsedMillis > freshness.inWholeMilliseconds -> LockDisplayState.STALE

            else -> when (current.state) {
                MechanicalState.LOCKED -> LockDisplayState.LOCKED_VERIFIED
                MechanicalState.UNLOCKED_VERIFIED -> LockDisplayState.UNLOCKED_VERIFIED
                MechanicalState.NOT_LOCKED_OR_MOVING -> LockDisplayState.NOT_LOCKED_OR_MOVING
            }
        }
    }
}

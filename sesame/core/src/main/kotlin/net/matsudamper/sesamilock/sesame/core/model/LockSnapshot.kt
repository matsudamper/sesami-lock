package net.matsudamper.sesamilock.sesame.core.model

public enum class MechanicalState {
    LOCKED,
    UNLOCKED_VERIFIED,
    NOT_LOCKED_OR_MOVING,
}

public enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATED,
}

public data class MechanicalObservation(
    val state: MechanicalState,
    val observedAtElapsedMillis: Long,
    val observedAtWallMillis: Long,
    val connectionGeneration: Long,
    val batteryCritical: Boolean,
)

public data class LockSnapshot(
    val lockId: LockId,
    val route: LockRoute,
    val connection: ConnectionState,
    val connectionGeneration: Long,
    val observation: MechanicalObservation?,
    val pushesUpdatesWhileConnected: Boolean,
) {
    public companion object {
        public fun initial(lockId: LockId, route: LockRoute, pushesUpdatesWhileConnected: Boolean): LockSnapshot = LockSnapshot(
            lockId = lockId,
            route = route,
            connection = ConnectionState.DISCONNECTED,
            connectionGeneration = 0,
            observation = null,
            pushesUpdatesWhileConnected = pushesUpdatesWhileConnected,
        )
    }
}

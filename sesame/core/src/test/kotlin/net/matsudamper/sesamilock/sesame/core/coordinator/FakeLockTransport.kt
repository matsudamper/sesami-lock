package net.matsudamper.sesamilock.sesame.core.coordinator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import net.matsudamper.sesamilock.sesame.core.model.ConnectionState
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalObservation
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.LockSnapshotStore
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport

internal class FakeLockTransport(
    override val route: LockRoute,
    private val lockId: LockId,
    private val clock: LockClock,
    pushesUpdatesWhileConnected: Boolean,
) : LockTransport {
    val store = LockSnapshotStore(LockSnapshot.initial(lockId, route, pushesUpdatesWhileConnected))
    val dispatched = mutableListOf<LockIntent>()
    var connectionGate: CompletableDeferred<ConnectionResult>? = null
    var dispatchResult: DispatchResult = DispatchResult.Accepted
    var authenticated = true
    var onDispatch: (LockIntent) -> Unit = {}
    var refreshCount = 0
    private var generation = 0L

    override fun snapshot(lockId: LockId): StateFlow<LockSnapshot> = store.snapshot

    override suspend fun ensureConnected(lockId: LockId): ConnectionResult {
        val gate = connectionGate
        if (gate != null) return gate.await()
        return connectNow()
    }

    fun connectNow(): ConnectionResult.Ready {
        generation += 1
        store.updateConnection(ConnectionState.AUTHENTICATED, generation)
        return ConnectionResult.Ready(lockId, generation)
    }

    fun disconnect() {
        store.updateConnection(ConnectionState.DISCONNECTED, generation)
    }

    fun observe(state: MechanicalState, generation: Long = this.generation): Boolean = store.offerObservation(
        MechanicalObservation(
            state = state,
            observedAtElapsedMillis = clock.elapsedRealtimeMillis(),
            observedAtWallMillis = clock.currentTimeMillis(),
            connectionGeneration = generation,
            batteryCritical = false,
        ),
    )

    override fun isAuthenticated(lockId: LockId): Boolean = authenticated

    override suspend fun dispatchExplicit(lockId: LockId, intent: LockIntent, requestId: RequestId): DispatchResult {
        dispatched += intent
        onDispatch(intent)
        return dispatchResult
    }

    override suspend fun refreshState(lockId: LockId) {
        refreshCount += 1
    }
}

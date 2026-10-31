package net.matsudamper.sesamilock.sesame.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.sesamilock.sesame.core.model.ConnectionState
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalObservation
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState

class LockSnapshotStoreTest {
    private val lockId = requireNotNull(LockId.fromDeviceUuid("8c9a7bb0-1b3c-4f43-9a6e-6c3c2f2b5e01"))

    private fun observation(state: MechanicalState, at: Long, generation: Long) = MechanicalObservation(
        state = state,
        observedAtElapsedMillis = at,
        observedAtWallMillis = at,
        connectionGeneration = generation,
        batteryCritical = false,
    )

    @Test
    fun observationFromOldConnectionDoesNotOverwriteNewer() {
        val store = LockSnapshotStore(LockSnapshot.initial(lockId, LockRoute.BLE, pushesUpdatesWhileConnected = true))
        store.updateConnection(ConnectionState.AUTHENTICATED, connectionGeneration = 2)
        assertTrue(store.offerObservation(observation(MechanicalState.NOT_LOCKED_OR_MOVING, at = 100, generation = 2)))

        assertFalse(store.offerObservation(observation(MechanicalState.LOCKED, at = 200, generation = 1)))

        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, store.snapshot.value.observation?.state)
    }

    @Test
    fun olderObservationOnSameConnectionIsRejected() {
        val store = LockSnapshotStore(LockSnapshot.initial(lockId, LockRoute.CLOUD, pushesUpdatesWhileConnected = false))
        store.updateConnection(ConnectionState.AUTHENTICATED, connectionGeneration = 1)
        store.offerObservation(observation(MechanicalState.NOT_LOCKED_OR_MOVING, at = 300, generation = 1))

        assertFalse(store.offerObservation(observation(MechanicalState.LOCKED, at = 200, generation = 1)))
    }

    @Test
    fun connectionStateOfOldGenerationIsIgnored() {
        val store = LockSnapshotStore(LockSnapshot.initial(lockId, LockRoute.BLE, pushesUpdatesWhileConnected = true))
        store.updateConnection(ConnectionState.AUTHENTICATED, connectionGeneration = 3)

        store.updateConnection(ConnectionState.DISCONNECTED, connectionGeneration = 2)

        assertEquals(ConnectionState.AUTHENTICATED, store.snapshot.value.connection)
    }
}

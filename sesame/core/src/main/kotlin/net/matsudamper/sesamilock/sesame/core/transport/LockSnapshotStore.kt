package net.matsudamper.sesamilock.sesame.core.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.matsudamper.sesamilock.sesame.core.model.ConnectionState
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalObservation

public class LockSnapshotStore(initial: LockSnapshot) {
    private val state = MutableStateFlow(initial)
    public val snapshot: StateFlow<LockSnapshot> = state.asStateFlow()

    public fun updateConnection(connection: ConnectionState, connectionGeneration: Long) {
        state.update { current ->
            if (connectionGeneration < current.connectionGeneration) {
                current
            } else {
                current.copy(connection = connection, connectionGeneration = connectionGeneration)
            }
        }
    }

    /**
     * 古い接続世代や古い時刻の観測で新しい状態を上書きしないように、受け入れ可能な観測だけを反映する。
     * @return 反映したら true
     */
    public fun offerObservation(observation: MechanicalObservation): Boolean {
        var accepted = false
        state.update { current ->
            accepted = isNewer(current, observation)
            if (accepted) current.copy(observation = observation) else current
        }
        return accepted
    }

    private fun isNewer(current: LockSnapshot, observation: MechanicalObservation): Boolean {
        if (observation.connectionGeneration < current.connectionGeneration) return false
        val previous = current.observation ?: return true
        if (observation.connectionGeneration < previous.connectionGeneration) return false
        if (observation.connectionGeneration > previous.connectionGeneration) return true
        return observation.observedAtElapsedMillis >= previous.observedAtElapsedMillis
    }
}

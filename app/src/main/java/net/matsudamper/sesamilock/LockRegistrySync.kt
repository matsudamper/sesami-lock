package net.matsudamper.sesamilock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.sesame.ble.SesameBleTransport
import net.matsudamper.sesamilock.sesame.cloud.SesameCloudTransport
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState

internal class LockRegistrySync(
    private val repository: EncryptedLockRepository,
    private val bleTransport: SesameBleTransport,
    private val cloudTransport: SesameCloudTransport,
) {
    private val mutableRejectedLockIds = MutableStateFlow<Set<LockId>>(setOf())
    val rejectedLockIds: StateFlow<Set<LockId>> = mutableRejectedLockIds.asStateFlow()
    private var registered: Map<LockId, RegisteredLock> = mapOf()

    fun start(scope: CoroutineScope) {
        scope.launch {
            repository.state.collect { state ->
                if (state is LockRepositoryState.Loaded) sync(state.locks)
            }
        }
    }

    private fun sync(locks: List<RegisteredLock>) {
        val next = locks.associateBy { it.id }
        registered.filterKeys { it !in next }.values.forEach { unregister(it) }
        val rejected = next.values.filter { it.id !in registered }.filterNot { register(it) }.map { it.id }.toSet()
        mutableRejectedLockIds.value = (mutableRejectedLockIds.value intersect next.keys) + rejected
        registered = next
    }

    private fun register(lock: RegisteredLock): Boolean = when (val credential = lock.credential) {
        is LockCredential.Ble -> bleTransport.register(lock.id, credential)

        is LockCredential.Cloud -> {
            cloudTransport.register(lock.id, credential)
            true
        }
    }

    private fun unregister(lock: RegisteredLock) {
        when (lock.credential) {
            is LockCredential.Ble -> bleTransport.unregister(lock.id)
            is LockCredential.Cloud -> cloudTransport.unregister(lock.id)
        }
    }
}

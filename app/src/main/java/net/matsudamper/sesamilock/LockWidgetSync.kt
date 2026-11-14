package net.matsudamper.sesamilock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.LockCommandCoordinator
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState
import net.matsudamper.sesamilock.ui.home.LockTexts
import net.matsudamper.sesamilock.widget.LockWidgetContent
import net.matsudamper.sesamilock.widget.LockWidgetController
import net.matsudamper.sesamilock.widget.LockWidgetIcon

internal class LockWidgetSync(
    private val controller: LockWidgetController,
    private val repository: EncryptedLockRepository,
    private val transports: Map<LockRoute, LockTransport>,
    private val coordinator: LockCommandCoordinator,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            repository.state.flatMapLatest { state ->
                when (state) {
                    LockRepositoryState.Loading -> emptyFlow()
                    LockRepositoryState.Unrecoverable -> flowOf(mapOf())
                    is LockRepositoryState.Loaded -> widgetContents(state.locks)
                }
            }.distinctUntilChanged().collectLatest(controller::updateAll)
        }
    }

    private fun widgetContents(locks: List<RegisteredLock>): Flow<Map<String, LockWidgetContent>> = if (locks.isEmpty()) {
        flowOf(mapOf())
    } else {
        combine(
            locks.map { lock ->
                combine(transports.getValue(lock.route).snapshot(lock.id), coordinator.phase(lock.id)) { snapshot, phase ->
                    lock.id.value to LockWidgetContent(
                        name = lock.displayName,
                        observedStatusIfKnown = observedStatus(snapshot),
                        inProgressLabel = (phase as? CommandPhase.InProgress)?.let(LockTexts::inProgress),
                    )
                }
            },
        ) { it.toMap() }
    }

    private fun observedStatus(snapshot: LockSnapshot): LockWidgetContent.ObservedStatus? {
        val observation = snapshot.observation ?: return null
        return LockWidgetContent.ObservedStatus(
            label = LockTexts.previousState(observation.state),
            icon = when (observation.state) {
                MechanicalState.LOCKED -> LockWidgetIcon.LOCKED
                MechanicalState.UNLOCKED_VERIFIED, MechanicalState.OUT_OF_LOCK_RANGE -> LockWidgetIcon.UNLOCKED
                MechanicalState.MOVING -> LockWidgetIcon.MOVING
            },
            detail = "確認 ${LockTexts.time(observation.observedAtWallMillis)}",
        )
    }
}

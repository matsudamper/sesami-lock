package net.matsudamper.sesamilock.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.LockCommandCoordinator
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState
import net.matsudamper.sesamilock.ui.home.LockTexts

internal class LockWidgetUpdater(
    private val context: Context,
    private val repository: EncryptedLockRepository,
    private val transports: Map<LockRoute, LockTransport>,
    private val coordinator: LockCommandCoordinator,
) {
    private val latestLocks = MutableStateFlow<Map<String, WidgetLock>?>(null)
    private val writeMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            repository.state.flatMapLatest { state ->
                when (state) {
                    LockRepositoryState.Loading -> emptyFlow()
                    LockRepositoryState.Unrecoverable -> flowOf(mapOf())
                    is LockRepositoryState.Loaded -> widgetLocks(state.locks)
                }
            }.distinctUntilChanged().collectLatest { locks ->
                latestLocks.value = locks
                updateAll(locks)
            }
        }
    }

    suspend fun assign(appWidgetId: Int, lockId: LockId) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        writeMutex.withLock {
            updateAppWidgetState(context, glanceId) { preferences ->
                preferences.clear()
                preferences[LockWidgetKeys.lockId] = lockId.value
                latestLocks.value?.get(lockId.value)?.let { preferences.write(it) }
            }
            LockWidget().update(context, glanceId)
        }
    }

    private fun widgetLocks(locks: List<RegisteredLock>): Flow<Map<String, WidgetLock>> = if (locks.isEmpty()) {
        flowOf(mapOf())
    } else {
        combine(
            locks.map { lock ->
                combine(transports.getValue(lock.route).snapshot(lock.id), coordinator.phase(lock.id)) { snapshot, phase ->
                    lock.id.value to WidgetLock(lock.displayName, status(snapshot, phase))
                }
            },
        ) { it.toMap() }
    }

    private suspend fun updateAll(locks: Map<String, WidgetLock>) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(LockWidget::class.java)
        writeMutex.withLock {
            glanceIds.forEach { glanceId -> update(glanceId, locks) }
        }
    }

    private suspend fun update(glanceId: GlanceId, locks: Map<String, WidgetLock>) {
        updateAppWidgetState(context, glanceId) { preferences ->
            val lockIdValue = preferences[LockWidgetKeys.lockId] ?: return@updateAppWidgetState
            val lock = locks[lockIdValue]
            if (lock == null) {
                preferences[LockWidgetKeys.statusLabel] = "鍵が削除されました"
                preferences[LockWidgetKeys.statusIcon] = LockWidgetIcon.UNKNOWN.name
                preferences.remove(LockWidgetKeys.detail)
            } else {
                preferences.write(lock)
            }
        }
        LockWidget().update(context, glanceId)
    }

    private fun MutablePreferences.write(lock: WidgetLock) {
        this[LockWidgetKeys.name] = lock.name
        val status = lock.statusIfKnown ?: return
        this[LockWidgetKeys.statusLabel] = status.label
        this[LockWidgetKeys.statusIcon] = status.icon.name
        this[LockWidgetKeys.detail] = status.detail
    }

    private fun status(snapshot: LockSnapshot, phase: CommandPhase): WidgetStatus? {
        val inProgress = phase as? CommandPhase.InProgress
        val observation = snapshot.observation
        val observedDetail = observation?.let { "確認 ${LockTexts.time(it.observedAtWallMillis)}" }.orEmpty()
        return when {
            inProgress != null -> WidgetStatus(LockTexts.inProgress(inProgress), LockWidgetIcon.MOVING, observedDetail)

            observation != null -> WidgetStatus(
                label = LockTexts.previousState(observation.state),
                icon = when (observation.state) {
                    MechanicalState.LOCKED -> LockWidgetIcon.LOCKED
                    MechanicalState.UNLOCKED_VERIFIED, MechanicalState.OUT_OF_LOCK_RANGE -> LockWidgetIcon.UNLOCKED
                    MechanicalState.MOVING -> LockWidgetIcon.MOVING
                },
                detail = observedDetail,
            )

            else -> null
        }
    }

    private data class WidgetLock(val name: String, val statusIfKnown: WidgetStatus?)

    private data class WidgetStatus(val label: String, val icon: LockWidgetIcon, val detail: String)
}

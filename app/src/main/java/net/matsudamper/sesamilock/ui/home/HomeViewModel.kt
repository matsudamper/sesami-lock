package net.matsudamper.sesamilock.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.sesamilock.BlePermissions
import net.matsudamper.sesamilock.LockRegistrySync
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.LockCommandCoordinator
import net.matsudamper.sesamilock.sesame.core.coordinator.LockTarget
import net.matsudamper.sesamilock.sesame.core.coordinator.StartResult
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockDisplayState
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.model.toDisplayState
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState

internal class HomeViewModel(
    private val repository: EncryptedLockRepository,
    private val transports: Map<LockRoute, LockTransport>,
    private val coordinator: LockCommandCoordinator,
    private val clock: LockClock,
    registrySync: LockRegistrySync,
    private val blePermissions: BlePermissions,
) : ViewModel() {
    private val viewState = MutableStateFlow(
        ViewState(
            pendingDeleteLockId = null,
            blePermissionsGranted = blePermissions.isGranted(),
            messages = mapOf(),
        ),
    )
    private var authenticationPromptCache: Pair<RequestId, HomeUiState.AuthenticationPrompt>? = null

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock.elapsedRealtimeMillis())
            delay(1.seconds)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val locksModel: Flow<LocksModel> = repository.state.flatMapLatest { state ->
        when (state) {
            LockRepositoryState.Loading -> flowOf(LocksModel.Loading)

            LockRepositoryState.Unrecoverable -> flowOf(LocksModel.Unrecoverable)

            is LockRepositoryState.Loaded -> if (state.locks.isEmpty()) {
                flowOf(LocksModel.Loaded(listOf()))
            } else {
                combine(
                    state.locks.map { lock ->
                        combine(transport(lock).snapshot(lock.id), coordinator.phase(lock.id)) { snapshot, phase ->
                            LockModel(lock, snapshot, phase)
                        }
                    },
                ) { models -> LocksModel.Loaded(models.toList()) }
            }
        }
    }

    private val listener = object : HomeUiState.Listener {
        override fun onResume() {
            viewState.update { it.copy(blePermissionsGranted = blePermissions.isGranted()) }
            refreshAll()
        }
    }

    private val unrecoverableListener = object : HomeUiState.UnrecoverableListener {
        override fun onDeleteAllClick() {
            viewModelScope.launch { repository.deleteAll() }
        }
    }

    private val permissionListener = object : HomeUiState.PermissionListener {
        override fun onPermissionResult() {
            viewState.update { it.copy(blePermissionsGranted = blePermissions.isGranted()) }
            refreshAll()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(locksModel, viewState, ticker, registrySync.rejectedLockIds) { locks, view, now, rejected ->
        buildUiState(locks, view, now, rejected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(LocksModel.Loading, viewState.value, clock.elapsedRealtimeMillis(), setOf()),
    )

    private fun buildUiState(locks: LocksModel, view: ViewState, now: Long, rejectedLockIds: Set<LockId>): HomeUiState {
        val models = (locks as? LocksModel.Loaded)?.locks.orEmpty()
        val needsBlePermission = !view.blePermissionsGranted && models.any { it.lock.route == LockRoute.BLE }
        return HomeUiState(
            content = when (locks) {
                LocksModel.Loading -> HomeUiState.Content.Loading

                LocksModel.Unrecoverable -> HomeUiState.Content.Unrecoverable(unrecoverableListener)

                is LocksModel.Loaded -> if (models.isEmpty()) {
                    HomeUiState.Content.Empty
                } else {
                    HomeUiState.Content.Locks(models.map { createCard(it, view, now, it.lock.id in rejectedLockIds) })
                }
            },
            permissionBanner = if (needsBlePermission) {
                HomeUiState.PermissionBanner(blePermissions.required, permissionListener)
            } else {
                null
            },
            unlockConfirmation = models.firstNotNullOfOrNull { createUnlockConfirmation(it, now) },
            authenticationPrompt = models.firstNotNullOfOrNull { createAuthenticationPrompt(it) },
            deleteConfirmation = view.pendingDeleteLockId?.let { id -> models.firstOrNull { it.lock.id == id } }?.let(::createDeleteConfirmation),
            listener = listener,
        )
    }

    private fun createCard(model: LockModel, view: ViewState, now: Long, registrationRejected: Boolean): HomeUiState.LockCard {
        val lock = model.lock
        val displayState = model.snapshot.toDisplayState(now, STATE_FRESHNESS)
        val observation = model.snapshot.observation
        val phase = model.phase
        val operation: Pair<String, HomeUiState.Tone>? = when (phase) {
            is CommandPhase.InProgress -> LockTexts.inProgress(phase) to HomeUiState.Tone.NEUTRAL
            is CommandPhase.Completed -> view.messages[lock.id]?.let { it to HomeUiState.Tone.NEUTRAL } ?: LockTexts.outcome(phase.intent, phase.outcome)
            CommandPhase.Idle -> view.messages[lock.id]?.let { it to HomeUiState.Tone.NEUTRAL }
        }
        val operable = phase !is CommandPhase.InProgress && !registrationRejected
        return HomeUiState.LockCard(
            name = lock.displayName,
            description = "${lock.model.displayName} ・ ${LockTexts.route(lock.route)}",
            stateLabel = LockTexts.displayState(displayState, observation?.state),
            stateTone = LockTexts.displayTone(displayState),
            observedAtLabel = observation?.let { "最終観測 ${LockTexts.time(it.observedAtWallMillis)}（${LockTexts.route(lock.route)}）" } ?: "観測なし",
            batteryWarningVisible = observation?.batteryCritical == true,
            operationMessage = operation?.first,
            operationTone = operation?.second ?: HomeUiState.Tone.NEUTRAL,
            operationProgressVisible = phase is CommandPhase.Connecting || phase is CommandPhase.Sending || phase is CommandPhase.AwaitingStateVerification,
            dismissOperationVisible = phase is CommandPhase.Completed || (phase is CommandPhase.Idle && lock.id in view.messages),
            registrationErrorVisible = registrationRejected,
            lockButtonEnabled = operable,
            unlockButtonEnabled = operable,
            listener = createCardListener(lock),
        )
    }

    private fun createCardListener(lock: RegisteredLock): HomeUiState.LockCardListener = object : HomeUiState.LockCardListener {
        override fun onLockClick() {
            clearMessage(lock.id)
            val result = coordinator.requestLock(LockTarget(lock.id, lock.route))
            if (result is StartResult.Rejected) showMessage(lock.id, LockTexts.rejected(result.reason))
        }

        override fun onUnlockLongPress() {
            clearMessage(lock.id)
            val result = coordinator.beginUnlock(LockTarget(lock.id, lock.route))
            if (result is StartResult.Rejected) showMessage(lock.id, LockTexts.rejected(result.reason))
        }

        override fun onRefreshClick() {
            refresh(lock)
        }

        override fun onDismissOperationClick() {
            clearMessage(lock.id)
            coordinator.dismissCompleted(lock.id)
        }

        override fun onDeleteClick() {
            viewState.update { it.copy(pendingDeleteLockId = lock.id) }
        }
    }

    private fun createUnlockConfirmation(model: LockModel, now: Long): HomeUiState.UnlockConfirmation? {
        val phase = model.phase as? CommandPhase.AwaitingConfirmation ?: return null
        val displayState = model.snapshot.toDisplayState(now, STATE_FRESHNESS)
        val observedLabel = buildString {
            append(LockTexts.displayState(displayState, model.snapshot.observation?.state))
            model.snapshot.observation?.let { append("（${LockTexts.time(it.observedAtWallMillis)}）") }
        }
        return HomeUiState.UnlockConfirmation(
            lockName = model.lock.displayName,
            routeLabel = LockTexts.route(model.lock.route),
            observedStateLabel = observedLabel,
            stateUnknownWarningVisible = displayState !in knownDisplayStates,
            listener = object : HomeUiState.UnlockConfirmationListener {
                override fun onConfirmClick() {
                    coordinator.confirmUnlock(phase.requestId)
                }

                override fun onCancel() {
                    coordinator.cancel(phase.requestId)
                    coordinator.dismissCompleted(model.lock.id)
                }
            },
        )
    }

    private fun createAuthenticationPrompt(model: LockModel): HomeUiState.AuthenticationPrompt? {
        val phase = model.phase as? CommandPhase.AwaitingAuthentication ?: return null
        authenticationPromptCache?.let { (requestId, prompt) -> if (requestId == phase.requestId) return prompt }
        val prompt = HomeUiState.AuthenticationPrompt(
            title = "「${model.lock.displayName}」を解錠",
            subtitle = "解錠のために端末認証をしてください",
            listener = object : HomeUiState.AuthenticationPromptListener {
                override fun onSucceeded() {
                    coordinator.onAuthenticationSucceeded(phase.requestId)
                }

                override fun onFailed(message: String) {
                    if (coordinator.onAuthenticationFailed(phase.requestId)) {
                        showMessage(model.lock.id, "解錠は送信していません: $message")
                    }
                }
            },
        )
        authenticationPromptCache = phase.requestId to prompt
        return prompt
    }

    private fun createDeleteConfirmation(model: LockModel): HomeUiState.DeleteConfirmation = HomeUiState.DeleteConfirmation(
        lockName = model.lock.displayName,
        listener = object : HomeUiState.DeleteConfirmationListener {
            override fun onConfirmClick() {
                viewState.update { it.copy(pendingDeleteLockId = null) }
                viewModelScope.launch { repository.remove(model.lock.id) }
            }

            override fun onCancel() {
                viewState.update { it.copy(pendingDeleteLockId = null) }
            }
        },
    )

    private fun refreshAll() {
        val state = repository.state.value as? LockRepositoryState.Loaded ?: return
        state.locks.forEach(::refresh)
    }

    private fun refresh(lock: RegisteredLock) {
        viewModelScope.launch {
            val transport = transport(lock)
            when (lock.route) {
                LockRoute.BLE -> {
                    val result = withTimeoutOrNull(OBSERVATION_CONNECT_TIMEOUT) { transport.ensureConnected(lock.id) }
                    when (result) {
                        null -> showMessage(lock.id, "状態の取得: 接続できませんでした（タイムアウト）")
                        is ConnectionResult.Failed -> showMessage(lock.id, "状態の取得: ${LockTexts.error(result.error)}")
                        is ConnectionResult.Ready -> clearMessage(lock.id)
                    }
                }

                LockRoute.CLOUD -> transport.refreshState(lock.id)
            }
        }
    }

    private fun showMessage(lockId: LockId, message: String) {
        viewState.update { it.copy(messages = it.messages + (lockId to message)) }
    }

    private fun clearMessage(lockId: LockId) {
        viewState.update { it.copy(messages = it.messages - lockId) }
    }

    private fun transport(lock: RegisteredLock): LockTransport = transports.getValue(lock.route)

    private data class ViewState(
        val pendingDeleteLockId: LockId?,
        val blePermissionsGranted: Boolean,
        val messages: Map<LockId, String>,
    )

    private data class LockModel(
        val lock: RegisteredLock,
        val snapshot: LockSnapshot,
        val phase: CommandPhase,
    )

    private sealed interface LocksModel {
        data object Loading : LocksModel

        data object Unrecoverable : LocksModel

        data class Loaded(val locks: List<LockModel>) : LocksModel
    }

    private companion object {
        private val STATE_FRESHNESS = 5.seconds
        private val OBSERVATION_CONNECT_TIMEOUT = 15.seconds
        private val knownDisplayStates = setOf(
            LockDisplayState.LOCKED_VERIFIED,
            LockDisplayState.UNLOCKED_VERIFIED,
            LockDisplayState.NOT_LOCKED_OR_MOVING,
        )
    }
}

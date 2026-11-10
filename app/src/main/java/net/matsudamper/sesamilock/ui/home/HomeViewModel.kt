package net.matsudamper.sesamilock.ui.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.google.zxing.WriterException
import net.matsudamper.sesamilock.BlePermissions
import net.matsudamper.sesamilock.LockRegistrySync
import net.matsudamper.sesamilock.QrCodeEncoder
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.LockCommandCoordinator
import net.matsudamper.sesamilock.sesame.core.coordinator.LockTarget
import net.matsudamper.sesamilock.sesame.core.coordinator.StartResult
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockDisplayState
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.toDisplayState
import net.matsudamper.sesamilock.sesame.core.transfer.LockTransferCodec
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.core.transport.RefreshResult
import net.matsudamper.sesamilock.sesame.core.transport.TransportError
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState
import net.matsudamper.sesamilock.ui.edit.EditLockSelection

internal class HomeViewModel(
    private val repository: EncryptedLockRepository,
    private val transports: Map<LockRoute, LockTransport>,
    private val coordinator: LockCommandCoordinator,
    private val clock: LockClock,
    private val registrySync: LockRegistrySync,
    private val blePermissions: BlePermissions,
    private val editLockSelection: EditLockSelection,
) : ViewModel() {
    private val viewState = MutableStateFlow(
        ViewState(
            pendingDeleteLockId = null,
            exportQr = null,
            blePermissionsGranted = blePermissions.isGranted(),
            messages = mapOf(),
            invalidCredentialLockIds = setOf(),
            pullRefreshing = false,
            editScreenRequested = false,
        ),
    )
    private val refreshJobs = mutableMapOf<LockId, Job>()

    private var snackbarCache: Pair<String, HomeUiState.Snackbar>? = null
    private val cardListeners = ConcurrentHashMap<LockId, Pair<RegisteredLock, HomeUiState.LockCardListener>>()

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

        override fun onPullToRefresh() {
            if (viewState.value.pullRefreshing) return
            viewState.update { it.copy(pullRefreshing = true) }
            viewModelScope.launch {
                try {
                    refreshAll().joinAll()
                } finally {
                    viewState.update { it.copy(pullRefreshing = false) }
                }
            }
        }

        override fun onEditScreenOpened() {
            viewState.update { it.copy(editScreenRequested = false) }
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

    init {
        viewModelScope.launch {
            var refreshedLockIds = setOf<LockId>()
            registrySync.registeredLocks.collect { locks ->
                locks.filter { it.id !in refreshedLockIds }.forEach(::refresh)
                refreshedLockIds = locks.map { it.id }.toSet()
            }
        }
    }

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
                    val busyDeviceUuids = models.filter { it.phase is CommandPhase.InProgress }.map { it.lock.id.deviceUuid }.toSet()
                    HomeUiState.Content.Locks(
                        models.map {
                            createCard(
                                model = it,
                                now = now,
                                registrationRejected = it.lock.id in rejectedLockIds,
                                deviceBusy = it.lock.id.deviceUuid in busyDeviceUuids,
                                credentialInvalid = it.lock.id in view.invalidCredentialLockIds,
                            )
                        },
                    )
                }
            },
            permissionBanner = if (needsBlePermission) {
                HomeUiState.PermissionBanner(blePermissions.required, permissionListener)
            } else {
                null
            },
            deleteConfirmation = view.pendingDeleteLockId?.let { id -> models.firstOrNull { it.lock.id == id } }?.let(::createDeleteConfirmation),
            exportQr = view.exportQr?.let(::createExportQr),
            snackbar = models.firstNotNullOfOrNull { createSnackbar(it, view) },
            pullRefreshIndicatorVisible = view.pullRefreshing,
            editScreenRequested = view.editScreenRequested,
            listener = listener,
        )
    }

    private fun createCard(
        model: LockModel,
        now: Long,
        registrationRejected: Boolean,
        deviceBusy: Boolean,
        credentialInvalid: Boolean,
    ): HomeUiState.LockCard {
        val lock = model.lock
        val freshness = when (lock.route) {
            LockRoute.BLE -> BLE_STATE_FRESHNESS
            LockRoute.CLOUD -> Duration.INFINITE
        }
        val displayState = model.snapshot.toDisplayState(now, freshness)
        val observation = model.snapshot.observation
        val phase = model.phase
        val nowWall = clock.currentTimeMillis()
        val observedAgo = observation?.let { LockTexts.relativeTime(it.observedAtWallMillis, nowWall) }
        val detail = when {
            observation == null -> "${lock.model.displayName} ・ ${LockTexts.route(lock.route)}"

            displayState == LockDisplayState.STALE || displayState == LockDisplayState.DISCONNECTED ->
                "${LockTexts.previousState(observation.state)}（$observedAgo）"

            else -> "$observedAgo に確認 ・ ${LockTexts.route(lock.route)}"
        }
        val inProgress = phase as? CommandPhase.InProgress
        val operable = !deviceBusy && !registrationRejected
        val cancellableIntent = when (phase) {
            is CommandPhase.Connecting -> phase.intent

            is CommandPhase.Sending -> phase.intent

            is CommandPhase.AwaitingStateVerification,
            is CommandPhase.Completed,
            CommandPhase.Idle,
            -> null
        }
        val statusIcon = if (credentialInvalid) {
            LockTexts.disconnectedIcon(lock.route)
        } else {
            LockTexts.statusIcon(displayState, lock.route)
        }
        val statusLabel = when {
            inProgress != null -> LockTexts.inProgress(inProgress)
            credentialInvalid -> "接続情報が間違っています"
            else -> LockTexts.status(displayState, lock.route)
        }
        return HomeUiState.LockCard(
            name = lock.displayName,
            detail = detail,
            statusLabel = statusLabel,
            statusIcon = statusIcon,
            statusTone = when (statusIcon) {
                HomeUiState.StatusIcon.LOCKED -> HomeUiState.Tone.LOCKED

                HomeUiState.StatusIcon.UNLOCKED -> HomeUiState.Tone.UNLOCKED

                HomeUiState.StatusIcon.MOVING,
                HomeUiState.StatusIcon.UNKNOWN,
                HomeUiState.StatusIcon.BLUETOOTH_DISCONNECTED,
                HomeUiState.StatusIcon.CLOUD_DISCONNECTED,
                -> HomeUiState.Tone.NEUTRAL
            },
            progressVisible = inProgress != null,
            warningMessage = when {
                registrationRejected -> "この鍵を読み込めませんでした。登録し直してください"
                observation?.batteryCritical == true -> "電池残量が少なくなっています"
                else -> null
            },
            lockButton = actionButton(LockIntent.LOCK, cancellableIntent, operable),
            unlockButton = actionButton(LockIntent.UNLOCK, cancellableIntent, operable),
            listener = cardListener(lock),
        )
    }

    private fun actionButton(intent: LockIntent, cancellableIntent: LockIntent?, operable: Boolean): HomeUiState.ActionButton = if (intent == cancellableIntent) {
        HomeUiState.ActionButton.Cancel
    } else {
        HomeUiState.ActionButton.HoldToRun(enabled = operable)
    }

    private fun cardListener(lock: RegisteredLock): HomeUiState.LockCardListener {
        val cached = cardListeners[lock.id]
        if (cached != null && cached.first === lock) return cached.second
        return createCardListener(lock).also { cardListeners[lock.id] = lock to it }
    }

    private fun createCardListener(lock: RegisteredLock): HomeUiState.LockCardListener = object : HomeUiState.LockCardListener {
        override fun onLockLongPress() {
            clearMessage(lock.id)
            val result = coordinator.requestLock(LockTarget(lock.id, lock.route))
            if (result is StartResult.Rejected) showMessage(lock.id, LockTexts.rejected(result.reason))
        }

        override fun onLockTap() {
            showMessage(lock.id, "施錠するには長押ししてください")
        }

        override fun onUnlockLongPress() {
            clearMessage(lock.id)
            val result = coordinator.requestUnlock(LockTarget(lock.id, lock.route))
            if (result is StartResult.Rejected) showMessage(lock.id, LockTexts.rejected(result.reason))
        }

        override fun onUnlockTap() {
            showMessage(lock.id, "解錠するには長押ししてください")
        }

        override fun onCancelClick() {
            val inProgress = coordinator.phase(lock.id).value as? CommandPhase.InProgress ?: return
            if (coordinator.cancel(inProgress.requestId)) {
                coordinator.dismissCompleted(lock.id)
            } else {
                showMessage(lock.id, "送信済みのため取り消せませんでした")
            }
        }

        override fun onExportClick() {
            val image = try {
                QrCodeEncoder.encode(LockTransferCodec.encode(lock)).asImageBitmap()
            } catch (_: WriterException) {
                showMessage(lock.id, "鍵の情報が大きすぎて QR コードにできません。表示名を短くしてください")
                return
            }
            viewState.update { it.copy(exportQr = ExportQrSource(lock.displayName, image)) }
        }

        override fun onEditClick() {
            editLockSelection.lockId = lock.id
            viewState.update { it.copy(editScreenRequested = true) }
        }

        override fun onDeleteClick() {
            viewState.update { it.copy(pendingDeleteLockId = lock.id) }
        }
    }

    private fun createSnackbar(model: LockModel, view: ViewState): HomeUiState.Snackbar? {
        val lockId = model.lock.id
        val phase = model.phase
        val transientMessage = view.messages[lockId]
        val (key, message, requiresAcknowledgement) = when {
            phase is CommandPhase.Completed -> {
                val outcome = LockTexts.outcome(phase.intent, phase.outcome)
                Triple(
                    "${lockId.value}:${phase.requestId.value}",
                    "${model.lock.displayName}: ${transientMessage ?: outcome.message}",
                    outcome.requiresAcknowledgement,
                )
            }

            transientMessage != null -> Triple("${lockId.value}:$transientMessage", "${model.lock.displayName}: $transientMessage", false)

            else -> return null
        }
        val cache = snackbarCache
        if (cache != null && cache.first == key) return cache.second
        val snackbar = HomeUiState.Snackbar(
            message = message,
            requiresAcknowledgement = requiresAcknowledgement,
            listener = object : HomeUiState.SnackbarListener {
                override fun onDismissed() {
                    clearMessage(lockId)
                    coordinator.dismissCompleted(lockId)
                }
            },
        )
        snackbarCache = key to snackbar
        return snackbar
    }

    private fun createExportQr(source: ExportQrSource): HomeUiState.ExportQr = HomeUiState.ExportQr(
        lockName = source.lockName,
        image = source.image,
        listener = object : HomeUiState.ExportQrListener {
            override fun onDismiss() {
                viewState.update { it.copy(exportQr = null) }
            }
        },
    )

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

    private fun refreshAll(): List<Job> = registrySync.registeredLocks.value.map(::refresh)

    private fun refresh(lock: RegisteredLock): Job {
        val running = refreshJobs[lock.id]
        if (running != null && running.isActive) return running
        return viewModelScope.launch {
            val transport = transport(lock)
            when (lock.route) {
                LockRoute.BLE -> {
                    val result = withTimeoutOrNull(OBSERVATION_CONNECT_TIMEOUT) { transport.ensureConnected(lock.id) }
                    when (result) {
                        null -> Unit
                        is ConnectionResult.Failed -> onRefreshFailed(lock.id, result.error)
                        is ConnectionResult.Ready -> onRefreshed(lock.id)
                    }
                }

                LockRoute.CLOUD -> when (val result = transport.refreshState(lock.id)) {
                    RefreshResult.Refreshed -> onRefreshed(lock.id)
                    is RefreshResult.Failed -> onRefreshFailed(lock.id, result.error)
                }
            }
        }.also { refreshJobs[lock.id] = it }
    }

    private fun onRefreshed(lockId: LockId) {
        clearMessage(lockId)
        viewState.update { it.copy(invalidCredentialLockIds = it.invalidCredentialLockIds - lockId) }
    }

    private fun onRefreshFailed(lockId: LockId, error: TransportError) {
        when (error) {
            TransportError.AUTHENTICATION_FAILED,
            TransportError.SERVER_AUTHORIZATION_REQUIRED,
            TransportError.HTTP_CLIENT_ERROR,
            -> viewState.update { it.copy(invalidCredentialLockIds = it.invalidCredentialLockIds + lockId) }

            TransportError.NETWORK,
            TransportError.TIMEOUT,
            -> showMessage(lockId, LockTexts.error(error))

            TransportError.PERMISSION_DENIED,
            TransportError.BLUETOOTH_OFF,
            TransportError.DEVICE_NOT_REGISTERED,
            TransportError.UNSUPPORTED_DEVICE,
            TransportError.NOT_CONNECTED,
            TransportError.HTTP_SERVER_ERROR,
            TransportError.INVALID_RESPONSE,
            TransportError.DEVICE_ERROR,
            TransportError.UNKNOWN,
            -> Unit
        }
    }

    private fun showMessage(lockId: LockId, message: String) {
        viewState.update { it.copy(messages = it.messages + (lockId to message)) }
    }

    private fun clearMessage(lockId: LockId) {
        viewState.update { it.copy(messages = it.messages - lockId) }
    }

    private fun transport(lock: RegisteredLock): LockTransport = transports.getValue(lock.route)

    private data class ExportQrSource(val lockName: String, val image: ImageBitmap)

    private data class ViewState(
        val pendingDeleteLockId: LockId?,
        val exportQr: ExportQrSource?,
        val blePermissionsGranted: Boolean,
        val messages: Map<LockId, String>,
        val invalidCredentialLockIds: Set<LockId>,
        val pullRefreshing: Boolean,
        val editScreenRequested: Boolean,
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
        private val BLE_STATE_FRESHNESS = 5.seconds
        private val OBSERVATION_CONNECT_TIMEOUT = 15.seconds
    }
}

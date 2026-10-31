package net.matsudamper.sesamilock.sesame.core.coordinator

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.sesamilock.sesame.core.diagnostics.DiagnosticPoint
import net.matsudamper.sesamilock.sesame.core.diagnostics.DiagnosticsRecorder
import net.matsudamper.sesamilock.sesame.core.model.ConnectionState
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

public class LockCommandCoordinator(
    transports: List<LockTransport>,
    private val clock: LockClock,
    private val config: LockSafetyConfig,
    private val foregroundState: ForegroundState,
    private val diagnostics: DiagnosticsRecorder,
    private val requestIdFactory: RequestIdFactory,
    private val scope: CoroutineScope,
) {
    private val transportsByRoute = transports.associateBy { it.route }
    private val guard = Any()
    private val activeRequests = mutableMapOf<LockId, ActiveRequest>()
    private val phases = mutableMapOf<LockId, MutableStateFlow<CommandPhase>>()

    public fun phase(lockId: LockId): StateFlow<CommandPhase> = synchronized(guard) { phaseFlow(lockId).asStateFlow() }

    public fun requestLock(target: LockTarget): StartResult {
        val request = when (val result = startRequest(target, LockIntent.LOCK)) {
            is RequestStart.Rejected -> return StartResult.Rejected(result.reason)
            is RequestStart.Started -> result.request
        }
        request.job = scope.launch { execute(request) }
        return StartResult.Started(request.requestId)
    }

    public fun beginUnlock(target: LockTarget): StartResult {
        val request = when (val result = startRequest(target, LockIntent.UNLOCK)) {
            is RequestStart.Rejected -> return StartResult.Rejected(result.reason)
            is RequestStart.Started -> result.request
        }
        scope.launch {
            delay(config.unlockConfirmationTimeout)
            val stillWaitingForUser = synchronized(guard) {
                request.stage == RequestStage.AWAITING_CONFIRMATION || request.stage == RequestStage.AWAITING_AUTHENTICATION
            }
            if (stillWaitingForUser) {
                abortIfNotSent(request, AbortReason.CONFIRMATION_EXPIRED, null)
            }
        }
        return StartResult.Started(request.requestId)
    }

    public fun confirmUnlock(requestId: RequestId): Boolean {
        val confirmed = synchronized(guard) {
            val request = findActive(requestId) ?: return@synchronized false
            if (request.stage != RequestStage.AWAITING_CONFIRMATION) return@synchronized false
            request.stage = RequestStage.AWAITING_AUTHENTICATION
            request.confirmedAtElapsedMillis = clock.elapsedRealtimeMillis()
            phaseFlow(request.target.lockId).value = CommandPhase.AwaitingAuthentication(requestId)
            true
        }
        if (confirmed) diagnostics.mark(requestId, DiagnosticPoint.CONFIRMED)
        return confirmed
    }

    public fun onAuthenticationSucceeded(requestId: RequestId): Boolean {
        val request = synchronized(guard) {
            val request = findActive(requestId) ?: return@synchronized null
            if (request.stage != RequestStage.AWAITING_AUTHENTICATION) return@synchronized null
            val now = clock.elapsedRealtimeMillis()
            if (now - request.createdAtElapsedMillis > config.unlockConfirmationTimeout.inWholeMilliseconds) return@synchronized null
            request.stage = RequestStage.EXECUTING
            request.authenticatedAtElapsedMillis = now
            request
        } ?: return false
        diagnostics.mark(requestId, DiagnosticPoint.AUTH_SUCCEEDED)
        request.job = scope.launch { execute(request) }
        return true
    }

    public fun onAuthenticationFailed(requestId: RequestId): Boolean {
        val request = synchronized(guard) { findActive(requestId) } ?: return false
        return abortIfNotSent(request, AbortReason.AUTHENTICATION_FAILED, null)
    }

    /**
     * 送信前の要求だけを破棄する。送信済みの命令は機器側で取り消せないので false を返す。
     */
    public fun cancel(requestId: RequestId): Boolean {
        val request = synchronized(guard) { findActive(requestId) } ?: return false
        return abortIfNotSent(request, AbortReason.CANCELLED, null)
    }

    public fun dismissCompleted(lockId: LockId) {
        synchronized(guard) {
            val flow = phaseFlow(lockId)
            if (flow.value is CommandPhase.Completed) {
                flow.value = CommandPhase.Idle
            }
        }
    }

    private fun startRequest(target: LockTarget, intent: LockIntent): RequestStart {
        val request = synchronized(guard) {
            if (target.route !in transportsByRoute) return RequestStart.Rejected(RejectReason.ROUTE_UNAVAILABLE)
            if (target.lockId in activeRequests) return RequestStart.Rejected(RejectReason.OPERATION_IN_PROGRESS)
            val request = ActiveRequest(
                requestId = requestIdFactory.create(),
                target = target,
                intent = intent,
                createdAtElapsedMillis = clock.elapsedRealtimeMillis(),
                stage = when (intent) {
                    LockIntent.LOCK -> RequestStage.EXECUTING
                    LockIntent.UNLOCK -> RequestStage.AWAITING_CONFIRMATION
                },
            )
            activeRequests[target.lockId] = request
            phaseFlow(target.lockId).value = when (intent) {
                LockIntent.LOCK -> CommandPhase.Connecting(request.requestId, intent)
                LockIntent.UNLOCK -> CommandPhase.AwaitingConfirmation(request.requestId)
            }
            request
        }
        diagnostics.start(request.requestId, target.lockId, target.route, intent)
        return RequestStart.Started(request)
    }

    private suspend fun execute(request: ActiveRequest) {
        val transport = transportsByRoute.getValue(request.target.route)
        val lockId = request.target.lockId
        updatePhaseIfActive(request, CommandPhase.Connecting(request.requestId, request.intent))
        diagnostics.mark(request.requestId, DiagnosticPoint.CONNECT_STARTED)

        val remainingMillis = request.sendDeadlineElapsedMillis() - clock.elapsedRealtimeMillis()
        val connection = if (remainingMillis <= 0) {
            null
        } else {
            withTimeoutOrNull(remainingMillis.milliseconds) { transport.ensureConnected(lockId) }
        }
        when (connection) {
            null -> {
                abortIfNotSent(request, AbortReason.SEND_DEADLINE_EXPIRED, null)
                return
            }

            is ConnectionResult.Failed -> {
                abortIfNotSent(request, AbortReason.CONNECTION_FAILED, connection.error)
                return
            }

            is ConnectionResult.Ready -> Unit
        }
        diagnostics.mark(request.requestId, DiagnosticPoint.CONNECTION_AUTHENTICATED)

        val violation = synchronized(guard) {
            findGuardViolation(request, connection, transport).also { violation ->
                if (violation == null) {
                    request.sent = true
                    request.stage = RequestStage.SENT
                    phaseFlow(lockId).value = CommandPhase.Sending(request.requestId, request.intent)
                }
            }
        }
        if (violation != null) {
            abortIfNotSent(request, violation, null)
            return
        }

        withContext(NonCancellable) {
            val sentAtElapsedMillis = clock.elapsedRealtimeMillis()
            diagnostics.mark(request.requestId, DiagnosticPoint.REQUEST_SENT)
            val dispatchResult = transport.dispatchExplicit(lockId, request.intent, request.requestId)
            diagnostics.mark(request.requestId, DiagnosticPoint.ACK)
            diagnostics.recordDispatch(request.requestId, dispatchResult.toString())

            if (dispatchResult is DispatchResult.NotSent) {
                finish(request, CommandOutcome.NotSent(AbortReason.TRANSPORT_NOT_AUTHENTICATED, dispatchResult.error))
                return@withContext
            }
            updatePhaseIfActive(request, CommandPhase.AwaitingStateVerification(request.requestId, request.intent, dispatchResult))
            val verified = awaitVerification(transport, request, sentAtElapsedMillis, connection.connectionGeneration)
            if (verified) {
                diagnostics.mark(request.requestId, DiagnosticPoint.VERIFIED)
                finish(request, CommandOutcome.Verified)
            } else {
                finish(request, CommandOutcome.SentButNotVerified(dispatchResult))
            }
        }
    }

    private fun findGuardViolation(request: ActiveRequest, connection: ConnectionResult.Ready, transport: LockTransport): AbortReason? {
        val lockId = request.target.lockId
        if (activeRequests[lockId] !== request || request.sent) return AbortReason.CANCELLED
        if (clock.elapsedRealtimeMillis() > request.sendDeadlineElapsedMillis()) return AbortReason.SEND_DEADLINE_EXPIRED
        if (connection.lockId != lockId) return AbortReason.DEVICE_MISMATCH
        if (!transport.isAuthenticated(lockId)) return AbortReason.TRANSPORT_NOT_AUTHENTICATED
        when (request.intent) {
            LockIntent.LOCK -> Unit

            LockIntent.UNLOCK -> {
                if (request.confirmedAtElapsedMillis == null) return AbortReason.NOT_CONFIRMED
                if (request.authenticatedAtElapsedMillis == null) return AbortReason.NOT_AUTHENTICATED
                if (!foregroundState.isInForeground()) return AbortReason.APP_NOT_IN_FOREGROUND
            }
        }
        return null
    }

    /**
     * 送信後に、目標の機械状態が [LockSafetyConfig.verificationSettleDuration] の間保たれることを確認する。
     * 通知型の経路では送信前からの同じ接続の観測も使うが、ポーリング型の経路では送信後に取得した観測だけを使う。
     */
    private suspend fun awaitVerification(
        transport: LockTransport,
        request: ActiveRequest,
        sentAtElapsedMillis: Long,
        sentConnectionGeneration: Long,
    ): Boolean {
        val lockId = request.target.lockId
        val expectedState = when (request.intent) {
            LockIntent.LOCK -> MechanicalState.LOCKED
            LockIntent.UNLOCK -> MechanicalState.UNLOCKED_VERIFIED
        }
        val snapshots = transport.snapshot(lockId)
        val matches = { snapshot: LockSnapshot ->
            isExpectedState(snapshot, expectedState, sentAtElapsedMillis, sentConnectionGeneration)
        }
        return withTimeoutOrNull(config.verificationTimeout) {
            coroutineScope {
                val observer = launch {
                    snapshots.first { snapshot ->
                        val observedAt = snapshot.observation?.observedAtElapsedMillis
                        observedAt != null && observedAt > sentAtElapsedMillis
                    }
                    diagnostics.mark(request.requestId, DiagnosticPoint.MECHANICAL_STATUS)
                }
                val refresher = launch {
                    while (isActive) {
                        delay(config.stateRefreshInterval)
                        transport.refreshState(lockId)
                    }
                }
                try {
                    awaitSettled(snapshots, matches, config.verificationSettleDuration)
                } finally {
                    observer.cancel()
                    refresher.cancel()
                }
            }
        } ?: false
    }

    private suspend fun awaitSettled(snapshots: StateFlow<LockSnapshot>, matches: (LockSnapshot) -> Boolean, settle: Duration): Boolean {
        while (true) {
            snapshots.first(matches)
            val leftExpectedStateDuringSettle = withTimeoutOrNull(settle) {
                snapshots.first { !matches(it) }
                true
            } ?: false
            if (!leftExpectedStateDuringSettle) return true
        }
    }

    private fun isExpectedState(
        snapshot: LockSnapshot,
        expectedState: MechanicalState,
        sentAtElapsedMillis: Long,
        sentConnectionGeneration: Long,
    ): Boolean {
        val observation = snapshot.observation ?: return false
        if (snapshot.connection != ConnectionState.AUTHENTICATED) return false
        if (observation.state != expectedState) return false
        if (observation.connectionGeneration < sentConnectionGeneration) return false
        val observedAfterSend = observation.observedAtElapsedMillis > sentAtElapsedMillis
        val liveOnCurrentConnection = snapshot.pushesUpdatesWhileConnected && observation.connectionGeneration == snapshot.connectionGeneration
        return observedAfterSend || liveOnCurrentConnection
    }

    private fun abortIfNotSent(request: ActiveRequest, reason: AbortReason, error: TransportError?): Boolean {
        synchronized(guard) {
            if (request.sent || activeRequests[request.target.lockId] !== request) return false
            activeRequests.remove(request.target.lockId)
            request.stage = RequestStage.FINISHED
            phaseFlow(request.target.lockId).value = CommandPhase.Completed(request.requestId, request.intent, CommandOutcome.NotSent(reason, error))
        }
        request.job?.cancel()
        diagnostics.finish(request.requestId, "NOT_SENT:${reason.name}", error?.name)
        return true
    }

    private fun finish(request: ActiveRequest, outcome: CommandOutcome) {
        synchronized(guard) {
            if (activeRequests[request.target.lockId] === request) {
                activeRequests.remove(request.target.lockId)
            }
            request.stage = RequestStage.FINISHED
            phaseFlow(request.target.lockId).value = CommandPhase.Completed(request.requestId, request.intent, outcome)
        }
        val error = when (outcome) {
            is CommandOutcome.NotSent -> outcome.error?.name
            is CommandOutcome.SentButNotVerified -> outcome.dispatchResult.toString()
            CommandOutcome.Verified -> null
        }
        diagnostics.finish(request.requestId, outcome.diagnosticLabel(), error)
    }

    private fun updatePhaseIfActive(request: ActiveRequest, phase: CommandPhase) {
        synchronized(guard) {
            if (activeRequests[request.target.lockId] === request) {
                phaseFlow(request.target.lockId).value = phase
            }
        }
    }

    private fun findActive(requestId: RequestId): ActiveRequest? = activeRequests.values.firstOrNull { it.requestId == requestId }

    private fun phaseFlow(lockId: LockId): MutableStateFlow<CommandPhase> = phases.getOrPut(lockId) { MutableStateFlow(CommandPhase.Idle) }

    private fun ActiveRequest.sendDeadlineElapsedMillis(): Long = when (intent) {
        LockIntent.LOCK -> createdAtElapsedMillis + config.lockSendDeadlineAfterTap.inWholeMilliseconds

        LockIntent.UNLOCK -> {
            val authenticatedAt = authenticatedAtElapsedMillis
            if (authenticatedAt == null) Long.MIN_VALUE else authenticatedAt + config.unlockSendDeadlineAfterAuthentication.inWholeMilliseconds
        }
    }

    private fun CommandOutcome.diagnosticLabel(): String = when (this) {
        CommandOutcome.Verified -> "VERIFIED"
        is CommandOutcome.SentButNotVerified -> "SENT_NOT_VERIFIED"
        is CommandOutcome.NotSent -> "NOT_SENT:${reason.name}"
    }

    private sealed interface RequestStart {
        data class Started(val request: ActiveRequest) : RequestStart

        data class Rejected(val reason: RejectReason) : RequestStart
    }

    private enum class RequestStage {
        AWAITING_CONFIRMATION,
        AWAITING_AUTHENTICATION,
        EXECUTING,
        SENT,
        FINISHED,
    }

    private class ActiveRequest(
        val requestId: RequestId,
        val target: LockTarget,
        val intent: LockIntent,
        val createdAtElapsedMillis: Long,
        var stage: RequestStage,
    ) {
        var confirmedAtElapsedMillis: Long? = null
        var authenticatedAtElapsedMillis: Long? = null
        var sent: Boolean = false
        var job: Job? = null
    }
}

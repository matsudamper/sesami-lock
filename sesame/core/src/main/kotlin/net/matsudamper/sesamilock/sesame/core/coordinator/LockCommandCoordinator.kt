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
import net.matsudamper.sesamilock.sesame.core.transport.SendGate
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

    public fun requestLock(target: LockTarget): StartResult = request(target, LockIntent.LOCK)

    public fun requestUnlock(target: LockTarget): StartResult = request(target, LockIntent.UNLOCK)

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

    private fun request(target: LockTarget, intent: LockIntent): StartResult {
        val request = synchronized(guard) {
            if (target.route !in transportsByRoute) return StartResult.Rejected(RejectReason.ROUTE_UNAVAILABLE)
            if (activeRequests.keys.any { it.deviceUuid == target.lockId.deviceUuid }) return StartResult.Rejected(RejectReason.OPERATION_IN_PROGRESS)
            val request = ActiveRequest(
                requestId = requestIdFactory.create(),
                target = target,
                intent = intent,
                createdAtElapsedMillis = clock.elapsedRealtimeMillis(),
            )
            activeRequests[target.lockId] = request
            phaseFlow(target.lockId).value = CommandPhase.Connecting(request.requestId, intent)
            request
        }
        diagnostics.start(request.requestId, target.lockId, target.route, intent)
        request.job = scope.launch { execute(request) }
        return StartResult.Started(request.requestId)
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

        val violation = synchronized(guard) { findGuardViolation(request, connection, transport) }
        if (violation != null) {
            abortIfNotSent(request, violation, null)
            return
        }
        updatePhaseIfActive(request, CommandPhase.Sending(request.requestId, request.intent))

        withContext(NonCancellable) {
            var gateRefusal: AbortReason? = null
            val sendGate = SendGate {
                synchronized(guard) {
                    val lastMomentViolation = findGuardViolation(request, connection, transport)
                    if (lastMomentViolation == null) {
                        request.sent = true
                    } else {
                        gateRefusal = lastMomentViolation
                    }
                    lastMomentViolation == null
                }
            }
            val sentAtElapsedMillis = clock.elapsedRealtimeMillis()
            diagnostics.mark(request.requestId, DiagnosticPoint.REQUEST_SENT)
            val dispatchResult = transport.dispatchExplicit(lockId, request.intent, request.requestId, sendGate)
            diagnostics.mark(request.requestId, DiagnosticPoint.ACK)
            diagnostics.recordDispatch(request.requestId, dispatchResult.toString())

            val committed = synchronized(guard) { request.sent }
            if (!committed) {
                abortIfNotSent(request, gateRefusal ?: AbortReason.TRANSPORT_NOT_AUTHENTICATED, (dispatchResult as? DispatchResult.NotSent)?.error)
                return@withContext
            }
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
                if (!foregroundState.isInForeground()) return AbortReason.APP_NOT_IN_FOREGROUND
            }
        }
        return null
    }

    /**
     * 送信後に、目標の機械状態が [LockSafetyConfig.verificationSettleDuration] の間保たれることを確認する。
     * 通知型の経路では送信前からの同じ接続の観測も使うが、ポーリング型の経路では送信後に取得し、かつ接続時より新しい世代（状態の版）の観測だけを使う。
     */
    private suspend fun awaitVerification(
        transport: LockTransport,
        request: ActiveRequest,
        sentAtElapsedMillis: Long,
        sentConnectionGeneration: Long,
    ): Boolean {
        val lockId = request.target.lockId
        val expectedStates = when (request.intent) {
            LockIntent.LOCK -> setOf(MechanicalState.LOCKED)
            LockIntent.UNLOCK -> setOf(MechanicalState.UNLOCKED_VERIFIED, MechanicalState.OUT_OF_LOCK_RANGE)
        }
        val snapshots = transport.snapshot(lockId)
        val matches = { snapshot: LockSnapshot ->
            isExpectedState(snapshot, expectedStates, sentAtElapsedMillis, sentConnectionGeneration)
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
        expectedStates: Set<MechanicalState>,
        sentAtElapsedMillis: Long,
        sentConnectionGeneration: Long,
    ): Boolean {
        val observation = snapshot.observation ?: return false
        if (snapshot.connection != ConnectionState.AUTHENTICATED) return false
        if (observation.state !in expectedStates) return false
        if (observation.connectionGeneration < sentConnectionGeneration) return false
        val observedAfterSend = observation.observedAtElapsedMillis > sentAtElapsedMillis
        return if (snapshot.pushesUpdatesWhileConnected) {
            observedAfterSend || observation.connectionGeneration == snapshot.connectionGeneration
        } else {
            observedAfterSend && observation.connectionGeneration > sentConnectionGeneration
        }
    }

    private fun abortIfNotSent(request: ActiveRequest, reason: AbortReason, error: TransportError?): Boolean {
        synchronized(guard) {
            if (request.sent || activeRequests[request.target.lockId] !== request) return false
            activeRequests.remove(request.target.lockId)
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
        LockIntent.UNLOCK -> createdAtElapsedMillis + config.unlockSendDeadlineAfterTap.inWholeMilliseconds
    }

    private fun CommandOutcome.diagnosticLabel(): String = when (this) {
        CommandOutcome.Verified -> "VERIFIED"
        is CommandOutcome.SentButNotVerified -> "SENT_NOT_VERIFIED"
        is CommandOutcome.NotSent -> "NOT_SENT:${reason.name}"
    }

    private class ActiveRequest(
        val requestId: RequestId,
        val target: LockTarget,
        val intent: LockIntent,
        val createdAtElapsedMillis: Long,
    ) {
        var sent: Boolean = false
        var job: Job? = null
    }
}

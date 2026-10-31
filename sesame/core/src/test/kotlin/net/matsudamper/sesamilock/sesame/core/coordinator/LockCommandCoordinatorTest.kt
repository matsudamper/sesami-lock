package net.matsudamper.sesamilock.sesame.core.coordinator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.matsudamper.sesamilock.sesame.core.diagnostics.DiagnosticsRecorder
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

@OptIn(ExperimentalCoroutinesApi::class)
class LockCommandCoordinatorTest {
    private val lockId = requireNotNull(LockId.fromDeviceUuid("8c9a7bb0-1b3c-4f43-9a6e-6c3c2f2b5e01"))
    private val bleTarget = LockTarget(lockId, LockRoute.BLE)

    private class Fixture(
        val coordinator: LockCommandCoordinator,
        val ble: FakeLockTransport,
        val cloud: FakeLockTransport,
        val foreground: MutableForeground,
    )

    private class MutableForeground : ForegroundState {
        var value = true

        override fun isInForeground(): Boolean = value
    }

    private fun TestScope.fixture(): Fixture {
        val clock = object : LockClock {
            override fun elapsedRealtimeMillis(): Long = testScheduler.currentTime

            override fun currentTimeMillis(): Long = testScheduler.currentTime
        }
        val ble = FakeLockTransport(LockRoute.BLE, lockId, clock, pushesUpdatesWhileConnected = true)
        val cloud = FakeLockTransport(LockRoute.CLOUD, lockId, clock, pushesUpdatesWhileConnected = false)
        val foreground = MutableForeground()
        var counter = 0
        val coordinator = LockCommandCoordinator(
            transports = listOf(ble, cloud),
            clock = clock,
            config = LockSafetyConfig.Default,
            foregroundState = foreground,
            diagnostics = DiagnosticsRecorder(clock, capacity = 50),
            requestIdFactory = { RequestId("request-${counter++}") },
            scope = backgroundScope,
        )
        return Fixture(coordinator, ble, cloud, foreground)
    }

    private fun Fixture.startedUnlock(): RequestId {
        val result = coordinator.beginUnlock(bleTarget)
        return assertIs<StartResult.Started>(result).requestId
    }

    private fun Fixture.completedOutcome(): CommandOutcome = assertIs<CommandPhase.Completed>(coordinator.phase(lockId).value).outcome

    @Test
    fun lockFromLockedStateSendsOnlyLock() = runTest {
        val fixture = fixture()
        fixture.ble.connectNow()
        fixture.ble.observe(MechanicalState.LOCKED)

        fixture.coordinator.requestLock(bleTarget)
        advanceTimeBy(20.seconds)

        assertEquals(listOf(LockIntent.LOCK), fixture.ble.dispatched)
    }

    @Test
    fun lockFromNotLockedStateSendsOnlyLockAndVerifiesAfterNewObservation() = runTest {
        val fixture = fixture()
        fixture.ble.connectNow()
        fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING)
        fixture.ble.onDispatch = { fixture.ble.observe(MechanicalState.LOCKED) }

        fixture.coordinator.requestLock(bleTarget)
        advanceTimeBy(5.seconds)

        assertEquals(listOf(LockIntent.LOCK), fixture.ble.dispatched)
        assertEquals(CommandOutcome.Verified, fixture.completedOutcome())
    }

    @Test
    fun lockWithUnknownStateSendsOnlyLock() = runTest {
        val fixture = fixture()

        fixture.coordinator.requestLock(bleTarget)
        advanceTimeBy(20.seconds)

        assertEquals(listOf(LockIntent.LOCK), fixture.ble.dispatched)
        assertIs<CommandOutcome.SentButNotVerified>(fixture.completedOutcome())
    }

    @Test
    fun cancelledUnlockConfirmationSendsNothing() = runTest {
        val fixture = fixture()
        val requestId = fixture.startedUnlock()

        assertTrue(fixture.coordinator.cancel(requestId))
        advanceTimeBy(70.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
        assertEquals(CommandOutcome.NotSent(AbortReason.CANCELLED, null), fixture.completedOutcome())
    }

    @Test
    fun failedAuthenticationSendsNothing() = runTest {
        val fixture = fixture()
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)

        fixture.coordinator.onAuthenticationFailed(requestId)
        assertFalse(fixture.coordinator.onAuthenticationSucceeded(requestId))
        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
    }

    @Test
    fun authenticationWithoutConfirmationIsIgnored() = runTest {
        val fixture = fixture()
        val requestId = fixture.startedUnlock()

        assertFalse(fixture.coordinator.onAuthenticationSucceeded(requestId))
        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
    }

    @Test
    fun unlockIsNotSentWhenConnectionIsReadyAfterDeadline() = runTest {
        val fixture = fixture()
        val gate = CompletableDeferred<ConnectionResult>()
        fixture.ble.connectionGate = gate
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(11.seconds)
        gate.complete(fixture.ble.connectNow())
        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
        assertEquals(CommandOutcome.NotSent(AbortReason.SEND_DEADLINE_EXPIRED, null), fixture.completedOutcome())
    }

    @Test
    fun repeatedUnlockRequestsSendAtMostOnce() = runTest {
        val fixture = fixture()
        val requestId = fixture.startedUnlock()
        repeat(9) {
            assertEquals(StartResult.Rejected(RejectReason.OPERATION_IN_PROGRESS), fixture.coordinator.beginUnlock(bleTarget))
        }

        fixture.coordinator.confirmUnlock(requestId)
        assertTrue(fixture.coordinator.onAuthenticationSucceeded(requestId))
        assertFalse(fixture.coordinator.onAuthenticationSucceeded(requestId))
        assertFalse(fixture.coordinator.confirmUnlock(requestId))
        advanceTimeBy(30.seconds)

        assertEquals(listOf(LockIntent.UNLOCK), fixture.ble.dispatched)
    }

    @Test
    fun lockIsRejectedWhileUnlockIsPending() = runTest {
        val fixture = fixture()
        fixture.startedUnlock()

        assertEquals(StartResult.Rejected(RejectReason.OPERATION_IN_PROGRESS), fixture.coordinator.requestLock(bleTarget))
        advanceTimeBy(5.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
    }

    @Test
    fun lostAcknowledgementIsNotRetried() = runTest {
        val fixture = fixture()
        fixture.ble.dispatchResult = DispatchResult.OutcomeUnknown(TransportError.TIMEOUT)
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(60.seconds)

        assertEquals(listOf(LockIntent.UNLOCK), fixture.ble.dispatched)
        assertEquals(
            CommandOutcome.SentButNotVerified(DispatchResult.OutcomeUnknown(TransportError.TIMEOUT)),
            fixture.completedOutcome(),
        )
    }

    @Test
    fun lockOutcomeIsObservedEvenWhenAcknowledgementIsLost() = runTest {
        val fixture = fixture()
        fixture.ble.connectNow()
        fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING)
        fixture.ble.dispatchResult = DispatchResult.OutcomeUnknown(TransportError.TIMEOUT)
        fixture.ble.onDispatch = { fixture.ble.observe(MechanicalState.LOCKED) }

        fixture.coordinator.requestLock(bleTarget)
        advanceTimeBy(20.seconds)

        assertEquals(listOf(LockIntent.LOCK), fixture.ble.dispatched)
        assertEquals(CommandOutcome.Verified, fixture.completedOutcome())
    }

    @Test
    fun cancelAfterSendIsRefused() = runTest {
        val fixture = fixture()
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)
        runCurrent()

        assertFalse(fixture.coordinator.cancel(requestId))
        assertEquals(listOf(LockIntent.UNLOCK), fixture.ble.dispatched)
    }

    @Test
    fun notLockedOrMovingIsNotTreatedAsVerifiedUnlock() = runTest {
        val fixture = fixture()
        fixture.ble.onDispatch = { fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING) }
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(30.seconds)

        assertIs<CommandOutcome.SentButNotVerified>(fixture.completedOutcome())
    }

    @Test
    fun verifiedUnlockRequiresStrictObservation() = runTest {
        val fixture = fixture()
        fixture.ble.onDispatch = { fixture.ble.observe(MechanicalState.UNLOCKED_VERIFIED) }
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(5.seconds)

        assertEquals(CommandOutcome.Verified, fixture.completedOutcome())
    }

    @Test
    fun bleFailureDoesNotFallBackToCloud() = runTest {
        val fixture = fixture()
        fixture.ble.connectionGate = CompletableDeferred(ConnectionResult.Failed(TransportError.BLUETOOTH_OFF))
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
        assertEquals(listOf(), fixture.cloud.dispatched)
        assertEquals(CommandOutcome.NotSent(AbortReason.CONNECTION_FAILED, TransportError.BLUETOOTH_OFF), fixture.completedOutcome())
    }

    @Test
    fun permissionDeniedSendsNothing() = runTest {
        val fixture = fixture()
        fixture.ble.connectionGate = CompletableDeferred(ConnectionResult.Failed(TransportError.PERMISSION_DENIED))

        fixture.coordinator.requestLock(bleTarget)
        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
    }

    @Test
    fun unlockIsNotSentInBackground() = runTest {
        val fixture = fixture()
        fixture.foreground.value = false
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
        assertEquals(CommandOutcome.NotSent(AbortReason.APP_NOT_IN_FOREGROUND, null), fixture.completedOutcome())
    }

    @Test
    fun unlockIsNotSentWhenTransportLostAuthentication() = runTest {
        val fixture = fixture()
        fixture.ble.authenticated = false
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
    }

    @Test
    fun unconfirmedUnlockExpires() = runTest {
        val fixture = fixture()
        val requestId = fixture.startedUnlock()

        advanceTimeBy(61.seconds)

        assertFalse(fixture.coordinator.confirmUnlock(requestId))
        assertEquals(CommandOutcome.NotSent(AbortReason.CONFIRMATION_EXPIRED, null), fixture.completedOutcome())
        assertEquals(listOf(), fixture.ble.dispatched)
    }

    @Test
    fun staleLockedObservationIsNotAcceptedForPolledRoute() = runTest {
        val fixture = fixture()
        val cloudTarget = LockTarget(lockId, LockRoute.CLOUD)
        fixture.cloud.connectNow()
        fixture.cloud.observe(MechanicalState.LOCKED)
        advanceTimeBy(1.seconds)

        fixture.coordinator.requestLock(cloudTarget)
        advanceTimeBy(20.seconds)

        assertEquals(listOf(LockIntent.LOCK), fixture.cloud.dispatched)
        assertIs<CommandOutcome.SentButNotVerified>(fixture.completedOutcome())
        assertTrue(fixture.cloud.refreshCount > 0)
    }

    @Test
    fun observationFromOldConnectionIsNotUsedForVerification() = runTest {
        val fixture = fixture()
        fixture.ble.connectNow()
        fixture.ble.onDispatch = {
            fixture.ble.connectNow()
            fixture.ble.observe(MechanicalState.LOCKED, generation = 1)
        }

        fixture.coordinator.requestLock(bleTarget)
        advanceTimeBy(20.seconds)

        assertIs<CommandOutcome.SentButNotVerified>(fixture.completedOutcome())
    }

    @Test
    fun lockThatIsUndoneDuringSettleIsNotVerified() = runTest {
        val fixture = fixture()
        fixture.ble.connectNow()
        fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING)
        fixture.ble.onDispatch = { fixture.ble.observe(MechanicalState.LOCKED) }

        fixture.coordinator.requestLock(bleTarget)
        runCurrent()
        advanceTimeBy(500)
        fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING)
        advanceTimeBy(20.seconds)

        assertIs<CommandOutcome.SentButNotVerified>(fixture.completedOutcome())
    }

    @Test
    fun settleRestartsWhenStateFlickersDuringSettle() = runTest {
        val fixture = fixture()
        fixture.ble.connectNow()
        fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING)
        fixture.ble.onDispatch = { fixture.ble.observe(MechanicalState.LOCKED) }

        fixture.coordinator.requestLock(bleTarget)
        runCurrent()
        advanceTimeBy(500)
        fixture.ble.observe(MechanicalState.NOT_LOCKED_OR_MOVING)
        advanceTimeBy(900)
        fixture.ble.observe(MechanicalState.LOCKED)
        advanceTimeBy(200)

        assertIs<CommandPhase.AwaitingStateVerification>(fixture.coordinator.phase(lockId).value)

        advanceTimeBy(2.seconds)

        assertEquals(CommandOutcome.Verified, fixture.completedOutcome())
    }

    @Test
    fun unlockIsNotSentWhenAppLeavesForegroundWhileTransportPrepares() = runTest {
        val fixture = fixture()
        fixture.ble.beforeCommit = { fixture.foreground.value = false }
        val requestId = fixture.startedUnlock()
        fixture.coordinator.confirmUnlock(requestId)
        fixture.coordinator.onAuthenticationSucceeded(requestId)

        advanceTimeBy(30.seconds)

        assertEquals(listOf(), fixture.ble.dispatched)
        assertEquals(CommandOutcome.NotSent(AbortReason.APP_NOT_IN_FOREGROUND, TransportError.NOT_CONNECTED), fixture.completedOutcome())
    }

    @Test
    fun polledRouteIsVerifiedOnlyByNewerStateVersion() = runTest {
        val fixture = fixture()
        val cloudTarget = LockTarget(lockId, LockRoute.CLOUD)
        fixture.cloud.onDispatch = {
            fixture.cloud.connectNow()
            fixture.cloud.observe(MechanicalState.LOCKED)
        }

        fixture.coordinator.requestLock(cloudTarget)
        runCurrent()
        advanceTimeBy(1)
        fixture.cloud.observe(MechanicalState.LOCKED)
        advanceTimeBy(5.seconds)

        assertEquals(CommandOutcome.Verified, fixture.completedOutcome())
    }

    @Test
    fun polledRouteIgnoresSameStateVersionFetchedAfterSend() = runTest {
        val fixture = fixture()
        val cloudTarget = LockTarget(lockId, LockRoute.CLOUD)

        fixture.coordinator.requestLock(cloudTarget)
        runCurrent()
        advanceTimeBy(1)
        fixture.cloud.observe(MechanicalState.LOCKED)
        advanceTimeBy(20.seconds)

        assertIs<CommandOutcome.SentButNotVerified>(fixture.completedOutcome())
    }
}

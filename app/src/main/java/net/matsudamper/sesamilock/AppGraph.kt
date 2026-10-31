package net.matsudamper.sesamilock

import android.app.Application
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.sesame.ble.SesameBleConfig
import net.matsudamper.sesamilock.sesame.ble.SesameBleTransport
import net.matsudamper.sesamilock.sesame.ble.UnlockVerificationPolicy
import net.matsudamper.sesamilock.sesame.cloud.SesameCloudTransport
import net.matsudamper.sesamilock.sesame.core.coordinator.LockCommandCoordinator
import net.matsudamper.sesamilock.sesame.core.coordinator.LockSafetyConfig
import net.matsudamper.sesamilock.sesame.core.coordinator.RequestIdFactory
import net.matsudamper.sesamilock.sesame.core.diagnostics.DiagnosticsRecorder
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository

internal class AppGraph(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var backgroundDisconnectJob: Job? = null

    val clock: LockClock = SystemLockClock
    val repository = EncryptedLockRepository(application)
    val diagnostics = DiagnosticsRecorder(clock, capacity = DIAGNOSTICS_CAPACITY)
    val bleTransport = SesameBleTransport(
        context = application,
        clock = clock,
        config = SesameBleConfig(
            historyTag = HISTORY_TAG,
            acknowledgementTimeout = 8.seconds,
            unlockVerification = UnlockVerificationPolicy.Disabled,
        ),
    )
    val cloudTransport = SesameCloudTransport(clock = clock, historyTag = HISTORY_TAG)
    val transports: Map<LockRoute, LockTransport> = listOf(bleTransport, cloudTransport).associateBy { it.route }
    val foregroundTracker = ActivityForegroundTracker(onVisibilityChanged = ::onVisibilityChanged)
    val coordinator = LockCommandCoordinator(
        transports = transports.values.toList(),
        clock = clock,
        config = LockSafetyConfig.Default,
        foregroundState = foregroundTracker,
        diagnostics = diagnostics,
        requestIdFactory = RequestIdFactory.RandomUuid,
        scope = applicationScope,
    )
    val registrySync = LockRegistrySync(repository, bleTransport, cloudTransport)

    fun start() {
        registrySync.start(applicationScope)
        applicationScope.launch { repository.load() }
    }

    private fun onVisibilityChanged(visible: Boolean) {
        synchronized(this) {
            backgroundDisconnectJob?.cancel()
            backgroundDisconnectJob = if (visible) {
                null
            } else {
                applicationScope.launch {
                    delay(BACKGROUND_DISCONNECT_DELAY)
                    bleTransport.disconnectAll()
                }
            }
        }
    }

    private companion object {
        private const val HISTORY_TAG = "sesami-lock"
        private const val DIAGNOSTICS_CAPACITY = 100
        private val BACKGROUND_DISCONNECT_DELAY = 30.seconds
    }
}

package net.matsudamper.sesamilock.sesame.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import co.candyhouse.sesame.ble.CHDeviceLoginStatus
import co.candyhouse.sesame.ble.CHDeviceStatus
import co.candyhouse.sesame.ble.CHDeviceStatusDelegate
import co.candyhouse.sesame.ble.CHDevices
import co.candyhouse.sesame.ble.os2.sesame2.CHSesame2
import co.candyhouse.sesame.ble.os3.sesame5.CHSesame5
import net.matsudamper.sesamilock.sesame.core.model.ConnectionState
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.MechanicalObservation
import net.matsudamper.sesamilock.sesame.core.transport.LockSnapshotStore

internal class BleLockSession(
    val device: CHDevices,
    val store: LockSnapshotStore,
    private val clock: LockClock,
    private val policy: UnlockVerificationPolicy,
) {
    private val mutableDeviceStatus = MutableStateFlow(device.deviceStatus)
    val deviceStatus: StateFlow<CHDeviceStatus> = mutableDeviceStatus.asStateFlow()
    private var connectionGeneration = store.snapshot.value.connectionGeneration
    private var loggedIn = false
    private var mechanicalStatusIsFromCurrentConnection = false

    val delegate: CHDeviceStatusDelegate = object : CHDeviceStatusDelegate {
        override fun onBleDeviceStatusChanged(device: CHDevices, status: CHDeviceStatus) {
            onStatusChanged(status)
        }

        override fun onMechStatus(device: CHDevices) {
            onMechanicalStatusReceived()
        }
    }

    val isLoggedIn: Boolean get() = synchronized(this) { loggedIn }

    val currentGeneration: Long get() = synchronized(this) { connectionGeneration }

    fun attach() {
        device.delegate = delegate
        onStatusChanged(device.deviceStatus)
    }

    fun detach() {
        device.delegate = null
        device.disconnect {}
        synchronized(this) {
            loggedIn = false
            mechanicalStatusIsFromCurrentConnection = false
            store.updateConnection(ConnectionState.DISCONNECTED, connectionGeneration)
        }
    }

    private fun onStatusChanged(status: CHDeviceStatus) {
        synchronized(this) {
            val nowLoggedIn = status.value == CHDeviceLoginStatus.logined
            if (nowLoggedIn && !loggedIn) {
                connectionGeneration += 1
            }
            if (!nowLoggedIn && status != CHDeviceStatus.BleLogining) {
                mechanicalStatusIsFromCurrentConnection = false
            }
            loggedIn = nowLoggedIn
            store.updateConnection(status.toConnectionState(), connectionGeneration)
            if (nowLoggedIn && mechanicalStatusIsFromCurrentConnection) {
                publishObservation()
            }
        }
        mutableDeviceStatus.value = status
    }

    private fun onMechanicalStatusReceived() {
        synchronized(this) {
            mechanicalStatusIsFromCurrentConnection = true
            if (loggedIn) publishObservation()
        }
    }

    private fun publishObservation() {
        val reading = readMechanicalStatus() ?: return
        val batteryCritical = device.mechStatus?.isBatteryCritical == true
        store.offerObservation(
            MechanicalObservation(
                state = MechanicalClassifier.classify(reading, policy),
                observedAtElapsedMillis = clock.elapsedRealtimeMillis(),
                observedAtWallMillis = clock.currentTimeMillis(),
                connectionGeneration = connectionGeneration,
                batteryCritical = batteryCritical,
            ),
        )
    }

    private fun readMechanicalStatus(): MechanicalReading? {
        val mech = device.mechStatus ?: return null
        return when (device) {
            is CHSesame5 -> MechanicalReading(
                isInLockRange = mech.isInLockRange,
                isInUnlockRange = mech.isInUnlockRange,
                isStopped = mech.isStop,
                isMoving = mech.isStop == false,
                position = mech.position.toInt(),
                unlockPosition = device.mechSetting?.unlockPosition?.toInt(),
            )

            is CHSesame2 -> MechanicalReading(
                isInLockRange = mech.isInLockRange,
                isInUnlockRange = mech.isInUnlockRange,
                isStopped = mech.isStop,
                isMoving = device.deviceStatus == CHDeviceStatus.Moved,
                position = mech.position.toInt(),
                unlockPosition = device.mechSetting?.unlockPosition?.toInt(),
            )

            else -> null
        }
    }

    private fun CHDeviceStatus.toConnectionState(): ConnectionState = when {
        value == CHDeviceLoginStatus.logined -> ConnectionState.AUTHENTICATED
        this in connectingStatuses -> ConnectionState.CONNECTING
        else -> ConnectionState.DISCONNECTED
    }

    private companion object {
        private val connectingStatuses = setOf(
            CHDeviceStatus.BleConnecting,
            CHDeviceStatus.WaitingGatt,
            CHDeviceStatus.DiscoverServices,
            CHDeviceStatus.BleLogining,
        )
    }
}

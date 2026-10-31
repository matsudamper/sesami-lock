package net.matsudamper.sesamilock.sesame.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import co.candyhouse.sesame.ble.CHBleManager
import co.candyhouse.sesame.ble.CHDevice
import co.candyhouse.sesame.ble.CHDeviceLoginStatus
import co.candyhouse.sesame.ble.CHDeviceStatus
import co.candyhouse.sesame.ble.CHDevices
import co.candyhouse.sesame.ble.os2.sesame2.CHSesame2
import co.candyhouse.sesame.ble.os3.sesame5.CHSesame5
import co.candyhouse.sesame.utils.CHEmpty
import co.candyhouse.sesame.utils.CHResult
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.LockSnapshotStore
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.core.transport.SendGate
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

public class SesameBleTransport(
    context: Context,
    private val clock: LockClock,
    private val config: SesameBleConfig,
) : LockTransport {
    override val route: LockRoute = LockRoute.BLE
    private val appContext = context.applicationContext
    private val sessions = ConcurrentHashMap<LockId, BleLockSession>()
    private val stores = ConcurrentHashMap<LockId, LockSnapshotStore>()

    init {
        SesameSdkRuntime.ensureHostInitialized()
    }

    public fun register(lockId: LockId, credential: LockCredential.Ble): Boolean {
        if (sessions.containsKey(lockId)) return true
        val device = CHBleManager.restoreDevice(credential.toChDevice()) ?: return false
        if (device !is CHSesame5 && device !is CHSesame2) return false
        if (device.productModel.deviceModel() != credential.model.deviceModel) return false
        val session = BleLockSession(device, store(lockId), clock, config.unlockVerification)
        sessions[lockId] = session
        session.attach()
        return true
    }

    public fun unregister(lockId: LockId) {
        sessions.remove(lockId)?.let { session -> runCatching { session.detach() } }
    }

    public fun disconnectAll() {
        sessions.values.forEach { session -> runCatching { session.device.disconnect {} } }
        runCatching { CHBleManager.disableScan {} }
    }

    override fun snapshot(lockId: LockId): StateFlow<LockSnapshot> = store(lockId).snapshot

    override suspend fun ensureConnected(lockId: LockId): ConnectionResult {
        val session = sessions[lockId] ?: return ConnectionResult.Failed(TransportError.DEVICE_NOT_REGISTERED)
        if (!hasRequiredPermissions()) return ConnectionResult.Failed(TransportError.PERMISSION_DENIED)
        if (!isBluetoothEnabled()) return ConnectionResult.Failed(TransportError.BLUETOOTH_OFF)
        SesameSdkRuntime.ensureBluetoothInitialized(appContext)
        if (!enableScan()) return ConnectionResult.Failed(TransportError.UNKNOWN)

        val status = session.deviceStatus
            .onEach { status -> if (status == CHDeviceStatus.ReceivedAdV) connect(session.device) }
            .first { status -> status.value == CHDeviceLoginStatus.logined || status == CHDeviceStatus.WaitingForAuth }
        return if (status == CHDeviceStatus.WaitingForAuth) {
            ConnectionResult.Failed(TransportError.SERVER_AUTHORIZATION_REQUIRED)
        } else {
            ConnectionResult.Ready(lockId, session.currentGeneration)
        }
    }

    override fun isAuthenticated(lockId: LockId): Boolean {
        val session = sessions[lockId] ?: return false
        return session.isLoggedIn && session.device.deviceStatus.value == CHDeviceLoginStatus.logined
    }

    override suspend fun dispatchExplicit(lockId: LockId, intent: LockIntent, requestId: RequestId, sendGate: SendGate): DispatchResult {
        val session = sessions[lockId] ?: return DispatchResult.NotSent(TransportError.DEVICE_NOT_REGISTERED)
        val device = session.device
        if (device.deviceStatus.value != CHDeviceLoginStatus.logined) return DispatchResult.NotSent(TransportError.NOT_CONNECTED)
        val command = ExplicitLockCommand.from(device, intent) ?: return DispatchResult.NotSent(TransportError.UNSUPPORTED_DEVICE)
        val historyTag = config.historyTag.toByteArray(Charsets.UTF_8)
        if (!sendGate.tryCommit()) return DispatchResult.NotSent(TransportError.NOT_CONNECTED)

        val acknowledged = withTimeoutOrNull(config.acknowledgementTimeout) {
            suspendCancellableCoroutine { continuation ->
                val returnedFromSdk = AtomicBoolean(false)
                val callback: CHResult<CHEmpty> = { result ->
                    val dispatchResult = when {
                        result.isSuccess -> DispatchResult.Accepted
                        !returnedFromSdk.get() -> DispatchResult.NotSent(TransportError.NOT_CONNECTED)
                        else -> DispatchResult.RejectedByDevice(TransportError.DEVICE_ERROR)
                    }
                    if (continuation.isActive) continuation.resume(dispatchResult)
                }
                command.send(historyTag, callback)
                returnedFromSdk.set(true)
            }
        }
        if (acknowledged == null && intent == LockIntent.UNLOCK) {
            discardPendingSdkCommands(session)
        }
        return acknowledged ?: DispatchResult.OutcomeUnknown(TransportError.TIMEOUT)
    }

    override suspend fun refreshState(lockId: LockId): Unit = Unit

    /**
     * SDK は先行コマンドの送信や応答を待つ間、後続のコマンドを内部キューに保持する。
     * 応答待ちのまま時間切れになった解錠が、期限後や背面移行後に送られないよう GATT 接続ごと切断する。
     * OS2 は再ログインで内部キューが作り直されるため、切断前に積まれたコマンドは再接続後も送られない。
     */
    private fun discardPendingSdkCommands(session: BleLockSession) {
        runCatching { session.device.disconnect {} }
    }

    private fun store(lockId: LockId): LockSnapshotStore = stores.getOrPut(lockId) {
        LockSnapshotStore(LockSnapshot.initial(lockId, LockRoute.BLE, pushesUpdatesWhileConnected = true))
    }

    private fun connect(device: CHDevices) {
        runCatching { device.connect {} }
    }

    private suspend fun enableScan(): Boolean = suspendCancellableCoroutine { continuation ->
        runCatching {
            CHBleManager.enableScan(false) { result ->
                if (continuation.isActive) continuation.resume(result.isSuccess)
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(false)
        }
    }

    private fun hasRequiredPermissions(): Boolean = requiredRuntimePermissions().all {
        appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBluetoothEnabled(): Boolean {
        val manager = appContext.getSystemService(BluetoothManager::class.java) ?: return false
        return manager.adapter?.isEnabled == true
    }

    private fun LockCredential.Ble.toChDevice(): CHDevice = CHDevice(
        deviceUUID = deviceUuid.lowercase(Locale.ROOT),
        deviceModel = model.deviceModel,
        historyTag = null,
        keyIndex = keyIndexHex,
        secretKey = secretKeyHex,
        sesame2PublicKey = publicKeyHex,
    )

    public companion object {
        public fun requiredRuntimePermissions(): List<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
}

private sealed interface ExplicitLockCommand {
    fun send(historyTag: ByteArray, callback: CHResult<CHEmpty>)

    class Sesame5Lock(private val device: CHSesame5) : ExplicitLockCommand {
        override fun send(historyTag: ByteArray, callback: CHResult<CHEmpty>) = device.lock(historyTag, callback)
    }

    class Sesame5Unlock(private val device: CHSesame5) : ExplicitLockCommand {
        override fun send(historyTag: ByteArray, callback: CHResult<CHEmpty>) = device.unlock(historyTag, callback)
    }

    class Sesame2Lock(private val device: CHSesame2) : ExplicitLockCommand {
        override fun send(historyTag: ByteArray, callback: CHResult<CHEmpty>) = device.lock(historyTag, callback)
    }

    class Sesame2Unlock(private val device: CHSesame2) : ExplicitLockCommand {
        override fun send(historyTag: ByteArray, callback: CHResult<CHEmpty>) = device.unlock(historyTag, callback)
    }

    companion object {
        fun from(device: CHDevices, intent: LockIntent): ExplicitLockCommand? = when (device) {
            is CHSesame5 -> when (intent) {
                LockIntent.LOCK -> Sesame5Lock(device)
                LockIntent.UNLOCK -> Sesame5Unlock(device)
            }

            is CHSesame2 -> when (intent) {
                LockIntent.LOCK -> Sesame2Lock(device)
                LockIntent.UNLOCK -> Sesame2Unlock(device)
            }

            else -> null
        }
    }
}

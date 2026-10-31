package net.matsudamper.sesamilock.sesame.ble

import android.content.Context
import co.candyhouse.sesame.ble.CHBleHost
import co.candyhouse.sesame.ble.CHBleManager
import co.candyhouse.sesame.ble.CHBleSupport
import co.candyhouse.sesame.ble.CHDevice
import co.candyhouse.sesame.ble.CHKeyPersistence
import co.candyhouse.sesame.ble.CHRegistrationResult
import co.candyhouse.sesame.utils.CHResult
import co.candyhouse.sesame.utils.HttpResponseCallback

internal object SesameSdkRuntime {
    private var hostInitialized = false
    private var bluetoothInitialized = false

    fun ensureHostInitialized() {
        synchronized(this) {
            if (hostInitialized) return
            CHBleSupport.initialize(OfflineBleHost, NonPersistentKeys, "")
            hostInitialized = true
        }
    }

    /**
     * SDK の CHBleManager は初期化時に Bluetooth アダプタへ触れるため、実行時権限を得た後に呼ぶ。
     */
    fun ensureBluetoothInitialized(context: Context) {
        synchronized(this) {
            ensureHostInitialized()
            if (bluetoothInitialized) return
            CHBleManager(context.applicationContext)
            bluetoothInitialized = true
        }
    }

    private object OfflineBleHost : CHBleHost {
        override fun isNetworkAvailable(): Boolean = false

        override fun shouldUploadHistory(deviceId: String): Boolean = false

        override fun signGuestKey(deviceId: String, token: String, secretKey: String, onResponse: CHResult<String>) {
            onResponse(Result.failure(UnsupportedOperationException("server signed guest keys are not supported")))
        }

        override fun updateDeviceFirmwareVersion(deviceUUID: String, versionTag: String, onResponse: CHResult<Any>) {
            onResponse(Result.failure(UnsupportedOperationException()))
        }

        override fun postSS2History(deviceID: String, hisHex: String, onResponse: CHResult<Any>) {
            onResponse(Result.failure(UnsupportedOperationException()))
        }

        override fun postOS3History(deviceID: String, hisHex: String, onResponse: CHResult<Any>) {
            onResponse(Result.failure(UnsupportedOperationException()))
        }

        override fun postBatteryData(deviceID: String, payloadString: String, onResponse: CHResult<Any>) {
            onResponse(Result.failure(UnsupportedOperationException()))
        }

        override fun registerOs2(
            deviceId: String,
            appKey: String,
            nonce: String,
            registrationKey: String,
            productType: String,
            onResponse: CHResult<CHRegistrationResult>,
        ) {
            onResponse(Result.failure(UnsupportedOperationException("device registration is not supported")))
        }

        override fun registerOs3(deviceId: String, productType: String, publicKey: String, onResponse: CHResult<Any>) {
            onResponse(Result.failure(UnsupportedOperationException("device registration is not supported")))
        }
    }

    private object NonPersistentKeys : CHKeyPersistence {
        override fun insert(device: CHDevice, onResponse: HttpResponseCallback<String>) {
            onResponse(Result.success(""))
        }

        override fun deleteByDeviceId(deviceId: String, onResponse: HttpResponseCallback<Int>) {
            onResponse(Result.success(0))
        }
    }
}

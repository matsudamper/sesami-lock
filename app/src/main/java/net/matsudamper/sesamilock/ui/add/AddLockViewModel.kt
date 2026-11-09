package net.matsudamper.sesamilock.ui.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.QrImageDecoder
import net.matsudamper.sesamilock.sesame.core.key.KeyImportError
import net.matsudamper.sesamilock.sesame.core.key.KeyImportResult
import net.matsudamper.sesamilock.sesame.core.key.SesameKeyImporter
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel
import net.matsudamper.sesamilock.sesame.core.transfer.LockTransferCodec
import net.matsudamper.sesamilock.sesame.core.transfer.LockTransferError
import net.matsudamper.sesamilock.sesame.core.transfer.LockTransferResult
import net.matsudamper.sesamilock.sesame.credential.AddLockResult
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository

internal class AddLockViewModel(
    private val repository: EncryptedLockRepository,
    private val qrImageDecoder: QrImageDecoder,
) : ViewModel() {
    private val form = MutableStateFlow(
        Form(
            name = "",
            route = LockRoute.BLE,
            keyText = "",
            deviceUuid = "",
            apiKey = "",
            secretKey = "",
            cloudModel = SesameModel.SESAME_5,
            error = null,
            qrReading = false,
            saving = false,
            closeRequested = false,
        ),
    )

    private val listener = object : AddLockUiState.Listener {
        override fun onNameChange(value: String) {
            form.update { it.copy(name = value, error = null) }
        }

        override fun onBleRouteClick() {
            form.update { it.copy(route = LockRoute.BLE, error = null) }
        }

        override fun onCloudRouteClick() {
            form.update { it.copy(route = LockRoute.CLOUD, error = null) }
        }

        override fun onKeyTextChange(value: String) {
            form.update { it.copy(keyText = value, error = null) }
        }

        override fun onQrImagePicked(uri: Uri) {
            readQrImage(uri)
        }

        override fun onTransferQrImagePicked(uri: Uri) {
            readTransferQrImage(uri)
        }

        override fun onDeviceUuidChange(value: String) {
            form.update { it.copy(deviceUuid = value, error = null) }
        }

        override fun onApiKeyChange(value: String) {
            form.update { it.copy(apiKey = value, error = null) }
        }

        override fun onSecretKeyChange(value: String) {
            form.update { it.copy(secretKey = value, error = null) }
        }

        override fun onSaveClick() {
            save()
        }

        override fun onBackClick() {
            form.update { it.clearedSecrets().copy(closeRequested = true) }
        }
    }

    val uiState: StateFlow<AddLockUiState> = form.map(::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(form.value),
    )

    private fun buildUiState(form: Form): AddLockUiState = AddLockUiState(
        name = form.name,
        bleRouteSelected = form.route == LockRoute.BLE,
        cloudRouteSelected = form.route == LockRoute.CLOUD,
        bleInput = if (form.route == LockRoute.BLE) {
            AddLockUiState.BleInput(
                keyText = form.keyText,
                keyError = form.error?.messageFor(FormField.KEY),
                qrReadingProgressVisible = form.qrReading,
                qrPickButtonEnabled = !form.qrReading,
            )
        } else {
            null
        },
        cloudInput = if (form.route == LockRoute.CLOUD) {
            AddLockUiState.CloudInput(
                deviceUuid = form.deviceUuid,
                deviceUuidError = form.error?.messageFor(FormField.DEVICE_UUID),
                apiKey = form.apiKey,
                apiKeyError = form.error?.messageFor(FormField.API_KEY),
                secretKey = form.secretKey,
                secretKeyError = form.error?.messageFor(FormField.SECRET_KEY),
                selectedModelLabel = form.cloudModel.displayName,
                models = SesameModel.entries.map { model ->
                    AddLockUiState.ModelOption(
                        label = model.displayName,
                        selected = model == form.cloudModel,
                        listener = object : AddLockUiState.ModelOptionListener {
                            override fun onClick() {
                                this@AddLockViewModel.form.update { it.copy(cloudModel = model) }
                            }
                        },
                    )
                },
            )
        } else {
            null
        },
        generalErrorMessage = form.error?.messageFor(FormField.GENERAL),
        saveButtonEnabled = !form.saving && !form.closeRequested,
        transferImportProgressVisible = form.qrReading,
        transferImportButtonEnabled = !form.qrReading,
        closeRequested = form.closeRequested,
        listener = listener,
    )

    private fun save() {
        val current = form.value
        if (current.saving) return
        val parsed = when (current.route) {
            LockRoute.BLE -> parseBle(current)
            LockRoute.CLOUD -> parseCloud(current)
        }
        val lock = when (parsed) {
            is Parsed.Error -> {
                form.update { it.copy(error = parsed.error) }
                return
            }

            is Parsed.Lock -> parsed.lock
        }
        form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (repository.add(lock)) {
                AddLockResult.ADDED -> form.update { it.clearedSecrets().copy(saving = false, closeRequested = true) }
                AddLockResult.ALREADY_REGISTERED -> form.update { it.copy(saving = false, error = FormError(FormField.GENERAL, "この SESAME は同じ経路で登録済みです。既存の鍵は変更していません")) }
                AddLockResult.NOT_READY -> form.update { it.copy(saving = false, error = FormError(FormField.GENERAL, "保存領域を読み込めていません")) }
            }
        }
    }

    private fun readQrImage(uri: Uri) {
        if (form.value.qrReading) return
        form.update { it.copy(qrReading = true, error = null) }
        viewModelScope.launch {
            val text = runCatching { qrImageDecoder.decode(uri) }.getOrNull()
            form.update { current ->
                if (text == null) {
                    current.copy(qrReading = false, error = FormError(FormField.KEY, "画像から QR コードを読み取れませんでした"))
                } else {
                    current.copy(qrReading = false, keyText = text)
                }
            }
        }
    }

    private fun readTransferQrImage(uri: Uri) {
        if (form.value.qrReading) return
        form.update { it.copy(qrReading = true, error = null) }
        viewModelScope.launch {
            val text = runCatching { qrImageDecoder.decode(uri) }.getOrNull()
            form.update { current ->
                val failure = { message: String -> current.copy(qrReading = false, error = FormError(FormField.GENERAL, message)) }
                if (text == null) {
                    failure("画像から QR コードを読み取れませんでした")
                } else {
                    when (val result = LockTransferCodec.decode(text)) {
                        is LockTransferResult.Failure -> failure(result.error.message())
                        is LockTransferResult.Success -> current.withTransferred(result)
                    }
                }
            }
        }
    }

    private fun Form.withTransferred(result: LockTransferResult.Success): Form = when (val credential = result.credential) {
        is LockCredential.Ble -> copy(
            qrReading = false,
            name = result.displayName,
            route = LockRoute.BLE,
            keyText = SesameKeyImporter.exportJson(credential),
        )

        is LockCredential.Cloud -> copy(
            qrReading = false,
            name = result.displayName,
            route = LockRoute.CLOUD,
            cloudModel = credential.model,
            deviceUuid = credential.deviceUuid,
            apiKey = credential.apiKey,
            secretKey = credential.secretKeyHex,
        )
    }

    private fun LockTransferError.message(): String = when (this) {
        LockTransferError.NOT_TRANSFER_DATA -> "このアプリで書き出した QR コードではありません"
        LockTransferError.UNSUPPORTED_VERSION -> "新しい形式の QR コードです。アプリを更新してください"
        LockTransferError.INVALID_CONTENT -> "QR コードの内容が正しくありません"
    }

    private fun parseBle(form: Form): Parsed = when (val result = SesameKeyImporter.import(form.keyText)) {
        is KeyImportResult.Failure -> Parsed.Error(FormError(FormField.KEY, result.error.message()))

        is KeyImportResult.Success -> {
            val id = LockId.of(result.credential.deviceUuid, LockRoute.BLE)
            if (id == null) {
                Parsed.Error(FormError(FormField.KEY, KeyImportError.INVALID_UUID.message()))
            } else {
                val name = form.name.trim().ifEmpty { result.suggestedName ?: result.credential.model.displayName }
                Parsed.Lock(RegisteredLock(id, name, result.credential))
            }
        }
    }

    private fun parseCloud(form: Form): Parsed {
        val id = LockId.of(form.deviceUuid.trim(), LockRoute.CLOUD)
            ?: return Parsed.Error(FormError(FormField.DEVICE_UUID, "UUID の形式が正しくありません"))
        val apiKey = form.apiKey.trim()
        if (apiKey.isEmpty()) return Parsed.Error(FormError(FormField.API_KEY, "API キーを入力してください"))
        val secret = form.secretKey.trim().lowercase(Locale.ROOT)
        if (!secretKeyPattern.matches(secret)) return Parsed.Error(FormError(FormField.SECRET_KEY, "32 桁の 16 進数で入力してください"))
        val credential = LockCredential.Cloud(
            deviceUuid = id.deviceUuid,
            model = form.cloudModel,
            apiKey = apiKey,
            secretKeyHex = secret,
        )
        val name = form.name.trim().ifEmpty { form.cloudModel.displayName }
        return Parsed.Lock(RegisteredLock(id, name, credential))
    }

    private sealed interface Parsed {
        data class Lock(val lock: RegisteredLock) : Parsed

        data class Error(val error: FormError) : Parsed
    }

    private enum class FormField {
        KEY,
        DEVICE_UUID,
        API_KEY,
        SECRET_KEY,
        GENERAL,
    }

    private data class FormError(val field: FormField, val message: String) {
        fun messageFor(target: FormField): String? = message.takeIf { field == target }
    }

    private data class Form(
        val name: String,
        val route: LockRoute,
        val keyText: String,
        val deviceUuid: String,
        val apiKey: String,
        val secretKey: String,
        val cloudModel: SesameModel,
        val error: FormError?,
        val qrReading: Boolean,
        val saving: Boolean,
        val closeRequested: Boolean,
    ) {
        fun clearedSecrets(): Form = copy(keyText = "", apiKey = "", secretKey = "")

        override fun toString(): String = "Form(route=$route, saving=$saving, closeRequested=$closeRequested)"
    }

    private companion object {
        private val secretKeyPattern = Regex("^[0-9a-f]{32}$")
    }
}

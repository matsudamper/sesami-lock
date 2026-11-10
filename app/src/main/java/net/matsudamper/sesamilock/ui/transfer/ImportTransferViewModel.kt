package net.matsudamper.sesamilock.ui.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.QrImageDecoder
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

internal class ImportTransferViewModel(
    private val repository: EncryptedLockRepository,
    private val qrImageDecoder: QrImageDecoder,
) : ViewModel() {
    private val form = MutableStateFlow(
        Form(
            loaded = null,
            name = "",
            errorMessage = null,
            reading = false,
            saving = false,
            closeRequested = false,
        ),
    )

    private val listener = object : ImportTransferUiState.Listener {
        override fun onQrImagePicked(uri: Uri) {
            readQrImage(uri)
        }

        override fun onQrScanned(text: String) {
            if (form.value.reading) return
            form.update { it.withTransferText(text) }
        }

        override fun onNameChange(value: String) {
            form.update { it.copy(name = value, errorMessage = null) }
        }

        override fun onSaveClick() {
            save()
        }

        override fun onBackClick() {
            form.update { it.copy(loaded = null, closeRequested = true) }
        }
    }

    val uiState: StateFlow<ImportTransferUiState> = form.map(::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(form.value),
    )

    private fun buildUiState(form: Form): ImportTransferUiState = ImportTransferUiState(
        preview = form.loaded?.let { loaded ->
            ImportTransferUiState.Preview(
                name = form.name,
                routeLabel = when (loaded.credential.route) {
                    LockRoute.BLE -> "Bluetooth"
                    LockRoute.CLOUD -> "クラウド"
                },
                modelLabel = loaded.credential.model().displayName,
            )
        },
        errorMessage = form.errorMessage,
        readProgressVisible = form.reading,
        readButtonEnabled = !form.reading && !form.saving,
        saveButtonEnabled = form.loaded != null && !form.saving && !form.reading && !form.closeRequested,
        closeRequested = form.closeRequested,
        listener = listener,
    )

    private fun readQrImage(uri: Uri) {
        if (form.value.reading) return
        form.update { it.copy(reading = true, errorMessage = null) }
        viewModelScope.launch {
            val text = runCatching { qrImageDecoder.decode(uri) }.getOrNull()
            form.update { current ->
                if (text == null) {
                    current.copy(reading = false, errorMessage = "画像から QR コードを読み取れませんでした")
                } else {
                    current.withTransferText(text)
                }
            }
        }
    }

    private fun Form.withTransferText(text: String): Form = when (val result = LockTransferCodec.decode(text)) {
        is LockTransferResult.Failure -> copy(reading = false, loaded = null, errorMessage = result.error.message())
        is LockTransferResult.Success -> copy(reading = false, loaded = result, name = result.displayName, errorMessage = null)
    }

    private fun save() {
        val current = form.value
        val loaded = current.loaded ?: return
        if (current.saving) return
        val credential = loaded.credential
        val id = LockId.of(credential.deviceUuid(), credential.route)
        if (id == null) {
            form.update { it.copy(errorMessage = LockTransferError.INVALID_CONTENT.message()) }
            return
        }
        val name = current.name.trim().ifEmpty { credential.model().displayName }
        form.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            when (repository.add(RegisteredLock(id, name, credential))) {
                AddLockResult.ADDED -> form.update { it.copy(loaded = null, saving = false, closeRequested = true) }
                AddLockResult.ALREADY_REGISTERED -> form.update { it.copy(saving = false, errorMessage = "この SESAME は同じ経路で登録済みです。既存の鍵は変更していません") }
                AddLockResult.NOT_READY -> form.update { it.copy(saving = false, errorMessage = "保存領域を読み込めていません") }
            }
        }
    }

    private fun LockCredential.model(): SesameModel = when (this) {
        is LockCredential.Ble -> model
        is LockCredential.Cloud -> model
    }

    private fun LockCredential.deviceUuid(): String = when (this) {
        is LockCredential.Ble -> deviceUuid
        is LockCredential.Cloud -> deviceUuid
    }

    private fun LockTransferError.message(): String = when (this) {
        LockTransferError.NOT_TRANSFER_DATA -> "このアプリで共有した QR コードではありません"
        LockTransferError.UNSUPPORTED_VERSION -> "新しい形式の QR コードです。アプリを更新してください"
        LockTransferError.INVALID_CONTENT -> "QR コードの内容が正しくありません"
    }

    private data class Form(
        val loaded: LockTransferResult.Success?,
        val name: String,
        val errorMessage: String?,
        val reading: Boolean,
        val saving: Boolean,
        val closeRequested: Boolean,
    ) {
        override fun toString(): String = "Form(loaded=${loaded != null}, saving=$saving, closeRequested=$closeRequested)"
    }
}

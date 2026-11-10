package net.matsudamper.sesamilock.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.QrImageDecoder
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.LockCommandCoordinator
import net.matsudamper.sesamilock.sesame.core.key.KeyImportError
import net.matsudamper.sesamilock.sesame.core.key.KeyImportResult
import net.matsudamper.sesamilock.sesame.core.key.SesameKeyImporter
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState
import net.matsudamper.sesamilock.sesame.credential.UpdateLockResult
import net.matsudamper.sesamilock.ui.add.message

internal class EditLockViewModel(
    private val repository: EncryptedLockRepository,
    private val coordinator: LockCommandCoordinator,
    private val qrImageDecoder: QrImageDecoder,
    private val lockId: LockId,
) : ViewModel() {
    private val state = MutableStateFlow<State>(State.Loading)

    private val listener = object : EditLockUiState.Listener {
        override fun onNameChange(value: String) {
            updateForm { it.copy(name = value, error = null) }
        }

        override fun onKeyTextChange(value: String) {
            updateForm { it.copy(keyText = value, error = null) }
        }

        override fun onQrImagePicked(uri: Uri) {
            readQrImage(uri)
        }

        override fun onQrScanned(text: String) {
            if ((state.value as? State.Editing)?.form?.qrReading == true) return
            updateForm { it.copy(keyText = text, error = null) }
        }

        override fun onDeviceUuidChange(value: String) {
            updateForm { it.copy(deviceUuid = value, error = null) }
        }

        override fun onApiKeyChange(value: String) {
            updateForm { it.copy(apiKey = value, error = null) }
        }

        override fun onSecretKeyChange(value: String) {
            updateForm { it.copy(secretKey = value, error = null) }
        }

        override fun onSaveClick() {
            save()
        }

        override fun onBackClick() {
            state.value = State.Closed
        }
    }

    val uiState: StateFlow<EditLockUiState> = state.map(::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(state.value),
    )

    init {
        viewModelScope.launch {
            val locks = repository.state.filterIsInstance<LockRepositoryState.Loaded>().first().locks
            val lock = locks.firstOrNull { it.id == lockId }
            state.value = if (lock == null) State.Closed else State.Editing(Form.from(lock))
        }
    }

    private fun buildUiState(state: State): EditLockUiState = EditLockUiState(
        content = when (state) {
            State.Loading, State.Closed -> EditLockUiState.Content.Loading
            is State.Editing -> buildForm(state.form)
        },
        closeRequested = state == State.Closed,
        listener = listener,
    )

    private fun buildForm(form: Form): EditLockUiState.Content.Form {
        val credential = form.original.credential
        return EditLockUiState.Content.Form(
            name = form.name,
            nameError = form.error?.messageFor(FormField.NAME),
            routeLabel = when (credential.route) {
                LockRoute.BLE -> "Bluetooth"
                LockRoute.CLOUD -> "クラウド"
            },
            bleInput = if (credential is LockCredential.Ble) {
                EditLockUiState.BleInput(
                    keyText = form.keyText,
                    keyError = form.error?.messageFor(FormField.KEY),
                    qrReadingProgressVisible = form.qrReading,
                    qrPickButtonEnabled = !form.qrReading,
                )
            } else {
                null
            },
            cloudInput = if (credential is LockCredential.Cloud) {
                EditLockUiState.CloudInput(
                    deviceUuid = form.deviceUuid,
                    deviceUuidError = form.error?.messageFor(FormField.DEVICE_UUID),
                    apiKey = form.apiKey,
                    apiKeyError = form.error?.messageFor(FormField.API_KEY),
                    secretKey = form.secretKey,
                    secretKeyError = form.error?.messageFor(FormField.SECRET_KEY),
                    selectedModelLabel = form.cloudModel.displayName,
                    models = SesameModel.entries.map { model ->
                        EditLockUiState.ModelOption(
                            label = model.displayName,
                            listener = object : EditLockUiState.ModelOptionListener {
                                override fun onClick() {
                                    updateForm { it.copy(cloudModel = model) }
                                }
                            },
                        )
                    },
                )
            } else {
                null
            },
            generalErrorMessage = form.error?.messageFor(FormField.GENERAL),
            saveButtonEnabled = !form.saving && !form.qrReading,
        )
    }

    private fun save() {
        val form = (state.value as? State.Editing)?.form ?: return
        if (form.saving || form.qrReading) return
        val name = form.name.trim()
        if (name.isEmpty()) return showError(FormError(FormField.NAME, "表示名を入力してください"))
        val edited = when (val original = form.original.credential) {
            is LockCredential.Ble -> editedBle(original, form) ?: return
            is LockCredential.Cloud -> editedCloud(original, form) ?: return
        }
        val (newId, credential) = edited
        if (coordinator.phase(lockId).value is CommandPhase.InProgress) {
            return showError(FormError(FormField.GENERAL, "施錠・解錠の操作中は保存できません。操作が終わってから保存してください"))
        }
        updateForm { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = repository.update(form.original.id, RegisteredLock(id = newId, displayName = name, credential = credential))
            when (result) {
                UpdateLockResult.UPDATED -> state.value = State.Closed

                UpdateLockResult.ALREADY_REGISTERED -> showSaveFailure(
                    when (credential.route) {
                        LockRoute.BLE -> FormError(FormField.KEY, "この UUID の Bluetooth の鍵はすでに登録されています")
                        LockRoute.CLOUD -> FormError(FormField.DEVICE_UUID, "この UUID のクラウドの鍵はすでに登録されています")
                    },
                )

                UpdateLockResult.NOT_FOUND -> showSaveFailure(FormError(FormField.GENERAL, "この鍵は削除されています"))

                UpdateLockResult.NOT_READY -> showSaveFailure(FormError(FormField.GENERAL, "保存領域を読み込めていません"))
            }
        }
    }

    private fun readQrImage(uri: Uri) {
        val form = (state.value as? State.Editing)?.form ?: return
        if (form.qrReading) return
        updateForm { it.copy(qrReading = true, error = null) }
        viewModelScope.launch {
            val text = runCatching { qrImageDecoder.decode(uri) }.getOrNull()
            updateForm { current ->
                if (text == null) {
                    current.copy(qrReading = false, error = FormError(FormField.KEY, "画像から QR コードを読み取れませんでした"))
                } else {
                    current.copy(qrReading = false, keyText = text)
                }
            }
        }
    }

    private fun editedBle(original: LockCredential.Ble, form: Form): Pair<LockId, LockCredential.Ble>? {
        if (form.keyText == SesameKeyImporter.exportJson(original)) return form.original.id to original
        val credential = when (val result = SesameKeyImporter.import(form.keyText)) {
            is KeyImportResult.Failure -> {
                showError(FormError(FormField.KEY, result.error.message()))
                return null
            }

            is KeyImportResult.Success -> result.credential
        }
        val id = LockId.of(credential.deviceUuid, LockRoute.BLE)
        if (id == null) {
            showError(FormError(FormField.KEY, KeyImportError.INVALID_UUID.message()))
            return null
        }
        return id to credential
    }

    private fun editedCloud(original: LockCredential.Cloud, form: Form): Pair<LockId, LockCredential.Cloud>? {
        val id = LockId.of(form.deviceUuid.trim(), LockRoute.CLOUD)
        if (id == null) {
            showError(FormError(FormField.DEVICE_UUID, "UUID の形式が正しくありません"))
            return null
        }
        val apiKey = form.apiKey.trim()
        if (apiKey.isEmpty()) {
            showError(FormError(FormField.API_KEY, "API キーを入力してください"))
            return null
        }
        val secret = form.secretKey.trim().lowercase(Locale.ROOT)
        if (!secretKeyPattern.matches(secret)) {
            showError(FormError(FormField.SECRET_KEY, "32 桁の 16 進数で入力してください"))
            return null
        }
        val unchanged = id.deviceUuid == original.deviceUuid &&
            apiKey == original.apiKey &&
            secret == original.secretKeyHex &&
            form.cloudModel == original.model
        val credential = if (unchanged) {
            original
        } else {
            LockCredential.Cloud(deviceUuid = id.deviceUuid, model = form.cloudModel, apiKey = apiKey, secretKeyHex = secret)
        }
        return id to credential
    }

    private fun showSaveFailure(error: FormError) {
        updateForm { it.copy(saving = false, error = error) }
    }

    private fun showError(error: FormError) {
        updateForm { it.copy(error = error) }
    }

    private fun updateForm(transform: (Form) -> Form) {
        state.update { current -> if (current is State.Editing) State.Editing(transform(current.form)) else current }
    }

    private sealed interface State {
        data object Loading : State

        data object Closed : State

        data class Editing(val form: Form) : State
    }

    private enum class FormField {
        NAME,
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
        val original: RegisteredLock,
        val name: String,
        val keyText: String,
        val qrReading: Boolean,
        val deviceUuid: String,
        val apiKey: String,
        val secretKey: String,
        val cloudModel: SesameModel,
        val error: FormError?,
        val saving: Boolean,
    ) {
        override fun toString(): String = "Form(route=${original.route}, saving=$saving)"

        companion object {
            fun from(lock: RegisteredLock): Form {
                val cloud = lock.credential as? LockCredential.Cloud
                val ble = lock.credential as? LockCredential.Ble
                return Form(
                    original = lock,
                    name = lock.displayName,
                    keyText = ble?.let(SesameKeyImporter::exportJson).orEmpty(),
                    qrReading = false,
                    deviceUuid = cloud?.deviceUuid.orEmpty(),
                    apiKey = cloud?.apiKey.orEmpty(),
                    secretKey = cloud?.secretKeyHex.orEmpty(),
                    cloudModel = lock.model,
                    error = null,
                    saving = false,
                )
            }
        }
    }

    private companion object {
        private val secretKeyPattern = Regex("^[0-9a-f]{32}$")
    }
}

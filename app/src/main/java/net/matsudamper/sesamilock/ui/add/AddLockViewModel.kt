package net.matsudamper.sesamilock.ui.add

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
import net.matsudamper.sesamilock.sesame.core.key.KeyImportError
import net.matsudamper.sesamilock.sesame.core.key.KeyImportResult
import net.matsudamper.sesamilock.sesame.core.key.SesameKeyImporter
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel
import net.matsudamper.sesamilock.sesame.credential.AddLockResult
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository

internal class AddLockViewModel(
    private val repository: EncryptedLockRepository,
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
            errorMessage = null,
            saving = false,
            closeRequested = false,
        ),
    )

    private val listener = object : AddLockUiState.Listener {
        override fun onNameChange(value: String) {
            form.update { it.copy(name = value, errorMessage = null) }
        }

        override fun onBleRouteClick() {
            form.update { it.copy(route = LockRoute.BLE, errorMessage = null) }
        }

        override fun onCloudRouteClick() {
            form.update { it.copy(route = LockRoute.CLOUD, errorMessage = null) }
        }

        override fun onKeyTextChange(value: String) {
            form.update { it.copy(keyText = value, errorMessage = null) }
        }

        override fun onDeviceUuidChange(value: String) {
            form.update { it.copy(deviceUuid = value, errorMessage = null) }
        }

        override fun onApiKeyChange(value: String) {
            form.update { it.copy(apiKey = value, errorMessage = null) }
        }

        override fun onSecretKeyChange(value: String) {
            form.update { it.copy(secretKey = value, errorMessage = null) }
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
        bleInput = if (form.route == LockRoute.BLE) AddLockUiState.BleInput(form.keyText) else null,
        cloudInput = if (form.route == LockRoute.CLOUD) {
            AddLockUiState.CloudInput(
                deviceUuid = form.deviceUuid,
                apiKey = form.apiKey,
                secretKey = form.secretKey,
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
        errorMessage = form.errorMessage,
        saveButtonEnabled = !form.saving && !form.closeRequested,
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
                form.update { it.copy(errorMessage = parsed.message) }
                return
            }

            is Parsed.Lock -> parsed.lock
        }
        form.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            when (repository.add(lock)) {
                AddLockResult.ADDED -> form.update { it.clearedSecrets().copy(saving = false, closeRequested = true) }
                AddLockResult.ALREADY_REGISTERED -> form.update { it.copy(saving = false, errorMessage = "この SESAME は登録済みです。既存の鍵は変更していません") }
                AddLockResult.NOT_READY -> form.update { it.copy(saving = false, errorMessage = "保存領域を読み込めていません") }
            }
        }
    }

    private fun parseBle(form: Form): Parsed = when (val result = SesameKeyImporter.import(form.keyText)) {
        is KeyImportResult.Failure -> Parsed.Error(importErrorMessage(result.error))

        is KeyImportResult.Success -> {
            val id = LockId.fromDeviceUuid(result.credential.deviceUuid)
            if (id == null) {
                Parsed.Error(importErrorMessage(KeyImportError.INVALID_UUID))
            } else {
                val name = form.name.trim().ifEmpty { result.suggestedName ?: result.credential.model.displayName }
                Parsed.Lock(RegisteredLock(id, name, result.credential))
            }
        }
    }

    private fun parseCloud(form: Form): Parsed {
        val id = LockId.fromDeviceUuid(form.deviceUuid.trim()) ?: return Parsed.Error("UUID の形式が正しくありません")
        val secret = form.secretKey.trim().lowercase(Locale.ROOT)
        if (!secretKeyPattern.matches(secret)) return Parsed.Error("秘密鍵は 32 桁の 16 進数で入力してください")
        val apiKey = form.apiKey.trim()
        if (apiKey.isEmpty()) return Parsed.Error("API キーを入力してください")
        val credential = LockCredential.Cloud(
            deviceUuid = id.value,
            model = form.cloudModel,
            apiKey = apiKey,
            secretKeyHex = secret,
        )
        val name = form.name.trim().ifEmpty { form.cloudModel.displayName }
        return Parsed.Lock(RegisteredLock(id, name, credential))
    }

    private fun importErrorMessage(error: KeyImportError): String = when (error) {
        KeyImportError.EMPTY -> "鍵データを入力してください"
        KeyImportError.UNRECOGNIZED_FORMAT -> "対応していない形式です。従来形式の共有 QR の URL（ssm://...）か、鍵の JSON を入力してください"
        KeyImportError.ENCRYPTED_TOKEN_QR -> "暗号化された共有 QR（10 分間有効の形式）は読み込めません。公式アプリで従来形式の QR を発行してください"
        KeyImportError.INVALID_ENCODING -> "鍵データを読み取れませんでした"
        KeyImportError.UNSUPPORTED_MODEL -> "この機種には対応していません（対応: SESAME 3 / 4 / 5 / 6 / miwa 系のドア錠）"
        KeyImportError.INVALID_UUID -> "UUID の形式が正しくありません"
        KeyImportError.INVALID_KEY_FORMAT -> "鍵の形式が正しくありません"
        KeyImportError.SERVER_SIGNED_GUEST_KEY -> "サーバー署名が必要なゲスト鍵です。オーナーまたはマネージャーの鍵を使ってください"
    }

    private sealed interface Parsed {
        data class Lock(val lock: RegisteredLock) : Parsed

        data class Error(val message: String) : Parsed
    }

    private data class Form(
        val name: String,
        val route: LockRoute,
        val keyText: String,
        val deviceUuid: String,
        val apiKey: String,
        val secretKey: String,
        val cloudModel: SesameModel,
        val errorMessage: String?,
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

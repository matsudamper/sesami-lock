package net.matsudamper.sesamilock.ui.edit

import androidx.compose.runtime.Immutable

@Immutable
internal data class EditLockUiState(
    val content: Content,
    val closeRequested: Boolean,
    val listener: Listener,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data class Form(
            val name: String,
            val nameError: String?,
            val routeLabel: String,
            val readOnlyDeviceUuid: String?,
            val cloudInput: CloudInput?,
            val generalErrorMessage: String?,
            val saveButtonEnabled: Boolean,
        ) : Content
    }

    @Immutable
    data class CloudInput(
        val deviceUuid: String,
        val deviceUuidError: String?,
        val apiKey: String,
        val apiKeyError: String?,
        val secretKey: String,
        val secretKeyError: String?,
        val selectedModelLabel: String,
        val models: List<ModelOption>,
    )

    @Immutable
    data class ModelOption(
        val label: String,
        val listener: ModelOptionListener,
    )

    interface Listener {
        fun onNameChange(value: String)

        fun onDeviceUuidChange(value: String)

        fun onApiKeyChange(value: String)

        fun onSecretKeyChange(value: String)

        fun onSaveClick()

        fun onBackClick()
    }

    interface ModelOptionListener {
        fun onClick()
    }
}

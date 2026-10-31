package net.matsudamper.sesamilock.ui.add

import androidx.compose.runtime.Immutable

@Immutable
internal data class AddLockUiState(
    val name: String,
    val bleRouteSelected: Boolean,
    val bleInput: BleInput?,
    val cloudInput: CloudInput?,
    val errorMessage: String?,
    val saveButtonEnabled: Boolean,
    val closeRequested: Boolean,
    val listener: Listener,
) {
    @Immutable
    data class BleInput(
        val keyText: String,
    )

    @Immutable
    data class CloudInput(
        val deviceUuid: String,
        val apiKey: String,
        val secretKey: String,
        val models: List<ModelOption>,
    )

    @Immutable
    data class ModelOption(
        val label: String,
        val selected: Boolean,
        val listener: ModelOptionListener,
    )

    interface Listener {
        fun onNameChange(value: String)

        fun onBleRouteClick()

        fun onCloudRouteClick()

        fun onKeyTextChange(value: String)

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

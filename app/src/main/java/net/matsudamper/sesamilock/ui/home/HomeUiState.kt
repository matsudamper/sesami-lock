package net.matsudamper.sesamilock.ui.home

import androidx.compose.runtime.Immutable

@Immutable
internal data class HomeUiState(
    val content: Content,
    val permissionBanner: PermissionBanner?,
    val deleteConfirmation: DeleteConfirmation?,
    val listener: Listener,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data class Unrecoverable(val listener: UnrecoverableListener) : Content

        data object Empty : Content

        data class Locks(val cards: List<LockCard>) : Content
    }

    @Immutable
    data class LockCard(
        val name: String,
        val description: String,
        val stateLabel: String,
        val stateTone: Tone,
        val observedAtLabel: String,
        val batteryWarningVisible: Boolean,
        val operationMessage: String?,
        val operationTone: Tone,
        val operationProgressVisible: Boolean,
        val dismissOperationVisible: Boolean,
        val registrationErrorVisible: Boolean,
        val lockButtonEnabled: Boolean,
        val unlockButtonEnabled: Boolean,
        val listener: LockCardListener,
    )

    enum class Tone {
        NEUTRAL,
        LOCKED,
        UNLOCKED,
        WARNING,
    }

    @Immutable
    data class PermissionBanner(
        val requiredPermissions: List<String>,
        val listener: PermissionListener,
    )

    @Immutable
    data class DeleteConfirmation(
        val lockName: String,
        val listener: DeleteConfirmationListener,
    )

    interface Listener {
        fun onResume()
    }

    interface UnrecoverableListener {
        fun onDeleteAllClick()
    }

    interface LockCardListener {
        fun onLockClick()

        fun onUnlockLongPress()

        fun onRefreshClick()

        fun onDismissOperationClick()

        fun onDeleteClick()
    }

    interface PermissionListener {
        fun onPermissionResult()
    }

    interface DeleteConfirmationListener {
        fun onConfirmClick()

        fun onCancel()
    }
}

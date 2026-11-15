package net.matsudamper.sesamilock.ui.widget

import androidx.compose.runtime.Immutable

@Immutable
internal data class LockWidgetConfigureUiState(
    val content: Content,
    val completed: Boolean,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        data class Locks(val items: List<LockItem>) : Content
    }

    @Immutable
    data class LockItem(
        val name: String,
        val routeLabel: String,
        val enabled: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }
}

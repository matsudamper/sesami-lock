package net.matsudamper.sesamilock.ui.widget

import androidx.annotation.ColorRes
import androidx.compose.runtime.Immutable

@Immutable
internal data class LockWidgetConfigureUiState(
    val content: Content,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        data class Locks(
            val stylePreview: StylePreview,
            val colorOptions: List<StyleOption>,
            val opacityOptions: List<StyleOption>,
            val items: List<LockItem>,
        ) : Content
    }

    @Immutable
    data class StylePreview(
        @param:ColorRes val backgroundColorRes: Int,
        @param:ColorRes val outlineColorRes: Int,
        @param:ColorRes val nameColorRes: Int,
        @param:ColorRes val statusColorRes: Int,
    )

    @Immutable
    data class StyleOption(
        val label: String,
        val selected: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }

    @Immutable
    data class LockItem(
        val name: String,
        val routeLabel: String,
        val selected: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }
}

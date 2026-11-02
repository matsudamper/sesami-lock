package net.matsudamper.sesamilock.ui.diagnostics

import androidx.compose.runtime.Immutable

@Immutable
internal data class DiagnosticsUiState(
    val items: List<Item>,
    val shareText: String?,
) {
    @Immutable
    data class Item(
        val title: String,
        val timings: String,
        val result: String,
    )
}

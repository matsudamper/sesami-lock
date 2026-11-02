package net.matsudamper.sesamilock.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.matsudamper.sesamilock.sesame.core.diagnostics.DiagnosticRecord
import net.matsudamper.sesamilock.sesame.core.diagnostics.DiagnosticsRecorder
import net.matsudamper.sesamilock.ui.home.LockTexts

internal class DiagnosticsViewModel(
    private val recorder: DiagnosticsRecorder,
) : ViewModel() {
    val uiState: StateFlow<DiagnosticsUiState> = recorder.records.map(::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(recorder.records.value),
    )

    private fun buildUiState(records: List<DiagnosticRecord>): DiagnosticsUiState = DiagnosticsUiState(
        items = records.map { record ->
            DiagnosticsUiState.Item(
                title = "${LockTexts.time(record.startedAtWallMillis)} ${record.intent.name} ${LockTexts.route(record.route)} [${record.lockIdHash}]",
                timings = record.elapsedMillisByPoint.entries
                    .sortedBy { it.value }
                    .joinToString(" / ") { (point, millis) -> "${point.key}=${millis}ms" },
                result = listOfNotNull(record.dispatchResult, record.outcome, record.error).joinToString(" / ").ifEmpty { "進行中" },
            )
        },
        shareText = if (records.isEmpty()) null else recorder.exportJson(),
    )
}

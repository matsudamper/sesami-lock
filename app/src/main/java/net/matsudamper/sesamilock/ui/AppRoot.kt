package net.matsudamper.sesamilock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.matsudamper.sesamilock.AppGraph
import net.matsudamper.sesamilock.BlePermissions
import net.matsudamper.sesamilock.ui.add.AddLockScreen
import net.matsudamper.sesamilock.ui.add.AddLockViewModel
import net.matsudamper.sesamilock.ui.diagnostics.DiagnosticsScreen
import net.matsudamper.sesamilock.ui.diagnostics.DiagnosticsViewModel
import net.matsudamper.sesamilock.ui.edit.EditLockScreen
import net.matsudamper.sesamilock.ui.edit.EditLockViewModel
import net.matsudamper.sesamilock.ui.home.HomeScreen
import net.matsudamper.sesamilock.ui.home.HomeViewModel
import net.matsudamper.sesamilock.ui.settings.SettingsScreen
import net.matsudamper.sesamilock.ui.settings.SettingsViewModel
import net.matsudamper.sesamilock.ui.transfer.ImportTransferScreen
import net.matsudamper.sesamilock.ui.transfer.ImportTransferViewModel

private enum class Destination {
    HOME,
    ADD_LOCK,
    IMPORT_TRANSFER,
    DIAGNOSTICS,
    SETTINGS,
    EDIT_LOCK,
}

@Composable
internal fun AppRoot(
    graph: AppGraph,
    blePermissions: BlePermissions,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var addLockVisit by rememberSaveable { mutableIntStateOf(0) }
    var importTransferVisit by rememberSaveable { mutableIntStateOf(0) }
    var editLockVisit by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = destination == Destination.DIAGNOSTICS || destination == Destination.SETTINGS) {
        destination = Destination.HOME
    }
    when (destination) {
        Destination.HOME -> {
            val viewModel = viewModel {
                HomeViewModel(
                    repository = graph.repository,
                    transports = graph.transports,
                    coordinator = graph.coordinator,
                    clock = graph.clock,
                    registrySync = graph.registrySync,
                    blePermissions = blePermissions,
                    editLockSelection = graph.editLockSelection,
                )
            }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(uiState.editScreenRequested) {
                if (uiState.editScreenRequested) {
                    uiState.listener.onEditScreenOpened()
                    editLockVisit += 1
                    destination = Destination.EDIT_LOCK
                }
            }
            HomeScreen(
                uiState = uiState,
                onAddFromSesameAppClick = {
                    addLockVisit += 1
                    destination = Destination.ADD_LOCK
                },
                onAddFromThisAppClick = {
                    importTransferVisit += 1
                    destination = Destination.IMPORT_TRANSFER
                },
                onDiagnosticsClick = { destination = Destination.DIAGNOSTICS },
                onSettingsClick = { destination = Destination.SETTINGS },
                modifier = modifier,
            )
        }

        Destination.ADD_LOCK -> {
            val viewModel = viewModel(key = "add-lock-$addLockVisit") { AddLockViewModel(graph.repository, graph.qrImageDecoder) }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AddLockScreen(
                uiState = uiState,
                onClosed = { destination = Destination.HOME },
                modifier = modifier,
            )
        }

        Destination.IMPORT_TRANSFER -> {
            val viewModel = viewModel(key = "import-transfer-$importTransferVisit") { ImportTransferViewModel(graph.repository, graph.qrImageDecoder) }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ImportTransferScreen(
                uiState = uiState,
                onClosed = { destination = Destination.HOME },
                modifier = modifier,
            )
        }

        Destination.DIAGNOSTICS -> {
            val viewModel = viewModel { DiagnosticsViewModel(graph.diagnostics) }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            DiagnosticsScreen(
                uiState = uiState,
                onBack = { destination = Destination.HOME },
                modifier = modifier,
            )
        }

        Destination.SETTINGS -> {
            val viewModel = viewModel { SettingsViewModel(graph.nfcOpenAppTagWriter) }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                uiState = uiState,
                onBack = { destination = Destination.HOME },
                modifier = modifier,
            )
        }

        Destination.EDIT_LOCK -> {
            val lockId = graph.editLockSelection.lockId
            if (lockId == null) {
                LaunchedEffect(Unit) { destination = Destination.HOME }
            } else {
                val viewModel = viewModel(key = "edit-lock-$editLockVisit") { EditLockViewModel(graph.repository, graph.coordinator, graph.qrImageDecoder, lockId) }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                EditLockScreen(
                    uiState = uiState,
                    onClosed = { destination = Destination.HOME },
                    modifier = modifier,
                )
            }
        }
    }
}

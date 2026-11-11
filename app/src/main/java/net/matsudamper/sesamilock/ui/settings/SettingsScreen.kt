package net.matsudamper.sesamilock.ui.settings

import android.nfc.NfcAdapter
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.sesamilock.R

private const val LatestReleaseUrl = "https://github.com/matsudamper/sesami-lock/releases/latest"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    NfcReaderModeEffect(
        enabled = uiState.nfcReaderModeEnabled,
        listener = uiState.listener,
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(LatestReleaseUrl) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("最新版をダウンロード", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "GitHub のリリースページを開きます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uiState.listener.onWriteNfcTagClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("アプリを開く NFC タグを書き込む", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "書き込んだタグをかざすとこのアプリが開きます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val nfcWriteDialog = uiState.nfcWriteDialog
    if (nfcWriteDialog != null) {
        NfcWriteDialog(
            dialog = nfcWriteDialog,
            onDismiss = { uiState.listener.onNfcWriteDialogDismiss() },
        )
    }
}

@Composable
private fun NfcWriteDialog(
    dialog: SettingsUiState.NfcWriteDialog,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(dialog.message)
                if (dialog.progressVisible) {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(dialog.dismissButtonText) }
        },
    )
}

@Composable
private fun NfcReaderModeEffect(
    enabled: Boolean,
    listener: SettingsUiState.Listener,
) {
    val activity = LocalActivity.current
    val currentListener by rememberUpdatedState(listener)
    DisposableEffect(activity, enabled) {
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (activity == null || adapter == null || !enabled) {
            onDispose { }
        } else {
            adapter.enableReaderMode(
                activity,
                { tag -> currentListener.onNfcTagDiscovered(tag) },
                NfcReaderFlags,
                null,
            )
            onDispose { adapter.disableReaderMode(activity) }
        }
    }
}

private const val NfcReaderFlags = NfcAdapter.FLAG_READER_NFC_A or
    NfcAdapter.FLAG_READER_NFC_B or
    NfcAdapter.FLAG_READER_NFC_F or
    NfcAdapter.FLAG_READER_NFC_V

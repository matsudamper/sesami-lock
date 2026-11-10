package net.matsudamper.sesamilock.ui.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.sesamilock.R
import net.matsudamper.sesamilock.ui.component.QrImageSourceButtons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportTransferScreen(
    uiState: ImportTransferUiState,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnClosed = rememberUpdatedState(onClosed)
    LaunchedEffect(uiState.closeRequested) {
        if (uiState.closeRequested) currentOnClosed.value()
    }
    BackHandler { uiState.listener.onBackClick() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("同じアプリからインポート") },
                navigationIcon = {
                    IconButton(onClick = { uiState.listener.onBackClick() }) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "他の端末のこのアプリで、鍵のメニューの「共有」から表示した QR コードをカメラで読み取るか、スクリーンショットを選んでください。",
                style = MaterialTheme.typography.bodyMedium,
            )
            QrImageSourceButtons(
                enabled = uiState.readButtonEnabled,
                progressVisible = uiState.readProgressVisible,
                onImagePicked = { uiState.listener.onQrImagePicked(it) },
                onQrScanned = { uiState.listener.onQrScanned(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.preview?.let { preview ->
                OutlinedTextField(
                    value = preview.name,
                    onValueChange = { uiState.listener.onNameChange(it) },
                    label = { Text("表示名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ReadOnlyItem(label = "通信経路", value = preview.routeLabel)
                ReadOnlyItem(label = "機種", value = preview.modelLabel)
            }
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { uiState.listener.onSaveClick() },
                enabled = uiState.saveButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("登録")
            }
        }
    }
}

@Composable
private fun ReadOnlyItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

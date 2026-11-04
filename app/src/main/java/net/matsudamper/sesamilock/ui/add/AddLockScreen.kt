package net.matsudamper.sesamilock.ui.add

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.sesamilock.R
import net.matsudamper.sesamilock.ui.component.SecretTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddLockScreen(
    uiState: AddLockUiState,
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
                title = { Text("鍵を追加") },
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
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { uiState.listener.onNameChange(it) },
                label = { Text("表示名（省略可）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("通信経路", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.bleRouteSelected,
                        onClick = { uiState.listener.onBleRouteClick() },
                        label = { Text("Bluetooth") },
                    )
                    FilterChip(
                        selected = uiState.cloudRouteSelected,
                        onClick = { uiState.listener.onCloudRouteClick() },
                        label = { Text("クラウド") },
                    )
                }
            }
            uiState.bleInput?.let { BleInputContent(it, uiState.listener) }
            uiState.cloudInput?.let { CloudInputContent(it, uiState.listener) }
            uiState.generalErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
private fun BleInputContent(input: AddLockUiState.BleInput, listener: AddLockUiState.Listener, modifier: Modifier = Modifier) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) listener.onQrImagePicked(uri)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "公式アプリの「鍵をシェア」で表示した QR コードのスクリーンショットを選ぶか、QR の内容を貼り付けてください。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = input.qrPickButtonEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (input.qrReadingProgressVisible) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painter = painterResource(R.drawable.ic_qr_code_scanner), contentDescription = null)
                }
            }
            Text("画像から QR コードを読み込む", modifier = Modifier.padding(start = 8.dp))
        }
        SecretTextField(
            value = input.keyText,
            onValueChange = { listener.onKeyTextChange(it) },
            label = "鍵データ",
            singleLine = false,
            errorMessage = input.keyError,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )
    }
}

@Composable
private fun CloudInputContent(input: AddLockUiState.CloudInput, listener: AddLockUiState.Listener, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Hub 3 経由で操作します。SESAME Biz の開発者ページで発行した API キーと、SESAME の UUID・秘密鍵を入力してください。",
            style = MaterialTheme.typography.bodyMedium,
        )
        ModelSelector(input = input)
        OutlinedTextField(
            value = input.deviceUuid,
            onValueChange = { listener.onDeviceUuidChange(it) },
            label = { Text("UUID") },
            singleLine = true,
            isError = input.deviceUuidError != null,
            supportingText = input.deviceUuidError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SecretTextField(
            value = input.apiKey,
            onValueChange = { listener.onApiKeyChange(it) },
            label = "API キー",
            singleLine = true,
            errorMessage = input.apiKeyError,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretTextField(
            value = input.secretKey,
            onValueChange = { listener.onSecretKeyChange(it) },
            label = "秘密鍵",
            singleLine = true,
            errorMessage = input.secretKeyError,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelSelector(input: AddLockUiState.CloudInput, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input.selectedModelLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("機種") },
            trailingIcon = { Icon(painter = painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            input.models.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        option.listener.onClick()
                        expanded = false
                    },
                )
            }
        }
    }
}

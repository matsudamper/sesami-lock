package net.matsudamper.sesamilock.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import net.matsudamper.sesamilock.ui.component.QrImageSourceButtons
import net.matsudamper.sesamilock.ui.component.SecretTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditLockScreen(
    uiState: EditLockUiState,
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
                title = { Text("鍵を編集") },
                navigationIcon = {
                    IconButton(onClick = { uiState.listener.onBackClick() }) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val content = uiState.content) {
            EditLockUiState.Content.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is EditLockUiState.Content.Form -> FormContent(
                form = content,
                listener = uiState.listener,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun FormContent(form: EditLockUiState.Content.Form, listener: EditLockUiState.Listener, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { listener.onNameChange(it) },
            label = { Text("表示名") },
            singleLine = true,
            isError = form.nameError != null,
            supportingText = form.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        ReadOnlyItem(label = "通信経路", value = form.routeLabel)
        form.bleInput?.let { BleInputContent(it, listener) }
        form.cloudInput?.let { CloudInputContent(it, listener) }
        form.generalErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { listener.onSaveClick() },
            enabled = form.saveButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("保存")
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

@Composable
private fun BleInputContent(input: EditLockUiState.BleInput, listener: EditLockUiState.Listener, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QrImageSourceButtons(
            enabled = input.qrPickButtonEnabled,
            progressVisible = input.qrReadingProgressVisible,
            onImagePicked = { listener.onQrImagePicked(it) },
            onQrScanned = { listener.onQrScanned(it) },
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun CloudInputContent(input: EditLockUiState.CloudInput, listener: EditLockUiState.Listener, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ModelSelector(input: EditLockUiState.CloudInput, modifier: Modifier = Modifier) {
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

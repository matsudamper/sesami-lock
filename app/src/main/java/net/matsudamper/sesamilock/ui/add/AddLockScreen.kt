package net.matsudamper.sesamilock.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { uiState.listener.onBackClick() }) { Text("戻る") }
                Text("鍵を追加", style = MaterialTheme.typography.headlineSmall)
            }
            SafetyNotice()
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { uiState.listener.onNameChange(it) },
                label = { Text("表示名（省略可）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("通信経路", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.bleRouteSelected,
                    onClick = { uiState.listener.onBleRouteClick() },
                    label = { Text("BLE（推奨）") },
                )
                FilterChip(
                    selected = !uiState.bleRouteSelected,
                    onClick = { uiState.listener.onCloudRouteClick() },
                    label = { Text("クラウド（Hub 3 + Biz API）") },
                )
            }
            uiState.bleInput?.let { BleInputContent(it, uiState.listener) }
            uiState.cloudInput?.let { CloudInputContent(it, uiState.listener) }
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { uiState.listener.onSaveClick() },
                enabled = uiState.saveButtonEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun SafetyNotice(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("登録前の確認", style = MaterialTheme.typography.titleMedium)
            Text("・このアプリには自動解錠・接近解錠・通知からの解錠はありません。公式アプリの手ぶら解錠・オートロック設定は別に管理してください。")
            Text("・最初の動作確認は、ドアを開けた状態で物理鍵を手元に置いて行ってください。")
            Text("・施錠位置・解錠位置の設定は変更しません。公式アプリで正しく校正されていることを確認してください。")
            Text("・鍵情報はこの端末内で暗号化して保存し、バックアップには含めません。")
            Text("・BLE で必要なのはオーナー / マネージャーの鍵です。サーバー署名が必要なゲスト鍵は使えません。")
        }
    }
}

@Composable
private fun BleInputContent(input: AddLockUiState.BleInput, listener: AddLockUiState.Listener, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("対応機種: SESAME 3 / 4 / 5 / 5 Pro / 5 US / 6 / 6 Pro / 6 Pro SlidingDoor / miwa")
        Text(
            "公式アプリの「鍵をシェア」で従来形式の QR を発行し、その URL（ssm://UI?t=sk&...）を貼り付けてください。" +
                "エクスポート済みの鍵 JSON も読み込めます。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input.keyText,
            onValueChange = { listener.onKeyTextChange(it) },
            label = { Text("鍵データ") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
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
            "SESAME Biz 開発者ページで発行した本人用の API キーと、SESAME の秘密鍵を入力してください。BLE が失敗してもクラウドに自動で切り替えることはありません。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input.deviceUuid,
            onValueChange = { listener.onDeviceUuidChange(it) },
            label = { Text("SESAME の UUID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = input.apiKey,
            onValueChange = { listener.onApiKeyChange(it) },
            label = { Text("API キー") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = input.secretKey,
            onValueChange = { listener.onSecretKeyChange(it) },
            label = { Text("秘密鍵（32 桁の 16 進数）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("機種", style = MaterialTheme.typography.titleMedium)
        input.models.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option.selected, role = Role.RadioButton, onClick = { option.listener.onClick() }),
            ) {
                RadioButton(selected = option.selected, onClick = null)
                Text(option.label, color = if (option.selected) MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        }
    }
}

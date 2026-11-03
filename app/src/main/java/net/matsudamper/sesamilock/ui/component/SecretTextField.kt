package net.matsudamper.sesamilock.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import net.matsudamper.sesamilock.R

@Composable
internal fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    painter = painterResource(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (revealed) "隠す" else "表示する",
                )
            }
        },
        modifier = modifier,
    )
}

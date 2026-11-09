package net.matsudamper.sesamilock.ui.component

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import java.io.File
import net.matsudamper.sesamilock.R

private enum class QrImageSource {
    CAMERA,
    GALLERY,
}

/**
 * QR コードの画像をカメラで撮影するか、端末の画像から選ぶボタン。
 * 撮影画像には鍵の秘密情報が写るため、アプリ専用キャッシュの 1 ファイルに上書きし、画面を離れたら消す。
 * 撮影中に回転などで Activity が作り直されるときは、戻ってきた撮影結果を読めるよう残す。
 */
@Composable
internal fun QrImageSourceButtons(
    enabled: Boolean,
    progressVisible: Boolean,
    onImageReady: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var lastSource by rememberSaveable { mutableStateOf(QrImageSource.GALLERY) }
    val captureUri = remember(context) { qrCaptureUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) onImageReady(captureUri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImageReady(uri)
    }
    val activity = LocalActivity.current
    DisposableEffect(context, activity) {
        onDispose {
            if (!activity.willBeRecreated()) qrCaptureFile(context).delete()
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceButton(
            label = "カメラで撮影して読み込む",
            iconRes = R.drawable.ic_photo_camera,
            enabled = enabled,
            progressVisible = progressVisible && lastSource == QrImageSource.CAMERA,
            onClick = {
                lastSource = QrImageSource.CAMERA
                try {
                    cameraLauncher.launch(captureUri)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "カメラアプリが見つかりません", Toast.LENGTH_SHORT).show()
                }
            },
        )
        SourceButton(
            label = "画像から QR コードを読み込む",
            iconRes = R.drawable.ic_qr_code_scanner,
            enabled = enabled,
            progressVisible = progressVisible && lastSource == QrImageSource.GALLERY,
            onClick = {
                lastSource = QrImageSource.GALLERY
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

@Composable
private fun SourceButton(
    label: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    progressVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (progressVisible) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(painter = painterResource(iconRes), contentDescription = null)
            }
        }
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun Activity?.willBeRecreated(): Boolean {
    val lifecycle = (this as? ComponentActivity)?.lifecycle ?: return false
    return lifecycle.currentState == Lifecycle.State.DESTROYED && !isFinishing
}

private fun qrCaptureFile(context: Context): File = File(File(context.cacheDir, "qr_capture"), "capture.jpg")

private fun qrCaptureUri(context: Context): Uri {
    val file = qrCaptureFile(context)
    file.parentFile?.mkdirs()
    return FileProvider.getUriForFile(context, "${context.packageName}.qrcapture", file)
}

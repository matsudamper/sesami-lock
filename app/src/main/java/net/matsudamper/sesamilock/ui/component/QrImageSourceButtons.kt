package net.matsudamper.sesamilock.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlinx.coroutines.awaitCancellation
import net.matsudamper.sesamilock.QrFrameAnalyzer
import net.matsudamper.sesamilock.R

private enum class CameraAvailability {
    CHECKING,
    READY,
    PERMISSION_DENIED,
    UNAVAILABLE,
}

/**
 * QR コードをカメラでリアルタイムに読み取るか、端末の画像から選ぶボタン。
 */
@Composable
internal fun QrImageSourceButtons(
    enabled: Boolean,
    progressVisible: Boolean,
    onImagePicked: (Uri) -> Unit,
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scannerVisible by rememberSaveable { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceButton(
            label = "カメラで QR コードを読み取る",
            iconRes = R.drawable.ic_photo_camera,
            enabled = enabled,
            progressVisible = false,
            onClick = { scannerVisible = true },
        )
        SourceButton(
            label = "画像から QR コードを読み込む",
            iconRes = R.drawable.ic_qr_code_scanner,
            enabled = enabled,
            progressVisible = progressVisible,
            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
    if (scannerVisible) {
        QrScannerDialog(
            onScanned = { text ->
                scannerVisible = false
                onQrScanned(text)
            },
            onDismiss = { scannerVisible = false },
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

@Composable
private fun QrScannerDialog(onScanned: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var availability by remember {
        mutableStateOf(if (context.hasCameraPermission()) CameraAvailability.READY else CameraAvailability.CHECKING)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        availability = if (granted) CameraAvailability.READY else CameraAvailability.PERMISSION_DENIED
    }
    LaunchedEffect(Unit) {
        if (availability == CameraAvailability.CHECKING) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when (availability) {
                CameraAvailability.CHECKING -> Unit

                CameraAvailability.READY -> QrCameraPreview(
                    onScanned = onScanned,
                    onUnavailable = { availability = CameraAvailability.UNAVAILABLE },
                    modifier = Modifier.fillMaxSize(),
                )

                CameraAvailability.PERMISSION_DENIED -> ScannerMessage("カメラの権限がないため読み取れません。設定からカメラを許可してください")

                CameraAvailability.UNAVAILABLE -> ScannerMessage("カメラを起動できませんでした")
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "閉じる", tint = Color.White)
                }
                Box(modifier = Modifier.weight(1f))
                Text(
                    "QR コードを画面に映してください",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScannerMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QrCameraPreview(onScanned: (String) -> Unit, onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val previewView = remember(context) { PreviewView(context) }
    LaunchedEffect(lifecycleOwner, previewView) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(ANALYSIS_RESOLUTION, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
            .apply {
                setAnalyzer(analysisExecutor, QrFrameAnalyzer { text -> mainExecutor.execute { currentOnScanned(text) } })
            }
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            currentOnUnavailable()
        } finally {
            provider.unbind(preview, analysis)
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private val ANALYSIS_RESOLUTION = Size(1280, 720)

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

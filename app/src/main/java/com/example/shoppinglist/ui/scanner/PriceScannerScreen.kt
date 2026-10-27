package com.example.shoppinglist.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.ocr.PriceCandidate
import com.example.shoppinglist.ocr.PriceTagAnalyzer
import com.example.shoppinglist.ui.common.formatLei
import java.util.concurrent.Executors

private const val TAG = "PriceScannerScreen"

@Composable
fun PriceScannerScreen(
    viewModel: PriceScannerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val item by viewModel.item.collectAsState()
    val catalogPrice by viewModel.catalogPrice.collectAsState()
    val detectedPrice by viewModel.detectedPrice.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    LaunchedEffect(camera, torchOn) {
        camera?.let { bound ->
            if (bound.cameraInfo.hasFlashUnit()) bound.cameraControl.enableTorch(torchOn)
        }
    }

    var manualDialogOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onFrameResult = viewModel::onFrameResult,
                onCameraBound = { bound ->
                    camera = bound
                    cameraError = null
                },
                onCameraError = { message ->
                    camera = null
                    cameraError = message
                },
            )
            ScannerOverlay(modifier = Modifier.fillMaxSize())
        } else {
            PermissionRequest(
                modifier = Modifier.align(Alignment.Center),
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
        ) {
            Text("Înapoi", color = Color.White)
        }

        ScannerPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            item = item,
            catalogPrice = catalogPrice,
            detectedPrice = detectedPrice,
            cameraError = cameraError,
            saveError = saveError,
            hasFlash = hasFlash,
            torchOn = torchOn,
            onToggleTorch = { torchOn = !torchOn },
            onSave = { detectedPrice?.let { viewModel.saveShelfPrice(it, onSaved = onClose) } },
            onOpenManual = { manualDialogOpen = true },
        )
    }

    if (manualDialogOpen) {
        ManualPriceDialog(
            initialPrice = detectedPrice,
            onDismiss = { manualDialogOpen = false },
            onConfirm = { price ->
                manualDialogOpen = false
                viewModel.saveShelfPrice(price, onSaved = onClose)
            },
        )
    }
}

/** Dialog cu câmp numeric pentru prețul introdus manual, ex. "4.25" sau "4,25". */
@Composable
private fun ManualPriceDialog(
    initialPrice: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by rememberSaveable {
        mutableStateOf(initialPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    val parsed = parseManualPrice(text)
    val showError = text.isNotBlank() && parsed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preț raft") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Preț (lei), ex. 4.25") },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Text(
                        text = "Introdu un preț între 0.01 și 9999.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
            ) {
                Text("Salvează")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anulează")
            }
        },
    )
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    onFrameResult: (PriceCandidate?) -> Unit,
    onCameraBound: (Camera) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameResult by rememberUpdatedState(onFrameResult)
    val currentOnCameraBound by rememberUpdatedState(onCameraBound)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = PriceTagAnalyzer { candidate -> currentOnFrameResult(candidate) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }
                cameraProvider.unbindAll()
                val bound = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                currentOnCameraBound(bound)
            } catch (e: Exception) {
                Log.e(TAG, "Camera nu a putut fi pornită", e)
                currentOnCameraError("Camera nu a putut fi pornită.")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ScannerOverlay(modifier: Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color.Black.copy(alpha = 0.55f))
        val frameWidth = size.width * 0.8f
        val frameHeight = size.height * 0.22f
        val topLeft = Offset(
            x = (size.width - frameWidth) / 2f,
            y = (size.height - frameHeight) / 2f - size.height * 0.08f,
        )
        val frameSize = Size(frameWidth, frameHeight)
        val corner = CornerRadius(24.dp.toPx())
        drawRoundRect(
            color = Color.Transparent,
            topLeft = topLeft,
            size = frameSize,
            cornerRadius = corner,
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = Color.White,
            topLeft = topLeft,
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun PermissionRequest(
    modifier: Modifier,
    onRequest: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Aplicația are nevoie de acces la cameră ca să citească prețul de pe etichetă.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest) {
            Text("Permite camera")
        }
    }
}

@Composable
private fun ScannerPanel(
    modifier: Modifier,
    item: ShoppingItem?,
    catalogPrice: Double?,
    detectedPrice: Double?,
    cameraError: String?,
    saveError: String?,
    hasFlash: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onSave: () -> Unit,
    onOpenManual: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(targetState = detectedPrice, label = "detectedPrice") { price ->
                Text(
                    text = if (price != null) {
                        "Preț raft: ${price.formatLei()}"
                    } else {
                        "Îndreaptă camera spre eticheta de preț"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = price != null, onClick = onOpenManual),
                )
            }

            cameraError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            item?.let { current ->
                val reference = catalogPrice ?: current.estimatedPrice.takeIf { it > 0.0 }
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Estimat: ${reference?.formatLei() ?: "fără preț"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (detectedPrice != null && reference != null) {
                    val difference = detectedPrice - reference
                    val sign = if (difference > 0) "+" else ""
                    Text(
                        text = "Diferență: $sign${difference.formatLei()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = detectedPrice != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Salvează prețul la raft")
                }
                OutlinedButton(
                    onClick = onOpenManual,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("✏️ Introdu manual")
                }
            }

            saveError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            if (hasFlash) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleTorch) {
                        Text(if (torchOn) "Lanternă: oprește" else "Lanternă: pornește")
                    }
                }
            }
        }
    }
}

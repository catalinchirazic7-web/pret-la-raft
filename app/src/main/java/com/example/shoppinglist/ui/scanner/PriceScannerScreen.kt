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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.data.TargetStore
import com.example.shoppinglist.ocr.PriceTagAnalyzer
import com.example.shoppinglist.ocr.ScanResult
import com.example.shoppinglist.ocr.StablePrice
import com.example.shoppinglist.ui.common.formatLei
import java.util.Locale
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
    val detected by viewModel.detected.collectAsState()
    val cardOpen by viewModel.cardOpen.collectAsState()
    val cardId by viewModel.cardId.collectAsState()
    val sessionCount by viewModel.sessionCount.collectAsState()
    val store by viewModel.store.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
    val recentCart by viewModel.recentCart.collectAsState()
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

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            ScannerPanel(
                item = item,
                catalogPrice = catalogPrice,
                detected = detected,
                cameraError = cameraError,
                saveError = saveError,
                hasFlash = hasFlash,
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                onOpenCard = viewModel::openCard,
            )
            LiveCartBar(
                sessionCount = sessionCount,
                store = store,
                total = cartTotal,
                recent = recentCart,
                onSelectStore = viewModel::selectStore,
                onGoToList = onClose,
            )
        }

        if (cardOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                key(cardId) {
                    ConfirmationCard(
                        initialTitle = detected?.title.orEmpty(),
                        initialPrice = detected?.price?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                        onRescan = viewModel::rescan,
                        onConfirm = { title, price ->
                            viewModel.confirm(title, price, onDone = onClose)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    onFrameResult: (ScanResult) -> Unit,
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
        val analyzer = PriceTagAnalyzer { result -> currentOnFrameResult(result) }
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
    item: ShoppingItem?,
    catalogPrice: Double?,
    detected: StablePrice?,
    cameraError: String?,
    saveError: String?,
    hasFlash: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onOpenCard: () -> Unit,
) {
    val detectedPrice = detected?.price
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.clickable(enabled = price != null, onClick = onOpenCard),
                )
            }

            detected?.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            cameraError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            item?.let { current ->
                val reference = catalogPrice ?: current.estimatedPrice.takeIf { it > 0.0 }
                Text(
                    text = "${current.title} · Estimat: ${reference?.formatLei() ?: "fără preț"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (detectedPrice != null && reference != null) {
                    val difference = detectedPrice - reference
                    val sign = if (difference > 0) "+" else ""
                    Text(
                        text = "Diferență: $sign${difference.formatLei()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            saveError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onOpenCard,
                    modifier = Modifier.semantics { contentDescription = "Introducere manuală preț" },
                ) {
                    Text("✏️ Introdu manual")
                }
                if (hasFlash) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onToggleTorch) {
                        Text(if (torchOn) "Lanternă: oprește" else "Lanternă: pornește")
                    }
                }
            }
        }
    }
}

/** Cardul de confirmare: denumirea și prețul pot fi corectate înainte de salvarea în coș. */
@Composable
private fun ConfirmationCard(
    initialTitle: String,
    initialPrice: String,
    onRescan: () -> Unit,
    onConfirm: (String, Double) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var priceText by rememberSaveable { mutableStateOf(initialPrice) }
    val price = parseManualPrice(priceText)
    val titleValid = title.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Confirmă produsul",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Denumire produs") },
                singleLine = true,
                isError = !titleValid,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Preț (lei), ex. 4.25") },
                singleLine = true,
                isError = priceText.isNotBlank() && price == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
                    Text("Rescanează")
                }
                Button(
                    onClick = { price?.let { onConfirm(title.trim(), it) } },
                    enabled = titleValid && price != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Adaugă în coș")
                }
            }
        }
    }
}

/** Bara de jos cu totalul coșului pentru magazinul activ și ultimele produse adăugate. */
@Composable
private fun LiveCartBar(
    sessionCount: Int,
    store: TargetStore,
    total: Double,
    recent: List<ShoppingItem>,
    onSelectStore: (TargetStore) -> Unit,
    onGoToList: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xE6121417),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$sessionCount articole în coș",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                listOf(TargetStore.LIDL, TargetStore.PENNY).forEach { option ->
                    FilterChip(
                        selected = option == store,
                        onClick = { onSelectStore(option) },
                        label = { Text(option.displayName) },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Text(
                text = "Total: ${total.formatLei()}",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )

            if (recent.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recent.forEach { product ->
                        Text(
                            text = "${product.title} · ${product.estimatedPrice.formatLei()}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Button(onClick = onGoToList, modifier = Modifier.fillMaxWidth()) {
                Text("Mergi la listă")
            }
        }
    }
}

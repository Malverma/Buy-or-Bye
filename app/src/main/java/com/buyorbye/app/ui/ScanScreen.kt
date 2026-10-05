package com.buyorbye.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.buyorbye.app.data.ScanResult
import com.buyorbye.app.data.Scanner
import com.buyorbye.app.data.toUprightBitmap
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        cameraGranted = grants[Manifest.permission.CAMERA] == true || cameraGranted
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            launcher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            CameraCapture(scanner = vm.scanner, onResult = vm::onScanned)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Buy or Bye needs the camera to read barcodes and price tags.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (asked) {
                    Button(onClick = { launcher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Text("Allow camera") }
                }
                TextButton(onClick = vm::startManual) { Text("Search by name instead") }
            }
        }

        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { vm.navigate(Screen.History) }) {
                Icon(Icons.Outlined.History, contentDescription = "History", tint = Color.White)
            }
            IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraCapture(scanner: Scanner, onResult: (ScanResult) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Set once a result is delivered so the analyzer and shutter can't both fire.
    val done = remember { AtomicBoolean(false) }
    var busy by remember { mutableStateOf(false) }
    // Taps before the camera is bound would be silently dropped, so gate the shutter on this.
    var cameraReady by remember { mutableStateOf(false) }
    var barcodeSeen by remember { mutableStateOf(false) }

    fun deliver(result: ScanResult) {
        if (done.compareAndSet(false, true)) onResult(result)
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
        analysis.setAnalyzer(analysisExecutor, BarcodeAnalyzer(scanner, scope, done, onSeen = { barcodeSeen = true }) {
            deliver(it)
        })
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
            cameraReady = true
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            analysis.clearAnalyzer()
            runCatching { providerFuture.get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Framing guide
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .height(220.dp)
                .border(2.dp, if (barcodeSeen) BuyGreen else Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        )

        Column(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (barcodeSeen) "Barcode found…" else "Fit the product and its price tag in the frame",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(if (busy || !cameraReady) Color.Gray else Color.White, CircleShape)
                    .semantics { contentDescription = "Take photo" }
                    .let { m ->
                        if (busy || !cameraReady) m else m.then(
                            Modifier.clickableNoRipple {
                                busy = true
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            scope.launch {
                                                val media = image.image
                                                val photo = image.toUprightBitmap()
                                                val result = if (media != null) {
                                                    scanner.analyze(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees), photo = photo)
                                                } else {
                                                    ScanResult(null, null, emptyList(), photo)
                                                }
                                                image.close()
                                                deliver(result)
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            busy = false
                                        }
                                    },
                                )
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
            }
        }
    }
}

/**
 * Watches frames for a UPC/EAN barcode. A code must be read on two frames in a row
 * before it's accepted, which filters out partial misreads. The shelf tag is then
 * read from the same frame.
 */
@OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(
    private val scanner: Scanner,
    private val scope: CoroutineScope,
    private val done: AtomicBoolean,
    private val onSeen: () -> Unit,
    private val onResult: (ScanResult) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastCode: String? = null

    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (done.get() || media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scope.launch {
            try {
                val code = scanner.barcode(image)
                if (code != null) onSeen()
                if (code != null && code == lastCode && !done.get()) {
                    onResult(scanner.analyze(image, knownUpc = code, photo = proxy.toUprightBitmap()))
                }
                lastCode = code
            } finally {
                proxy.close()
            }
        }
    }
}

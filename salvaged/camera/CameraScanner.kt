package com.iqforge.camera

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.iqforge.ui.theme.*
import java.util.concurrent.Executors

/**
 * CameraX + Google ML Kit OCR scanner for "Point & Review" code scanning off laptop screens/monitors.
 */
@Composable
fun CameraScanScreen(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Point camera at code on your screen") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // CameraX Live Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        Log.e("CameraScanner", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder Scanner Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Close + Flash
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Surface.copy(alpha = 0.8f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextHi)
                }

                Text(
                    "SCAN CODE DIFF",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                    color = Accent
                )

                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        cameraControl?.enableTorch(isFlashOn)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Surface.copy(alpha = 0.8f))
                ) {
                    Icon(
                        Icons.Default.FlashOn,
                        contentDescription = "Flash",
                        tint = if (isFlashOn) Brass else TextLo
                    )
                }
            }

            // Viewfinder Target Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
                    .border(2.dp, Accent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = TextHi,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Bottom Actions: Capture Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Button(
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        isProcessing = true
                        statusText = "Analyzing code with ML Kit..."

                        val executor = Executors.newSingleThreadExecutor()
                        capture.takePicture(
                            executor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )

                                        recognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                val rawText = visionText.text
                                                imageProxy.close()
                                                isProcessing = false

                                                if (rawText.isNotBlank()) {
                                                    onCodeScanned(cleanScannedCode(rawText))
                                                } else {
                                                    statusText = "No code text detected. Try again."
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                imageProxy.close()
                                                isProcessing = false
                                                statusText = "OCR failed: ${e.localizedMessage}"
                                            }
                                    } else {
                                        imageProxy.close()
                                        isProcessing = false
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isProcessing = false
                                    statusText = "Capture failed: ${exception.localizedMessage}"
                                }
                            }
                        )
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth(0.7f)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = AppBg,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AppBg)
                            Text(
                                "CAPTURE & REVIEW",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMono
                                ),
                                color = AppBg
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Clean up scanned OCR lines into diff text format */
private fun cleanScannedCode(raw: String): String {
    val lines = raw.lines().map { it.trimEnd() }
    val isDiff = lines.any { it.startsWith("+") || it.startsWith("-") || it.startsWith("@@") }
    return if (isDiff) {
        lines.joinToString("\n")
    } else {
        // Wrap as mock diff if it's raw code
        "diff --git a/ScannedCode.kt b/ScannedCode.kt\n" +
        "--- a/ScannedCode.kt\n" +
        "+++ b/ScannedCode.kt\n" +
        lines.joinToString("\n") { "+ $it" }
    }
}

package com.ccmonitor.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ccmonitor.AuthRepository
import com.ccmonitor.ConnectionHelper
import com.ccmonitor.repository.SettingsRepository
import com.ccmonitor.ui.theme.ClaudeCodeMonitorTheme
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScanActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            ClaudeCodeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QRScanScreen(
                        onQRScanned = { qrContent ->
                            handleQRScanned(qrContent)
                        },
                        onFlashToggle = { toggleFlash() },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun handleQRScanned(qrContent: String) {
        // Extract both server URL and guest token from QR content
        // Expected format: https://cc-monitor.tiarkaerell.com/auth?token=<guest-token>
        val qrData = extractQRData(qrContent)

        if (qrData != null) {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            lifecycleScope.launch {
                try {
                    // Auto-configure server settings from QR code
                    val settingsRepository = SettingsRepository.getInstance(this@QRScanActivity)
                    settingsRepository.saveServerUrl(qrData.serverUrl)

                    Toast.makeText(this@QRScanActivity, "Server configured: ${qrData.serverUrl}", Toast.LENGTH_SHORT).show()

                    // Create instances with the correct server URL
                    val authRepository = AuthRepository.getInstance(this@QRScanActivity, qrData.serverUrl)
                    val connectionHelper = ConnectionHelper.getInstance(this@QRScanActivity, qrData.wsUrl)

                    // Authenticate with the server
                    val result = authRepository.authenticateWithQRToken(qrData.guestToken, deviceId)
                    result.fold(
                        onSuccess = { authData ->
                            Toast.makeText(this@QRScanActivity, "Authentication successful!", Toast.LENGTH_SHORT).show()

                            // Connect to WebSocket with correct URL
                            connectionHelper.connect(authData.apiKey)

                            // Close this activity and return to main
                            finish()
                        },
                        onFailure = { error ->
                            Toast.makeText(this@QRScanActivity, "Authentication failed: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                } catch (e: Exception) {
                    Toast.makeText(this@QRScanActivity, "Configuration failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show()
        }
    }

    data class QRData(
        val serverUrl: String,
        val wsUrl: String,
        val guestToken: String
    )

    private fun extractQRData(qrContent: String): QRData? {
        // Parse URL and extract server info and token parameter
        return try {
            val url = java.net.URL(qrContent)
            val query = url.query ?: return null
            val params = query.split("&").associate { param ->
                val (key, value) = param.split("=")
                key to value
            }

            val guestToken = params["token"] ?: return null

            // Extract base server URL (remove path and query)
            val serverUrl = "${url.protocol}://${url.host}${if (url.port != -1 && url.port != 80 && url.port != 443) ":${url.port}" else ""}"

            // Convert HTTP URL to WebSocket URL (same host and port, just change protocol)
            // Since you're using reverse proxy with WebSocket support,
            // the WebSocket uses the same URL as HTTP, just with ws/wss protocol
            val wsUrl = serverUrl.replace("https://", "wss://").replace("http://", "ws://")

            QRData(
                serverUrl = serverUrl,
                wsUrl = wsUrl,
                guestToken = guestToken
            )
        } catch (e: Exception) {
            null
        }
    }

    // Legacy function for compatibility
    private fun extractGuestToken(qrContent: String): String? {
        return extractQRData(qrContent)?.guestToken
    }

    private fun toggleFlash() {
        camera?.let { camera ->
            if (camera.cameraInfo.hasFlashUnit()) {
                val currentTorchState = camera.cameraInfo.torchState.value ?: false
                camera.cameraControl.enableTorch(currentTorchState == false)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScanScreen(
    onQRScanned: (String) -> Unit,
    onFlashToggle: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isFlashOn by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(true) }
    var scanResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isFlashOn = !isFlashOn
                        onFlashToggle()
                    }) {
                        Icon(
                            if (isFlashOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = if (isFlashOn) "Turn off flash" else "Turn on flash"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isScanning) {
                // Camera preview with QR scanning
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            // Preview
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // Image analysis for barcode scanning
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(
                                        ContextCompat.getMainExecutor(ctx),
                                        BarcodeAnalyzer { barcodes ->
                                            barcodes.firstOrNull()?.let { barcode ->
                                                barcode.rawValue?.let { value ->
                                                    scanResult = value
                                                    isScanning = false
                                                    onQRScanned(value)
                                                }
                                            }
                                        }
                                    )
                                }

                            // Camera selector
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                // Unbind all use cases before binding new ones
                                cameraProvider.unbindAll()

                                // Bind use cases to camera
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalyzer
                                )

                            } catch (exc: Exception) {
                                // Handle camera binding error
                            }

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Scanning overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top instruction
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = "Point your camera at the QR code displayed on your server",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    // Bottom actions
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scanning for QR codes...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator()
                        }
                    }
                }
            } else {
                // Processing result
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Authenticating...",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    scanResult?.let { result ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "QR Code: ${result.take(50)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedButton(
                        onClick = {
                            isScanning = true
                            scanResult = null
                        }
                    ) {
                        Text("Scan Again")
                    }
                }
            }
        }
    }
}

private class BarcodeAnalyzer(
    private val onBarcodeDetected: (List<Barcode>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        onBarcodeDetected(barcodes)
                    }
                }
                .addOnFailureListener {
                    // Handle failure
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
package com.example.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.SampleKiranaData
import com.example.data.model.ProductItem
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerDialog(
    inventoryProducts: List<ProductItem> = emptyList(),
    onDismiss: () -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var manualBarcode by remember { mutableStateOf("") }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    fun handleBarcodeFound(code: String) {
        val trimmed = code.trim()
        if (trimmed.isNotBlank()) {
            triggerHapticFeedback()
            onBarcodeDetected(trimmed)
            onDismiss()
        }
    }

    // Photo picker to scan barcode from image file
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val code = BarcodeAnalyzer.decodeBitmap(bitmap)
                    if (!code.isNullOrBlank()) {
                        handleBarcodeFound(code)
                    } else {
                        Toast.makeText(context, "No barcode detected in image. Try another photo.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Merge custom inventory products with top sample items for instant 1-tap testing
    val displayProducts = remember(inventoryProducts) {
        val list = mutableListOf<ProductItem>()
        // Add all products with barcodes first
        list.addAll(inventoryProducts.filter { it.barcode.isNotBlank() })
        // Add sample kirana products if not already included
        SampleKiranaData.sampleProducts.forEach { sample ->
            if (list.none { it.barcode == sample.barcode }) {
                list.add(sample)
            }
        }
        list
    }

    // Suggestions matching manual search
    val searchMatches = remember(manualBarcode, displayProducts) {
        if (manualBarcode.isBlank()) emptyList()
        else displayProducts.filter {
            it.barcode.contains(manualBarcode, ignoreCase = true) ||
            it.name.contains(manualBarcode, ignoreCase = true) ||
            it.bengaliName.contains(manualBarcode, ignoreCase = true)
        }.take(3)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("barcode_scanner_dialog"),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF006C4C), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Barcode Scanner",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Camera & Manual Keypad",
                                color = Color(0xFFFFB77C),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Scan from Gallery Button
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("scan_from_gallery_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Scan Image",
                                tint = Color(0xFFFFD54F)
                            )
                        }

                        // Torch button
                        if (hasCameraPermission && cameraAvailable) {
                            IconButton(
                                onClick = {
                                    isTorchOn = !isTorchOn
                                    camera?.cameraControl?.enableTorch(isTorchOn)
                                },
                                modifier = Modifier.testTag("torch_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flashlight",
                                    tint = if (isTorchOn) Color(0xFFFFD54F) else Color.White
                                )
                            }
                        }

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_scanner_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Camera Viewfinder or Fallback
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission && cameraAvailable) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                val cameraExecutor = Executors.newSingleThreadExecutor()

                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val analyzer = BarcodeAnalyzer { code ->
                                            handleBarcodeFound(code)
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also {
                                                it.setAnalyzer(cameraExecutor, analyzer)
                                            }

                                        cameraProvider.unbindAll()

                                        val selector = when {
                                            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                            else -> null
                                        }

                                        if (selector != null) {
                                            val boundCamera = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                selector,
                                                preview,
                                                imageAnalysis
                                            )
                                            camera = boundCamera
                                        } else {
                                            cameraAvailable = false
                                            cameraErrorMessage = "No physical camera detected on this device/emulator"
                                        }
                                    } catch (e: Exception) {
                                        cameraAvailable = false
                                        cameraErrorMessage = e.message ?: "Camera unavailable"
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!hasCameraPermission) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera Permission",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera permission required for live scan",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006C4C)),
                                modifier = Modifier.testTag("request_camera_permission_button")
                            ) {
                                Text("Grant Camera Permission")
                            }
                        }
                    } else {
                        // Emulator / Camera-less Mode Helper
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Notice",
                                tint = Color(0xFFFFB77C),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Camera Not Detected (Emulator Mode)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use Quick Barcodes below, upload photo, or enter digits",
                                color = Color(0xFFCCCCCC),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Scanner Target Box with Laser Animation
                    val infiniteTransition = rememberInfiniteTransition(label = "laser")
                    val laserOffsetY by infiniteTransition.animateFloat(
                        initialValue = 0.05f,
                        targetValue = 0.95f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "laserAnim"
                    )

                    Box(
                        modifier = Modifier
                            .size(260.dp, 160.dp)
                            .border(2.dp, Color(0xFF00E676), RoundedCornerShape(16.dp))
                    ) {
                        // Laser scan bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .padding(top = (laserOffsetY * 155).dp)
                                .background(Color(0xFFFF1744))
                        )
                    }

                    Text(
                        text = "Align barcode within frame or tap product below",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Barcode Palette (Interactive live buttons for quick testing on Emulator & Device)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Instant Barcode Tap (${displayProducts.size} items available):",
                        color = Color(0xFFFFB77C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap to Scan ⚡",
                        color = Color(0xFF81C784),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayProducts) { item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF262626)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable { handleBarcodeFound(item.barcode) }
                                .testTag("sim_barcode_${item.barcode}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(
                                    text = item.name.take(20) + (if (item.name.length > 20) ".." else ""),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "₹${item.sellingPrice.toInt()}",
                                        color = Color(0xFFFFB77C),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.barcode,
                                        color = Color(0xFF69F0AE),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Autocomplete Suggestions
                if (searchMatches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        searchMatches.forEach { match ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E392A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { handleBarcodeFound(match.barcode) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(match.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(match.barcode, color = Color(0xFF81C784), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("Tap to Scan ➔", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Manual Barcode & Keypad Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualBarcode,
                        onValueChange = { manualBarcode = it },
                        placeholder = { Text("Enter / paste barcode digits...", color = Color.Gray, fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (manualBarcode.isNotBlank()) {
                                    handleBarcodeFound(manualBarcode)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF006C4C),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_barcode_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualBarcode.isNotBlank()) {
                                handleBarcodeFound(manualBarcode)
                            }
                        },
                        enabled = manualBarcode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006C4C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("submit_manual_barcode_button")
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Lookup", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lookup", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

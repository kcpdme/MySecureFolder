package com.kcpd.myfolder.ui.camera

import android.Manifest
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.kcpd.myfolder.data.model.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoCameraScreen(
    navController: NavController,
    folderId: String? = null,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            PhotoCameraPreview(
                onPhotoCaptured = { file ->
                    viewModel.addMediaFile(file, MediaType.PHOTO, folderId)
                    showFlash = true
                },
                onClose = { navController.navigateUp() }
            )

            // Flash effect
            if (showFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(100)
                    showFlash = false
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Take Photo") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is required")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoCameraPreview(
    onPhotoCaptured: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var aspectRatio by remember { mutableStateOf(AspectRatio.RATIO_4_3) }

    val previewView = remember { 
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER 
        }
    }
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }

    DisposableEffect(lensFacing, aspectRatio) {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Configure Resolution based on Ratio
            // Increased to ~5MP (Standard High Quality) to satisfy S23 Ultra users while saving space vs 12MP
            val targetResolution = if (aspectRatio == AspectRatio.RATIO_16_9) {
                Size(1440, 2560) // 16:9 (QHD/3.7MP)
            } else {
                Size(1920, 2560) // 4:3 (5MP)
            }

            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build()
                .apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

            val newImageCapture = ImageCapture.Builder()
                .setTargetResolution(targetResolution)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(85)
                .build()
            imageCapture = newImageCapture

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    newImageCapture
                )
                cameraControl = camera.cameraControl

                // Update zoom capabilities
                val zoomState = camera.cameraInfo.zoomState.value
                minZoom = zoomState?.minZoomRatio ?: 1f
                maxZoom = zoomState?.maxZoomRatio ?: 1f

                camera.cameraControl.setZoomRatio(zoomRatio)

                if (flashEnabled) {
                    newImageCapture.flashMode = ImageCapture.FLASH_MODE_ON
                } else {
                    newImageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))

        onDispose { }
    }

    LaunchedEffect(flashEnabled, imageCapture) {
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    LaunchedEffect(zoomRatio) {
        cameraControl?.setZoomRatio(zoomRatio)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newZoom = (zoomRatio * zoom).coerceIn(0.5f, 5f)
                        zoomRatio = newZoom
                    }
                }
        )

        // Top controls bar (Ratio Toggle + Close)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // Respect notch/pinhole
                .padding(top = 5.dp, start = 16.dp, end = 16.dp) // Extra margin
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aspect Ratio Toggle
            TextButton(
                onClick = {
                    aspectRatio = if (aspectRatio == AspectRatio.RATIO_4_3) {
                        AspectRatio.RATIO_16_9
                    } else {
                        AspectRatio.RATIO_4_3
                    }
                },
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    )
            ) {
                Text(
                    text = if (aspectRatio == AspectRatio.RATIO_4_3) "4:3" else "16:9",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        // Zoom controls (Dynamic based on camera capabilities)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Define standard zoom levels
            val standardLevels = listOf(0.6f, 1f, 2f, 5f, 10f)
            
            // Filter levels supported by the device
            val supportedLevels = standardLevels.filter { it in minZoom..maxZoom }
            
            // Always ensure 1x is present (or min if > 1x)
            val finalLevels = if (supportedLevels.isEmpty()) listOf(1f) else supportedLevels

            finalLevels.forEach { level ->
                TextButton(
                    onClick = { zoomRatio = level },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (zoomRatio == level) Color.Yellow else Color.White
                    )
                ) {
                    Text(
                        text = if (level < 1f) "${level}x" else "${level.toInt()}x",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Bottom Controls Row: Flash | Shutter | Flip
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Button
            IconButton(
                onClick = { flashEnabled = !flashEnabled },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }

            // Shutter Button
            FloatingActionButton(
                onClick = {
                    val capture = imageCapture ?: return@FloatingActionButton
                    val photoFile = createMediaFile(context, "jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        androidx.core.content.ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onPhotoCaptured(photoFile)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                            }
                        }
                    )
                },
                modifier = Modifier.size(72.dp),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Take Photo",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Flip Camera Button
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera",
                    tint = Color.White
                )
            }
        }
    }
}

private fun createMediaFile(context: android.content.Context, extension: String): File {
    val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(mediaDir, "MEDIA_${timestamp}.$extension")
}

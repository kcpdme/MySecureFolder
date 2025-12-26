package com.kcpd.myfolder.ui.camera

import android.Manifest
import android.net.Uri
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kcpd.myfolder.data.model.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

enum class CaptureMode {
    PHOTO, VIDEO, AUDIO
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    initialMode: CaptureMode = CaptureMode.PHOTO,
    folderId: String? = null,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var captureMode by remember { mutableStateOf(initialMode) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    var showFlash by remember { mutableStateOf(false) }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (permissionsState.allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
            ) {
                when (captureMode) {
                    CaptureMode.PHOTO, CaptureMode.VIDEO -> {
                        CameraPreview(
                            captureMode = captureMode,
                            onPhotoCaptured = { file ->
                                viewModel.addMediaFile(file, MediaType.PHOTO, folderId)
                                showFlash = true
                            },
                            onVideoRecordingStart = {
                                isRecording = true
                                recordingDuration = 0L
                            },
                            onVideoRecordingStop = { file ->
                                isRecording = false
                                viewModel.addMediaFile(file, MediaType.VIDEO, folderId)
                                showFlash = true
                            }
                        )
                    }
                    CaptureMode.AUDIO -> {
                        AudioRecordingScreen(
                            onRecordingComplete = { file ->
                                viewModel.addMediaFile(file, MediaType.AUDIO, folderId)
                            }
                        )
                    }
                }

                // Flash effect for photo/video capture
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

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isRecording && captureMode == CaptureMode.VIDEO) {
                        Text(
                            text = formatDuration(recordingDuration),
                            color = Color.Red,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { captureMode = CaptureMode.PHOTO },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Photo Mode",
                                tint = if (captureMode == CaptureMode.PHOTO) Color.White else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { captureMode = CaptureMode.VIDEO },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = "Video Mode",
                                tint = if (captureMode == CaptureMode.VIDEO) Color.White else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { captureMode = CaptureMode.AUDIO },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Audio Mode",
                                tint = if (captureMode == CaptureMode.AUDIO) Color.White else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera and microphone permissions are required")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    captureMode: CaptureMode,
    onPhotoCaptured: (File) -> Unit,
    onVideoRecordingStart: () -> Unit,
    onVideoRecordingStop: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var recording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }

    DisposableEffect(lensFacing) {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraControl = camera.cameraControl

                // Set initial zoom
                camera.cameraControl.setZoomRatio(zoomRatio)

                // Enable flash if needed
                if (flashEnabled) {
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
                } else {
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))

        onDispose {
            recording?.stop()
        }
    }

    // Update flash mode when changed
    LaunchedEffect(flashEnabled) {
        imageCapture.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    // Update zoom when changed
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

        // Top controls bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flash toggle
            IconButton(
                onClick = { flashEnabled = !flashEnabled },
                modifier = Modifier
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

            // Camera flip
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
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

        // Zoom controls (horizontal, Samsung-style)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val zoomLevels = listOf(0.5f, 1f, 2f, 5f)

            zoomLevels.forEach { level ->
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

        FloatingActionButton(
            onClick = {
                when (captureMode) {
                    CaptureMode.PHOTO -> {
                        val photoFile = createMediaFile(context, "jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
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
                    }
                    CaptureMode.VIDEO -> {
                        if (isRecording) {
                            recording?.stop()
                            isRecording = false
                        } else {
                            val videoFile = createMediaFile(context, "mp4")
                            onVideoRecordingStart()
                            isRecording = true
                        }
                    }
                    else -> {}
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .size(72.dp),
            containerColor = if (isRecording) Color.Red else Color.White,
            shape = CircleShape
        ) {
            Icon(
                when (captureMode) {
                    CaptureMode.PHOTO -> Icons.Default.PhotoCamera
                    CaptureMode.VIDEO -> if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord
                    else -> Icons.Default.PhotoCamera
                },
                contentDescription = "Capture",
                tint = if (isRecording) Color.White else Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun AudioRecordingScreen(
    onRecordingComplete: (File) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    var mediaRecorder: android.media.MediaRecorder? by remember { mutableStateOf(null) }
    var audioFile: File? by remember { mutableStateOf(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDuration += 1000
            }
        } else {
            recordingDuration = 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = if (isRecording) Color.Red else Color.White,
                modifier = Modifier.size(120.dp)
            )

            Text(
                text = if (isRecording) formatDuration(recordingDuration) else "Ready to record",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )

            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        mediaRecorder?.apply {
                            stop()
                            release()
                        }
                        mediaRecorder = null
                        isRecording = false
                        audioFile?.let { onRecordingComplete(it) }
                    } else {
                        audioFile = createMediaFile(context, "m4a")
                        mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            android.media.MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            android.media.MediaRecorder()
                        }.apply {
                            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(audioFile?.absolutePath)
                            prepare()
                            start()
                        }
                        isRecording = true
                    }
                },
                modifier = Modifier.size(72.dp),
                containerColor = if (isRecording) Color.Red else Color.White
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop" else "Record",
                    tint = if (isRecording) Color.White else Color.Red,
                    modifier = Modifier.size(32.dp)
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

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

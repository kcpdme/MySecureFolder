package com.kcpd.myfolder.ui.camera

import android.Manifest
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.video.*
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kcpd.myfolder.data.model.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoRecorderScreen(
    navController: NavController,
    folderId: String? = null,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                title = { Text("Record Video") },
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
                VideoRecorderPreview(
                    onVideoRecorded = { file ->
                        viewModel.addMediaFile(file, MediaType.VIDEO, folderId)
                        showFlash = true
                    }
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
fun VideoRecorderPreview(
    onVideoRecorded: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoomRatio by remember { mutableStateOf(1f) }

    // Timer for recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0L
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDuration += 1000
            }
        } else {
            recordingDuration = 0L
        }
    }

    DisposableEffect(lensFacing) {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                cameraControl = camera.cameraControl
                camera.cameraControl.setZoomRatio(zoomRatio)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            recording?.stop()
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

        // Recording duration indicator
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(
                        Color.Red.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = formatDuration(recordingDuration),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Zoom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
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

        // Bottom Controls Row: Shutter | Flip
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer to balance layout (since we don't have Flash here)
            Spacer(modifier = Modifier.size(48.dp))

            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        recording?.stop()
                        isRecording = false
                    } else {
                        val videoFile = createMediaFile(context, "mp4")
                        val outputOptions = FileOutputOptions.Builder(videoFile).build()

                        recording = videoCapture?.output
                            ?.prepareRecording(context, outputOptions)
                            ?.withAudioEnabled()
                            ?.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                when (recordEvent) {
                                    is VideoRecordEvent.Finalize -> {
                                        if (recordEvent.hasError()) {
                                            recordEvent.cause?.printStackTrace()
                                        } else {
                                            onVideoRecorded(videoFile)
                                        }
                                    }
                                }
                            }
                        isRecording = true
                    }
                },
                modifier = Modifier.size(72.dp),
                containerColor = if (isRecording) Color.Red else Color.White,
                shape = CircleShape
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    tint = if (isRecording) Color.White else Color.Red,
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

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

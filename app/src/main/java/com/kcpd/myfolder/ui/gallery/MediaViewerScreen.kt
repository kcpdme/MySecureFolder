package com.kcpd.myfolder.ui.gallery

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.data.repository.MediaRepository
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    navController: NavController,
    initialIndex: Int = 0,
    category: String? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val allMediaFiles by viewModel.mediaFiles.collectAsState()

    // Filter media files by category if provided
    val mediaFiles = remember(allMediaFiles, category) {
        if (category != null) {
            val folderCategory = FolderCategory.fromPath(category)
            if (folderCategory != null) {
                allMediaFiles.filter { it.mediaType == folderCategory.mediaType }
            } else {
                allMediaFiles
            }
        } else {
            allMediaFiles
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mediaFiles.size - 1).coerceAtLeast(0)),
        pageCount = { mediaFiles.size }
    )
    var showControls by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val mediaFile = mediaFiles[page]

            when (mediaFile.mediaType) {
                MediaType.PHOTO -> {
                    ZoomableImage(
                        mediaFile = mediaFile,
                        onTap = { showControls = !showControls }
                    )
                }
                MediaType.VIDEO -> {
                    VideoPlayer(
                        mediaFile = mediaFile,
                        onTap = { showControls = !showControls }
                    )
                }
                MediaType.AUDIO -> {
                    AudioPlayer(
                        mediaFile = mediaFile,
                        onTap = { showControls = !showControls }
                    )
                }
                MediaType.NOTE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Note,
                            contentDescription = "Note",
                            modifier = Modifier.size(120.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                MediaType.PDF -> {
                    PdfViewer(
                        mediaFile = mediaFile,
                        onTap = { showControls = !showControls }
                    )
                }
            }
        }

        // Top app bar
        if (showControls) {
            TopAppBar(
                title = {
                    Text(
                        if (mediaFiles.isNotEmpty())
                            mediaFiles[pagerState.currentPage].fileName
                        else
                            ""
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (mediaFiles.isNotEmpty()) {
                            viewModel.shareMediaFile(mediaFiles[pagerState.currentPage])
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = {
                        if (mediaFiles.isNotEmpty()) {
                            viewModel.deleteMediaFile(mediaFiles[pagerState.currentPage])
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Page indicator
        if (showControls && mediaFiles.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${mediaFiles.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ZoomableImage(
    mediaFile: MediaFile,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        offset = Offset.Zero
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)

                    if (newScale > 1f) {
                        val newOffset = offset + pan

                        // Calculate max offset to prevent showing black areas
                        val maxX = (imageSize.width * (newScale - 1)) / 2f
                        val maxY = (imageSize.height * (newScale - 1)) / 2f

                        offset = Offset(
                            newOffset.x.coerceIn(-maxX, maxX),
                            newOffset.y.coerceIn(-maxY, maxY)
                        )
                    } else {
                        offset = Offset.Zero
                    }

                    scale = newScale
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaFile.filePath)
                .crossfade(true)
                .build(),
            contentDescription = mediaFile.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    imageSize = androidx.compose.ui.geometry.Size(
                        placeable.width.toFloat(),
                        placeable.height.toFloat()
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
        )
    }
}

@Composable
fun VideoPlayer(
    mediaFile: MediaFile,
    onTap: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(java.io.File(mediaFile.filePath)))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(mediaFile.filePath) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AudioPlayer(
    mediaFile: MediaFile,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(java.io.File(mediaFile.filePath)))
            setMediaItem(mediaItem)
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = this@apply.duration
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            kotlinx.coroutines.delay(100)
        }
    }

    DisposableEffect(mediaFile.filePath) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = "Audio",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = mediaFile.fileName,
                style = MaterialTheme.typography.headlineSmall
            )

            // Progress slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = {
                        exoPlayer.seekTo((it * duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0))
                }) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "Rewind",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = {
                    exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(duration))
                }) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "Forward",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PdfViewer(
    mediaFile: MediaFile,
    onTap: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPage by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var pdfBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var decryptedFile by remember { mutableStateOf<java.io.File?>(null) }

    // Decrypt PDF file on first load
    LaunchedEffect(mediaFile.filePath) {
        try {
            android.util.Log.d("PdfViewer", "╔═══════════════════════════════════════╗")
            android.util.Log.d("PdfViewer", "║  PDF VIEWER INITIALIZING              ║")
            android.util.Log.d("PdfViewer", "╚═══════════════════════════════════════╝")
            android.util.Log.d("PdfViewer", "Encrypted PDF: ${mediaFile.filePath}")
            android.util.Log.d("PdfViewer", "Decrypting PDF for viewing...")

            val file = viewModel.decryptForPlayback(mediaFile)
            decryptedFile = file
            android.util.Log.d("PdfViewer", "✓ PDF decrypted to: ${file.absolutePath}")
            android.util.Log.d("PdfViewer", "  Decrypted size: ${file.length()} bytes")
        } catch (e: Exception) {
            android.util.Log.e("PdfViewer", "✗ Failed to decrypt PDF", e)
            errorMessage = "Failed to decrypt PDF: ${e.message}"
        }
    }

    // Clean up decrypted file on dispose
    DisposableEffect(mediaFile.filePath) {
        onDispose {
            decryptedFile?.let { file ->
                if (file.exists()) {
                    android.util.Log.d("PdfViewer", "Cleaning up decrypted PDF...")
                    file.delete()
                    android.util.Log.d("PdfViewer", "✓ Temp file deleted")
                }
            }
        }
    }

    // Load PDF page
    LaunchedEffect(decryptedFile, currentPage) {
        val file = decryptedFile ?: return@LaunchedEffect

        try {
            android.util.Log.d("PdfViewer", "Loading PDF page $currentPage...")

            if (!file.exists()) {
                android.util.Log.e("PdfViewer", "File does not exist: ${file.absolutePath}")
                errorMessage = "PDF file not found"
                return@LaunchedEffect
            }

            android.util.Log.d("PdfViewer", "Opening PDF renderer...")
            val pdfRenderer = android.graphics.pdf.PdfRenderer(
                android.os.ParcelFileDescriptor.open(
                    file,
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )
            )

            pageCount = pdfRenderer.pageCount
            android.util.Log.d("PdfViewer", "PDF has $pageCount pages")

            if (currentPage >= pageCount) {
                currentPage = pageCount - 1
            }

            android.util.Log.d("PdfViewer", "Rendering page ${currentPage + 1}/$pageCount...")
            val page = pdfRenderer.openPage(currentPage)

            // Create bitmap with good quality (2x scale for crisp rendering)
            val bitmap = android.graphics.Bitmap.createBitmap(
                page.width * 2,
                page.height * 2,
                android.graphics.Bitmap.Config.ARGB_8888
            )

            // Render page to bitmap with white background
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)

            page.render(
                bitmap,
                null,
                null,
                android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            pdfBitmap = bitmap
            android.util.Log.d("PdfViewer", "Page rendered successfully")

            page.close()
            pdfRenderer.close()
        } catch (e: Exception) {
            android.util.Log.e("PdfViewer", "Failed to render PDF", e)
            errorMessage = "Failed to load PDF: ${e.message}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        offset = Offset.Zero
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)

                    if (newScale > 1f) {
                        offset = offset + pan
                    } else {
                        offset = Offset.Zero
                    }

                    scale = newScale
                }
            }
    ) {
        when {
            errorMessage != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            pdfBitmap != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(pdfBitmap)
                        .crossfade(true)
                        .build(),
                    contentDescription = "PDF Page ${currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }

        // Page navigation controls
        if (pageCount > 1 && pdfBitmap != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Icon(
                        Icons.Default.NavigateBefore,
                        contentDescription = "Previous page",
                        tint = Color.White
                    )
                }

                Text(
                    text = "${currentPage + 1} / $pageCount",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )

                IconButton(
                    onClick = { if (currentPage < pageCount - 1) currentPage++ },
                    enabled = currentPage < pageCount - 1
                ) {
                    Icon(
                        Icons.Default.NavigateNext,
                        contentDescription = "Next page",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis < 0) return "00:00"
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

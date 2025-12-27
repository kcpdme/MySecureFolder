package com.kcpd.myfolder.ui.viewer

import android.net.Uri
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.ui.gallery.GalleryViewModel
import com.kcpd.myfolder.ui.util.ScreenSecureEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    navController: NavController,
    initialIndex: Int = 0,
    category: String? = null,
    fileId: String? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    // Prevent screenshots and screen recording for security
    ScreenSecureEffect()

    android.util.Log.d("PhotoViewerScreen", "PhotoViewerScreen created: initialIndex=$initialIndex, category=$category, fileId=$fileId")

    val allMediaFiles by viewModel.mediaFiles.collectAsState()

    android.util.Log.d("PhotoViewerScreen", "All media files count: ${allMediaFiles.size}")

    // Filter to show only photos
    val photoFiles = remember(allMediaFiles) {
        val filtered = allMediaFiles.filter { it.mediaType == MediaType.PHOTO }
        android.util.Log.d("PhotoViewerScreen", "Filtered photo files count: ${filtered.size}")
        filtered.forEachIndexed { index, file ->
            android.util.Log.d("PhotoViewerScreen", "[$index] Photo: ${file.fileName}, path: ${file.filePath}")
        }
        filtered
    }

    // If fileId is provided, find the correct index in the filtered list
    val actualIndex = remember(photoFiles, fileId, initialIndex) {
        if (fileId != null) {
            val foundIndex = photoFiles.indexOfFirst { it.id == fileId }
            if (foundIndex != -1) {
                android.util.Log.d("PhotoViewerScreen", "Found file by ID at index $foundIndex")
                foundIndex
            } else {
                android.util.Log.w("PhotoViewerScreen", "File ID $fileId not found, using initialIndex $initialIndex")
                initialIndex
            }
        } else {
            initialIndex
        }
    }

    val pagerState = rememberPagerState(
        initialPage = actualIndex.coerceIn(0, (photoFiles.size - 1).coerceAtLeast(0)),
        pageCount = { photoFiles.size }
    )
    var showControls by remember { mutableStateOf(true) }
    var showCropMode by remember { mutableStateOf(false) }

    android.util.Log.d("PhotoViewerScreen", "PagerState: initialPage=${initialIndex.coerceIn(0, (photoFiles.size - 1).coerceAtLeast(0))}, pageCount=${photoFiles.size}")

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            android.util.Log.d("PhotoViewerScreen", "Rendering page: $page")
            val photoFile = photoFiles[page]
            android.util.Log.d("PhotoViewerScreen", "Photo file for page $page: ${photoFile.fileName}")
            ZoomableImage(
                mediaFile = photoFile,
                onTap = { showControls = !showControls }
            )
        }

        // Top app bar
        if (showControls) {
            TopAppBar(
                title = {
                    Text(
                        if (photoFiles.isNotEmpty())
                            photoFiles[pagerState.currentPage].fileName
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
                        if (photoFiles.isNotEmpty()) {
                            viewModel.rotatePhoto(photoFiles[pagerState.currentPage]) { newFile ->
                                // Photo rotated and saved as new file
                                // The pager will update automatically when mediaFiles updates
                            }
                        }
                    }) {
                        Icon(Icons.Default.RotateRight, "Rotate")
                    }
                    IconButton(onClick = {
                        showCropMode = true
                        showControls = false
                    }) {
                        Icon(Icons.Default.Crop, "Crop")
                    }
                    IconButton(onClick = {
                        if (photoFiles.isNotEmpty()) {
                            viewModel.shareMediaFile(photoFiles[pagerState.currentPage])
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = {
                        if (photoFiles.isNotEmpty()) {
                            viewModel.deleteMediaFile(photoFiles[pagerState.currentPage])
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
        if (showControls && photoFiles.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${photoFiles.size}",
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

        // Crop mode overlay
        if (showCropMode && photoFiles.isNotEmpty()) {
            CropModeOverlay(
                mediaFile = photoFiles[pagerState.currentPage],
                onCropConfirm = { cropRect ->
                    viewModel.cropPhoto(photoFiles[pagerState.currentPage], cropRect) { newFile ->
                        showCropMode = false
                        showControls = true
                    }
                },
                onCropCancel = {
                    showCropMode = false
                    showControls = true
                }
            )
        }
    }
}

@Composable
fun ZoomableImage(
    mediaFile: MediaFile,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    // Stable model key to prevent re-loading on recomposition
    val context = LocalContext.current
    val imageModel = remember(mediaFile.id) {
        android.util.Log.d("ZoomableImage", "Creating image model for: ${mediaFile.fileName}")
        ImageRequest.Builder(context)
            .data(mediaFile)
            .memoryCacheKey(mediaFile.id)  // Cache by file ID
            .diskCacheKey(mediaFile.id)    // Cache by file ID
            .crossfade(true)
            .build()
    }

    // Separate modifier chains based on zoom state to allow HorizontalPager to receive swipes when not zoomed
    val baseModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            containerSize = androidx.compose.ui.geometry.Size(
                placeable.width.toFloat(),
                placeable.height.toFloat()
            )
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                        offset = Offset.Zero
                    }
                },
                onTap = { onTap() }
            )
        }

    // Only add transform gestures when zoomed - otherwise let HorizontalPager handle swipes
    val gestureModifier = if (scale > 1f) {
        Modifier.pointerInput(scale, containerSize) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val oldScale = scale
                val newScale = (scale * zoom).coerceIn(1f, 5f)

                // Apply zoom
                scale = newScale

                // Calculate new offset
                if (newScale > 1f) {
                    // Adjust offset for zoom around centroid
                    val newOffset = if (oldScale != newScale) {
                        // Zooming - keep centroid point fixed
                        val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                        val zoomChange = newScale / oldScale
                        offset * zoomChange + (centroid - containerCenter) * (zoomChange - 1f)
                    } else {
                        // Panning - just add pan delta
                        offset + pan
                    }

                    // Calculate bounds - with ContentScale.Fit, the image is centered and scaled to fit
                    // Maximum translation is when scaled image edge reaches container edge
                    val scaledWidth = containerSize.width * newScale
                    val scaledHeight = containerSize.height * newScale

                    // Max offset is half the difference between scaled size and container size
                    val maxX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
                    val maxY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)

                    // Constrain offset to prevent black areas
                    offset = Offset(
                        x = newOffset.x.coerceIn(-maxX, maxX),
                        y = newOffset.y.coerceIn(-maxY, maxY)
                    )
                } else {
                    // Scale is 1f - reset offset
                    offset = Offset.Zero
                }
            }
        }
    } else {
        Modifier  // No gesture interception when not zoomed - allows HorizontalPager to swipe
    }

    Box(modifier = baseModifier.then(gestureModifier)) {
        AsyncImage(
            model = imageModel,  // Use stable remembered model
            contentDescription = mediaFile.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
fun CropModeOverlay(
    mediaFile: MediaFile,
    onCropConfirm: (Rect) -> Unit,
    onCropCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // Crop rectangle in screen coordinates (initially centered 80% of container)
    var cropRect by remember {
        mutableStateOf(
            androidx.compose.ui.geometry.Rect(
                left = 0f,
                top = 0f,
                right = 100f,
                bottom = 100f
            )
        )
    }

    // Initialize crop rect when container size is known
    LaunchedEffect(containerSize) {
        if (containerSize != IntSize.Zero && cropRect.width < 50f) {
            val margin = containerSize.width * 0.1f
            cropRect = androidx.compose.ui.geometry.Rect(
                left = margin,
                top = containerSize.height * 0.2f,
                right = containerSize.width - margin,
                bottom = containerSize.height * 0.8f
            )
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragHandle by remember { mutableStateOf<DragHandle?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        // Image
        val context = LocalContext.current
        val imageModel = remember(mediaFile.id) {
            ImageRequest.Builder(context)
                .data(mediaFile)
                .memoryCacheKey(mediaFile.id)
                .diskCacheKey(mediaFile.id)
                .build()
        }

        var imagePainter by remember { mutableStateOf<AsyncImagePainter?>(null) }

        AsyncImage(
            model = imageModel,
            contentDescription = mediaFile.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    imagePainter = state.painter as? AsyncImagePainter
                    // Calculate actual image size on screen
                    val intrinsicSize = state.painter.intrinsicSize
                    if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                        val containerAspect = containerSize.width.toFloat() / containerSize.height
                        val imageAspect = intrinsicSize.width / intrinsicSize.height

                        imageSize = if (imageAspect > containerAspect) {
                            // Image is wider - width constrained
                            IntSize(
                                width = containerSize.width,
                                height = (containerSize.width / imageAspect).toInt()
                            )
                        } else {
                            // Image is taller - height constrained
                            IntSize(
                                width = (containerSize.height * imageAspect).toInt(),
                                height = containerSize.height
                            )
                        }
                    }
                }
            }
        )

        // Crop overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragHandle = getDragHandle(offset, cropRect, 40f)
                        },
                        onDrag = { change, dragAmount ->
                            if (isDragging) {
                                cropRect = when (dragHandle) {
                                    DragHandle.TOP_LEFT -> cropRect.copy(
                                        left = (cropRect.left + dragAmount.x).coerceIn(0f, cropRect.right - 50f),
                                        top = (cropRect.top + dragAmount.y).coerceIn(0f, cropRect.bottom - 50f)
                                    )
                                    DragHandle.TOP_RIGHT -> cropRect.copy(
                                        right = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + 50f, containerSize.width.toFloat()),
                                        top = (cropRect.top + dragAmount.y).coerceIn(0f, cropRect.bottom - 50f)
                                    )
                                    DragHandle.BOTTOM_LEFT -> cropRect.copy(
                                        left = (cropRect.left + dragAmount.x).coerceIn(0f, cropRect.right - 50f),
                                        bottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + 50f, containerSize.height.toFloat())
                                    )
                                    DragHandle.BOTTOM_RIGHT -> cropRect.copy(
                                        right = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + 50f, containerSize.width.toFloat()),
                                        bottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + 50f, containerSize.height.toFloat())
                                    )
                                    DragHandle.CENTER -> {
                                        val newLeft = (cropRect.left + dragAmount.x).coerceIn(0f, containerSize.width - cropRect.width)
                                        val newTop = (cropRect.top + dragAmount.y).coerceIn(0f, containerSize.height - cropRect.height)
                                        cropRect.copy(
                                            left = newLeft,
                                            top = newTop,
                                            right = newLeft + cropRect.width,
                                            bottom = newTop + cropRect.height
                                        )
                                    }
                                    null -> cropRect
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragHandle = null
                        }
                    )
                }
        ) {
            // Draw darkened overlay outside crop area
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset.Zero,
                size = Size(size.width, cropRect.top)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(0f, cropRect.bottom),
                size = Size(size.width, size.height - cropRect.bottom)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(0f, cropRect.top),
                size = Size(cropRect.left, cropRect.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(cropRect.right, cropRect.top),
                size = Size(size.width - cropRect.right, cropRect.height)
            )

            // Draw crop rectangle border
            drawRect(
                color = Color.White,
                topLeft = Offset(cropRect.left, cropRect.top),
                size = Size(cropRect.width, cropRect.height),
                style = Stroke(width = 2f)
            )

            // Draw corner handles
            val handleSize = 40f
            listOf(
                Offset(cropRect.left, cropRect.top),
                Offset(cropRect.right - handleSize, cropRect.top),
                Offset(cropRect.left, cropRect.bottom - handleSize),
                Offset(cropRect.right - handleSize, cropRect.bottom - handleSize)
            ).forEach { offset ->
                drawRect(
                    color = Color.White,
                    topLeft = offset,
                    size = Size(handleSize, handleSize),
                    style = Stroke(width = 3f)
                )
            }
        }

        // Crop controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onCropCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    // Convert screen coordinates to image coordinates
                    if (imageSize.width > 0 && imageSize.height > 0 && containerSize.width > 0) {
                        // Calculate image offset in container (centered)
                        val imageLeft = (containerSize.width - imageSize.width) / 2f
                        val imageTop = (containerSize.height - imageSize.height) / 2f

                        // Convert crop rect from screen to image coordinates
                        val scaleX = imagePainter?.intrinsicSize?.width ?: 1f / imageSize.width
                        val scaleY = imagePainter?.intrinsicSize?.height ?: 1f / imageSize.height

                        val imageCropRect = Rect(
                            ((cropRect.left - imageLeft) * scaleX).toInt().coerceAtLeast(0),
                            ((cropRect.top - imageTop) * scaleY).toInt().coerceAtLeast(0),
                            ((cropRect.right - imageLeft) * scaleX).toInt(),
                            ((cropRect.bottom - imageTop) * scaleY).toInt()
                        )

                        onCropConfirm(imageCropRect)
                    }
                }
            ) {
                Text("Crop")
            }
        }
    }
}

private enum class DragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

private fun getDragHandle(
    offset: Offset,
    cropRect: androidx.compose.ui.geometry.Rect,
    handleSize: Float
): DragHandle? {
    return when {
        offset.x in (cropRect.left - handleSize)..(cropRect.left + handleSize) &&
        offset.y in (cropRect.top - handleSize)..(cropRect.top + handleSize) -> DragHandle.TOP_LEFT

        offset.x in (cropRect.right - handleSize)..(cropRect.right + handleSize) &&
        offset.y in (cropRect.top - handleSize)..(cropRect.top + handleSize) -> DragHandle.TOP_RIGHT

        offset.x in (cropRect.left - handleSize)..(cropRect.left + handleSize) &&
        offset.y in (cropRect.bottom - handleSize)..(cropRect.bottom + handleSize) -> DragHandle.BOTTOM_LEFT

        offset.x in (cropRect.right - handleSize)..(cropRect.right + handleSize) &&
        offset.y in (cropRect.bottom - handleSize)..(cropRect.bottom + handleSize) -> DragHandle.BOTTOM_RIGHT

        offset.x in cropRect.left..cropRect.right &&
        offset.y in cropRect.top..cropRect.bottom -> DragHandle.CENTER

        else -> null
    }
}

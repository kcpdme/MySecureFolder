package com.kcpd.myfolder.ui.viewer

import android.net.Uri
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.ui.gallery.GalleryViewModel
import com.kcpd.myfolder.ui.util.ScreenSecureEffect

enum class EditMode {
    ROTATE, CROP
}

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
    var showBottomSheet by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf<EditMode?>(null) }
    var rotationAngle by remember { mutableIntStateOf(0) } // 0, 90, 180, 270
    var editCropRect by remember { mutableStateOf<Rect?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

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
                    val fileName = if (photoFiles.isNotEmpty()) photoFiles[pagerState.currentPage].fileName else ""
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(Icons.Default.MoreVert, "More options")
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

        // Bottom Sheet for actions
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    // Rotate option
                    ListItem(
                        headlineContent = { Text("Rotate") },
                        leadingContent = {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                        },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                editMode = EditMode.ROTATE
                                rotationAngle = 0
                                showControls = false
                            }
                        }
                    )
                    
                    // Crop option
                    ListItem(
                        headlineContent = { Text("Crop") },
                        leadingContent = {
                            Icon(Icons.Default.Crop, contentDescription = "Crop")
                        },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                editMode = EditMode.CROP
                                editCropRect = null
                                showControls = false
                            }
                        }
                    )
                    
                    // Share option
                    ListItem(
                        headlineContent = { Text("Share") },
                        leadingContent = {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                if (photoFiles.isNotEmpty()) {
                                    viewModel.shareMediaFile(photoFiles[pagerState.currentPage])
                                }
                            }
                        }
                    )
                    
                    // Delete option
                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                                if (photoFiles.isNotEmpty()) {
                                    viewModel.deleteMediaFile(photoFiles[pagerState.currentPage])
                                    navController.navigateUp()
                                }
                            }
                        }
                    )
                }
            }
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

        // Edit mode overlay
        if (editMode != null && photoFiles.isNotEmpty()) {
            EditModeOverlay(
                mediaFile = photoFiles[pagerState.currentPage],
                editMode = editMode!!,
                initialRotation = rotationAngle,
                initialCropRect = editCropRect,
                onRotationChange = { newAngle -> rotationAngle = newAngle },
                onCropRectChange = { newRect -> editCropRect = newRect },
                onSave = { finalRotation, finalCropRect ->
                    val currentFile = photoFiles[pagerState.currentPage]
                    when (editMode!!) {
                        EditMode.ROTATE -> {
                            if (finalRotation != 0) {
                                viewModel.rotatePhoto(currentFile, finalRotation.toFloat()) { newFile ->
                                    editMode = null
                                    rotationAngle = 0
                                    showControls = true
                                }
                            } else {
                                // No rotation needed
                                editMode = null
                                showControls = true
                            }
                        }
                        EditMode.CROP -> {
                            if (finalCropRect != null) {
                                viewModel.cropPhoto(currentFile, finalCropRect) { newFile ->
                                    editMode = null
                                    editCropRect = null
                                    showControls = true
                                }
                            }
                        }
                    }
                },
                onCancel = {
                    editMode = null
                    rotationAngle = 0
                    editCropRect = null
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
    var imageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

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

    // Calculate the actual displayed image size based on ContentScale.Fit
    fun calculateImageBounds(currentScale: Float): Pair<Float, Float> {
        if (imageSize.width <= 0 || imageSize.height <= 0 || containerSize.width <= 0 || containerSize.height <= 0) {
            return Pair(0f, 0f)
        }
        
        // With ContentScale.Fit, the image is scaled to fit within the container while maintaining aspect ratio
        val imageAspect = imageSize.width / imageSize.height
        val containerAspect = containerSize.width / containerSize.height
        
        val displayedWidth: Float
        val displayedHeight: Float
        
        if (imageAspect > containerAspect) {
            // Image is wider - width fills container
            displayedWidth = containerSize.width
            displayedHeight = containerSize.width / imageAspect
        } else {
            // Image is taller - height fills container
            displayedHeight = containerSize.height
            displayedWidth = containerSize.height * imageAspect
        }
        
        // Calculate max offset based on scaled displayed size vs container
        val scaledWidth = displayedWidth * currentScale
        val scaledHeight = displayedHeight * currentScale
        
        val maxX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)
        
        return Pair(maxX, maxY)
    }

    Box(
        modifier = Modifier
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
                // Combined gesture detector for tap, double-tap, and pinch-to-zoom
                awaitEachGesture {
                    // Wait for first finger down
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val firstDownTime = System.currentTimeMillis()
                    var firstUpTime = 0L
                    var transformStarted = false
                    
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        
                        if (pointerCount >= 2) {
                            // Multi-touch detected - this is a pinch gesture
                            transformStarted = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldScale = scale
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale
                                
                                if (newScale > 1f) {
                                    val newOffset = if (zoomChange != 1f) {
                                        val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                        val zoomRatio = newScale / oldScale
                                        offset * zoomRatio + (centroid - containerCenter) * (zoomRatio - 1f)
                                    } else {
                                        offset + panChange
                                    }
                                    
                                    val (maxX, maxY) = calculateImageBounds(newScale)
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxX, maxX),
                                        y = newOffset.y.coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                            
                            // Consume the changes
                            event.changes.forEach { 
                                if (it.positionChanged()) it.consume() 
                            }
                        } else if (pointerCount == 1 && !transformStarted) {
                            // Single finger - check for pan when zoomed
                            val change = event.changes.first()
                            
                            if (scale > 1f && change.positionChanged()) {
                                // Panning when zoomed
                                val panChange = change.position - change.previousPosition
                                val newOffset = offset + panChange
                                val (maxX, maxY) = calculateImageBounds(scale)
                                offset = Offset(
                                    x = newOffset.x.coerceIn(-maxX, maxX),
                                    y = newOffset.y.coerceIn(-maxY, maxY)
                                )
                                change.consume()
                            }
                            
                            if (!change.pressed) {
                                // Finger released
                                firstUpTime = System.currentTimeMillis()
                            }
                        } else if (pointerCount == 0) {
                            // All fingers released
                            break
                        }
                    } while (event.changes.any { it.pressed })
                    
                    // After gesture ends, check if it was a tap or double-tap
                    if (!transformStarted && firstUpTime > 0) {
                        val tapDuration = firstUpTime - firstDownTime
                        val tapPosition = firstDown.position
                        
                        if (tapDuration < 300) {
                            // Quick tap - wait briefly to check for double tap
                            val secondDown = withTimeoutOrNull(300) {
                                awaitFirstDown(requireUnconsumed = false)
                            }
                            
                            if (secondDown != null) {
                                // Double tap detected
                                val doubleTapPosition = secondDown.position
                                
                                // Wait for second tap to complete
                                waitForUpOrCancellation()
                                
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val newScale = 2.5f
                                    scale = newScale
                                    val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val zoomChange = newScale / 1f
                                    val newOffset = (doubleTapPosition - containerCenter) * (1f - zoomChange)
                                    val (maxX, maxY) = calculateImageBounds(newScale)
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxX, maxX),
                                        y = newOffset.y.coerceIn(-maxY, maxY)
                                    )
                                }
                            } else {
                                // Single tap
                                onTap()
                            }
                        }
                    }
                }
            }
    ) {
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
                ),
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val intrinsicSize = state.painter.intrinsicSize
                    if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                        imageSize = intrinsicSize
                    }
                }
            }
        )
    }
}

@Composable
fun EditModeOverlay(
    mediaFile: MediaFile,
    editMode: EditMode,
    initialRotation: Int,
    initialCropRect: Rect?,
    onRotationChange: (Int) -> Unit,
    onCropRectChange: (Rect?) -> Unit,
    onSave: (rotation: Int, cropRect: Rect?) -> Unit,
    onCancel: () -> Unit
) {
    var rotationAngle by remember(mediaFile.id) { mutableIntStateOf(initialRotation) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imagePainter by remember { mutableStateOf<Painter?>(null) }

    // Derive imageSize from containerSize and imagePainter to handle race conditions
    val imageSize = remember(containerSize, imagePainter) {
        val painter = imagePainter
        android.util.Log.d("PhotoViewer", "Calculating imageSize. Container: $containerSize, Painter: ${painter != null}")
        
        if (painter != null && containerSize.width > 0 && containerSize.height > 0) {
            val intrinsicSize = painter.intrinsicSize
            android.util.Log.d("PhotoViewer", "Intrinsic size: $intrinsicSize")
            
            if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                val containerAspect = containerSize.width.toFloat() / containerSize.height
                val imageAspect = intrinsicSize.width / intrinsicSize.height

                val size = if (imageAspect > containerAspect) {
                    IntSize(
                        width = containerSize.width,
                        height = (containerSize.width / imageAspect).toInt()
                    )
                } else {
                    IntSize(
                        width = (containerSize.height * imageAspect).toInt(),
                        height = containerSize.height
                    )
                }
                android.util.Log.d("PhotoViewer", "Calculated imageSize: $size")
                size
            } else {
                android.util.Log.w("PhotoViewer", "Invalid intrinsic size")
                IntSize.Zero
            }
        } else {
            android.util.Log.w("PhotoViewer", "Missing dependencies for imageSize")
            IntSize.Zero
        }
    }

    // Crop rectangle in screen coordinates
    var cropRect by remember(mediaFile.id) {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(initialCropRect?.let {
            androidx.compose.ui.geometry.Rect(
                it.left.toFloat(),
                it.top.toFloat(),
                it.right.toFloat(),
                it.bottom.toFloat()
            )
        })
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragHandle by remember { mutableStateOf<DragHandle?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                containerSize = size
                // Initialize crop rect when container size is known
                if (editMode == EditMode.CROP && cropRect == null && size.width > 0) {
                    // Default to a square at center, roughly half the screen size
                    val minDim = minOf(size.width, size.height).toFloat()
                    val side = minDim * 0.5f
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    
                    cropRect = androidx.compose.ui.geometry.Rect(
                        left = left,
                        top = top,
                        right = left + side,
                        bottom = top + side
                    )
                }
            }
    ) {
        // Image preview with rotation/crop
        val context = LocalContext.current
        val imageModel = remember(mediaFile.id) {
            ImageRequest.Builder(context)
                .data(mediaFile)
                .memoryCacheKey(mediaFile.id)
                .diskCacheKey(mediaFile.id)
                .build()
        }

        AsyncImage(
            model = imageModel,
            contentDescription = mediaFile.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .graphicsLayer {
                    rotationZ = rotationAngle.toFloat()
                },
            onState = { state ->
                android.util.Log.d("PhotoViewer", "AsyncImage state: $state")
                if (state is AsyncImagePainter.State.Success) {
                    android.util.Log.d("PhotoViewer", "Image loaded successfully. Painter: ${state.painter}")
                    imagePainter = state.painter
                } else if (state is AsyncImagePainter.State.Error) {
                    android.util.Log.e("PhotoViewer", "Image load failed: ${state.result.throwable}")
                }
            }
        )

        // Crop overlay (only in crop mode)
        if (editMode == EditMode.CROP && cropRect != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                // Larger touch target (80dp) for easier dragging
                                dragHandle = getDragHandle(offset, cropRect!!, 80f)
                            },
                            onDrag = { change, dragAmount ->
                                if (isDragging && cropRect != null) {
                                    val minSize = 100f // Minimum crop size
                                    cropRect = when (dragHandle) {
                                        DragHandle.TOP_LEFT -> cropRect!!.copy(
                                            left = (cropRect!!.left + dragAmount.x).coerceIn(0f, cropRect!!.right - minSize),
                                            top = (cropRect!!.top + dragAmount.y).coerceIn(0f, cropRect!!.bottom - minSize)
                                        )
                                        DragHandle.TOP_RIGHT -> cropRect!!.copy(
                                            right = (cropRect!!.right + dragAmount.x).coerceIn(cropRect!!.left + minSize, containerSize.width.toFloat()),
                                            top = (cropRect!!.top + dragAmount.y).coerceIn(0f, cropRect!!.bottom - minSize)
                                        )
                                        DragHandle.BOTTOM_LEFT -> cropRect!!.copy(
                                            left = (cropRect!!.left + dragAmount.x).coerceIn(0f, cropRect!!.right - minSize),
                                            bottom = (cropRect!!.bottom + dragAmount.y).coerceIn(cropRect!!.top + minSize, containerSize.height.toFloat())
                                        )
                                        DragHandle.BOTTOM_RIGHT -> cropRect!!.copy(
                                            right = (cropRect!!.right + dragAmount.x).coerceIn(cropRect!!.left + minSize, containerSize.width.toFloat()),
                                            bottom = (cropRect!!.bottom + dragAmount.y).coerceIn(cropRect!!.top + minSize, containerSize.height.toFloat())
                                        )
                                        // Add edge handles for easier resizing
                                        DragHandle.TOP_EDGE -> cropRect!!.copy(
                                            top = (cropRect!!.top + dragAmount.y).coerceIn(0f, cropRect!!.bottom - minSize)
                                        )
                                        DragHandle.BOTTOM_EDGE -> cropRect!!.copy(
                                            bottom = (cropRect!!.bottom + dragAmount.y).coerceIn(cropRect!!.top + minSize, containerSize.height.toFloat())
                                        )
                                        DragHandle.LEFT_EDGE -> cropRect!!.copy(
                                            left = (cropRect!!.left + dragAmount.x).coerceIn(0f, cropRect!!.right - minSize)
                                        )
                                        DragHandle.RIGHT_EDGE -> cropRect!!.copy(
                                            right = (cropRect!!.right + dragAmount.x).coerceIn(cropRect!!.left + minSize, containerSize.width.toFloat())
                                        )
                                        DragHandle.CENTER -> {
                                            val newLeft = (cropRect!!.left + dragAmount.x).coerceIn(0f, containerSize.width - cropRect!!.width)
                                            val newTop = (cropRect!!.top + dragAmount.y).coerceIn(0f, containerSize.height - cropRect!!.height)
                                            cropRect!!.copy(
                                                left = newLeft,
                                                top = newTop,
                                                right = newLeft + cropRect!!.width,
                                                bottom = newTop + cropRect!!.height
                                            )
                                        }
                                        null -> cropRect!!
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
                val rect = cropRect!!
                // Draw darkened overlay outside crop area
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset.Zero,
                    size = Size(size.width, rect.top)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, rect.bottom),
                    size = Size(size.width, size.height - rect.bottom)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, rect.top),
                    size = Size(rect.left, rect.height)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(rect.right, rect.top),
                    size = Size(size.width - rect.right, rect.height)
                )

                // Draw crop rectangle border
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 2f)
                )

                // Draw grid lines (thirds)
                val thirdWidth = rect.width / 3f
                val thirdHeight = rect.height / 3f

                // Vertical grid lines
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(rect.left + thirdWidth, rect.top),
                    end = Offset(rect.left + thirdWidth, rect.bottom),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(rect.left + thirdWidth * 2, rect.top),
                    end = Offset(rect.left + thirdWidth * 2, rect.bottom),
                    strokeWidth = 1f
                )

                // Horizontal grid lines
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(rect.left, rect.top + thirdHeight),
                    end = Offset(rect.right, rect.top + thirdHeight),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(rect.left, rect.top + thirdHeight * 2),
                    end = Offset(rect.right, rect.top + thirdHeight * 2),
                    strokeWidth = 1f
                )

                // Draw thick corner handles (L-shape) like Google Photos
                val cornerLength = 60f
                val cornerThickness = 8f
                val cornerColor = Color.White

                // Top Left
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.left - cornerThickness/2, rect.top),
                    end = Offset(rect.left + cornerLength, rect.top),
                    strokeWidth = cornerThickness
                )
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.left, rect.top - cornerThickness/2),
                    end = Offset(rect.left, rect.top + cornerLength),
                    strokeWidth = cornerThickness
                )

                // Top Right
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.right - cornerLength, rect.top),
                    end = Offset(rect.right + cornerThickness/2, rect.top),
                    strokeWidth = cornerThickness
                )
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.right, rect.top - cornerThickness/2),
                    end = Offset(rect.right, rect.top + cornerLength),
                    strokeWidth = cornerThickness
                )

                // Bottom Left
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.left - cornerThickness/2, rect.bottom),
                    end = Offset(rect.left + cornerLength, rect.bottom),
                    strokeWidth = cornerThickness
                )
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.left, rect.bottom - cornerLength),
                    end = Offset(rect.left, rect.bottom + cornerThickness/2),
                    strokeWidth = cornerThickness
                )

                // Bottom Right
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.right - cornerLength, rect.bottom),
                    end = Offset(rect.right + cornerThickness/2, rect.bottom),
                    strokeWidth = cornerThickness
                )
                drawLine(
                    color = cornerColor,
                    start = Offset(rect.right, rect.bottom - cornerLength),
                    end = Offset(rect.right, rect.bottom + cornerThickness/2),
                    strokeWidth = cornerThickness
                )

                // Draw edge handles (rectangular indicators at midpoint of each edge)
                val edgeHandleWidth = 60f
                val edgeHandleThickness = 8f
                val edgeColor = Color.White

                // Top edge handle
                drawLine(
                    color = edgeColor,
                    start = Offset(rect.left + rect.width / 2 - edgeHandleWidth / 2, rect.top),
                    end = Offset(rect.left + rect.width / 2 + edgeHandleWidth / 2, rect.top),
                    strokeWidth = edgeHandleThickness
                )

                // Bottom edge handle
                drawLine(
                    color = edgeColor,
                    start = Offset(rect.left + rect.width / 2 - edgeHandleWidth / 2, rect.bottom),
                    end = Offset(rect.left + rect.width / 2 + edgeHandleWidth / 2, rect.bottom),
                    strokeWidth = edgeHandleThickness
                )

                // Left edge handle
                drawLine(
                    color = edgeColor,
                    start = Offset(rect.left, rect.top + rect.height / 2 - edgeHandleWidth / 2),
                    end = Offset(rect.left, rect.top + rect.height / 2 + edgeHandleWidth / 2),
                    strokeWidth = edgeHandleThickness
                )

                // Right edge handle
                drawLine(
                    color = edgeColor,
                    start = Offset(rect.right, rect.top + rect.height / 2 - edgeHandleWidth / 2),
                    end = Offset(rect.right, rect.top + rect.height / 2 + edgeHandleWidth / 2),
                    strokeWidth = edgeHandleThickness
                )
            }
        }

        // Controls at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rotate button (only in rotate mode)
            if (editMode == EditMode.ROTATE) {
                Button(
                    onClick = {
                        rotationAngle = (rotationAngle + 90) % 360
                        onRotationChange(rotationAngle)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.RotateRight, "Rotate", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rotate 90°")
                }
            }

            // Save and Cancel buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Cancel")
                }
                    val context = LocalContext.current
                    Button(
                        onClick = {
                            when (editMode) {
                                EditMode.ROTATE -> {
                                    onSave(rotationAngle, null)
                                }
                                EditMode.CROP -> {
                                    // Convert screen coordinates to image coordinates
                                    if (imageSize.width > 0 && imageSize.height > 0 && containerSize.width > 0 && cropRect != null) {
                                        val imageLeft = (containerSize.width - imageSize.width) / 2f
                                        val imageTop = (containerSize.height - imageSize.height) / 2f

                                        val intrinsicWidth = imagePainter?.intrinsicSize?.width ?: 0f
                                        val intrinsicHeight = imagePainter?.intrinsicSize?.height ?: 0f
                                        
                                        // Ensure we have valid dimensions
                                        if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                                            val scaleX = intrinsicWidth / imageSize.width
                                            val scaleY = intrinsicHeight / imageSize.height

                                            val imageCropRect = Rect(
                                                ((cropRect!!.left - imageLeft) * scaleX).toInt().coerceAtLeast(0),
                                                ((cropRect!!.top - imageTop) * scaleY).toInt().coerceAtLeast(0),
                                                ((cropRect!!.right - imageLeft) * scaleX).toInt(),
                                                ((cropRect!!.bottom - imageTop) * scaleY).toInt()
                                            )

                                            onSave(0, imageCropRect)
                                        } else {
                                            // Fallback or error if intrinsic size is missing
                                            android.util.Log.e("PhotoViewer", "Cannot save: Image intrinsic size invalid or not loaded")
                                            android.widget.Toast.makeText(context, "Image not fully loaded, please wait", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        android.util.Log.e("PhotoViewer", "Cannot save: Invalid state. imageSize=$imageSize, cropRect=$cropRect")
                                        if (imageSize.width <= 0 || imageSize.height <= 0) {
                                            android.widget.Toast.makeText(context, "Image not loaded", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save")
                    }
            }
        }
    }
}

// Keep old function signature for compatibility, but redirect to new implementation
@Deprecated("Use EditModeOverlay instead")
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
                                    DragHandle.TOP_EDGE -> cropRect.copy(
                                        top = (cropRect.top + dragAmount.y).coerceIn(0f, cropRect.bottom - 50f)
                                    )
                                    DragHandle.BOTTOM_EDGE -> cropRect.copy(
                                        bottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + 50f, containerSize.height.toFloat())
                                    )
                                    DragHandle.LEFT_EDGE -> cropRect.copy(
                                        left = (cropRect.left + dragAmount.x).coerceIn(0f, cropRect.right - 50f)
                                    )
                                    DragHandle.RIGHT_EDGE -> cropRect.copy(
                                        right = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + 50f, containerSize.width.toFloat())
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
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    LEFT_EDGE, RIGHT_EDGE, TOP_EDGE, BOTTOM_EDGE,
    CENTER
}

private fun getDragHandle(
    offset: Offset,
    cropRect: androidx.compose.ui.geometry.Rect,
    handleSize: Float
): DragHandle? {
    // Check corners first with the provided handle size (should be large for easy touch)
    // We use a slightly larger area for corners to prioritize them over edges
    val cornerHitSize = handleSize * 1.5f

    return when {
        // Corners
        offset.x in (cropRect.left - cornerHitSize)..(cropRect.left + cornerHitSize) &&
        offset.y in (cropRect.top - cornerHitSize)..(cropRect.top + cornerHitSize) -> DragHandle.TOP_LEFT

        offset.x in (cropRect.right - cornerHitSize)..(cropRect.right + cornerHitSize) &&
        offset.y in (cropRect.top - cornerHitSize)..(cropRect.top + cornerHitSize) -> DragHandle.TOP_RIGHT

        offset.x in (cropRect.left - cornerHitSize)..(cropRect.left + cornerHitSize) &&
        offset.y in (cropRect.bottom - cornerHitSize)..(cropRect.bottom + cornerHitSize) -> DragHandle.BOTTOM_LEFT

        offset.x in (cropRect.right - cornerHitSize)..(cropRect.right + cornerHitSize) &&
        offset.y in (cropRect.bottom - cornerHitSize)..(cropRect.bottom + cornerHitSize) -> DragHandle.BOTTOM_RIGHT

        // Edges
        offset.x in (cropRect.left - handleSize)..(cropRect.left + handleSize) &&
        offset.y in cropRect.top..cropRect.bottom -> DragHandle.LEFT_EDGE

        offset.x in (cropRect.right - handleSize)..(cropRect.right + handleSize) &&
        offset.y in cropRect.top..cropRect.bottom -> DragHandle.RIGHT_EDGE

        offset.y in (cropRect.top - handleSize)..(cropRect.top + handleSize) &&
        offset.x in cropRect.left..cropRect.right -> DragHandle.TOP_EDGE

        offset.y in (cropRect.bottom - handleSize)..(cropRect.bottom + handleSize) &&
        offset.x in cropRect.left..cropRect.right -> DragHandle.BOTTOM_EDGE

        // Center
        offset.x in cropRect.left..cropRect.right &&
        offset.y in cropRect.top..cropRect.bottom -> DragHandle.CENTER

        else -> null
    }
}

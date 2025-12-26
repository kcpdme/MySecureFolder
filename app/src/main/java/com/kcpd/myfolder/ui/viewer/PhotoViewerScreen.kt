package com.kcpd.myfolder.ui.viewer

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
    viewModel: GalleryViewModel = hiltViewModel()
) {
    // Prevent screenshots and screen recording for security
    ScreenSecureEffect()

    android.util.Log.d("PhotoViewerScreen", "PhotoViewerScreen created: initialIndex=$initialIndex, category=$category")

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

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photoFiles.size - 1).coerceAtLeast(0)),
        pageCount = { photoFiles.size }
    )
    var showControls by remember { mutableStateOf(true) }

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

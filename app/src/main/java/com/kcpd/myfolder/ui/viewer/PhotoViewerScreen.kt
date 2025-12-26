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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    navController: NavController,
    initialIndex: Int = 0,
    category: String? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
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
    android.util.Log.d("ZoomableImage", "Rendering ZoomableImage for: ${mediaFile.fileName}")

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
                .data(mediaFile)  // Pass MediaFile directly - custom fetcher will decrypt it
                .crossfade(true)
                .listener(
                    onStart = {
                        android.util.Log.d("ZoomableImage", "Image load started: ${mediaFile.fileName}")
                    },
                    onSuccess = { _, _ ->
                        android.util.Log.d("ZoomableImage", "Image load SUCCESS: ${mediaFile.fileName}")
                    },
                    onError = { _, result ->
                        android.util.Log.e("ZoomableImage", "Image load ERROR: ${mediaFile.fileName}, error: ${result.throwable.message}", result.throwable)
                    }
                )
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

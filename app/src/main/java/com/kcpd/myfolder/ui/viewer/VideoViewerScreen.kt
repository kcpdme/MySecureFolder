package com.kcpd.myfolder.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.ui.gallery.GalleryViewModel
import com.kcpd.myfolder.ui.util.ScreenSecureEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoViewerScreen(
    navController: NavController,
    initialIndex: Int = 0,
    category: String? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    // Prevent screenshots and screen recording for security
    ScreenSecureEffect()

    val allMediaFiles by viewModel.mediaFiles.collectAsState()

    // Filter to show only videos
    val videoFiles = remember(allMediaFiles) {
        allMediaFiles.filter { it.mediaType == MediaType.VIDEO }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (videoFiles.size - 1).coerceAtLeast(0)),
        pageCount = { videoFiles.size }
    )
    var showControls by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val videoFile = videoFiles[page]
            VideoPlayer(
                mediaFile = videoFile,
                isCurrentPage = page == pagerState.currentPage
            )
        }

        // Top app bar
        if (showControls) {
            TopAppBar(
                title = {
                    Text(
                        if (videoFiles.isNotEmpty())
                            videoFiles[pagerState.currentPage].fileName
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
                        if (videoFiles.isNotEmpty()) {
                            viewModel.shareMediaFile(videoFiles[pagerState.currentPage])
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = {
                        if (videoFiles.isNotEmpty()) {
                            viewModel.deleteMediaFile(videoFiles[pagerState.currentPage])
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
        if (showControls && videoFiles.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${videoFiles.size}",
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
fun VideoPlayer(
    mediaFile: MediaFile,
    isCurrentPage: Boolean
) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = hiltViewModel()
    var decryptedFile by remember { mutableStateOf<java.io.File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Decrypt file on first composition
    LaunchedEffect(mediaFile.id) {
        isLoading = true
        error = null
        try {
            decryptedFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.decryptForPlayback(mediaFile)
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Decryption failed", e)
            error = "Failed to load video: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val exoPlayer = remember(decryptedFile) {
        decryptedFile?.let { file ->
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    // Pause when not current page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            exoPlayer?.pause()
        }
    }

    DisposableEffect(mediaFile.filePath) {
        onDispose {
            exoPlayer?.release()
            // Clean up temp file securely
            decryptedFile?.let { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayer", "Failed to delete temp file", e)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            error != null -> {
                Text(
                    text = error!!,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            exoPlayer != null -> {
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
    }
}

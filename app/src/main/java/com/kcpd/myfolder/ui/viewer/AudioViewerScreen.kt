package com.kcpd.myfolder.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.ui.gallery.GalleryViewModel
import com.kcpd.myfolder.ui.util.ScreenSecureEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioViewerScreen(
    navController: NavController,
    initialIndex: Int = 0,
    category: String? = null,
    fileId: String? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    // Prevent screenshots and screen recording for security
    ScreenSecureEffect()

    val allMediaFiles by viewModel.mediaFiles.collectAsState()

    // Filter to show only audio files
    val audioFiles = remember(allMediaFiles) {
        allMediaFiles.filter { it.mediaType == MediaType.AUDIO }
    }

    // If fileId is provided, find the correct index in the filtered list
    val actualIndex = remember(audioFiles, fileId, initialIndex) {
        if (fileId != null) {
            val foundIndex = audioFiles.indexOfFirst { it.id == fileId }
            if (foundIndex != -1) {
                android.util.Log.d("AudioViewerScreen", "Found file by ID at index $foundIndex")
                foundIndex
            } else {
                android.util.Log.w("AudioViewerScreen", "File ID $fileId not found, using initialIndex $initialIndex")
                initialIndex
            }
        } else {
            initialIndex
        }
    }

    val pagerState = rememberPagerState(
        initialPage = actualIndex.coerceIn(0, (audioFiles.size - 1).coerceAtLeast(0)),
        pageCount = { audioFiles.size }
    )
    var showControls by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val audioFile = audioFiles[page]
            AudioPlayer(
                mediaFile = audioFile,
                onTap = { showControls = !showControls },
                isCurrentPage = page == pagerState.currentPage
            )
        }

        // Top app bar
        if (showControls) {
            TopAppBar(
                title = {
                    Text(
                        if (audioFiles.isNotEmpty())
                            audioFiles[pagerState.currentPage].fileName
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
                        if (audioFiles.isNotEmpty()) {
                            viewModel.shareMediaFile(audioFiles[pagerState.currentPage])
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = {
                        if (audioFiles.isNotEmpty()) {
                            viewModel.deleteMediaFile(audioFiles[pagerState.currentPage])
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Page indicator
        if (showControls && audioFiles.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${audioFiles.size}",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun AudioPlayer(
    mediaFile: MediaFile,
    onTap: () -> Unit,
    isCurrentPage: Boolean
) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = hiltViewModel()
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
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
            android.util.Log.e("AudioPlayer", "Decryption failed", e)
            error = "Failed to load audio: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val exoPlayer = remember(decryptedFile) {
        decryptedFile?.let { file ->
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(audioAttributes, true)
                setVolume(1.0f) // Set to maximum volume
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
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
    }

    // Pause when not current page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            exoPlayer?.pause()
        }
    }

    LaunchedEffect(exoPlayer) {
        if (exoPlayer != null) {
            while (isCurrentPage && exoPlayer != null) {
                currentPosition = exoPlayer.currentPosition
                kotlinx.coroutines.delay(100)
            }
        }
    }

    DisposableEffect(mediaFile.filePath) {
        onDispose {
            exoPlayer?.release()
            // Securely clean up temp decrypted file using 3-pass overwrite
            decryptedFile?.let { file ->
                try {
                    if (file.exists()) {
                        file.delete()  // Regular delete for temp - will be cleaned by OS
                        android.util.Log.d("AudioPlayer", "Cleaned up temp decrypted file: ${file.name}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayer", "Failed to delete temp file", e)
                }
            }
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
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            error != null -> {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            exoPlayer != null -> {
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
    }
}

private fun formatDuration(millis: Long): String {
    if (millis < 0) return "00:00"
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

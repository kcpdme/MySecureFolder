package com.kcpd.myfolder.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.ui.audio.AudioPlaybackManager
import com.kcpd.myfolder.ui.gallery.GalleryViewModel
import com.kcpd.myfolder.ui.theme.TellaAccent
import com.kcpd.myfolder.ui.theme.TellaPurple
import com.kcpd.myfolder.ui.theme.TellaPurpleLight
import com.kcpd.myfolder.ui.util.ScreenSecureEffect
import kotlinx.coroutines.delay

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
    
    val context = LocalContext.current
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
    
    // Initialize AudioPlaybackManager with playlist
    LaunchedEffect(audioFiles) {
        AudioPlaybackManager.initialize(context)
        AudioPlaybackManager.setPlaylist(audioFiles)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TellaPurple)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val audioFile = audioFiles[page]
            AudioPlayerContent(
                mediaFile = audioFile,
                isCurrentPage = page == pagerState.currentPage,
                viewModel = viewModel
            )
        }

        // Top app bar with gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            TellaPurple.copy(alpha = 0.95f),
                            TellaPurple.copy(alpha = 0.8f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 200f
                    )
                )
                .align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    if (audioFiles.isNotEmpty()) {
                        Column {
                            Text(
                                text = audioFiles[pagerState.currentPage].fileName,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (audioFiles.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} of ${audioFiles.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        navController.navigateUp() 
                    }) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (audioFiles.isNotEmpty()) {
                            viewModel.shareMediaFile(audioFiles[pagerState.currentPage])
                        }
                    }) {
                        Icon(
                            Icons.Default.Share, 
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        if (audioFiles.isNotEmpty()) {
                            val currentFile = audioFiles[pagerState.currentPage]
                            // Stop if deleting currently playing file
                            if (AudioPlaybackManager.playbackState.value.currentMediaFile?.id == currentFile.id) {
                                AudioPlaybackManager.stop()
                            }
                            viewModel.deleteMediaFile(currentFile)
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    }
}

@Composable
fun AudioPlayerContent(
    mediaFile: MediaFile,
    isCurrentPage: Boolean,
    viewModel: GalleryViewModel
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var decryptedFile by remember { mutableStateOf<java.io.File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // Decrypt file on first composition
    LaunchedEffect(mediaFile.id) {
        isLoading = true
        error = null
        isInitialized = false
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

    // Set up audio playback when decrypted file is ready
    LaunchedEffect(decryptedFile, isCurrentPage) {
        decryptedFile?.let { file ->
            if (isCurrentPage && !isInitialized) {
                AudioPlaybackManager.play(mediaFile, file, context)
                isInitialized = true
            } else if (!isCurrentPage) {
                AudioPlaybackManager.pause()
            }
        }
    }
    
    // Update position periodically
    LaunchedEffect(isCurrentPage) {
        while (isCurrentPage) {
            AudioPlaybackManager.updatePosition()
            currentPosition = AudioPlaybackManager.currentPosition.value
            duration = AudioPlaybackManager.duration.value
            isPlaying = AudioPlaybackManager.playbackState.value.isPlaying
            delay(100)
        }
    }

    // Pause when not current page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            AudioPlaybackManager.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TellaPurple),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = TellaAccent,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Loading audio...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = error!!,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 80.dp)
                ) {
                    Spacer(modifier = Modifier.weight(0.5f))
                    
                    // Album art placeholder with music icon
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TellaPurpleLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Audio",
                            modifier = Modifier.size(100.dp),
                            tint = TellaAccent
                        )
                    }

                    // File name
                    Text(
                        text = mediaFile.fileName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.weight(0.5f))

                    // Progress section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress slider
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { newValue ->
                                val newPosition = (newValue * duration).toLong()
                                AudioPlaybackManager.seekTo(newPosition)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = TellaAccent,
                                activeTrackColor = TellaAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        // Time labels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Playback controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        // Rewind button
                        IconButton(
                            onClick = { AudioPlaybackManager.seekBackward() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }

                        // Play/Pause button
                        FloatingActionButton(
                            onClick = { AudioPlaybackManager.togglePlayPause() },
                            containerColor = TellaAccent,
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Forward button
                        IconButton(
                            onClick = { AudioPlaybackManager.seekForward() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10 seconds",
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(0.3f))
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

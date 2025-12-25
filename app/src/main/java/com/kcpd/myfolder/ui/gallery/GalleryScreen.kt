package com.kcpd.myfolder.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    var selectedFile by remember { mutableStateOf<MediaFile?>(null) }
    var showS3Dialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Files") },
                actions = {
                    IconButton(onClick = { navController.navigate("s3_config") }) {
                        Icon(Icons.Default.Settings, "S3 Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("camera") }
            ) {
                Icon(Icons.Default.Add, "Add Media")
            }
        }
    ) { padding ->
        if (mediaFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No files yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to capture photos, videos, or audio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mediaFiles.size) { index ->
                    val mediaFile = mediaFiles[index]
                    MediaThumbnail(
                        mediaFile = mediaFile,
                        onClick = {
                            // Navigate to full-screen viewer
                            navController.navigate("media_viewer/$index")
                        },
                        onLongClick = {
                            // Show detail dialog on long press
                            selectedFile = mediaFile
                        }
                    )
                }
            }
        }

        selectedFile?.let { file ->
            MediaDetailDialog(
                mediaFile = file,
                onDismiss = { selectedFile = null },
                onDelete = {
                    viewModel.deleteMediaFile(it)
                    selectedFile = null
                },
                onUpload = {
                    showS3Dialog = true
                },
                onShare = { viewModel.shareMediaFile(it) }
            )
        }

        if (showS3Dialog && selectedFile != null) {
            UploadDialog(
                mediaFile = selectedFile!!,
                onDismiss = { showS3Dialog = false },
                onUpload = { file ->
                    viewModel.uploadToS3(file)
                    showS3Dialog = false
                    selectedFile = null
                }
            )
        }
    }
}

@Composable
fun MediaThumbnail(
    mediaFile: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() }
                )
            }
    ) {
        when (mediaFile.mediaType) {
            MediaType.PHOTO -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaFile.filePath)
                        .crossfade(true)
                        .build(),
                    contentDescription = mediaFile.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaType.VIDEO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(mediaFile.filePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = mediaFile.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            MediaType.AUDIO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Audio",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            MediaType.NOTE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Note,
                        contentDescription = "Note",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }

        if (mediaFile.isUploaded) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = "Uploaded",
                tint = Color.White,
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailDialog(
    mediaFile: MediaFile,
    onDismiss: () -> Unit,
    onDelete: (MediaFile) -> Unit,
    onUpload: (MediaFile) -> Unit,
    onShare: (MediaFile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mediaFile.fileName) },
        text = {
            Column {
                when (mediaFile.mediaType) {
                    MediaType.PHOTO -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(mediaFile.filePath)
                                .crossfade(true)
                                .build(),
                            contentDescription = mediaFile.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    MediaType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    MediaType.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Audio",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    MediaType.NOTE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Note,
                                contentDescription = "Note",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Type: ${mediaFile.mediaType.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Size: ${formatFileSize(mediaFile.size)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Created: ${formatDate(mediaFile.createdAt)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mediaFile.isUploaded && mediaFile.s3Url != null) {
                    Text(
                        "Status: Uploaded to S3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!mediaFile.isUploaded) {
                    TextButton(onClick = { onUpload(mediaFile) }) {
                        Icon(Icons.Default.CloudUpload, "Upload")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload")
                    }
                }
                TextButton(onClick = { onShare(mediaFile) }) {
                    Icon(Icons.Default.Share, "Share")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(
                    onClick = { onDelete(mediaFile) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, "Delete")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun UploadDialog(
    mediaFile: MediaFile,
    onDismiss: () -> Unit,
    onUpload: (MediaFile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudUpload, "Upload") },
        title = { Text("Upload to S3/Minio") },
        text = { Text("Upload ${mediaFile.fileName} to cloud storage?") },
        confirmButton = {
            TextButton(onClick = { onUpload(mediaFile) }) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

package com.kcpd.myfolder.ui.folder

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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onMediaClick: (Int) -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    var selectedFile by remember { mutableStateOf<MediaFile?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isMultiSelectMode)
                            "${selectedFiles.size} selected"
                        else
                            viewModel.category.displayName
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isMultiSelectMode) {
                            isMultiSelectMode = false
                            selectedFiles = emptySet()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        IconButton(
                            onClick = {
                                if (selectedFiles.size == mediaFiles.size) {
                                    selectedFiles = emptySet()
                                } else {
                                    selectedFiles = mediaFiles.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                if (selectedFiles.size == mediaFiles.size)
                                    Icons.Default.CheckBoxOutlineBlank
                                else
                                    Icons.Default.CheckBox,
                                "Select All"
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedFiles.forEach { id ->
                                    mediaFiles.find { it.id == id }?.let { file ->
                                        viewModel.deleteFile(file)
                                    }
                                }
                                isMultiSelectMode = false
                                selectedFiles = emptySet()
                            },
                            enabled = selectedFiles.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete Selected",
                                tint = if (selectedFiles.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        if (mediaFiles.isNotEmpty()) {
                            IconButton(onClick = {
                                isMultiSelectMode = true
                            }) {
                                Icon(Icons.Default.CheckCircle, "Select")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(getActionIcon(viewModel.category), "Add ${viewModel.category.displayName}")
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
                        viewModel.category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No ${viewModel.category.displayName.lowercase()} yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        getEmptyStateMessage(viewModel.category),
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
                        isSelected = selectedFiles.contains(mediaFile.id),
                        isMultiSelectMode = isMultiSelectMode,
                        onClick = {
                            if (isMultiSelectMode) {
                                selectedFiles = if (selectedFiles.contains(mediaFile.id)) {
                                    selectedFiles - mediaFile.id
                                } else {
                                    selectedFiles + mediaFile.id
                                }
                            } else {
                                onMediaClick(index)
                            }
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) {
                                isMultiSelectMode = true
                                selectedFiles = setOf(mediaFile.id)
                            }
                        },
                        isUploading = viewModel.isUploading(mediaFile.id)
                    )
                }
            }
        }

        selectedFile?.let { file ->
            MediaDetailDialog(
                mediaFile = file,
                onDismiss = { selectedFile = null },
                onDelete = {
                    viewModel.deleteFile(it)
                    selectedFile = null
                },
                onUpload = {
                    showUploadDialog = true
                }
            )
        }

        if (showUploadDialog && selectedFile != null) {
            UploadDialog(
                mediaFile = selectedFile!!,
                onDismiss = { showUploadDialog = false },
                onUpload = { file ->
                    viewModel.uploadFile(file)
                    showUploadDialog = false
                    selectedFile = null
                }
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    mediaFile: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isUploading: Boolean,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Note,
                            contentDescription = "Note",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaFile.fileName.removeSuffix(".txt"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2
                        )
                    }
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

        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Multi-select overlay
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else
                            Color.Transparent
                    )
            )

            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .align(Alignment.TopStart)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .padding(2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDetailDialog(
    mediaFile: MediaFile,
    onDismiss: () -> Unit,
    onDelete: (MediaFile) -> Unit,
    onUpload: (MediaFile) -> Unit
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
private fun UploadDialog(
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

@Composable
private fun getActionIcon(category: FolderCategory) = when (category) {
    FolderCategory.PHOTOS -> Icons.Default.CameraAlt
    FolderCategory.VIDEOS -> Icons.Default.Videocam
    FolderCategory.RECORDINGS -> Icons.Default.Mic
    FolderCategory.NOTES -> Icons.Default.Edit
}

private fun getEmptyStateMessage(category: FolderCategory): String {
    return when (category) {
        FolderCategory.PHOTOS -> "Tap + to capture photos"
        FolderCategory.VIDEOS -> "Tap + to record videos"
        FolderCategory.RECORDINGS -> "Tap + to record audio"
        FolderCategory.NOTES -> "Tap + to create a note"
    }
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

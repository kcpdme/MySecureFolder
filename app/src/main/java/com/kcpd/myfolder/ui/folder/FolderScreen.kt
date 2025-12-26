package com.kcpd.myfolder.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

enum class FolderViewMode {
    GRID, LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    onBackClick: () -> Unit,
    onAddClick: (String?) -> Unit,
    onMediaClick: (Int) -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()

    // Log media files data
    android.util.Log.d("FolderScreen", "Category: ${viewModel.category}")
    android.util.Log.d("FolderScreen", "MediaFiles count: ${mediaFiles.size}")
    mediaFiles.forEachIndexed { index, file ->
        android.util.Log.d("FolderScreen", "[$index] File: ${file.fileName}, Type: ${file.mediaType}, Path: ${file.filePath}")
    }

    var selectedFile by remember { mutableStateOf<MediaFile?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    val hasContent = folders.isNotEmpty() || mediaFiles.isNotEmpty()
    val selectedCount = selectedFiles.size + selectedFolders.size
    val totalCount = mediaFiles.size + folders.size

    // Default to LIST mode for Notes and Recordings, GRID for others
    val defaultViewMode = when (viewModel.category) {
        FolderCategory.NOTES, FolderCategory.RECORDINGS -> FolderViewMode.LIST
        else -> FolderViewMode.GRID
    }
    var viewMode by remember { mutableStateOf(defaultViewMode) }

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
                            selectedFolders = emptySet()
                        } else if (currentFolderId != null) {
                            // Navigate up from folder
                            val parentId = currentFolder?.parentFolderId
                            viewModel.navigateToFolder(parentId)
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        // Select All button - modern circular design
                        IconButton(
                            onClick = {
                                if (selectedCount == totalCount && selectedCount > 0) {
                                    // All selected, deselect all
                                    selectedFiles = emptySet()
                                    selectedFolders = emptySet()
                                } else {
                                    // Some or none selected, select all
                                    selectedFiles = mediaFiles.map { it.id }.toSet()
                                    selectedFolders = folders.map { it.id }.toSet()
                                }
                            }
                        ) {
                            when {
                                selectedCount == totalCount && selectedCount > 0 -> {
                                    // All selected - filled circle with checkmark
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Done,
                                            contentDescription = "Deselect All",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                selectedCount > 0 -> {
                                    // Some selected - filled circle with minus
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Select All",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                else -> {
                                    // None selected - empty circle
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        // Move button - always visible in multi-select mode
                        IconButton(
                            onClick = { showMoveDialog = true },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                Icons.Default.DriveFileMove,
                                "Move",
                                tint = if (selectedCount > 0)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        // Share button - always visible in multi-select mode
                        IconButton(
                            onClick = {
                                val selectedMediaFiles = mediaFiles.filter { selectedFiles.contains(it.id) }
                                if (selectedMediaFiles.isNotEmpty()) {
                                    if (selectedMediaFiles.size == 1) {
                                        viewModel.shareMediaFile(selectedMediaFiles[0])
                                    } else {
                                        FolderActions.shareMultipleFiles(context, selectedMediaFiles)
                                    }
                                }
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                Icons.Default.Share,
                                "Share",
                                tint = if (selectedCount > 0)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        // Delete button - always visible in multi-select mode
                        IconButton(
                            onClick = {
                                selectedFiles.forEach { id ->
                                    mediaFiles.find { it.id == id }?.let { file ->
                                        viewModel.deleteFile(file)
                                    }
                                }
                                selectedFolders.forEach { id ->
                                    viewModel.deleteFolder(id)
                                }
                                isMultiSelectMode = false
                                selectedFiles = emptySet()
                                selectedFolders = emptySet()
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete Selected",
                                tint = if (selectedCount > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, "Create Folder")
                        }
                        IconButton(onClick = {
                            viewMode = if (viewMode == FolderViewMode.GRID) FolderViewMode.LIST else FolderViewMode.GRID
                        }) {
                            Icon(
                                if (viewMode == FolderViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                "Toggle View"
                            )
                        }
                        if (hasContent) {
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
            FloatingActionButton(onClick = { onAddClick(currentFolderId) }) {
                Icon(getActionIcon(viewModel.category), "Add ${viewModel.category.displayName}")
            }
        }
    ) { padding ->
        if (!hasContent) {
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
            if (viewMode == FolderViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = padding,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Folders first
                    items(folders.size) { index ->
                        val folder = folders[index]
                        FolderThumbnail(
                            folder = folder,
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedFolders = if (selectedFolders.contains(folder.id)) {
                                        selectedFolders - folder.id
                                    } else {
                                        selectedFolders + folder.id
                                    }
                                } else {
                                    viewModel.navigateToFolder(folder.id)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedFolders = setOf(folder.id)
                                }
                            },
                            isSelected = selectedFolders.contains(folder.id),
                            isMultiSelectMode = isMultiSelectMode
                        )
                    }

                    // Then media files
                    items(mediaFiles.size) { index ->
                        val mediaFile = mediaFiles[index]
                        android.util.Log.d("FolderScreen_Grid", "Rendering grid item [$index]: ${mediaFile.fileName}")
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
                                    android.util.Log.d("FolderScreen_Grid", "Grid item clicked: index=$index, file=${mediaFile.fileName}")
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
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Folders first
                    items(folders.size) { index ->
                        val folder = folders[index]
                        FolderListItem(
                            folder = folder,
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedFolders = if (selectedFolders.contains(folder.id)) {
                                        selectedFolders - folder.id
                                    } else {
                                        selectedFolders + folder.id
                                    }
                                } else {
                                    viewModel.navigateToFolder(folder.id)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedFolders = setOf(folder.id)
                                }
                            },
                            isSelected = selectedFolders.contains(folder.id),
                            isMultiSelectMode = isMultiSelectMode
                        )
                    }

                    // Then media files
                    items(mediaFiles.size) { index ->
                        val mediaFile = mediaFiles[index]
                        android.util.Log.d("FolderScreen_List", "Rendering list item [$index]: ${mediaFile.fileName}")
                        FolderMediaListItem(
                            mediaFile = mediaFile,
                            isSelected = selectedFiles.contains(mediaFile.id),
                            isMultiSelectMode = isMultiSelectMode,
                            isUploading = viewModel.isUploading(mediaFile.id),
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedFiles = if (selectedFiles.contains(mediaFile.id)) {
                                        selectedFiles - mediaFile.id
                                    } else {
                                        selectedFiles + mediaFile.id
                                    }
                                } else {
                                    android.util.Log.d("FolderScreen_List", "List item clicked: index=$index, file=${mediaFile.fileName}")
                                    onMediaClick(index)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedFiles = setOf(mediaFile.id)
                                }
                            }
                        )
                    }
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

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { showCreateFolderDialog = false },
                onConfirm = { name, color ->
                    viewModel.createFolder(name, color)
                    showCreateFolderDialog = false
                }
            )
        }

        if (showMoveDialog) {
            MoveToFolderDialog(
                folders = folders,
                currentFolderId = currentFolderId,
                onDismiss = { showMoveDialog = false },
                onConfirm = { targetFolderId ->
                    selectedFiles.forEach { fileId ->
                        mediaFiles.find { it.id == fileId }?.let { file ->
                            viewModel.moveToFolder(file, targetFolderId)
                        }
                    }
                    showMoveDialog = false
                    isMultiSelectMode = false
                    selectedFiles = emptySet()
                    selectedFolders = emptySet()
                }
            )
        }
    }
}

@Composable
internal fun MediaThumbnail(
    mediaFile: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isUploading: Boolean,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    android.util.Log.d("MediaThumbnail", "Rendering thumbnail for: ${mediaFile.fileName}, type: ${mediaFile.mediaType}")

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .pointerInput(mediaFile.id) {
                detectTapGestures(
                    onTap = {
                        android.util.Log.d("MediaThumbnail", "Thumbnail tapped: ${mediaFile.fileName}")
                        onClick()
                    },
                    onLongPress = {
                        android.util.Log.d("MediaThumbnail", "Thumbnail long pressed: ${mediaFile.fileName}")
                        onLongClick()
                    }
                )
            }
    ) {
        when (mediaFile.mediaType) {
            MediaType.PHOTO -> {
                android.util.Log.d("MediaThumbnail", "Loading photo: ${mediaFile.fileName}, has thumbnail: ${mediaFile.thumbnail != null}")
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        // Use thumbnail if available (fast, no decryption), otherwise fall back to full file
                        .data(mediaFile.thumbnail ?: mediaFile)
                        .crossfade(true)
                        .listener(
                            onStart = {
                                android.util.Log.d("MediaThumbnail", "Image load started: ${mediaFile.fileName}")
                            },
                            onSuccess = { _, _ ->
                                android.util.Log.d("MediaThumbnail", "Image load SUCCESS: ${mediaFile.fileName}")
                            },
                            onError = { _, result ->
                                android.util.Log.e("MediaThumbnail", "Image load ERROR: ${mediaFile.fileName}, error: ${result.throwable.message}", result.throwable)
                            }
                        )
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
                            // Use thumbnail if available (fast, no decryption), otherwise fall back to full file
                            .data(mediaFile.thumbnail ?: mediaFile)
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

        // Modern selection indicator - small circular badge
        if (isMultiSelectMode && isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Border animation for selection
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(0.dp)
                    )
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
                                .data(mediaFile)  // Pass MediaFile directly for decryption
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

@Composable
internal fun FolderMediaListItem(
    mediaFile: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isUploading: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(mediaFile.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier.size(60.dp)
            ) {
                when (mediaFile.mediaType) {
                    MediaType.PHOTO -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                // Use thumbnail if available, otherwise fall back to full file
                                .data(mediaFile.thumbnail ?: mediaFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = mediaFile.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    MediaType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    // Use thumbnail if available, otherwise fall back to full file
                                    .data(mediaFile.thumbnail ?: mediaFile)
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    MediaType.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Audio",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    MediaType.NOTE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Note,
                                contentDescription = "Note",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (mediaFile.isUploaded) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = "Uploaded",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp)
                            .align(Alignment.TopEnd)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(2.dp)
                    )
                }
            }

            // File info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = mediaFile.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mediaFile.mediaType.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatFileSize(mediaFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = formatDate(mediaFile.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Selection indicator - modern circular design
            if (isMultiSelectMode) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        }
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

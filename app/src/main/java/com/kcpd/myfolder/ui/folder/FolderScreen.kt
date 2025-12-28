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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import kotlinx.coroutines.launch
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
    onMediaClick: (Int, MediaFile) -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val uploadingFiles by viewModel.uploadingFiles.collectAsState()
    val uploadQueue by viewModel.uploadQueue.collectAsState()
    val uploadResults by viewModel.uploadResults.collectAsState()

    val scope = rememberCoroutineScope()

    // Log media files data
    android.util.Log.d("FolderScreen", "Category: ${viewModel.category}")
    android.util.Log.d("FolderScreen", "MediaFiles count: ${mediaFiles.size}")
    android.util.Log.d("FolderScreen", "Uploading files: ${uploadingFiles.size} files: $uploadingFiles")
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
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // current/total
    var showSearch by remember { mutableStateOf(false) }
    var showUploadQueue by remember { mutableStateOf(false) }

    val hasContent = folders.isNotEmpty() || mediaFiles.isNotEmpty()
    val selectedCount = selectedFiles.size + selectedFolders.size
    val totalCount = mediaFiles.size + folders.size

    // Default to LIST mode for all categories
    val defaultViewMode = FolderViewMode.LIST
    var viewMode by remember { mutableStateOf(defaultViewMode) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar when import error occurs
    LaunchedEffect(importErrorMessage) {
        importErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    val scope = rememberCoroutineScope()
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

                        // Upload button - always visible in multi-select mode
                        IconButton(
                            onClick = {
                                scope.launch {
                                    // Get directly selected files
                                    val selectedMediaFiles = mediaFiles.filter { selectedFiles.contains(it.id) }

                                    // Get files from selected folders
                                    val filesFromFolders = if (selectedFolders.isNotEmpty()) {
                                        viewModel.getFilesFromFolders(selectedFolders)
                                    } else {
                                        emptyList()
                                    }

                                    // Combine and upload all files
                                    val allFilesToUpload = (selectedMediaFiles + filesFromFolders).distinctBy { it.id }
                                    viewModel.uploadFiles(allFilesToUpload)

                                    // Clear selection and exit multi-select mode
                                    selectedFiles = emptySet()
                                    selectedFolders = emptySet()
                                    isMultiSelectMode = false
                                    // Show upload queue bottom sheet
                                    showUploadQueue = true
                                }
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                "Upload Selected",
                                tint = if (selectedCount > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
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
                        val scope = rememberCoroutineScope()
                        val importFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                android.util.Log.d("Import", "Selected ${uris.size} files to import")
                                isImporting = true
                                importErrorMessage = null
                                scope.launch {
                                    try {
                                        viewModel.importFiles(uris).collect { progress ->
                                            android.util.Log.d("Import", "Progress: $progress")
                                            when (progress) {
                                                is com.kcpd.myfolder.domain.usecase.ImportProgress.Importing -> {
                                                    importProgress = Pair(progress.currentFile, progress.totalFiles)
                                                }
                                                is com.kcpd.myfolder.domain.usecase.ImportProgress.FileImported -> {
                                                    android.util.Log.d("Import", "Imported: ${progress.mediaFile.fileName}")
                                                }
                                                is com.kcpd.myfolder.domain.usecase.ImportProgress.Error -> {
                                                    android.util.Log.e("Import", "Error importing ${progress.fileName}: ${progress.error.message}", progress.error)
                                                    importErrorMessage = "Error importing ${progress.fileName}: ${progress.error.message}"
                                                }
                                                is com.kcpd.myfolder.domain.usecase.ImportProgress.Completed -> {
                                                    android.util.Log.d("Import", "Import completed: ${progress.totalImported} files")
                                                    isImporting = false
                                                    importProgress = null
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Import", "Import failed", e)
                                        importErrorMessage = "Import failed: ${e.message}"
                                        isImporting = false
                                        importProgress = null
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { importFileLauncher.launch("*/*") },
                            enabled = !isImporting
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.FileDownload, "Import Files")
                            }
                        }
                        // Don't allow folder creation in ALL_FILES category
                        if (viewModel.category != FolderCategory.ALL_FILES) {
                            IconButton(onClick = { showCreateFolderDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, "Create Folder")
                            }
                        }
                        IconButton(onClick = {
                            viewMode = if (viewMode == FolderViewMode.GRID) FolderViewMode.LIST else FolderViewMode.GRID
                        }) {
                            Icon(
                                if (viewMode == FolderViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                "Toggle View"
                            )
                        }
                        // Search icon for ALL_FILES category
                        if (viewModel.category == FolderCategory.ALL_FILES) {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    "Search"
                                )
                            }
                        }
                        if (hasContent) {
                            IconButton(onClick = {
                                isMultiSelectMode = true
                            }) {
                                Icon(Icons.Default.CheckCircle, "Select")
                            }

                            // Overflow menu for bulk actions
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "More options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Upload All Files") },
                                        leadingIcon = {
                                            Icon(Icons.Default.CloudUpload, "Upload All")
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.uploadFiles(mediaFiles)
                                            showUploadQueue = true
                                        }
                                    )
                                }
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
            if (viewModel.category != FolderCategory.ALL_FILES) {
                FloatingActionButton(onClick = { onAddClick(currentFolderId) }) {
                    Icon(getActionIcon(viewModel.category), "Add ${viewModel.category.displayName}")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Floating upload status indicator (replaces simple progress bar)
            val totalUploads = uploadingFiles.size + uploadQueue.size
            if (totalUploads > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUploadQueue = true },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = if (uploadingFiles.size > 0)
                                        "Uploading ${uploadingFiles.size} file${if (uploadingFiles.size > 1) "s" else ""}"
                                    else
                                        "Preparing uploads...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                if (uploadQueue.size > 0) {
                                    Text(
                                        text = "${uploadQueue.size} in queue",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = "Show upload details",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Search bar for ALL_FILES category
            if (viewModel.category == FolderCategory.ALL_FILES && showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search files...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }

            if (!hasContent) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
                    if (viewModel.category != FolderCategory.ALL_FILES) {
                        Text(
                            getEmptyStateMessage(viewModel.category),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        } else {
            if (viewMode == FolderViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(
                        start = 13.dp,
                        end = 13.dp,
                        top = 0.dp,
                        bottom = 0.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    items(mediaFiles, key = { it.id }) { mediaFile ->
                        val index = mediaFiles.indexOf(mediaFile)
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
                                    onMediaClick(index, mediaFile)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedFiles = setOf(mediaFile.id)
                                }
                            },
                            isUploading = uploadingFiles.contains(mediaFile.id),
                            uploadResult = uploadResults[mediaFile.id],
                            onUploadClick = { viewModel.uploadFile(mediaFile) },
                            onErrorClick = { msg ->
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = msg,
                                        actionLabel = "Retry",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.uploadFile(mediaFile)
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
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
                    items(mediaFiles.size, key = { mediaFiles[it].id }) { index ->
                        val mediaFile = mediaFiles[index]
                        android.util.Log.d("FolderScreen_List", "Rendering list item [$index]: ${mediaFile.fileName}")
                        FolderMediaListItem(
                            mediaFile = mediaFile,
                            isSelected = selectedFiles.contains(mediaFile.id),
                            isMultiSelectMode = isMultiSelectMode,
                            isUploading = uploadingFiles.contains(mediaFile.id),
                            uploadResult = uploadResults[mediaFile.id],
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedFiles = if (selectedFiles.contains(mediaFile.id)) {
                                        selectedFiles - mediaFile.id
                                    } else {
                                        selectedFiles + mediaFile.id
                                    }
                                } else {
                                    android.util.Log.d("FolderScreen_List", "List item clicked: index=$index, file=${mediaFile.fileName}")
                                    onMediaClick(index, mediaFile)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedFiles = setOf(mediaFile.id)
                                }
                            },
                            onUploadClick = { viewModel.uploadFile(mediaFile) },
                            onErrorClick = { msg ->
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = msg,
                                        actionLabel = "Retry",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.uploadFile(mediaFile)
                                    }
                                }
                            }
                        )
                    }
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

        // Import progress overlay
        if (isImporting && importProgress != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Importing files...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${importProgress?.first} / ${importProgress?.second}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Upload Queue Bottom Sheet
        if (showUploadQueue) {
            ModalBottomSheet(
                onDismissRequest = { showUploadQueue = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                UploadQueueSheet(
                    uploadingFiles = uploadingFiles,
                    queuedFiles = uploadQueue,
                    allFiles = mediaFiles,
                    onDismiss = { showUploadQueue = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadQueueSheet(
    uploadingFiles: Set<String>,
    queuedFiles: List<String>,
    allFiles: List<MediaFile>,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Upload Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "${uploadingFiles.size} uploading, ${queuedFiles.size} waiting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        Divider()

        // Uploading files section
        if (uploadingFiles.isNotEmpty()) {
            Text(
                text = "Uploading Now",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.primary
            )

            uploadingFiles.forEach { fileId ->
                val file = allFiles.find { it.id == fileId }
                file?.let {
                    UploadQueueItem(
                        fileName = it.fileName,
                        fileSize = it.size,
                        status = UploadStatus.UPLOADING
                    )
                }
            }
        }

        // Queued files section
        if (queuedFiles.isNotEmpty()) {
            Text(
                text = "Waiting in Queue",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            queuedFiles.forEach { fileId ->
                val file = allFiles.find { it.id == fileId }
                file?.let {
                    UploadQueueItem(
                        fileName = it.fileName,
                        fileSize = it.size,
                        status = UploadStatus.QUEUED
                    )
                }
            }
        }

        // Empty state
        if (uploadingFiles.isEmpty() && queuedFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No uploads in progress",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "All files uploaded successfully",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

enum class UploadStatus {
    UPLOADING, QUEUED
}

@Composable
fun UploadQueueItem(
    fileName: String,
    fileSize: Long,
    status: UploadStatus
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        if (status == UploadStatus.UPLOADING) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                Icons.Default.Schedule,
                contentDescription = "Queued",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        // File info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(fileSize),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status text
        Text(
            text = if (status == UploadStatus.UPLOADING) "Uploading..." else "Waiting",
            style = MaterialTheme.typography.bodySmall,
            color = if (status == UploadStatus.UPLOADING)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

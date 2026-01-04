package com.kcpd.myfolder.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.ui.upload.UploadStatusScreen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFolderClick: (FolderCategory) -> Unit,
    onSettingsClick: () -> Unit,
    onCloudRemotesClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRecorderClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val allFilesCount by viewModel.allFilesCount.collectAsState()
    val photosCount by viewModel.photosCount.collectAsState()
    val videosCount by viewModel.videosCount.collectAsState()
    val recordingsCount by viewModel.recordingsCount.collectAsState()
    val notesCount by viewModel.notesCount.collectAsState()
    val pdfsCount by viewModel.pdfsCount.collectAsState()
    val otherCount by viewModel.otherCount.collectAsState()

    val allFilesSize by viewModel.allFilesSize.collectAsState()
    val photosSize by viewModel.photosSize.collectAsState()
    val videosSize by viewModel.videosSize.collectAsState()
    val recordingsSize by viewModel.recordingsSize.collectAsState()
    val notesSize by viewModel.notesSize.collectAsState()
    val pdfsSize by viewModel.pdfsSize.collectAsState()
    val otherSize by viewModel.otherSize.collectAsState()
    
    val activeRemoteType by viewModel.activeRemoteType.collectAsState()
    
    // Upload status states for sync indicator
    val uploadStates by viewModel.uploadStates.collectAsState()
    val activeUploadsCount by viewModel.activeUploadsCount.collectAsState()
    val pendingQueueCount by viewModel.pendingQueueCount.collectAsState()
    val showUploadSheet by viewModel.showUploadSheet.collectAsState()
    
    // Calculate upload summary for sync indicator
    // Use file-based counting for intuitive display
    val inProgressFilesCount = uploadStates.values.count { it.activeCount > 0 }
    val hasActiveUploads = inProgressFilesCount > 0 || pendingQueueCount > 0
    val hasUploadStates = uploadStates.isNotEmpty()
    val completedCount = uploadStates.values.count { it.isComplete }
    val totalCount = uploadStates.size
    val failedCount = uploadStates.values.count { it.allFailed }
    
    // Infinite rotation animation for sync icon when uploads are active
    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Debug logging only in debug builds
    LaunchedEffect(Unit) {
        if (com.kcpd.myfolder.BuildConfig.DEBUG) {
            val buildTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            android.util.Log.d("HomeScreen", "═══════════════════════════════════════")
            android.util.Log.d("HomeScreen", "🏠 HOME SCREEN v2.0 LOADED 🏠")
            android.util.Log.d("HomeScreen", "Build Time: $buildTime")
            android.util.Log.d("HomeScreen", "═══════════════════════════════════════")
        }
    }

    var selectedItem by remember { mutableStateOf("Folders") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("My Folder")
                        Text(
                            text = "Remote: ${if (activeRemoteType == com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) "Google Drive" else "S3 MinIO"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Sync Status Icon - shows when there are uploads
                    if (hasUploadStates) {
                        BadgedBox(
                            badge = {
                                if (hasActiveUploads) {
                                    // Show in-progress files count as badge
                                    // If we have in-progress files from memory, use that; otherwise show pending count
                                    val badgeCount = if (inProgressFilesCount > 0) inProgressFilesCount 
                                                    else (totalCount - completedCount).coerceAtLeast(1)
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "$badgeCount",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                } else if (failedCount > 0) {
                                    // Show failed count badge in red
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = "$failedCount",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = { viewModel.showUploadSheet() }) {
                                Icon(
                                    imageVector = if (hasActiveUploads) Icons.Default.Sync else Icons.Default.CloudUpload,
                                    contentDescription = "Upload Status",
                                    tint = when {
                                        hasActiveUploads -> MaterialTheme.colorScheme.primary
                                        failedCount > 0 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = if (hasActiveUploads) {
                                        Modifier.rotate(rotationAngle)
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                    }
                    
                    IconButton(onClick = onCloudRemotesClick) {
                        Icon(Icons.Default.Cloud, contentDescription = "Cloud Remotes")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                    label = { Text("Folders") },
                    selected = selectedItem == "Folders",
                    onClick = { selectedItem = "Folders" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Camera") },
                    label = { Text("Camera") },
                    selected = selectedItem == "Camera",
                    onClick = { onCameraClick() }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Recorder") },
                    label = { Text("Recorder") },
                    selected = selectedItem == "Recorder",
                    onClick = { onRecorderClick() }
                )
            }
        }
    ) { paddingValues ->
        if (selectedItem == "Folders") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Landscape tiles like Tella
                FolderCard(
                    category = FolderCategory.ALL_FILES,
                    count = allFilesCount,
                    size = viewModel.formatFileSize(allFilesSize),
                    onClick = { onFolderClick(FolderCategory.ALL_FILES) },
                    modifier = Modifier.fillMaxWidth()
                )
                FolderCard(
                    category = FolderCategory.PHOTOS,
                    count = photosCount,
                    size = viewModel.formatFileSize(photosSize),
                    onClick = { onFolderClick(FolderCategory.PHOTOS) },
                    modifier = Modifier.fillMaxWidth()
                )
                FolderCard(
                    category = FolderCategory.VIDEOS,
                    count = videosCount,
                    size = viewModel.formatFileSize(videosSize),
                    onClick = { onFolderClick(FolderCategory.VIDEOS) },
                    modifier = Modifier.fillMaxWidth()
                )
                FolderCard(
                    category = FolderCategory.RECORDINGS,
                    count = recordingsCount,
                    size = viewModel.formatFileSize(recordingsSize),
                    onClick = { onFolderClick(FolderCategory.RECORDINGS) },
                    modifier = Modifier.fillMaxWidth()
                )
                FolderCard(
                    category = FolderCategory.NOTES,
                    count = notesCount,
                    size = viewModel.formatFileSize(notesSize),
                    onClick = { onFolderClick(FolderCategory.NOTES) },
                    modifier = Modifier.fillMaxWidth()
                )
                FolderCard(
                    category = FolderCategory.PDFS,
                    count = pdfsCount,
                    size = viewModel.formatFileSize(pdfsSize),
                    onClick = { onFolderClick(FolderCategory.PDFS) },
                    modifier = Modifier.fillMaxWidth()
                )
                FolderCard(
                    category = FolderCategory.OTHER,
                    count = otherCount,
                    size = viewModel.formatFileSize(otherSize),
                    onClick = { onFolderClick(FolderCategory.OTHER) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Upload Status Screen (full-screen dialog)
    if (showUploadSheet && uploadStates.isNotEmpty()) {
        UploadStatusScreen(
            uploadStates = uploadStates,
            onDismiss = { viewModel.dismissUploadSheet() },
            onRetry = { fileId, remoteId -> viewModel.retryUpload(fileId, remoteId) },
            onClearCompleted = { viewModel.clearCompletedUploads() },
            onCancelAllPending = { viewModel.cancelAllPendingUploads() },
            onRetryAllFailed = { viewModel.retryAllFailedUploads() },
            pendingQueueCount = pendingQueueCount
        )
    }
}

@Composable
fun FolderCard(
    category: FolderCategory,
    count: Int,
    size: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = when (category) {
        FolderCategory.ALL_FILES -> Color(0xFFFFC107) // Mustard
        FolderCategory.PHOTOS -> Color(0xFF4CAF50) // Green
        FolderCategory.VIDEOS -> Color(0xFF2196F3) // Blue
        FolderCategory.RECORDINGS -> Color(0xFFF44336) // Crimson
        FolderCategory.NOTES -> Color.White
        FolderCategory.PDFS -> Color(0xFFE57373) // Light Red
        FolderCategory.OTHER -> Color(0xFFFFC107) // Reuse existing color
    }

    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.displayName,
                    modifier = Modifier.size(40.dp),
                    tint = iconColor
                )
                Column {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    if (count > 0) {
                        Text(
                            text = "$count files • $size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Right: Arrow icon
            if (count > 0) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun getActionIcon(category: FolderCategory) = when (category) {
    FolderCategory.PHOTOS -> Icons.Default.CameraAlt
    FolderCategory.VIDEOS -> Icons.Default.Videocam
    FolderCategory.RECORDINGS -> Icons.Default.Mic
    FolderCategory.NOTES -> Icons.Default.Edit
    FolderCategory.PDFS -> Icons.Default.FileUpload
    FolderCategory.OTHER -> Icons.Default.Folder
    FolderCategory.ALL_FILES -> Icons.Default.Folder
}

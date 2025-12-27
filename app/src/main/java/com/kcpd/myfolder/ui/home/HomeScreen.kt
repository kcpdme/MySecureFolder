package com.kcpd.myfolder.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kcpd.myfolder.data.model.FolderCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFolderClick: (FolderCategory) -> Unit,
    onSettingsClick: () -> Unit,
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

    val allFilesSize by viewModel.allFilesSize.collectAsState()
    val photosSize by viewModel.photosSize.collectAsState()
    val videosSize by viewModel.videosSize.collectAsState()
    val recordingsSize by viewModel.recordingsSize.collectAsState()
    val notesSize by viewModel.notesSize.collectAsState()
    val pdfsSize by viewModel.pdfsSize.collectAsState()

    // VERSION VERIFICATION LOGGING
    LaunchedEffect(Unit) {
        val buildTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        android.util.Log.d("HomeScreen", "═══════════════════════════════════════")
        android.util.Log.d("HomeScreen", "🏠 HOME SCREEN v2.0 LOADED 🏠")
        android.util.Log.d("HomeScreen", "Build Time: $buildTime")
        android.util.Log.d("HomeScreen", "Photo orientation: Tella approach (pre-rotate before encryption)")
        android.util.Log.d("HomeScreen", "Camera controls: Bottom layout")
        android.util.Log.d("HomeScreen", "═══════════════════════════════════════")

        snackbarHostState.showSnackbar(
            message = "✅ App v2.0 - Build: $buildTime",
            duration = SnackbarDuration.Long
        )
    }

    var selectedItem by remember { mutableStateOf("Folders") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Folder") },
                actions = {
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
            }
        }
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
                    tint = androidx.compose.ui.graphics.Color.White
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
    FolderCategory.ALL_FILES -> Icons.Default.Folder
}

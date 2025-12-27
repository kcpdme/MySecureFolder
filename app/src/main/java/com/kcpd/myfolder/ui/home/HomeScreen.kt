package com.kcpd.myfolder.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val allFilesCount by viewModel.allFilesCount.collectAsState()
    val photosCount by viewModel.photosCount.collectAsState()
    val videosCount by viewModel.videosCount.collectAsState()
    val recordingsCount by viewModel.recordingsCount.collectAsState()
    val notesCount by viewModel.notesCount.collectAsState()
    val pdfsCount by viewModel.pdfsCount.collectAsState()

    Scaffold(
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
        }
    ) { paddingValues ->
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
                onClick = { onFolderClick(FolderCategory.ALL_FILES) },
                modifier = Modifier.fillMaxWidth()
            )
            FolderCard(
                category = FolderCategory.PHOTOS,
                count = photosCount,
                onClick = { onFolderClick(FolderCategory.PHOTOS) },
                modifier = Modifier.fillMaxWidth()
            )
            FolderCard(
                category = FolderCategory.VIDEOS,
                count = videosCount,
                onClick = { onFolderClick(FolderCategory.VIDEOS) },
                modifier = Modifier.fillMaxWidth()
            )
            FolderCard(
                category = FolderCategory.RECORDINGS,
                count = recordingsCount,
                onClick = { onFolderClick(FolderCategory.RECORDINGS) },
                modifier = Modifier.fillMaxWidth()
            )
            FolderCard(
                category = FolderCategory.NOTES,
                count = notesCount,
                onClick = { onFolderClick(FolderCategory.NOTES) },
                modifier = Modifier.fillMaxWidth()
            )
            FolderCard(
                category = FolderCategory.PDFS,
                count = pdfsCount,
                onClick = { onFolderClick(FolderCategory.PDFS) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FolderCard(
    category: FolderCategory,
    count: Int,
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
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }

            // Right: Count badge
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
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

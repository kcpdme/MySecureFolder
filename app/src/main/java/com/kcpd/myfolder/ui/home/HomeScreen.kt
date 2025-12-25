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
    val photosCount by viewModel.photosCount.collectAsState()
    val videosCount by viewModel.videosCount.collectAsState()
    val recordingsCount by viewModel.recordingsCount.collectAsState()
    val notesCount by viewModel.notesCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Folder") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First row: Photos and Videos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FolderCard(
                    category = FolderCategory.PHOTOS,
                    count = photosCount,
                    onClick = { onFolderClick(FolderCategory.PHOTOS) },
                    modifier = Modifier.weight(1f)
                )
                FolderCard(
                    category = FolderCategory.VIDEOS,
                    count = videosCount,
                    onClick = { onFolderClick(FolderCategory.VIDEOS) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Second row: Recordings and Notes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FolderCard(
                    category = FolderCategory.RECORDINGS,
                    count = recordingsCount,
                    onClick = { onFolderClick(FolderCategory.RECORDINGS) },
                    modifier = Modifier.weight(1f)
                )
                FolderCard(
                    category = FolderCategory.NOTES,
                    count = notesCount,
                    onClick = { onFolderClick(FolderCategory.NOTES) },
                    modifier = Modifier.weight(1f)
                )
            }
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
        modifier = modifier
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.displayName,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // File count badge
            if (count > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(text = count.toString())
                }
            }

            // Floating action button
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = getActionIcon(category),
                    contentDescription = "Add ${category.displayName}",
                    modifier = Modifier.size(24.dp)
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
}

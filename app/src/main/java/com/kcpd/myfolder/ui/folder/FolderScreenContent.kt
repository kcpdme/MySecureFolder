package com.kcpd.myfolder.ui.folder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.UserFolder

@Composable
fun FolderScreenContent(
    folders: List<UserFolder>,
    mediaFiles: List<MediaFile>,
    viewMode: FolderViewMode,
    isMultiSelectMode: Boolean,
    selectedFolders: Set<String>,
    selectedFiles: Set<String>,
    isUploading: (String) -> Boolean,
    onFolderClick: (UserFolder) -> Unit,
    onFolderLongClick: (UserFolder) -> Unit,
    onMediaClick: (Int) -> Unit,
    onMediaLongClick: (MediaFile) -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val hasContent = folders.isNotEmpty() || mediaFiles.isNotEmpty()

    if (!hasContent) {
        // Show empty state (handled by parent)
        return
    }

    if (viewMode == FolderViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Folders first
            items(folders.size) { index ->
                val folder = folders[index]
                FolderThumbnail(
                    folder = folder,
                    onClick = { onFolderClick(folder) },
                    onLongClick = { onFolderLongClick(folder) },
                    isSelected = selectedFolders.contains(folder.id),
                    isMultiSelectMode = isMultiSelectMode
                )
            }

            // Then media files
            items(mediaFiles.size) { index ->
                val mediaFile = mediaFiles[index]
                MediaThumbnail(
                    mediaFile = mediaFile,
                    onClick = {
                        if (isMultiSelectMode) {
                            onMediaLongClick(mediaFile)
                        } else {
                            onMediaClick(index)
                        }
                    },
                    onLongClick = { onMediaLongClick(mediaFile) },
                    isUploading = isUploading(mediaFile.id),
                    isSelected = selectedFiles.contains(mediaFile.id),
                    isMultiSelectMode = isMultiSelectMode
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Folders first
            items(folders) { folder ->
                FolderListItem(
                    folder = folder,
                    onClick = { onFolderClick(folder) },
                    onLongClick = { onFolderLongClick(folder) },
                    isSelected = selectedFolders.contains(folder.id),
                    isMultiSelectMode = isMultiSelectMode
                )
            }

            // Then media files
            items(mediaFiles) { mediaFile ->
                val index = mediaFiles.indexOf(mediaFile)
                FolderMediaListItem(
                    mediaFile = mediaFile,
                    onClick = {
                        if (isMultiSelectMode) {
                            onMediaLongClick(mediaFile)
                        } else {
                            onMediaClick(index)
                        }
                    },
                    onLongClick = { onMediaLongClick(mediaFile) },
                    isSelected = selectedFiles.contains(mediaFile.id),
                    isMultiSelectMode = isMultiSelectMode,
                    isUploading = isUploading(mediaFile.id)
                )
            }
        }
    }
}

@Composable
fun FolderScreenActions(
    isMultiSelectMode: Boolean,
    hasItems: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onToggleViewMode: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    currentViewMode: FolderViewMode
) {
    if (isMultiSelectMode) {
        IconButton(
            onClick = onSelectAll
        ) {
            Icon(
                if (selectedCount == totalCount)
                    Icons.Default.CheckBoxOutlineBlank
                else
                    Icons.Default.CheckBox,
                "Select All"
            )
        }

        if (selectedCount > 0) {
            IconButton(onClick = onMove) {
                Icon(Icons.Default.DriveFileMove, "Move")
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, "Share")
            }
        }

        IconButton(
            onClick = onDelete,
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
        IconButton(onClick = onToggleViewMode) {
            Icon(
                if (currentViewMode == FolderViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                "Toggle View"
            )
        }
        if (hasItems) {
            IconButton(onClick = onToggleSelectMode) {
                Icon(Icons.Default.CheckCircle, "Select")
            }
        }
    }
}

package com.kcpd.myfolder.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.UserFolder

@Composable
fun FolderBreadcrumb(
    category: FolderCategory,
    currentFolder: UserFolder?,
    folderPath: List<UserFolder>,
    onNavigate: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Root category
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (currentFolder == null)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.clickable { onNavigate(null) }
        )

        // Folder path
        folderPath.forEach { folder ->
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (folder.id == currentFolder?.id)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onNavigate(folder.id) }
            )
        }
    }
}

// Helper function to build folder path from root to current
fun buildFolderPath(
    currentFolderId: String?,
    allFolders: List<UserFolder>
): List<UserFolder> {
    if (currentFolderId == null) return emptyList()

    val path = mutableListOf<UserFolder>()
    var folderId: String? = currentFolderId

    while (folderId != null) {
        val folder = allFolders.find { it.id == folderId }
        if (folder != null) {
            path.add(0, folder) // Add to front
            folderId = folder.parentFolderId
        } else {
            break
        }
    }

    return path
}

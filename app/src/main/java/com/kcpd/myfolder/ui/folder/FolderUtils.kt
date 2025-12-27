package com.kcpd.myfolder.ui.folder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.kcpd.myfolder.data.model.FolderCategory
import java.text.SimpleDateFormat
import java.util.*

/**
 * Get the action icon for a folder category's FAB button.
 */
fun getActionIcon(category: FolderCategory): ImageVector = when (category) {
    FolderCategory.PHOTOS -> Icons.Default.CameraAlt
    FolderCategory.VIDEOS -> Icons.Default.Videocam
    FolderCategory.RECORDINGS -> Icons.Default.Mic
    FolderCategory.NOTES -> Icons.Default.Edit
    FolderCategory.PDFS -> Icons.Default.Scanner
    FolderCategory.ALL_FILES -> Icons.Default.Add
}

/**
 * Get the empty state message for a folder category.
 */
fun getEmptyStateMessage(category: FolderCategory): String {
    return when (category) {
        FolderCategory.PHOTOS -> "Tap + to capture photos"
        FolderCategory.VIDEOS -> "Tap + to record videos"
        FolderCategory.RECORDINGS -> "Tap + to record audio"
        FolderCategory.NOTES -> "Tap + to create a note"
        FolderCategory.PDFS -> "Tap + to scan documents or import PDFs"
        FolderCategory.ALL_FILES -> "Import files to get started"
    }
}

/**
 * Format file size in bytes to human-readable format (B, KB, MB).
 */
fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

/**
 * Format date to readable string format.
 */
fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

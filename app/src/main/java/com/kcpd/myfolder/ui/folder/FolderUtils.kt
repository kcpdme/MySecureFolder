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
    FolderCategory.OTHER -> Icons.Default.Add
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
        FolderCategory.OTHER -> "Use Import to add files"
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

/**
 * Get file extension from filename (e.g., "photo.jpg" -> "JPG").
 * Returns null if no extension found.
 */
fun getFileExtension(fileName: String): String? {
    val lastDot = fileName.lastIndexOf('.')
    return if (lastDot > 0 && lastDot < fileName.length - 1) {
        fileName.substring(lastDot + 1).uppercase()
    } else {
        null
    }
}

/**
 * Format MIME type to a more user-friendly display format.
 * e.g., "image/jpeg" -> "JPEG", "video/mp4" -> "MP4", "application/pdf" -> "PDF"
 */
fun formatMimeType(mimeType: String?): String? {
    if (mimeType.isNullOrEmpty()) return null
    
    // Extract the subtype part (after the /)
    val subtype = mimeType.substringAfter('/', "")
    if (subtype.isEmpty()) return mimeType.uppercase()
    
    // Handle common subtypes with better formatting
    return when (subtype.lowercase()) {
        "jpeg", "jpg" -> "JPEG"
        "png" -> "PNG"
        "gif" -> "GIF"
        "webp" -> "WEBP"
        "heic", "heif" -> "HEIC"
        "mp4" -> "MP4"
        "quicktime", "mov" -> "MOV"
        "x-matroska", "mkv" -> "MKV"
        "webm" -> "WEBM"
        "avi", "x-msvideo" -> "AVI"
        "mpeg", "mpg" -> "MPEG"
        "mp3", "mpeg3" -> "MP3"
        "x-wav", "wav" -> "WAV"
        "ogg" -> "OGG"
        "aac" -> "AAC"
        "flac", "x-flac" -> "FLAC"
        "m4a", "x-m4a" -> "M4A"
        "pdf" -> "PDF"
        "plain" -> "TXT"
        "html" -> "HTML"
        "json" -> "JSON"
        "xml" -> "XML"
        "zip" -> "ZIP"
        "x-rar-compressed", "rar" -> "RAR"
        else -> subtype.uppercase()
    }
}

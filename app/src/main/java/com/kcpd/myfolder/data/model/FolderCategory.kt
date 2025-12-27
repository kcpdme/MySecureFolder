package com.kcpd.myfolder.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class FolderCategory(
    val displayName: String,
    val icon: ImageVector,
    val path: String,
    val mediaType: MediaType?
) {
    ALL_FILES("All Files", Icons.Default.Folder, "all", null),
    PHOTOS("Photos", Icons.Default.Photo, "photos", MediaType.PHOTO),
    VIDEOS("Videos", Icons.Default.Videocam, "videos", MediaType.VIDEO),
    RECORDINGS("Recordings", Icons.Default.Mic, "recordings", MediaType.AUDIO),
    NOTES("Notes", Icons.Default.Note, "notes", MediaType.NOTE),
    PDFS("PDFs", Icons.Default.PictureAsPdf, "pdfs", MediaType.PDF);

    companion object {
        fun fromMediaType(mediaType: MediaType): FolderCategory {
            return entries.first { it.mediaType == mediaType }
        }

        fun fromPath(path: String): FolderCategory? {
            return entries.firstOrNull { it.path == path }
        }
    }
}

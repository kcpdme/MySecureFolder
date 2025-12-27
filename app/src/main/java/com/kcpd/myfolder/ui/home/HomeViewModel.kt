package com.kcpd.myfolder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    // File counts
    val allFilesCount: StateFlow<Int> = mediaRepository.mediaFiles.map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val photosCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.PHOTOS)

    val videosCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.VIDEOS)

    val recordingsCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.RECORDINGS)

    val notesCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.NOTES)

    val pdfsCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.PDFS)

    // File sizes (in bytes)
    val allFilesSize: StateFlow<Long> = mediaRepository.mediaFiles.map { files ->
        files.sumOf { it.size }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val photosSize: StateFlow<Long> =
        mediaRepository.getFileSizeForCategory(FolderCategory.PHOTOS)

    val videosSize: StateFlow<Long> =
        mediaRepository.getFileSizeForCategory(FolderCategory.VIDEOS)

    val recordingsSize: StateFlow<Long> =
        mediaRepository.getFileSizeForCategory(FolderCategory.RECORDINGS)

    val notesSize: StateFlow<Long> =
        mediaRepository.getFileSizeForCategory(FolderCategory.NOTES)

    val pdfsSize: StateFlow<Long> =
        mediaRepository.getFileSizeForCategory(FolderCategory.PDFS)

    fun getCountForCategory(category: FolderCategory): StateFlow<Int> {
        return when (category) {
            FolderCategory.ALL_FILES -> allFilesCount
            FolderCategory.PHOTOS -> photosCount
            FolderCategory.VIDEOS -> videosCount
            FolderCategory.RECORDINGS -> recordingsCount
            FolderCategory.NOTES -> notesCount
            FolderCategory.PDFS -> pdfsCount
        }
    }

    fun getSizeForCategory(category: FolderCategory): StateFlow<Long> {
        return when (category) {
            FolderCategory.ALL_FILES -> allFilesSize
            FolderCategory.PHOTOS -> photosSize
            FolderCategory.VIDEOS -> videosSize
            FolderCategory.RECORDINGS -> recordingsSize
            FolderCategory.NOTES -> notesSize
            FolderCategory.PDFS -> pdfsSize
        }
    }

    /**
     * Formats bytes to human-readable size (MB, KB, etc.)
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

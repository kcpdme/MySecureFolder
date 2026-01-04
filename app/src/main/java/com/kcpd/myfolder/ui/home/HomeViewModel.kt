package com.kcpd.myfolder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.repository.MediaRepository
import com.kcpd.myfolder.data.repository.RemoteConfigRepository
import com.kcpd.myfolder.data.repository.RemoteRepositoryManager
import com.kcpd.myfolder.domain.usecase.MultiRemoteUploadCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val remoteRepositoryManager: RemoteRepositoryManager,
    private val multiRemoteUploadCoordinator: MultiRemoteUploadCoordinator,
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    // Upload states from the global coordinator (shared across all screens)
    val uploadStates = multiRemoteUploadCoordinator.uploadStates
    val activeUploadsCount = multiRemoteUploadCoordinator.activeUploadsCount
    
    // Pending queue count from WorkManager
    val pendingQueueCount = multiRemoteUploadCoordinator.getUploadManager().getPendingCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Control visibility of upload sheet on home screen
    private val _showUploadSheet = MutableStateFlow(false)
    val showUploadSheet: StateFlow<Boolean> = _showUploadSheet.asStateFlow()
    
    /**
     * Show the upload status sheet
     */
    fun showUploadSheet() {
        _showUploadSheet.value = true
    }
    
    /**
     * Dismiss the upload sheet
     */
    fun dismissUploadSheet() {
        _showUploadSheet.value = false
    }
    
    /**
     * Retry a failed upload for a specific file to a specific remote
     */
    fun retryUpload(fileId: String, remoteId: String) {
        multiRemoteUploadCoordinator.retryUpload(fileId, remoteId, viewModelScope)
    }
    
    /**
     * Clear completed uploads from the UI
     */
    fun clearCompletedUploads() {
        viewModelScope.launch {
            multiRemoteUploadCoordinator.clearCompleted()
            multiRemoteUploadCoordinator.getUploadManager().clearCompleted()
        }
    }
    
    /**
     * Cancel all pending uploads
     */
    fun cancelAllPendingUploads() {
        viewModelScope.launch {
            multiRemoteUploadCoordinator.getUploadManager().cancelAllPending()
            multiRemoteUploadCoordinator.clearAll()
        }
    }
    
    /**
     * Retry all failed uploads
     */
    fun retryAllFailedUploads() {
        viewModelScope.launch {
            multiRemoteUploadCoordinator.retryAllFailedReliably()
        }
    }
    
    /**
     * Check if there are any upload states to show
     */
    fun hasUploadStates(): Boolean {
        return uploadStates.value.isNotEmpty()
    }

    // Legacy - kept for backward compatibility
    val activeRemoteType = remoteRepositoryManager.activeRemoteType.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = com.kcpd.myfolder.data.model.RemoteType.S3_MINIO
    )
    
    /**
     * Active remotes info - shows count and names of active upload destinations
     * Format: "2 remotes: MyS3, GDrive" or "No remotes configured"
     */
    val activeRemotesInfo: StateFlow<String> = remoteConfigRepository.getActiveRemotesFlow()
        .map { remotes ->
            when {
                remotes.isEmpty() -> "No remotes configured"
                remotes.size == 1 -> "Remote: ${remotes.first().name}"
                else -> "${remotes.size} remotes: ${remotes.take(2).joinToString(", ") { it.name }}${if (remotes.size > 2) "..." else ""}"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Loading...")

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

    val otherCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.OTHER)

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

    val otherSize: StateFlow<Long> =
        mediaRepository.getFileSizeForCategory(FolderCategory.OTHER)

    fun getCountForCategory(category: FolderCategory): StateFlow<Int> {
        return when (category) {
            FolderCategory.ALL_FILES -> allFilesCount
            FolderCategory.PHOTOS -> photosCount
            FolderCategory.VIDEOS -> videosCount
            FolderCategory.RECORDINGS -> recordingsCount
            FolderCategory.NOTES -> notesCount
            FolderCategory.PDFS -> pdfsCount
            FolderCategory.OTHER -> otherCount
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
            FolderCategory.OTHER -> otherSize
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

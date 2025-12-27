package com.kcpd.myfolder.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.FolderColor
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.UserFolder
import com.kcpd.myfolder.data.repository.FolderRepository
import com.kcpd.myfolder.data.repository.MediaRepository
import com.kcpd.myfolder.data.repository.RemoteStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val folderRepository: FolderRepository,
    private val remoteStorageRepository: RemoteStorageRepository,
    private val s3SessionManager: com.kcpd.myfolder.data.repository.S3SessionManager,
    private val importMediaUseCase: com.kcpd.myfolder.domain.usecase.ImportMediaUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val categoryPath: String = savedStateHandle.get<String>("category") ?: "photos"
    val category: FolderCategory = FolderCategory.fromPath(categoryPath) ?: FolderCategory.PHOTOS

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val currentFolder: StateFlow<UserFolder?> = _currentFolderId.asStateFlow().combine(folderRepository.folders) { folderId, _ ->
        folderId?.let { folderRepository.getFolderById(it) }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    val mediaFiles: StateFlow<List<MediaFile>> = combine(
        if (category == FolderCategory.ALL_FILES) mediaRepository.mediaFiles else mediaRepository.getFilesForCategory(category),
        _currentFolderId,
        _searchQuery
    ) { files, folderId, query ->
        var filtered = files

        // Filter by folder if not ALL_FILES category
        if (category != FolderCategory.ALL_FILES) {
            filtered = filtered.filter { it.folderId == folderId }
        }

        // Apply search filter
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.fileName.contains(query, ignoreCase = true)
            }
        }

        android.util.Log.d("FolderViewModel", "Category: ${category.displayName}, FolderId: $folderId, Query: $query")
        android.util.Log.d("FolderViewModel", "Total files for category: ${files.size}, Filtered: ${filtered.size}")
        filtered
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<UserFolder>> = combine(
        folderRepository.folders,
        _currentFolderId
    ) { allFolders, parentId ->
        allFolders.filter {
            it.categoryPath == categoryPath && it.parentFolderId == parentId
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uploadingFiles = MutableStateFlow<Set<String>>(emptySet())
    val uploadingFiles: StateFlow<Set<String>> = _uploadingFiles.asStateFlow()

    fun navigateToFolder(folderId: String?) {
        _currentFolderId.value = folderId
    }

    fun createFolder(name: String, color: FolderColor) {
        viewModelScope.launch {
            folderRepository.createFolder(
                name = name,
                color = color,
                categoryPath = categoryPath,
                parentFolderId = _currentFolderId.value
            )
        }
    }

    fun updateFolder(folder: UserFolder) {
        viewModelScope.launch {
            folderRepository.updateFolder(folder)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            // Delete all files in the folder first
            val filesToDelete = mediaRepository.getFilesInFolder(folderId, category)
            filesToDelete.forEach { mediaRepository.deleteMediaFile(it) }

            // Delete the folder
            folderRepository.deleteFolder(folderId)
        }
    }

    fun moveToFolder(mediaFile: MediaFile, folderId: String?) {
        viewModelScope.launch {
            mediaRepository.moveMediaFileToFolder(mediaFile, folderId)
        }
    }

    fun deleteFile(mediaFile: MediaFile) {
        viewModelScope.launch {
            mediaRepository.deleteMediaFile(mediaFile)
        }
    }

    fun uploadFile(mediaFile: MediaFile) {
        viewModelScope.launch {
            _uploadingFiles.value = _uploadingFiles.value + mediaFile.id
            android.util.Log.d("FolderViewModel", "Upload started for: ${mediaFile.fileName}, uploading state: true")
            try {
                // Add minimum delay to ensure progress indicator is visible
                delay(500)
                val result = remoteStorageRepository.uploadFile(mediaFile)
                result.onSuccess { url ->
                    val updatedFile = mediaFile.copy(isUploaded = true, s3Url = url)
                    mediaRepository.updateMediaFile(updatedFile)
                    android.util.Log.d("FolderViewModel", "File uploaded successfully: ${mediaFile.fileName} -> $url")
                }.onFailure { error ->
                    android.util.Log.e("FolderViewModel", "Upload failed for ${mediaFile.fileName}", error)
                    // Upload failed - could show toast or error state here
                }
            } catch (e: Exception) {
                android.util.Log.e("FolderViewModel", "Upload exception for ${mediaFile.fileName}", e)
            } finally {
                _uploadingFiles.value = _uploadingFiles.value - mediaFile.id
                android.util.Log.d("FolderViewModel", "Upload finished for: ${mediaFile.fileName}, uploading state: false")
            }
        }
    }

    fun isUploading(fileId: String): Boolean {
        return _uploadingFiles.value.contains(fileId)
    }

    /**
     * Verify if a file still exists on S3.
     * If file doesn't exist, mark it as not uploaded so it can be re-uploaded.
     */
    fun verifyUploadStatus(mediaFile: MediaFile) {
        viewModelScope.launch {
            val result = remoteStorageRepository.verifyFileExists(mediaFile)
            result.onSuccess { foundUrl ->
                if (foundUrl == null && mediaFile.isUploaded) {
                    // File was deleted from S3 - mark as not uploaded
                    mediaRepository.markAsNotUploaded(mediaFile.id)
                    android.util.Log.d("FolderViewModel", "File deleted from S3, marked for re-upload: ${mediaFile.fileName}")
                } else if (foundUrl != null && foundUrl != mediaFile.s3Url) {
                    // URL changed (or recovered)
                    val updated = mediaFile.copy(s3Url = foundUrl)
                    mediaRepository.updateMediaFile(updated)
                }
            }.onFailure { error ->
                android.util.Log.e("FolderViewModel", "Failed to verify upload status for ${mediaFile.fileName}", error)
            }
        }
    }

    /**
     * Sync upload status for all uploaded files in current view.
     * Checks S3 and marks files as not uploaded if they've been deleted.
     */
    fun syncUploadStatus() {
        viewModelScope.launch {
            val uploadedFiles = mediaFiles.value.filter { it.isUploaded }
            if (uploadedFiles.isEmpty()) {
                android.util.Log.d("FolderViewModel", "No uploaded files to sync")
                return@launch
            }

            android.util.Log.d("FolderViewModel", "Syncing upload status for ${uploadedFiles.size} files...")
            val results = remoteStorageRepository.verifyMultipleFiles(uploadedFiles)

            var markedCount = 0
            results.forEach { (fileId, foundUrl) ->
                if (foundUrl == null) {
                    mediaRepository.markAsNotUploaded(fileId)
                    markedCount++
                } else {
                     // Check if URL update is needed
                     val file = uploadedFiles.find { it.id == fileId }
                     if (file != null && file.s3Url != foundUrl) {
                         mediaRepository.markAsUploaded(fileId, foundUrl)
                     }
                }
            }

            android.util.Log.d("FolderViewModel", "Sync complete: $markedCount files marked for re-upload")
        }
    }

    fun shareMediaFile(mediaFile: MediaFile) {
        // This will be handled by FolderScreen using FolderActions
    }

    /**
     * Import files from device storage.
     * Returns a flow of import progress.
     */
    suspend fun importFiles(uris: List<android.net.Uri>) = importMediaUseCase.importFiles(uris, _currentFolderId.value)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}

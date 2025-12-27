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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val _uploadQueue = MutableStateFlow<List<String>>(emptyList())
    val uploadQueue: StateFlow<List<String>> = _uploadQueue.asStateFlow()

    // Semaphore to limit concurrent uploads (2 for Google Drive, 3 for B2)
    private val uploadSemaphore = Semaphore(2)

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

    /**
     * Upload a single file with rate limiting and concurrency control.
     * Uses a semaphore to limit concurrent uploads to 2-3 at a time.
     */
    fun uploadFile(mediaFile: MediaFile) {
        viewModelScope.launch {
            // Add to queue first
            _uploadQueue.value = _uploadQueue.value + mediaFile.id

            // Wait for available slot (max 2 concurrent uploads)
            uploadSemaphore.withPermit {
                // Remove from queue, add to uploading
                _uploadQueue.value = _uploadQueue.value - mediaFile.id
                _uploadingFiles.value = _uploadingFiles.value + mediaFile.id

                android.util.Log.d("FolderViewModel", "Upload started for: ${mediaFile.fileName}")
                try {
                    val result = remoteStorageRepository.uploadFile(mediaFile)
                    result.onSuccess { url ->
                        android.util.Log.d("FolderViewModel", "File uploaded successfully: ${mediaFile.fileName} -> $url")
                    }.onFailure { error ->
                        android.util.Log.e("FolderViewModel", "Upload failed for ${mediaFile.fileName}", error)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FolderViewModel", "Upload exception for ${mediaFile.fileName}", e)
                } finally {
                    _uploadingFiles.value = _uploadingFiles.value - mediaFile.id
                    android.util.Log.d("FolderViewModel", "Upload finished for: ${mediaFile.fileName}")

                    // Rate limiting: delay before next upload
                    // Google Drive: max 1000 requests/100s = ~10 req/s = 100ms delay
                    // B2: max 1000 requests/day for free tier = be conservative
                    delay(150)
                }
            }
        }
    }

    /**
     * Upload multiple files in batches with proper rate limiting.
     * Recommended for bulk operations.
     */
    fun uploadFiles(mediaFiles: List<MediaFile>) {
        mediaFiles.forEach { file ->
            uploadFile(file)
        }
    }

    fun isUploading(fileId: String): Boolean {
        return _uploadingFiles.value.contains(fileId)
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

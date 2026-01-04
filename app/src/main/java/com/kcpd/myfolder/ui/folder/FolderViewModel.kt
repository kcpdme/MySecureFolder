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
import kotlinx.coroutines.flow.first
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
    private val multiRemoteUploadCoordinator: com.kcpd.myfolder.domain.usecase.MultiRemoteUploadCoordinator,
    private val remoteConfigRepository: com.kcpd.myfolder.data.repository.RemoteConfigRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Provides access to MediaRepository for operations like decryption.
     */
    fun getMediaRepository(): MediaRepository = mediaRepository

    private val categoryPath: String = savedStateHandle.get<String>("category") ?: "photos"
    val category: FolderCategory = FolderCategory.fromPath(categoryPath) ?: FolderCategory.PHOTOS

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val currentFolder: StateFlow<UserFolder?> = _currentFolderId.asStateFlow().combine(folderRepository.folders) { folderId, _ ->
        folderId?.let { folderRepository.getFolderById(it) }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)
    
    /**
     * Get the breadcrumb path from root to current folder.
     * Returns list of (folderId, folderName) pairs where null id = root.
     */
    val folderBreadcrumbs: StateFlow<List<Pair<String?, String>>> = _currentFolderId.asStateFlow()
        .combine(folderRepository.folders) { currentId, allFolders ->
            val path = mutableListOf<Pair<String?, String>>()
            
            // Add root (category name)
            path.add(null to category.displayName)
            
            // Build path from root to current folder
            if (currentId != null) {
                val pathFolders = mutableListOf<UserFolder>()
                var folderId: String? = currentId
                
                while (folderId != null) {
                    val folder = allFolders.find { it.id == folderId }
                    if (folder != null) {
                        pathFolders.add(0, folder) // Add to front
                        folderId = folder.parentFolderId
                    } else {
                        break
                    }
                }
                
                pathFolders.forEach { folder ->
                    path.add(folder.id to folder.name)
                }
            }
            
            path
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), listOf(null to category.displayName))


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

    // Multi-remote upload state
    val uploadStates = multiRemoteUploadCoordinator.uploadStates
    val activeUploadsCount = multiRemoteUploadCoordinator.activeUploadsCount

    // Control visibility of upload sheet (allows user to hide it while uploads continue in background)
    // Starts as false, becomes true when uploads start
    private val _showUploadSheet = MutableStateFlow(false)
    val showUploadSheet: StateFlow<Boolean> = _showUploadSheet.asStateFlow()

    // Legacy upload state (deprecated - kept for backward compatibility during migration)
    @Deprecated("Use uploadStates from MultiRemoteUploadCoordinator instead")
    private val _uploadingFiles = MutableStateFlow<Set<String>>(emptySet())
    @Deprecated("Use uploadStates from MultiRemoteUploadCoordinator instead")
    val uploadingFiles: StateFlow<Set<String>> = _uploadingFiles.asStateFlow()

    @Deprecated("Use uploadStates from MultiRemoteUploadCoordinator instead")
    private val _uploadQueue = MutableStateFlow<List<String>>(emptyList())
    @Deprecated("Use uploadStates from MultiRemoteUploadCoordinator instead")
    val uploadQueue: StateFlow<List<String>> = _uploadQueue.asStateFlow()

    @Deprecated("Use uploadStates from MultiRemoteUploadCoordinator instead")
    private val _uploadResults = MutableStateFlow<Map<String, UploadResult>>(emptyMap())
    @Deprecated("Use uploadStates from MultiRemoteUploadCoordinator instead")
    val uploadResults: StateFlow<Map<String, UploadResult>> = _uploadResults.asStateFlow()

    @Deprecated("No longer needed - MultiRemoteUploadCoordinator manages concurrency")
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

    /**
     * Upload all files in the current view (including subfolders recursively).
     */
    fun uploadAllFilesInCurrentFolder() {
        viewModelScope.launch {
            val currentId = _currentFolderId.value
            val filesToUpload = if (category == FolderCategory.ALL_FILES) {
                mediaFiles.value
            } else {
                if (currentId == null) {
                    // Root view of a category: Upload ALL files of this category (including those in folders)
                    // mediaRepository.getFilesForCategory returns all files of that type, regardless of folder
                    mediaRepository.getFilesForCategory(category).first()
                } else {
                    // Inside a specific folder: Upload recursively
                    getFilesFromFolders(setOf(currentId))
                }
            }
            uploadFiles(filesToUpload)
        }
    }

    /**
     * Get all files from selected folders (recursively includes subfolders).
     * Returns a list of MediaFile objects that are within the specified folder IDs and their subfolders.
     */
    suspend fun getFilesFromFolders(folderIds: Set<String>): List<MediaFile> {
        val allFiles = mutableListOf<MediaFile>()
        val processedFolders = mutableSetOf<String>()
        // Get snapshot of all folders for traversal
        val allFolders = folderRepository.folders.first()

        // Process each folder recursively
        folderIds.forEach { folderId ->
            collectFilesRecursively(folderId, allFiles, processedFolders, allFolders)
        }

        android.util.Log.d("FolderViewModel", "getFilesFromFolders: Collected ${allFiles.size} files from ${folderIds.size} folders (including ${processedFolders.size} subfolders)")
        return allFiles
    }

    /**
     * Recursively collect files from a folder and all its subfolders.
     */
    private suspend fun collectFilesRecursively(
        folderId: String,
        allFiles: MutableList<MediaFile>,
        processedFolders: MutableSet<String>,
        allFolders: List<UserFolder>
    ) {
        // Avoid processing the same folder twice
        if (folderId in processedFolders) return
        processedFolders.add(folderId)

        // Get files directly in this folder
        val filesInFolder = mediaRepository.getFilesInFolder(folderId, category)
        allFiles.addAll(filesInFolder)
        android.util.Log.d("FolderViewModel", "Folder $folderId: Found ${filesInFolder.size} files")

        // Find all subfolders using the full list
        val subfolders = allFolders.filter { it.parentFolderId == folderId }
        android.util.Log.d("FolderViewModel", "Folder $folderId: Found ${subfolders.size} subfolders")

        // Recursively process each subfolder
        subfolders.forEach { subfolder ->
            collectFilesRecursively(subfolder.id, allFiles, processedFolders, allFolders)
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
     * Upload a single file to all active remotes.
     * Uses WorkManager for reliable background upload that survives app death.
     */
    fun uploadFile(mediaFile: MediaFile) {
        // Check if any active remotes are configured
        if (remoteConfigRepository.getActiveRemotesSync().isEmpty()) {
            android.util.Log.w("FolderViewModel", "No active remotes configured")
            // TODO: Show error to user via UI state
            return
        }

        // Show sheet IMMEDIATELY before starting upload
        _showUploadSheet.value = true
        android.util.Log.d("FolderViewModel", "Starting RELIABLE upload for: ${mediaFile.fileName}")
        
        // Use WorkManager-backed reliable upload
        viewModelScope.launch {
            multiRemoteUploadCoordinator.uploadFileReliably(mediaFile)
        }
    }

    /**
     * Upload multiple files to all active remotes in parallel.
     * Each file will be uploaded to all configured remotes concurrently.
     * 
     * Uses WorkManager for reliable background uploads that:
     * - Continue even if app is minimized or killed
     * - Automatically retry with exponential backoff
     * - Show progress notification
     * - Survive device reboot
     */
    fun uploadFiles(mediaFiles: List<MediaFile>) {
        val activeRemotes = remoteConfigRepository.getActiveRemotesSync()
        
        // Check if any active remotes are configured
        if (activeRemotes.isEmpty()) {
            android.util.Log.w("FolderViewModel", "No active remotes configured")
            // TODO: Show error to user via UI state
            return
        }

        android.util.Log.d("FolderViewModel", "Starting RELIABLE multi-remote upload for ${mediaFiles.size} files to ${activeRemotes.size} remotes")
        // Show sheet IMMEDIATELY before starting uploads
        _showUploadSheet.value = true
        
        // Use WorkManager-backed reliable upload
        viewModelScope.launch {
            multiRemoteUploadCoordinator.uploadFilesReliably(mediaFiles)
        }
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
            // Also clear from WorkManager queue
            multiRemoteUploadCoordinator.getUploadManager().clearCompleted()
        }
    }
    
    /**
     * Cancel all pending uploads in the WorkManager queue
     */
    fun cancelAllPendingUploads() {
        viewModelScope.launch {
            multiRemoteUploadCoordinator.getUploadManager().cancelAllPending()
            multiRemoteUploadCoordinator.clearAll()
        }
    }
    
    /**
     * Retry all failed uploads via WorkManager
     */
    fun retryAllFailedUploads() {
        viewModelScope.launch {
            multiRemoteUploadCoordinator.retryAllFailedReliably()
        }
    }
    
    /**
     * Get pending queue count from WorkManager
     */
    val pendingQueueCount = multiRemoteUploadCoordinator.getUploadManager().getPendingCountFlow()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Dismiss the upload sheet (uploads continue in background)
     */
    fun dismissUploadSheet() {
        _showUploadSheet.value = false
    }
    
    /**
     * Show the upload status sheet
     */
    fun showUploadSheet() {
        _showUploadSheet.value = true
    }

    /**
     * Check if a file is currently being uploaded
     */
    fun isUploading(fileId: String): Boolean {
        val state = multiRemoteUploadCoordinator.getFileUploadState(fileId)
        return state != null && !state.isComplete
    }

    /**
     * Check if any uploads are in progress or have results to show
     */
    fun hasActiveUploads(): Boolean {
        return multiRemoteUploadCoordinator.hasActiveUploads()
    }
    
    /**
     * Check if there are any upload states to show (active or completed)
     */
    fun hasUploadStates(): Boolean {
        return uploadStates.value.isNotEmpty()
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

    @Deprecated("Use clearCompletedUploads() instead")
    fun clearUploadResult(fileId: String) {
        _uploadResults.value = _uploadResults.value.minus(fileId)
    }
}

@Deprecated("Use FileUploadState from MultiRemoteUploadCoordinator instead")
sealed class UploadResult {
    object Success : UploadResult()
    data class Error(val message: String) : UploadResult()
}

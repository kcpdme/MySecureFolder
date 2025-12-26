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
import com.kcpd.myfolder.data.repository.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val folderRepository: FolderRepository,
    private val s3Repository: S3Repository,
    private val importMediaUseCase: com.kcpd.myfolder.domain.usecase.ImportMediaUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val categoryPath: String = savedStateHandle.get<String>("category") ?: "photos"
    val category: FolderCategory = FolderCategory.fromPath(categoryPath) ?: FolderCategory.PHOTOS

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    val currentFolder: StateFlow<UserFolder?> = _currentFolderId.asStateFlow().combine(folderRepository.folders) { folderId, _ ->
        folderId?.let { folderRepository.getFolderById(it) }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    val mediaFiles: StateFlow<List<MediaFile>> = combine(
        mediaRepository.getFilesForCategory(category),
        _currentFolderId
    ) { files, folderId ->
        val filtered = files.filter { it.folderId == folderId }
        android.util.Log.d("FolderViewModel", "Category: ${category.displayName}, FolderId: $folderId")
        android.util.Log.d("FolderViewModel", "Total files for category: ${files.size}, Filtered for folder: ${filtered.size}")
        filtered.forEachIndexed { index, file ->
            android.util.Log.d("FolderViewModel", "[$index] ${file.fileName} - Type: ${file.mediaType}, FolderId: ${file.folderId}")
        }
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
            try {
                s3Repository.uploadFile(mediaFile)
                val updatedFile = mediaFile.copy(isUploaded = true)
                mediaRepository.updateMediaFile(updatedFile)
            } catch (e: Exception) {
                // Handle upload error
                e.printStackTrace()
            } finally {
                _uploadingFiles.value = _uploadingFiles.value - mediaFile.id
            }
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
}

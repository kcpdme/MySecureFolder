package com.kcpd.myfolder.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.repository.MediaRepository
import com.kcpd.myfolder.data.repository.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val s3Repository: S3Repository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val categoryPath: String = savedStateHandle.get<String>("category") ?: "photos"
    val category: FolderCategory = FolderCategory.fromPath(categoryPath) ?: FolderCategory.PHOTOS

    val mediaFiles: StateFlow<List<MediaFile>> = mediaRepository.getFilesForCategory(category)

    private val _uploadingFiles = MutableStateFlow<Set<String>>(emptySet())
    val uploadingFiles: StateFlow<Set<String>> = _uploadingFiles.asStateFlow()

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
}

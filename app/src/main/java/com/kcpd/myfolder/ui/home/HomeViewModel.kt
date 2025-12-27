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
}

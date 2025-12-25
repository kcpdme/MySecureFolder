package com.kcpd.myfolder.ui.home

import androidx.lifecycle.ViewModel
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val photosCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.PHOTOS)

    val videosCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.VIDEOS)

    val recordingsCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.RECORDINGS)

    val notesCount: StateFlow<Int> =
        mediaRepository.getFileCountForCategory(FolderCategory.NOTES)

    fun getCountForCategory(category: FolderCategory): StateFlow<Int> {
        return when (category) {
            FolderCategory.PHOTOS -> photosCount
            FolderCategory.VIDEOS -> videosCount
            FolderCategory.RECORDINGS -> recordingsCount
            FolderCategory.NOTES -> notesCount
        }
    }
}

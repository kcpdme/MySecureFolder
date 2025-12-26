package com.kcpd.myfolder.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    fun addMediaFile(file: File, mediaType: MediaType, folderId: String? = null) {
        viewModelScope.launch {
            mediaRepository.addMediaFile(file, mediaType, folderId)
        }
    }
}

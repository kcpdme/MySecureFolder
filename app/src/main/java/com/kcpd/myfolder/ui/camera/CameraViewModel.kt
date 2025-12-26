package com.kcpd.myfolder.ui.camera

import androidx.lifecycle.ViewModel
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    fun addMediaFile(file: File, mediaType: MediaType, folderId: String? = null) {
        mediaRepository.addMediaFile(file, mediaType, folderId)
    }
}

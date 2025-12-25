package com.kcpd.myfolder.ui.gallery

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.repository.MediaRepository
import com.kcpd.myfolder.data.repository.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    application: Application,
    private val mediaRepository: MediaRepository,
    private val s3Repository: S3Repository
) : AndroidViewModel(application) {

    val mediaFiles: StateFlow<List<MediaFile>> = mediaRepository.mediaFiles

    fun deleteMediaFile(mediaFile: MediaFile) {
        val deleted = mediaRepository.deleteMediaFile(mediaFile)
        if (deleted) {
            Toast.makeText(getApplication(), "File deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }

    fun uploadToS3(mediaFile: MediaFile) {
        viewModelScope.launch {
            Toast.makeText(getApplication(), "Uploading ${mediaFile.fileName}...", Toast.LENGTH_SHORT).show()

            val result = s3Repository.uploadFile(mediaFile)
            result.onSuccess { url ->
                val updatedFile = mediaFile.copy(isUploaded = true, s3Url = url)
                mediaRepository.updateMediaFile(updatedFile)
                Toast.makeText(
                    getApplication(),
                    "Upload successful!",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    getApplication(),
                    "Upload failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun shareMediaFile(mediaFile: MediaFile) {
        try {
            val context = getApplication<Application>()
            val file = File(mediaFile.filePath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                else -> "*/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(
                getApplication(),
                "Failed to share file: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

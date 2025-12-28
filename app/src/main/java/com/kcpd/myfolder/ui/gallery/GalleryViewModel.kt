package com.kcpd.myfolder.ui.gallery

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
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
        viewModelScope.launch {
            val deleted = mediaRepository.deleteMediaFile(mediaFile)
            if (deleted) {
                Toast.makeText(getApplication(), "File securely deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Failed to delete file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun loadNoteContent(mediaFile: MediaFile): String {
        return mediaRepository.loadNoteContent(mediaFile)
    }

    /**
     * Decrypts media file to temporary location for playback.
     * Caller is responsible for cleanup.
     */
    suspend fun decryptForPlayback(mediaFile: MediaFile): File {
        return mediaRepository.decryptForViewing(mediaFile)
    }

    fun uploadToS3(mediaFile: MediaFile) {
        viewModelScope.launch {
            Toast.makeText(getApplication(), "Uploading ${mediaFile.fileName}...", Toast.LENGTH_SHORT).show()

            val result = s3Repository.uploadFile(mediaFile)
            result.onSuccess { url ->
                val updatedFile = mediaFile.copy()
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
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                // Decrypt file to temp location for sharing
                val decryptedFile = mediaRepository.decryptForViewing(mediaFile)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    decryptedFile
                )

                val mimeType = when (decryptedFile.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "mp4" -> "video/mp4"
                    "mov" -> "video/quicktime"
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "txt" -> "text/plain"
                    "pdf" -> "application/pdf"
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

                // Clean up temp file after a delay (sharing app should have accessed it by then)
                // This prevents accumulation of temp files in cache
                viewModelScope.launch {
                    kotlinx.coroutines.delay(30000) // Wait 30 seconds
                    try {
                        if (decryptedFile.exists()) {
                            decryptedFile.delete()
                            android.util.Log.d("GalleryViewModel", "Cleaned up shared temp file: ${decryptedFile.name}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GalleryViewModel", "Failed to cleanup shared file", e)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    getApplication(),
                    "Failed to share file: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Rotates a photo by the specified angle clockwise and saves as a new encrypted file.
     * @param mediaFile The photo to rotate
     * @param angle The angle to rotate (90, 180, 270)
     * @return The newly created rotated MediaFile, or null if rotation failed
     */
    fun rotatePhoto(mediaFile: MediaFile, angle: Float, onComplete: (MediaFile?) -> Unit) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                // Decrypt the file to temp location
                val decryptedFile = mediaRepository.decryptForViewing(mediaFile)

                // Load bitmap
                val originalBitmap = BitmapFactory.decodeFile(decryptedFile.absolutePath)
                    ?: throw IllegalStateException("Failed to decode image")

                // Rotate by specified angle
                val matrix = Matrix().apply {
                    postRotate(angle)
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0, 0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )

                // Save rotated bitmap to temp file
                val rotatedTempFile = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
                rotatedTempFile.outputStream().use { out ->
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // Clean up bitmaps
                originalBitmap.recycle()
                rotatedBitmap.recycle()
                decryptedFile.delete()

                // Add as new encrypted file with same folder as original
                val newMediaFile = mediaRepository.addMediaFile(
                    file = rotatedTempFile,
                    mediaType = MediaType.PHOTO,
                    folderId = mediaFile.folderId
                )

                // Clean up temp file
                rotatedTempFile.delete()

                Toast.makeText(context, "Photo rotated and saved", Toast.LENGTH_SHORT).show()
                onComplete(newMediaFile)
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "Failed to rotate photo", e)
                Toast.makeText(
                    getApplication(),
                    "Failed to rotate photo: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                onComplete(null)
            }
        }
    }

    /**
     * Crops a photo using the provided crop rectangle and saves as a new encrypted file.
     * @param mediaFile The photo to crop
     * @param cropRect The crop rectangle (x, y, width, height) in image coordinates
     * @return The newly created cropped MediaFile, or null if crop failed
     */
    fun cropPhoto(mediaFile: MediaFile, cropRect: Rect, onComplete: (MediaFile?) -> Unit) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                // Decrypt the file to temp location
                val decryptedFile = mediaRepository.decryptForViewing(mediaFile)

                // Load bitmap
                val originalBitmap = BitmapFactory.decodeFile(decryptedFile.absolutePath)
                    ?: throw IllegalStateException("Failed to decode image")

                // Validate crop rectangle
                val validCropRect = Rect(
                    cropRect.left.coerceIn(0, originalBitmap.width),
                    cropRect.top.coerceIn(0, originalBitmap.height),
                    cropRect.right.coerceIn(0, originalBitmap.width),
                    cropRect.bottom.coerceIn(0, originalBitmap.height)
                )

                if (validCropRect.width() <= 0 || validCropRect.height() <= 0) {
                    throw IllegalArgumentException("Invalid crop rectangle")
                }

                // Crop bitmap
                val croppedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    validCropRect.left,
                    validCropRect.top,
                    validCropRect.width(),
                    validCropRect.height()
                )

                // Save cropped bitmap to temp file
                val croppedTempFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                croppedTempFile.outputStream().use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // Clean up bitmaps
                originalBitmap.recycle()
                croppedBitmap.recycle()
                decryptedFile.delete()

                // Add as new encrypted file with same folder as original
                val newMediaFile = mediaRepository.addMediaFile(
                    file = croppedTempFile,
                    mediaType = MediaType.PHOTO,
                    folderId = mediaFile.folderId
                )

                // Clean up temp file
                croppedTempFile.delete()

                Toast.makeText(context, "Photo cropped and saved", Toast.LENGTH_SHORT).show()
                onComplete(newMediaFile)
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "Failed to crop photo", e)
                Toast.makeText(
                    getApplication(),
                    "Failed to crop photo: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                onComplete(null)
            }
        }
    }
}

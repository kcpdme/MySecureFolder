package com.kcpd.myfolder.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.kcpd.myfolder.data.database.dao.MediaFileDao
import com.kcpd.myfolder.data.database.entity.MediaFileEntity
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.security.SecureFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for importing media files from device storage into the encrypted vault.
 *
 * This handles:
 * - Reading files from Android Storage Access Framework (SAF)
 * - Detecting media type from MIME type
 * - Encrypting files
 * - Generating thumbnails
 * - Saving to encrypted database
 * - Progress reporting
 */
class ImportMediaUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureFileManager: SecureFileManager,
    private val mediaFileDao: MediaFileDao
) {
    /**
     * Imports a single media file.
     *
     * @param sourceUri Content URI from file picker (SAF)
     * @param targetFolderId Optional folder ID to import into
     * @return Result with imported MediaFile or error
     */
    suspend fun importFile(
        sourceUri: Uri,
        targetFolderId: String? = null
    ): Result<MediaFile> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // Get file info
            val fileName = getFileName(contentResolver, sourceUri)
            val mimeType = contentResolver.getType(sourceUri)
            val mediaType = getMediaTypeFromMime(mimeType)

            // Open input stream
            val inputStream = contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            // Create temp file
            val tempFile = File.createTempFile("import_", "_temp", context.cacheDir)

            try {
                // Copy from URI to temp file
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Encrypt the file
                val secureDir = File(secureFileManager.getSecureStorageDir(), mediaType.name.lowercase())
                secureDir.mkdirs()
                val encryptedFile = secureFileManager.encryptFile(tempFile, secureDir)

                // Generate thumbnail
                val thumbnail = when (mediaType) {
                    MediaType.PHOTO -> secureFileManager.generateImageThumbnail(encryptedFile)
                    MediaType.VIDEO -> secureFileManager.generateVideoThumbnail(encryptedFile)
                    else -> null
                }

                // Create database entity
                val id = UUID.randomUUID().toString()
                val originalSize = tempFile.length()
                val verificationHash = calculateHash(tempFile)

                val entity = MediaFileEntity(
                    id = id,
                    originalFileName = fileName,
                    encryptedFileName = encryptedFile.name,
                    encryptedFilePath = encryptedFile.absolutePath,
                    mediaType = mediaType.name,
                    encryptedThumbnailPath = null,
                    thumbnail = thumbnail,
                    duration = null, // TODO: Extract duration for audio/video
                    size = encryptedFile.length(),
                    createdAt = System.currentTimeMillis(),
                    isUploaded = false,
                    s3Url = null,
                    folderId = targetFolderId,
                    verificationHash = verificationHash,
                    originalSize = originalSize,
                    mimeType = mimeType
                )

                // Save to database
                mediaFileDao.insertFile(entity)

                // Return MediaFile
                Result.success(entity.toMediaFile())
            } finally {
                // Clean up temp file
                tempFile.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports multiple media files with progress tracking.
     *
     * @param sourceUris List of content URIs to import
     * @param targetFolderId Optional folder ID to import into
     * @return Flow emitting ImportProgress for each file
     */
    fun importFiles(
        sourceUris: List<Uri>,
        targetFolderId: String? = null
    ): Flow<ImportProgress> = flow {
        val total = sourceUris.size

        sourceUris.forEachIndexed { index, uri ->
            val progress = (index + 1) * 100 / total

            emit(ImportProgress.Importing(
                currentFile = index + 1,
                totalFiles = total,
                fileName = getFileName(context.contentResolver, uri),
                progress = progress
            ))

            val result = importFile(uri, targetFolderId)

            if (result.isSuccess) {
                emit(ImportProgress.FileImported(
                    mediaFile = result.getOrThrow(),
                    currentFile = index + 1,
                    totalFiles = total
                ))
            } else {
                emit(ImportProgress.Error(
                    error = result.exceptionOrNull() ?: Exception("Unknown error"),
                    fileName = getFileName(context.contentResolver, uri),
                    currentFile = index + 1,
                    totalFiles = total
                ))
            }
        }

        emit(ImportProgress.Completed(total))
    }

    /**
     * Gets the filename from a content URI.
     */
    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var fileName = "unknown"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }

        return fileName
    }

    /**
     * Determines MediaType from MIME type.
     */
    private fun getMediaTypeFromMime(mimeType: String?): MediaType {
        return when {
            mimeType == null -> MediaType.NOTE
            mimeType.startsWith("image/") -> MediaType.PHOTO
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            mimeType == "application/pdf" -> MediaType.PDF
            mimeType.startsWith("text/") -> MediaType.NOTE
            else -> MediaType.NOTE // Default to note for unknown types
        }
    }

    /**
     * Gets file extension from MIME type.
     */
    private fun getExtensionFromMime(mimeType: String?): String {
        if (mimeType == null) return "bin"
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    }

    /**
     * Calculates SHA-256 hash of a file.
     */
    private fun calculateHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Represents the progress of a file import operation.
 */
sealed class ImportProgress {
    /**
     * Import is in progress.
     */
    data class Importing(
        val currentFile: Int,
        val totalFiles: Int,
        val fileName: String,
        val progress: Int // 0-100
    ) : ImportProgress()

    /**
     * A file was successfully imported.
     */
    data class FileImported(
        val mediaFile: MediaFile,
        val currentFile: Int,
        val totalFiles: Int
    ) : ImportProgress()

    /**
     * An error occurred importing a file.
     */
    data class Error(
        val error: Throwable,
        val fileName: String,
        val currentFile: Int,
        val totalFiles: Int
    ) : ImportProgress()

    /**
     * All files have been imported.
     */
    data class Completed(
        val totalImported: Int
    ) : ImportProgress()
}

/**
 * Converts MediaFileEntity to MediaFile.
 */
private fun MediaFileEntity.toMediaFile(): MediaFile {
    return MediaFile(
        id = id,
        fileName = originalFileName,
        filePath = encryptedFilePath,
        mediaType = MediaType.valueOf(mediaType),
        thumbnailPath = encryptedThumbnailPath,
        thumbnail = thumbnail,
        duration = duration,
        size = size,
        createdAt = Date(createdAt),
        isUploaded = isUploaded,
        s3Url = s3Url,
        textContent = null, // Lazy loaded
        folderId = folderId,
        mimeType = mimeType
    )
}

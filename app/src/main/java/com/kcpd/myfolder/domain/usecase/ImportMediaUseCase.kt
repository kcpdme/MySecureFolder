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
        android.util.Log.d("ImportMediaUseCase", "═══════════════════════════════════════")
        android.util.Log.d("ImportMediaUseCase", "Starting import for URI: $sourceUri")
        android.util.Log.d("ImportMediaUseCase", "Target folder ID: $targetFolderId")

        try {
            val contentResolver = context.contentResolver

            // Get file info
            android.util.Log.d("ImportMediaUseCase", "Step 1: Getting file info...")
            val fileName = getFileName(contentResolver, sourceUri)
            val mimeType = contentResolver.getType(sourceUri)
            val mediaType = getMediaTypeFromMime(mimeType, fileName, sourceUri)
            android.util.Log.d("ImportMediaUseCase", "  ✓ File name: $fileName")
            android.util.Log.d("ImportMediaUseCase", "  ✓ MIME type: $mimeType")
            android.util.Log.d("ImportMediaUseCase", "  ✓ URI: $sourceUri")
            android.util.Log.d("ImportMediaUseCase", "  ✓ Media type: $mediaType")

            // Open input stream
            android.util.Log.d("ImportMediaUseCase", "Step 2: Opening input stream...")
            val inputStream = contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                android.util.Log.e("ImportMediaUseCase", "  ✗ Failed to open input stream!")
                return@withContext Result.failure(Exception("Failed to open input stream"))
            }
            android.util.Log.d("ImportMediaUseCase", "  ✓ Input stream opened")

            // Create temp file
            android.util.Log.d("ImportMediaUseCase", "Step 3: Creating temp file...")
            val tempFile = File.createTempFile("import_", "_temp", context.cacheDir)
            android.util.Log.d("ImportMediaUseCase", "  ✓ Temp file created: ${tempFile.absolutePath}")

            try {
                // Copy from URI to temp file
                android.util.Log.d("ImportMediaUseCase", "Step 4: Copying data to temp file...")
                var bytesCopied = 0L
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        bytesCopied = input.copyTo(output)
                    }
                }
                android.util.Log.d("ImportMediaUseCase", "  ✓ Copied $bytesCopied bytes")

                // Calculate hash and size BEFORE encryption (while temp file still exists)
                android.util.Log.d("ImportMediaUseCase", "Step 5: Calculating hash and size...")
                val originalSize = tempFile.length()
                android.util.Log.d("ImportMediaUseCase", "  ✓ Original size: $originalSize bytes")
                val verificationHash = calculateHash(tempFile)
                android.util.Log.d("ImportMediaUseCase", "  ✓ Hash: ${verificationHash.take(16)}...")

                // Encrypt the file
                android.util.Log.d("ImportMediaUseCase", "Step 6: Encrypting file...")
                val secureDir = File(secureFileManager.getSecureStorageDir(), mediaType.name.lowercase())
                android.util.Log.d("ImportMediaUseCase", "  Secure dir: ${secureDir.absolutePath}")
                secureDir.mkdirs()
                android.util.Log.d("ImportMediaUseCase", "  Starting encryption...")
                val encryptedFile = secureFileManager.encryptFile(tempFile, secureDir)
                android.util.Log.d("ImportMediaUseCase", "  ✓ Encrypted file: ${encryptedFile.absolutePath}")
                android.util.Log.d("ImportMediaUseCase", "  ✓ Encrypted size: ${encryptedFile.length()} bytes")

                // Generate thumbnail
                android.util.Log.d("ImportMediaUseCase", "Step 7: Generating thumbnail...")
                val thumbnail = when (mediaType) {
                    MediaType.PHOTO -> {
                        android.util.Log.d("ImportMediaUseCase", "  Generating image thumbnail...")
                        secureFileManager.generateImageThumbnail(encryptedFile)
                    }
                    MediaType.VIDEO -> {
                        android.util.Log.d("ImportMediaUseCase", "  Generating video thumbnail...")
                        secureFileManager.generateVideoThumbnail(encryptedFile)
                    }
                    else -> {
                        android.util.Log.d("ImportMediaUseCase", "  No thumbnail for $mediaType")
                        null
                    }
                }
                android.util.Log.d("ImportMediaUseCase", "  ✓ Thumbnail: ${if (thumbnail != null) "generated" else "none"}")

                // Create database entity
                android.util.Log.d("ImportMediaUseCase", "Step 8: Creating database entry...")
                val id = UUID.randomUUID().toString()
                android.util.Log.d("ImportMediaUseCase", "  Generated ID: $id")

                // Extract duration for audio/video files
                val duration = if (mediaType == MediaType.VIDEO || mediaType == MediaType.AUDIO) {
                    extractMediaDuration(tempFile.absolutePath)
                } else {
                    null
                }

                val entity = MediaFileEntity(
                    id = id,
                    originalFileName = fileName,
                    encryptedFileName = encryptedFile.name,
                    encryptedFilePath = encryptedFile.absolutePath,
                    mediaType = mediaType.name,
                    encryptedThumbnailPath = null,
                    thumbnail = thumbnail,
                    duration = duration,
                    size = encryptedFile.length(),
                    createdAt = System.currentTimeMillis(),
                    folderId = targetFolderId,
                    verificationHash = verificationHash,
                    originalSize = originalSize,
                    mimeType = mimeType
                )

                // Save to database
                android.util.Log.d("ImportMediaUseCase", "Step 9: Saving to database...")
                mediaFileDao.insertFile(entity)
                android.util.Log.d("ImportMediaUseCase", "  ✓ Database entry created")

                // Return MediaFile
                val mediaFile = entity.toMediaFile()
                android.util.Log.d("ImportMediaUseCase", "✓ Import successful!")
                android.util.Log.d("ImportMediaUseCase", "  Final media file ID: ${mediaFile.id}")
                android.util.Log.d("ImportMediaUseCase", "  Final media type: ${mediaFile.mediaType}")
                android.util.Log.d("ImportMediaUseCase", "═══════════════════════════════════════")
                Result.success(mediaFile)
            } finally {
                // Clean up temp file
                if (tempFile.exists()) {
                    android.util.Log.d("ImportMediaUseCase", "Cleaning up temp file...")
                    tempFile.delete()
                    android.util.Log.d("ImportMediaUseCase", "  ✓ Temp file deleted")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImportMediaUseCase", "✗ Import failed with exception!", e)
            android.util.Log.e("ImportMediaUseCase", "  Exception type: ${e.javaClass.name}")
            android.util.Log.e("ImportMediaUseCase", "  Message: ${e.message}")
            android.util.Log.e("ImportMediaUseCase", "  Stack trace:")
            e.printStackTrace()
            android.util.Log.d("ImportMediaUseCase", "═══════════════════════════════════════")
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
        android.util.Log.d("ImportMediaUseCase", "╔══════════════════════════════════════╗")
        android.util.Log.d("ImportMediaUseCase", "║   BATCH IMPORT STARTED               ║")
        android.util.Log.d("ImportMediaUseCase", "╚══════════════════════════════════════╝")
        android.util.Log.d("ImportMediaUseCase", "Total files to import: ${sourceUris.size}")
        android.util.Log.d("ImportMediaUseCase", "Target folder: $targetFolderId")

        val total = sourceUris.size

        sourceUris.forEachIndexed { index, uri ->
            val progress = (index + 1) * 100 / total
            val fileName = getFileName(context.contentResolver, uri)

            android.util.Log.d("ImportMediaUseCase", "")
            android.util.Log.d("ImportMediaUseCase", "┌─────────────────────────────────────┐")
            android.util.Log.d("ImportMediaUseCase", "│ Processing file ${index + 1}/$total")
            android.util.Log.d("ImportMediaUseCase", "│ URI: $uri")
            android.util.Log.d("ImportMediaUseCase", "│ Name: $fileName")
            android.util.Log.d("ImportMediaUseCase", "└─────────────────────────────────────┘")

            emit(ImportProgress.Importing(
                currentFile = index + 1,
                totalFiles = total,
                fileName = fileName,
                progress = progress
            ))

            val result = importFile(uri, targetFolderId)

            if (result.isSuccess) {
                val mediaFile = result.getOrThrow()
                android.util.Log.d("ImportMediaUseCase", "✓ File ${index + 1}/$total imported successfully!")
                emit(ImportProgress.FileImported(
                    mediaFile = mediaFile,
                    currentFile = index + 1,
                    totalFiles = total
                ))
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error")
                android.util.Log.e("ImportMediaUseCase", "✗ File ${index + 1}/$total failed to import: ${error.message}")
                emit(ImportProgress.Error(
                    error = error,
                    fileName = fileName,
                    currentFile = index + 1,
                    totalFiles = total
                ))
            }
        }

        android.util.Log.d("ImportMediaUseCase", "")
        android.util.Log.d("ImportMediaUseCase", "╔══════════════════════════════════════╗")
        android.util.Log.d("ImportMediaUseCase", "║   BATCH IMPORT COMPLETED             ║")
        android.util.Log.d("ImportMediaUseCase", "║   Total imported: $total                 ║")
        android.util.Log.d("ImportMediaUseCase", "╚══════════════════════════════════════╝")
        emit(ImportProgress.Completed(total))
    }

    /**
     * Gets the filename from a content URI.
     *
     * Handles both content:// URIs (from file picker) and file:// URIs (from ML Kit scanner).
     * Falls back to generating a timestamp-based filename if extraction fails.
     */
    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        android.util.Log.d("ImportMediaUseCase", "Extracting filename from URI: $uri")
        android.util.Log.d("ImportMediaUseCase", "  URI scheme: ${uri.scheme}")

        // Try ContentResolver query first (works for content:// URIs)
        var fileName: String? = null

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                        android.util.Log.d("ImportMediaUseCase", "  ✓ Filename from ContentResolver: $fileName")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("ImportMediaUseCase", "  ContentResolver query failed: ${e.message}")
        }

        // If ContentResolver failed or returned null/unknown, try extracting from URI path
        if (fileName.isNullOrBlank() || fileName == "unknown") {
            android.util.Log.d("ImportMediaUseCase", "  Trying to extract from URI path...")
            fileName = uri.lastPathSegment
            android.util.Log.d("ImportMediaUseCase", "  ✓ Filename from URI path: $fileName")
        }

        // Last resort: generate timestamp-based filename
        if (fileName.isNullOrBlank() || fileName == "unknown") {
            android.util.Log.d("ImportMediaUseCase", "  Generating timestamp-based filename...")
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(Date())

            // Determine extension from URI or MIME type
            val extension = when {
                uri.toString().contains(".pdf", ignoreCase = true) -> "pdf"
                uri.toString().contains(".jpg", ignoreCase = true) -> "jpg"
                uri.toString().contains(".png", ignoreCase = true) -> "png"
                uri.toString().contains(".mp4", ignoreCase = true) -> "mp4"
                else -> {
                    val mimeType = contentResolver.getType(uri)
                    when {
                        mimeType == "application/pdf" -> "pdf"
                        mimeType?.startsWith("image/") == true -> "jpg"
                        mimeType?.startsWith("video/") == true -> "mp4"
                        else -> "bin"
                    }
                }
            }

            fileName = "Scanned_Document_${timestamp}.$extension"
            android.util.Log.d("ImportMediaUseCase", "  ✓ Generated filename: $fileName")
        }

        return fileName ?: "unknown"
    }

    /**
     * Determines MediaType from MIME type, with fallback to filename and URI.
     *
     * ML Kit Document Scanner may return URIs without proper MIME types,
     * so we check filename extension and URI as fallbacks.
     */
    private fun getMediaTypeFromMime(mimeType: String?, fileName: String, uri: Uri): MediaType {
        // First try MIME type
        if (mimeType != null) {
            when {
                mimeType == "application/pdf" -> return MediaType.PDF
                mimeType.startsWith("image/") -> return MediaType.PHOTO
                mimeType.startsWith("video/") -> return MediaType.VIDEO
                mimeType.startsWith("audio/") -> return MediaType.AUDIO
                mimeType.startsWith("text/") -> return MediaType.NOTE
            }
        }

        // Fallback to filename extension
        when {
            fileName.endsWith(".pdf", ignoreCase = true) -> return MediaType.PDF
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) ||
            fileName.endsWith(".gif", ignoreCase = true) ||
            fileName.endsWith(".webp", ignoreCase = true) -> return MediaType.PHOTO
            fileName.endsWith(".mp4", ignoreCase = true) ||
            fileName.endsWith(".mkv", ignoreCase = true) ||
            fileName.endsWith(".webm", ignoreCase = true) ||
            fileName.endsWith(".avi", ignoreCase = true) -> return MediaType.VIDEO
            fileName.endsWith(".mp3", ignoreCase = true) ||
            fileName.endsWith(".m4a", ignoreCase = true) ||
            fileName.endsWith(".aac", ignoreCase = true) ||
            fileName.endsWith(".wav", ignoreCase = true) -> return MediaType.AUDIO
            fileName.endsWith(".txt", ignoreCase = true) ||
            fileName.endsWith(".md", ignoreCase = true) -> return MediaType.NOTE
        }

        // Last resort: check URI path
        val uriString = uri.toString()
        if (uriString.contains(".pdf", ignoreCase = true)) {
            return MediaType.PDF
        }

        // Default to NOTE for unknown types
        return MediaType.NOTE
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
        textContent = null, // Lazy loaded
        folderId = folderId,
        mimeType = mimeType
    )
}

/**
 * Extracts duration from audio or video file using MediaMetadataRetriever.
 * Returns duration in milliseconds, or null if extraction fails.
 */
fun extractMediaDuration(filePath: String): Long? {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val durationStr = retriever.extractMetadata(
            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
        )
        retriever.release()
        durationStr?.toLongOrNull()
    } catch (e: Exception) {
        android.util.Log.e("ImportMediaUseCase", "Failed to extract duration from $filePath", e)
        null
    }
}
package com.kcpd.myfolder.data.repository

import android.content.Context
import com.kcpd.myfolder.data.database.dao.MediaFileDao
import com.kcpd.myfolder.data.database.entity.MediaFileEntity
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import com.kcpd.myfolder.security.SecureFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaRepository with encryption and secure deletion.
 * Uses Room database for metadata and encrypted file storage.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaFileDao: MediaFileDao,
    private val secureFileManager: SecureFileManager
) {
    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val legacyMediaDir = File(context.filesDir, "media")

    // Cached StateFlows for each category
    private val categoryFilesCache = mutableMapOf<FolderCategory, StateFlow<List<MediaFile>>>()
    private val categoryCountsCache = mutableMapOf<FolderCategory, StateFlow<Int>>()

    init {
        scope.launch {
            // Migrate legacy unencrypted files
            migrateLegacyFiles()

            // Generate thumbnails for existing files that don't have them
            generateMissingThumbnails()

            // Load from database
            mediaFileDao.getAllFiles().collect { entities ->
                _mediaFiles.value = entities.map { it.toMediaFile() }
            }
        }
    }

    /**
     * Migrates legacy unencrypted files to encrypted storage.
     */
    private suspend fun migrateLegacyFiles() {
        if (!legacyMediaDir.exists()) return

        val secureDir = secureFileManager.getSecureStorageDir()

        FolderCategory.entries.forEach { category ->
            val categoryDir = File(legacyMediaDir, category.path)
            if (!categoryDir.exists()) return@forEach

            categoryDir.listFiles()?.forEach { file ->
                if (file.isFile && !secureFileManager.isEncrypted(file)) {
                    try {
                        // Encrypt and move to secure storage
                        val categorySecureDir = File(secureDir, category.path).apply { mkdirs() }
                        val encryptedFile = secureFileManager.encryptFile(file, categorySecureDir)

                        // Generate thumbnail if applicable
                        val thumbnail = when (category.mediaType) {
                            MediaType.PHOTO -> secureFileManager.generateImageThumbnail(encryptedFile)
                            MediaType.VIDEO -> secureFileManager.generateVideoThumbnail(encryptedFile)
                            else -> null
                        }

                        // Create database entry
                        val entity = MediaFileEntity(
                            id = UUID.randomUUID().toString(),
                            originalFileName = file.name,
                            encryptedFileName = encryptedFile.name,
                            encryptedFilePath = encryptedFile.absolutePath,
                            mediaType = category.mediaType?.name ?: "UNKNOWN",
                            encryptedThumbnailPath = null,
                            thumbnail = thumbnail,
                            duration = null,
                            size = encryptedFile.length(),
                            createdAt = file.lastModified(),
                            isUploaded = false,
                            s3Url = null,
                            folderId = null,
                            verificationHash = calculateHash(file),
                            originalSize = file.length(),
                            mimeType = getMimeType(file.extension)
                        )

                        mediaFileDao.insertFile(entity)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Generates thumbnails for existing media files that don't have them.
     * This runs once at startup to fix files imported before the thumbnail feature was added.
     */
    private suspend fun generateMissingThumbnails() {
        try {
            android.util.Log.d("MediaRepository", "Checking for files without thumbnails...")

            // Get all files from database
            val allFiles = mediaFileDao.getAllFilesOnce()
            val filesNeedingThumbnails = allFiles.filter { entity ->
                entity.thumbnail == null && (entity.mediaType == MediaType.PHOTO.name || entity.mediaType == MediaType.VIDEO.name)
            }

            if (filesNeedingThumbnails.isEmpty()) {
                android.util.Log.d("MediaRepository", "All files already have thumbnails")
                return
            }

            android.util.Log.d("MediaRepository", "Generating thumbnails for ${filesNeedingThumbnails.size} files...")

            filesNeedingThumbnails.forEach { entity ->
                try {
                    val encryptedFile = File(entity.encryptedFilePath)
                    if (!encryptedFile.exists()) {
                        android.util.Log.w("MediaRepository", "Skipping ${entity.originalFileName}: encrypted file not found")
                        return@forEach
                    }

                    // Generate thumbnail based on type
                    val thumbnail = when (MediaType.valueOf(entity.mediaType)) {
                        MediaType.PHOTO -> secureFileManager.generateImageThumbnail(encryptedFile)
                        MediaType.VIDEO -> secureFileManager.generateVideoThumbnail(encryptedFile)
                        else -> null
                    }

                    if (thumbnail != null) {
                        // Update entity with thumbnail
                        val updated = entity.copy(thumbnail = thumbnail)
                        mediaFileDao.updateFile(updated)
                        android.util.Log.d("MediaRepository", "Generated thumbnail for ${entity.originalFileName} (${thumbnail.size} bytes)")
                    } else {
                        android.util.Log.w("MediaRepository", "Failed to generate thumbnail for ${entity.originalFileName}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MediaRepository", "Error generating thumbnail for ${entity.originalFileName}", e)
                }
            }

            android.util.Log.d("MediaRepository", "Thumbnail generation complete")
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error in generateMissingThumbnails", e)
        }
    }

    /**
     * Adds a new media file with encryption.
     */
    suspend fun addMediaFile(file: File, mediaType: MediaType, folderId: String? = null): MediaFile {
        // Check if file exists
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
        }

        val category = FolderCategory.fromMediaType(mediaType)
        val secureDir = File(secureFileManager.getSecureStorageDir(), category.path).apply { mkdirs() }

        // Calculate hash and size BEFORE encryption
        val originalSize = file.length()
        val verificationHash = calculateHash(file)
        val fileName = file.name
        val mimeType = getMimeType(file.extension)

        // Encrypt the file
        val encryptedFile = secureFileManager.encryptFile(file, secureDir)

        // Generate thumbnail for photos and videos
        val thumbnail = when (mediaType) {
            MediaType.PHOTO -> secureFileManager.generateImageThumbnail(encryptedFile)
            MediaType.VIDEO -> secureFileManager.generateVideoThumbnail(encryptedFile)
            else -> null
        }

        // Create database entry
        val entity = MediaFileEntity(
            id = UUID.randomUUID().toString(),
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
            folderId = folderId,
            verificationHash = verificationHash,
            originalSize = originalSize,
            mimeType = mimeType
        )

        mediaFileDao.insertFile(entity)
        return entity.toMediaFile()
    }

    /**
     * Saves a note with encryption.
     */
    suspend fun saveNote(fileName: String, content: String, folderId: String? = null): MediaFile {
        val category = FolderCategory.NOTES
        val secureDir = File(secureFileManager.getSecureStorageDir(), category.path).apply { mkdirs() }

        // Create temp file with content
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeText(content)

        try {
            // Calculate hash and size BEFORE encryption
            val originalSize = tempFile.length()
            val verificationHash = calculateHash(tempFile)

            // Encrypt the note
            val encryptedFile = secureFileManager.encryptFile(tempFile, secureDir)

            // Create database entry
            val entity = MediaFileEntity(
                id = UUID.randomUUID().toString(),
                originalFileName = fileName,
                encryptedFileName = encryptedFile.name,
                encryptedFilePath = encryptedFile.absolutePath,
                mediaType = MediaType.NOTE.name,
                encryptedThumbnailPath = null,
                thumbnail = null, // Notes don't have thumbnails
                duration = null,
                size = encryptedFile.length(),
                createdAt = System.currentTimeMillis(),
                isUploaded = false,
                s3Url = null,
                folderId = folderId,
                verificationHash = verificationHash,
                originalSize = originalSize,
                mimeType = "text/plain"
            )

            mediaFileDao.insertFile(entity)
            return entity.toMediaFile()
        } finally {
            // Clean up temp file
            tempFile.delete()
        }
    }

    /**
     * Loads decrypted note content.
     */
    suspend fun loadNoteContent(mediaFile: MediaFile): String {
        if (mediaFile.mediaType != MediaType.NOTE) return ""

        val encryptedFile = File(mediaFile.filePath)
        if (!encryptedFile.exists()) return ""

        return try {
            val decryptedFile = secureFileManager.decryptFile(encryptedFile)
            val content = decryptedFile.readText()
            secureFileManager.secureDelete(decryptedFile)
            content
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Decrypts a media file for viewing.
     * Uses the original filename to ensure proper file extension.
     * Caller must securely delete the returned file after use.
     */
    suspend fun decryptForViewing(mediaFile: MediaFile): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        android.util.Log.d("MediaRepository", "Decrypting file for viewing...")
        android.util.Log.d("MediaRepository", "  Original filename: ${mediaFile.fileName}")
        android.util.Log.d("MediaRepository", "  Encrypted file: ${mediaFile.filePath}")

        val encryptedFile = File(mediaFile.filePath)
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        // Decrypt using SecureFileManager's method first
        val tempDecryptedFile = secureFileManager.decryptFile(encryptedFile)

        // Rename to use original filename to preserve extension
        val properTempFile = File(context.cacheDir, "temp_${mediaFile.fileName}")

        // If temp file already exists, delete it first
        if (properTempFile.exists()) {
            properTempFile.delete()
        }

        // Rename the temp file to have proper extension
        tempDecryptedFile.copyTo(properTempFile, overwrite = true)
        tempDecryptedFile.delete()

        android.util.Log.d("MediaRepository", "  ✓ Decrypted to: ${properTempFile.absolutePath}")
        android.util.Log.d("MediaRepository", "  ✓ File extension: ${properTempFile.extension}")

        properTempFile
    }

    /**
     * Securely deletes a media file.
     */
    suspend fun deleteMediaFile(mediaFile: MediaFile): Boolean {
        val encryptedFile = File(mediaFile.filePath)

        // Delete encrypted file securely
        val deleted = secureFileManager.secureDelete(encryptedFile)

        // Delete thumbnail if exists
        val entity = mediaFileDao.getFileById(mediaFile.id)
        entity?.encryptedThumbnailPath?.let { thumbnailPath ->
            secureFileManager.secureDelete(File(thumbnailPath))
        }

        // Remove from database
        if (deleted) {
            mediaFileDao.deleteFileById(mediaFile.id)
        }

        return deleted
    }

    suspend fun moveMediaFileToFolder(mediaFile: MediaFile, folderId: String?) {
        mediaFileDao.moveToFolder(mediaFile.id, folderId)
    }

    suspend fun updateMediaFile(mediaFile: MediaFile) {
        val entity = mediaFileDao.getFileById(mediaFile.id) ?: return

        val updated = entity.copy(
            originalFileName = mediaFile.fileName,
            folderId = mediaFile.folderId,
            isUploaded = mediaFile.isUploaded,
            s3Url = mediaFile.s3Url
        )

        mediaFileDao.updateFile(updated)
    }

    suspend fun markAsUploaded(mediaFileId: String, s3Url: String) {
        mediaFileDao.markAsUploaded(mediaFileId, s3Url)
    }

    fun getFilesForCategory(category: FolderCategory): StateFlow<List<MediaFile>> {
        return categoryFilesCache.getOrPut(category) {
            mediaFiles.map { files ->
                if (category.mediaType == null) files // ALL_FILES
                else files.filter { it.mediaType == category.mediaType }
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
        }
    }

    fun getFilesInFolder(folderId: String?, category: FolderCategory): List<MediaFile> {
        return _mediaFiles.value.filter {
            if (category.mediaType == null) true // ALL_FILES
            else it.mediaType == category.mediaType && it.folderId == folderId
        }
    }

    fun getFileCountForCategory(category: FolderCategory): StateFlow<Int> {
        return categoryCountsCache.getOrPut(category) {
            mediaFiles.map { files ->
                if (category.mediaType == null) files.size // ALL_FILES
                else files.count { it.mediaType == category.mediaType }
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )
        }
    }

    fun getCategoryDirectory(category: FolderCategory): File {
        return File(secureFileManager.getSecureStorageDir(), category.path).apply { mkdirs() }
    }

    /**
     * Calculates SHA-256 hash for file integrity verification.
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

    /**
     * Gets MIME type from file extension.
     */
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
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
            folderId = folderId,
            textContent = null, // Lazy loaded
            mimeType = mimeType
        )
    }
}

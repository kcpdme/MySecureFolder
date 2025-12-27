package com.kcpd.myfolder.data.repository

import android.content.Context
import com.kcpd.myfolder.data.database.AppDatabase
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
 * All operations are atomic using database transactions to ensure consistency.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
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
                            MediaType.PHOTO -> secureFileManager.generateImageThumbnail(
                                encryptedFile
                            )

                            MediaType.VIDEO -> secureFileManager.generateVideoThumbnail(
                                encryptedFile
                            )

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

            android.util.Log.d(
                "MediaRepository",
                "Generating thumbnails for ${filesNeedingThumbnails.size} files..."
            )

            filesNeedingThumbnails.forEach { entity ->
                try {
                    val encryptedFile = File(entity.encryptedFilePath)
                    if (!encryptedFile.exists()) {
                        android.util.Log.w(
                            "MediaRepository",
                            "Skipping ${entity.originalFileName}: encrypted file not found"
                        )
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
                        android.util.Log.d(
                            "MediaRepository",
                            "Generated thumbnail for ${entity.originalFileName} (${thumbnail.size} bytes)"
                        )
                    } else {
                        android.util.Log.w(
                            "MediaRepository",
                            "Failed to generate thumbnail for ${entity.originalFileName}"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "MediaRepository",
                        "Error generating thumbnail for ${entity.originalFileName}",
                        e
                    )
                }
            }

            android.util.Log.d("MediaRepository", "Thumbnail generation complete")
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Error in generateMissingThumbnails", e)
        }
    }

    /**
     * Adds a new media file with encryption.
     * Uses atomic transaction to ensure database and filesystem consistency.
     */
    suspend fun addMediaFile(
        file: File,
        mediaType: MediaType,
        folderId: String? = null
    ): MediaFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        android.util.Log.d("MediaRepository", "Adding media file: ${file.name}")

        // Check if file exists
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
        }

        val category = FolderCategory.fromMediaType(mediaType)
        val secureDir =
            File(secureFileManager.getSecureStorageDir(), category.path).apply { mkdirs() }

        // For photos: rotate based on EXIF orientation BEFORE encryption (Tella's approach)
        val fileToEncrypt = if (mediaType == MediaType.PHOTO) {
            rotatePhotoIfNeeded(file)
        } else {
            file
        }

        // Calculate hash and size BEFORE encryption
        val originalSize = fileToEncrypt.length()
        val verificationHash = calculateHash(fileToEncrypt)
        val fileName = file.name
        val mimeType = getMimeType(file.extension)

        var encryptedFile: File? = null
        var thumbnail: ByteArray? = null

        try {
            // Step 1: Encrypt the file (which is already rotated for photos)
            encryptedFile = secureFileManager.encryptFile(fileToEncrypt, secureDir)
            android.util.Log.d("MediaRepository", "  Encrypted to: ${encryptedFile.name}")

            // Clean up rotated temp file if we created one
            if (fileToEncrypt != file && fileToEncrypt.exists()) {
                fileToEncrypt.delete()
            }

            // Step 2: Generate thumbnail for photos and videos
            thumbnail = when (mediaType) {
                MediaType.PHOTO -> secureFileManager.generateImageThumbnail(encryptedFile)
                MediaType.VIDEO -> secureFileManager.generateVideoThumbnail(encryptedFile)
                else -> null
            }

            // Step 3: Create database entry (Room auto-wraps in transaction)
            val duration = if (mediaType == MediaType.VIDEO || mediaType == MediaType.AUDIO) {
                extractMediaDuration(file.absolutePath)
            } else {
                null
            }
            val entity = MediaFileEntity(
                id = UUID.randomUUID().toString(),
                originalFileName = fileName,
                encryptedFileName = encryptedFile.name,
                encryptedFilePath = encryptedFile.absolutePath,
                mediaType = mediaType.name,
                encryptedThumbnailPath = null,
                thumbnail = thumbnail,
                duration = duration,
                size = encryptedFile.length(),
                createdAt = System.currentTimeMillis(),
                isUploaded = false,
                s3Url = null,
                folderId = folderId,
                verificationHash = verificationHash,
                originalSize = originalSize,
                mimeType = mimeType
            )

            // Insert into database (atomic operation by Room)
            mediaFileDao.insertFile(entity)

            android.util.Log.d("MediaRepository", "  Database entry created")
            return@withContext entity.toMediaFile()

        } catch (e: Exception) {
            // Rollback: Delete encrypted file if database insert failed
            android.util.Log.e("MediaRepository", "Failed to add media file, rolling back", e)
            encryptedFile?.let { file ->
                if (file.exists()) {
                    android.util.Log.w("MediaRepository", "  Rolling back: deleting encrypted file")
                    secureFileManager.secureDelete(file)
                }
            }
            throw e
        }
    }

    /**
     * Saves a note with encryption.
     * Uses atomic transaction to ensure database and filesystem consistency.
     */
    suspend fun saveNote(fileName: String, content: String, folderId: String? = null): MediaFile =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            android.util.Log.d("MediaRepository", "Saving note: $fileName")

            val category = FolderCategory.NOTES
            val secureDir =
                File(secureFileManager.getSecureStorageDir(), category.path).apply { mkdirs() }

            // Create temp file with content
            val tempFile = File(context.cacheDir, fileName)
            tempFile.writeText(content)

            var encryptedFile: File? = null

            try {
                // Calculate hash and size BEFORE encryption
                val originalSize = tempFile.length()
                val verificationHash = calculateHash(tempFile)

                // Encrypt the note
                encryptedFile = secureFileManager.encryptFile(tempFile, secureDir)
                android.util.Log.d("MediaRepository", "  Note encrypted to: ${encryptedFile.name}")

                // Create database entry (Room auto-wraps in transaction)
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

                // Insert into database (atomic operation by Room)
                mediaFileDao.insertFile(entity)

                android.util.Log.d("MediaRepository", "  Database entry created")
                return@withContext entity.toMediaFile()

            } catch (e: Exception) {
                // Rollback: Delete encrypted file if database insert failed
                android.util.Log.e("MediaRepository", "Failed to save note, rolling back", e)
                encryptedFile?.let { file ->
                    if (file.exists()) {
                        android.util.Log.w(
                            "MediaRepository",
                            "  Rolling back: deleting encrypted note"
                        )
                        secureFileManager.secureDelete(file)
                    }
                }
                throw e
            } finally {
                // Always clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete()
                }
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
    suspend fun decryptForViewing(mediaFile: MediaFile): File =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            android.util.Log.d("MediaRepository", "Decrypting file for viewing...")
            android.util.Log.d("MediaRepository", "  Original filename: ${mediaFile.fileName}")
            android.util.Log.d("MediaRepository", "  Encrypted file: ${mediaFile.filePath}")

            val encryptedFile = File(mediaFile.filePath)
            require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

            // Decrypt directly to final temp file with correct extension
            val tempFile = File(context.cacheDir, "temp_${mediaFile.fileName}")

            // If temp file already exists, delete it first
            if (tempFile.exists()) {
                android.util.Log.d("MediaRepository", "  Deleting existing temp file")
                tempFile.delete()
            }

            android.util.Log.d("MediaRepository", "  Starting decryption...")

            // Decrypt directly to the temp file
            secureFileManager.getStreamingDecryptedInputStream(encryptedFile).use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            require(tempFile.exists()) { "Failed to create decrypted temp file: ${tempFile.absolutePath}" }
            android.util.Log.d(
                "MediaRepository",
                "  ✓ Decrypted to: ${tempFile.absolutePath} (${tempFile.length()} bytes)"
            )

            tempFile
        }

    /**
     * Analyzes storage usage and returns breakdown by file type.
     * Useful for debugging storage issues.
     */
    suspend fun analyzeStorageUsage(): Map<String, Long> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val result = mutableMapOf<String, Long>()

            try {
                val secureMediaDir = secureFileManager.getSecureStorageDir()

                // Count size by category
                com.kcpd.myfolder.data.model.FolderCategory.values().forEach { category ->
                    val categoryDir = File(secureMediaDir, category.path)
                    if (categoryDir.exists()) {
                        val size = categoryDir.walkTopDown()
                            .filter { it.isFile }
                            .map { it.length() }
                            .sum()
                        result[category.displayName] = size
                    }
                }

                // Database size (including WAL and SHM files)
                val dbFile = context.getDatabasePath("myfolder_database")
                var dbTotalSize = 0L
                if (dbFile.exists()) {
                    dbTotalSize += dbFile.length()
                    android.util.Log.d("MediaRepository", "DB main: ${dbFile.length()} bytes")
                }

                // Check for WAL file
                val walFile = File(dbFile.absolutePath + "-wal")
                if (walFile.exists()) {
                    dbTotalSize += walFile.length()
                    android.util.Log.d("MediaRepository", "DB WAL: ${walFile.length()} bytes")
                }

                // Check for SHM file
                val shmFile = File(dbFile.absolutePath + "-shm")
                if (shmFile.exists()) {
                    dbTotalSize += shmFile.length()
                    android.util.Log.d("MediaRepository", "DB SHM: ${shmFile.length()} bytes")
                }

                result["Database"] = dbTotalSize

                // All files in app data directory (for debugging)
                val filesDir = context.filesDir
                val totalFilesSize = filesDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
                android.util.Log.d("MediaRepository", "Total files/ size: $totalFilesSize bytes")

                // Find largest files to identify what's taking space
                val largeFiles = filesDir.walkTopDown()
                    .filter { it.isFile && it.length() > 1_000_000 } // Files > 1MB
                    .sortedByDescending { it.length() }
                    .take(10)

                android.util.Log.d("MediaRepository", "=== TOP 10 LARGEST FILES ===")
                largeFiles.forEach { file ->
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    android.util.Log.d(
                        "MediaRepository",
                        "  %.2f MB - %s".format(sizeMB, file.absolutePath)
                    )
                }

                // Check for orphaned files in root of secure_media
                val orphanedFiles = secureMediaDir.listFiles()?.filter { it.isFile }
                if (orphanedFiles != null && orphanedFiles.isNotEmpty()) {
                    val orphanedSize = orphanedFiles.sumOf { it.length() }
                    result["Orphaned Files"] = orphanedSize
                    android.util.Log.w(
                        "MediaRepository",
                        "Found ${orphanedFiles.size} orphaned files: $orphanedSize bytes"
                    )
                }

                // Cache size
                val cacheSize = context.cacheDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
                result["Cache"] = cacheSize

                // Code cache (compiled code, DEX files)
                val codeCacheDir = context.codeCacheDir
                if (codeCacheDir.exists()) {
                    val codeCacheSize = codeCacheDir.walkTopDown()
                        .filter { it.isFile }
                        .map { it.length() }
                        .sum()
                    result["Code Cache"] = codeCacheSize
                }

                android.util.Log.d("MediaRepository", "Storage analysis: $result")
                android.util.Log.d(
                    "MediaRepository",
                    "Total accounted: ${result.values.sum()} bytes"
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Failed to analyze storage", e)
            }

            result
        }

    /**
     * Cleans up orphaned files:
     * 1. Unencrypted originals in /media/ (should have been deleted after encryption)
     * 2. Ghost encrypted files in /secure_media/ (exist on disk but not in database)
     *
     * This fixes a bug where secureDelete didn't return the boolean properly,
     * causing files to remain on disk even after database entries were removed.
     *
     * SECURITY CRITICAL: Unencrypted files are plain text originals!
     */
    suspend fun cleanupOrphanedFiles(): Int =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var deletedCount = 0
            var deletedBytes = 0L

            try {
                // Step 1: Clean up orphaned unencrypted files in /files/media/
                val mediaDir = File(context.filesDir, "media")
                if (mediaDir.exists()) {
                    val orphanedFiles = mediaDir.listFiles()?.filter { it.isFile } ?: emptyList()

                    if (orphanedFiles.isNotEmpty()) {
                        android.util.Log.w(
                            "MediaRepository",
                            "Found ${orphanedFiles.size} orphaned UNENCRYPTED files in /media/"
                        )

                        orphanedFiles.forEach { file ->
                            val sizeMB = file.length() / (1024.0 * 1024.0)
                            android.util.Log.w(
                                "MediaRepository",
                                "  [UNENCRYPTED] Deleting: ${file.name} (%.2f MB)".format(sizeMB)
                            )

                            deletedBytes += file.length()
                            if (secureFileManager.secureDelete(file)) {
                                deletedCount++
                            }
                        }
                    }
                }

                // Step 2: Clean up ghost encrypted files in /files/secure_media/
                // These are files that exist on disk but have no database entry
                val secureMediaDir = secureFileManager.getSecureStorageDir()

                // Get all file paths from database
                val dbFiles = mediaFileDao.getAllFilesOnce()
                val dbFilePaths = dbFiles.map { it.encryptedFilePath }.toSet()

                android.util.Log.d(
                    "MediaRepository",
                    "Database has ${dbFilePaths.size} file entries"
                )

                // Scan all encrypted files on disk
                val allDiskFiles = mutableListOf<File>()
                com.kcpd.myfolder.data.model.FolderCategory.values().forEach { category ->
                    val categoryDir = File(secureMediaDir, category.path)
                    if (categoryDir.exists()) {
                        categoryDir.listFiles()?.filter { it.isFile }?.let { files ->
                            allDiskFiles.addAll(files)
                        }
                    }
                }

                android.util.Log.d(
                    "MediaRepository",
                    "Disk has ${allDiskFiles.size} encrypted files"
                )

                // Find ghost files (on disk but not in database)
                val ghostFiles = allDiskFiles.filter { it.absolutePath !in dbFilePaths }

                if (ghostFiles.isNotEmpty()) {
                    android.util.Log.w(
                        "MediaRepository",
                        "Found ${ghostFiles.size} GHOST encrypted files (not in database)"
                    )

                    ghostFiles.forEach { file ->
                        val sizeMB = file.length() / (1024.0 * 1024.0)
                        android.util.Log.w(
                            "MediaRepository",
                            "  [GHOST] Deleting: ${file.name} (%.2f MB)".format(sizeMB)
                        )

                        deletedBytes += file.length()
                        if (secureFileManager.secureDelete(file)) {
                            deletedCount++
                        }
                    }
                }

                val freedMB = deletedBytes / (1024.0 * 1024.0)
                android.util.Log.i(
                    "MediaRepository",
                    "Cleanup complete: Deleted $deletedCount files, freed %.2f MB".format(freedMB)
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Failed to cleanup orphaned files", e)
            }

            deletedCount
        }

    /**
     * Securely deletes a media file.
     * Uses atomic transaction to ensure database and filesystem consistency.
     *
     * CRITICAL: Always removes database entry to prevent ghost entries,
     * even if file deletion fails (file might not exist anymore).
     */
    suspend fun deleteMediaFile(mediaFile: MediaFile): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            android.util.Log.d("MediaRepository", "Deleting media file: ${mediaFile.fileName}")
            android.util.Log.d("MediaRepository", "  File path: ${mediaFile.filePath}")

            val encryptedFile = File(mediaFile.filePath)

            // Check if file exists before trying to delete
            val fileExists = encryptedFile.exists()
            android.util.Log.d("MediaRepository", "  File exists: $fileExists")

            try {
                // Step 1: Delete encrypted file securely (before DB transaction)
                var fileDeleted = false
                if (fileExists) {
                    fileDeleted = secureFileManager.secureDelete(encryptedFile)
                    android.util.Log.d("MediaRepository", "  File deletion result: $fileDeleted")

                    if (!fileDeleted && fileExists) {
                        // File still exists after deletion attempt - this is a problem
                        android.util.Log.e(
                            "MediaRepository",
                            "  CRITICAL: File deletion failed but file still exists!"
                        )
                        // Continue anyway - we'll remove DB entry to prevent ghost entries
                    }
                } else {
                    android.util.Log.w(
                        "MediaRepository",
                        "  File doesn't exist, skipping file deletion"
                    )
                }

                // Step 2: Delete thumbnail if exists
                val entity = mediaFileDao.getFileById(mediaFile.id)
                entity?.encryptedThumbnailPath?.let { thumbnailPath ->
                    val thumbFile = File(thumbnailPath)
                    if (thumbFile.exists()) {
                        val thumbDeleted = secureFileManager.secureDelete(thumbFile)
                        android.util.Log.d(
                            "MediaRepository",
                            "  Thumbnail deletion result: $thumbDeleted"
                        )
                    }
                }

                // Step 3: Remove from database (atomic operation by Room)
                // ALWAYS remove from database, even if file deletion fails
                // This prevents ghost entries where DB has a record but file doesn't exist
                mediaFileDao.deleteFileById(mediaFile.id)
                android.util.Log.d("MediaRepository", "  Database entry removed")

                // Return true if file was deleted OR didn't exist (successful cleanup either way)
                return@withContext fileDeleted || !fileExists

            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Error during deletion", e)
                // If we fail partway through, at least try to remove DB entry
                try {
                    mediaFileDao.deleteFileById(mediaFile.id)
                    android.util.Log.w("MediaRepository", "  Database entry removed despite errors")
                } catch (dbError: Exception) {
                    android.util.Log.e(
                        "MediaRepository",
                        "  Failed to remove database entry",
                        dbError
                    )
                }
                throw e
            }
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

    /**
     * Gets the total size (in bytes) of all files in a category.
     */
    fun getFileSizeForCategory(category: FolderCategory): StateFlow<Long> {
        return mediaFiles.map { files ->
            val filteredFiles = if (category.mediaType == null) {
                files // ALL_FILES
            } else {
                files.filter { it.mediaType == category.mediaType }
            }
            filteredFiles.sumOf { it.size }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )
    }

    fun getCategoryDirectory(category: FolderCategory): File {
        return File(secureFileManager.getSecureStorageDir(), category.path).apply { mkdirs() }
    }

    /**
     * Rotates photo based on EXIF orientation before encryption (Tella's approach).
     * Returns a new temp file with rotated bitmap, or the original file if no rotation needed.
     */
    private fun rotatePhotoIfNeeded(file: File): File {
        try {
            // Read EXIF orientation
            val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )

            android.util.Log.d(
                "MediaRepository",
                "Photo EXIF orientation: $orientation for ${file.name}"
            )

            // If no rotation needed, return original file
            if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL ||
                orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
            ) {
                android.util.Log.d("MediaRepository", "No rotation needed, using original file")
                return file
            }

            // Decode bitmap
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("Failed to decode photo: ${file.absolutePath}")

            // Apply rotation based on EXIF
            val rotatedBitmap = when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                    android.util.Log.d("MediaRepository", "Rotating 90°")
                    rotateBitmap(bitmap, 90f)
                }

                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                    android.util.Log.d("MediaRepository", "Rotating 180°")
                    rotateBitmap(bitmap, 180f)
                }

                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                    android.util.Log.d("MediaRepository", "Rotating 270°")
                    rotateBitmap(bitmap, 270f)
                }

                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    android.util.Log.d("MediaRepository", "Flipping horizontally")
                    flipBitmap(bitmap, horizontal = true, vertical = false)
                }

                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    android.util.Log.d("MediaRepository", "Flipping vertically")
                    flipBitmap(bitmap, horizontal = false, vertical = true)
                }

                else -> {
                    android.util.Log.d(
                        "MediaRepository",
                        "Unsupported orientation: $orientation, using original"
                    )
                    return file
                }
            }

            // Save rotated bitmap to temp file
            val tempFile = File(context.cacheDir, "rotated_${file.name}")
            tempFile.outputStream().use { out ->
                rotatedBitmap.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG,
                    100, // Use maximum quality since we'll encrypt it
                    out
                )
            }

            // Clean up bitmaps
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap.recycle()

            android.util.Log.d(
                "MediaRepository",
                "Saved rotated photo to temp file: ${tempFile.name}"
            )
            return tempFile

        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Failed to rotate photo, using original", e)
            return file
        }
    }

    /**
     * Rotates a bitmap by the given degrees.
     */
    private fun rotateBitmap(
        bitmap: android.graphics.Bitmap,
        degrees: Float
    ): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    /**
     * Flips a bitmap horizontally and/or vertically.
     */
    private fun flipBitmap(
        bitmap: android.graphics.Bitmap,
        horizontal: Boolean,
        vertical: Boolean
    ): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.preScale(
            if (horizontal) -1f else 1f,
            if (vertical) -1f else 1f
        )
        return android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
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

    /**
     * Extracts duration from audio or video file using MediaMetadataRetriever.
     * Returns duration in milliseconds, or null if extraction fails.
     */
    private fun extractMediaDuration(filePath: String): Long? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            retriever.release()
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Failed to extract duration from $filePath", e)
            null
        }
    }
}
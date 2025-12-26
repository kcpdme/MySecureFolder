package com.kcpd.myfolder.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles secure file operations including encryption/decryption and secure deletion.
 * All media files are encrypted at rest using AES-256-GCM.
 */
@Singleton
class SecureFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val ENCRYPTED_FILE_EXTENSION = ".enc"
        private const val BUFFER_SIZE = 8192
        private const val SECURE_DELETE_PASSES = 3 // DoD 5220.22-M standard
        private const val THUMBNAIL_SIZE = 200 // Thumbnail size in pixels (like Tella's 1/10 approach)
        private const val THUMBNAIL_QUALITY = 85 // JPEG compression quality
        private const val KEY_ALIAS = "myfolder_master_key"
    }

    /**
     * Gets the secure storage directory for encrypted files.
     */
    fun getSecureStorageDir(): File {
        return File(context.filesDir, "secure_media").apply { mkdirs() }
    }

    /**
     * Encrypts a file and stores it securely.
     * Returns the path to the encrypted file.
     */
    suspend fun encryptFile(sourceFile: File, destinationDir: File): File = withContext(Dispatchers.IO) {
        require(sourceFile.exists()) { "Source file does not exist: ${sourceFile.path}" }

        destinationDir.mkdirs()

        // Read source file
        val plainData = sourceFile.readBytes()

        // Encrypt the data
        val encryptedData = securityManager.encrypt(plainData)

        // Write to destination
        val encryptedFile = File(destinationDir, sourceFile.name + ENCRYPTED_FILE_EXTENSION)
        encryptedFile.writeBytes(encryptedData)

        // Securely delete the source file
        secureDelete(sourceFile)

        encryptedFile
    }

    /**
     * Decrypts an encrypted file to a temporary location for viewing.
     * Caller is responsible for securely deleting the decrypted file after use.
     */
    suspend fun decryptFile(encryptedFile: File): File = withContext(Dispatchers.IO) {
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        // Read encrypted data
        val encryptedData = encryptedFile.readBytes()

        // Decrypt
        val plainData = securityManager.decrypt(encryptedData)

        // Create temporary file (remove .enc extension)
        val originalName = encryptedFile.name.removeSuffix(ENCRYPTED_FILE_EXTENSION)
        val tempFile = File(context.cacheDir, "temp_$originalName")

        tempFile.writeBytes(plainData)
        tempFile
    }

    /**
     * Gets a decrypted InputStream from an encrypted file.
     * This is memory-efficient for use with image loaders like Coil.
     * The stream provides decrypted data on-the-fly without creating temporary files.
     *
     * @deprecated Use getStreamingDecryptedInputStream for better performance
     */
    @Deprecated("Use getStreamingDecryptedInputStream instead")
    fun getDecryptedInputStream(encryptedFile: File): java.io.InputStream {
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        // Read and decrypt the entire file
        // Note: For very large files, you might want to implement streaming decryption
        val encryptedData = encryptedFile.readBytes()
        val plainData = securityManager.decrypt(encryptedData)

        return java.io.ByteArrayInputStream(plainData)
    }

    /**
     * Gets a streaming decrypted InputStream from an encrypted file.
     * This is the most memory-efficient method - decrypts on-the-fly as data is read.
     *
     * Uses CipherInputStream for true chunk-by-chunk decryption without loading
     * the entire file into memory.
     *
     * This approach:
     * - Minimizes memory usage (only buffers 8KB chunks)
     * - Reduces battery consumption (streaming vs batch processing)
     * - Improves UI responsiveness (progressive loading)
     * - Never materializes full decrypted file in memory or disk
     * - Supports files of ANY size without OutOfMemoryError
     */
    fun getStreamingDecryptedInputStream(encryptedFile: File): java.io.InputStream {
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        val fileInputStream = FileInputStream(encryptedFile)

        try {
            // Read IV from the first 12 bytes
            val iv = ByteArray(12)
            val bytesRead = fileInputStream.read(iv)
            if (bytesRead != 12) {
                fileInputStream.close()
                throw IllegalStateException("Failed to read IV from encrypted file")
            }

            // Use key from SecurityManager (supports password-derived key)
            val key = securityManager.getFileEncryptionKey()

            // Initialize cipher for decryption
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, gcmSpec)

            // Return CipherInputStream that decrypts chunks on-the-fly
            return javax.crypto.CipherInputStream(fileInputStream, cipher)
        } catch (e: Exception) {
            // Clean up on error
            try {
                fileInputStream.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /**
     * Gets a streaming OutputStream for encrypting data directly to a file.
     * Data written to this stream is automatically encrypted and saved.
     *
     * This is memory-efficient for large files - encrypts chunks as they're written
     * rather than buffering the entire file in memory.
     *
     * Uses CipherOutputStream for true chunk-by-chunk encryption.
     */
    fun getStreamingEncryptionOutputStream(targetFile: File): java.io.OutputStream {
        targetFile.parentFile?.mkdirs()

        val fileOutputStream = FileOutputStream(targetFile)

        try {
            // Use key from SecurityManager (supports password-derived key)
            val key = securityManager.getFileEncryptionKey()

            // Initialize cipher for encryption
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)

            // Write IV to file first (12 bytes for GCM)
            val iv = cipher.iv
            fileOutputStream.write(iv)
            fileOutputStream.flush()

            // Return CipherOutputStream that encrypts chunks on-the-fly
            return javax.crypto.CipherOutputStream(fileOutputStream, cipher)
        } catch (e: Exception) {
            // Clean up on error
            try {
                fileOutputStream.close()
                targetFile.delete()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /**
     * Encrypts data in place, replacing the original file with encrypted version.
     */
    suspend fun encryptFileInPlace(file: File): File = withContext(Dispatchers.IO) {
        require(file.exists()) { "File does not exist: ${file.path}" }

        // Read and encrypt
        val plainData = file.readBytes()
        val encryptedData = securityManager.encrypt(plainData)

        // Create new encrypted file
        val encryptedFile = File(file.parentFile, file.name + ENCRYPTED_FILE_EXTENSION)
        encryptedFile.writeBytes(encryptedData)

        // Securely delete original
        secureDelete(file)

        encryptedFile
    }

    /**
     * Decrypts data in place, replacing the encrypted file with plaintext version.
     */
    suspend fun decryptFileInPlace(encryptedFile: File): File = withContext(Dispatchers.IO) {
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        // Read and decrypt
        val encryptedData = encryptedFile.readBytes()
        val plainData = securityManager.decrypt(encryptedData)

        // Create plaintext file
        val originalName = encryptedFile.name.removeSuffix(ENCRYPTED_FILE_EXTENSION)
        val plainFile = File(encryptedFile.parentFile, originalName)
        plainFile.writeBytes(plainData)

        // Securely delete encrypted file
        secureDelete(encryptedFile)

        plainFile
    }

    /**
     * Securely deletes a file by overwriting it multiple times before deletion.
     * Uses DoD 5220.22-M standard (3-pass overwrite).
     *
     * Pass 1: Overwrite with 0x00
     * Pass 2: Overwrite with 0xFF
     * Pass 3: Overwrite with random data
     */
    suspend fun secureDelete(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext false
        }

        try {
            val fileSize = file.length()

            if (fileSize > 0) {
                // Pass 1: Overwrite with zeros
                overwriteFile(file, 0x00.toByte(), fileSize)

                // Pass 2: Overwrite with ones
                overwriteFile(file, 0xFF.toByte(), fileSize)

                // Pass 3: Overwrite with random data
                overwriteFileRandom(file, fileSize)
            }

            // Finally delete the file
            file.delete()
        } catch (e: Exception) {
            // If secure deletion fails, still attempt regular deletion
            file.delete()
        }
    }

    /**
     * Overwrites file with a specific byte pattern.
     */
    private fun overwriteFile(file: File, pattern: Byte, size: Long) {
        FileOutputStream(file).use { fos ->
            val buffer = ByteArray(BUFFER_SIZE) { pattern }
            var remaining = size

            while (remaining > 0) {
                val toWrite = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
                fos.write(buffer, 0, toWrite)
                remaining -= toWrite
            }
            fos.fd.sync() // Force write to disk
        }
    }

    /**
     * Overwrites file with random data.
     */
    private fun overwriteFileRandom(file: File, size: Long) {
        val random = SecureRandom()
        FileOutputStream(file).use { fos ->
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = size

            while (remaining > 0) {
                val toWrite = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
                random.nextBytes(buffer)
                fos.write(buffer, 0, toWrite)
                remaining -= toWrite
            }
            fos.fd.sync() // Force write to disk
        }
    }

    /**
     * Securely deletes all files in a directory and the directory itself.
     */
    suspend fun secureDeleteDirectory(directory: File): Boolean = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext false
        }

        var success = true
        directory.listFiles()?.forEach { file ->
            success = if (file.isDirectory) {
                secureDeleteDirectory(file) && success
            } else {
                secureDelete(file) && success
            }
        }

        directory.delete() && success
    }

    /**
     * Creates a secure thumbnail by decrypting, creating thumbnail, and re-encrypting.
     */
    suspend fun createEncryptedThumbnail(
        encryptedSourceFile: File,
        thumbnailGenerator: suspend (File) -> File
    ): File = withContext(Dispatchers.IO) {
        // Decrypt to temp
        val tempDecrypted = decryptFile(encryptedSourceFile)

        try {
            // Generate thumbnail
            val thumbnail = thumbnailGenerator(tempDecrypted)

            // Encrypt thumbnail
            val encryptedThumbnail = encryptFileInPlace(thumbnail)

            encryptedThumbnail
        } finally {
            // Always clean up temp file
            secureDelete(tempDecrypted)
        }
    }

    /**
     * Checks if a file is encrypted (has .enc extension).
     */
    fun isEncrypted(file: File): Boolean {
        return file.name.endsWith(ENCRYPTED_FILE_EXTENSION)
    }

    /**
     * Gets the original filename from an encrypted file.
     */
    fun getOriginalFileName(encryptedFile: File): String {
        return encryptedFile.name.removeSuffix(ENCRYPTED_FILE_EXTENSION)
    }

    /**
     * Generates a thumbnail byte array from an encrypted image file.
     * The thumbnail is NOT encrypted - it's stored as a plain JPEG in the database.
     * This allows fast grid loading without decrypting full images.
     *
     * @param encryptedFile The encrypted image file
     * @return Thumbnail as JPEG byte array, or null if generation fails
     */
    suspend fun generateImageThumbnail(encryptedFile: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Decrypt the image
            val encryptedData = encryptedFile.readBytes()
            val plainData = securityManager.decrypt(encryptedData)

            // Decode to bitmap with efficient sampling
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(plainData, 0, plainData.size, options)

            // Tella's adaptive sizing: 1/10 of original dimensions for photos
            val targetWidth = options.outWidth / 10
            val targetHeight = options.outHeight / 10

            // Calculate sample size for efficient memory usage
            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
            options.inJustDecodeBounds = false

            // Decode the sampled bitmap
            val bitmap = BitmapFactory.decodeByteArray(plainData, 0, plainData.size, options)
                ?: return@withContext null

            // Extract thumbnail at 1/10 size (adaptive to original image)
            val thumbnail = ThumbnailUtils.extractThumbnail(
                bitmap,
                targetWidth,
                targetHeight,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT
            )

            // Compress to JPEG byte array
            val outputStream = ByteArrayOutputStream()
            thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
            val thumbnailBytes = outputStream.toByteArray()

            // Clean up
            if (thumbnail != bitmap) {
                thumbnail.recycle()
            }

            thumbnailBytes
        } catch (e: Exception) {
            android.util.Log.e("SecureFileManager", "Failed to generate thumbnail", e)
            null
        }
    }

    /**
     * Generates a thumbnail byte array from an encrypted video file.
     * Extracts a frame from the video and creates a thumbnail.
     *
     * @param encryptedFile The encrypted video file
     * @return Thumbnail as JPEG byte array, or null if generation fails
     */
    suspend fun generateVideoThumbnail(encryptedFile: File): ByteArray? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // Decrypt video to temp file (MediaMetadataRetriever needs a file path)
            tempFile = decryptFile(encryptedFile)

            // Extract frame from video
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)

            // Get frame at 1 second (or first frame if shorter)
            val bitmap = retriever.getFrameAtTime(1_000_000) // 1 second in microseconds
                ?: retriever.frameAtTime // Fallback to first frame
                ?: return@withContext null

            retriever.release()

            // Tella's adaptive sizing: 1/4 of original dimensions for videos
            val targetWidth = bitmap.width / 4
            val targetHeight = bitmap.height / 4

            // Create thumbnail at 1/4 size
            val thumbnail = ThumbnailUtils.extractThumbnail(
                bitmap,
                targetWidth,
                targetHeight,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT
            )

            // Compress to JPEG byte array
            val outputStream = ByteArrayOutputStream()
            thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
            val thumbnailBytes = outputStream.toByteArray()

            // Clean up
            if (thumbnail != bitmap) {
                thumbnail.recycle()
            }

            thumbnailBytes
        } catch (e: Exception) {
            android.util.Log.e("SecureFileManager", "Failed to generate video thumbnail", e)
            null
        } finally {
            // Securely delete temp file
            tempFile?.let { secureDelete(it) }
        }
    }

    /**
     * Calculates sample size for efficient bitmap decoding.
     * Based on Android's official sample code.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}

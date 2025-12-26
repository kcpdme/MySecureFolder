package com.kcpd.myfolder.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}

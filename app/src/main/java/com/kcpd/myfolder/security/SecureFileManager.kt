package com.kcpd.myfolder.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles secure file operations including encryption/decryption and secure deletion.
 * Implements the "Hybrid" security model with Envelope Encryption.
 */
@Singleton
class SecureFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val ENCRYPTED_FILE_EXTENSION = ".enc"
        private const val BUFFER_SIZE = 8192
        private const val THUMBNAIL_QUALITY = 85 // JPEG compression quality
    }

    @Serializable
    data class FileMetadata(
        val filename: String,
        val mimeType: String = "application/octet-stream",
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Gets the secure storage directory for encrypted files.
     */
    fun getSecureStorageDir(): File {
        return File(context.filesDir, "secure_media").apply { mkdirs() }
    }

    /**
     * Encrypts a file and stores it securely using Envelope Encryption.
     * Returns the path to the encrypted file.
     *
     * @param sourceFile The file to encrypt
     * @param destinationDir The directory where the encrypted file will be stored
     * @param originalFileName Optional original filename to store in metadata (defaults to sourceFile.name)
     * @return The encrypted file with a random UUID-based filename
     */
    suspend fun encryptFile(
        sourceFile: File,
        destinationDir: File,
        originalFileName: String? = null
    ): File = withContext(Dispatchers.IO) {
        require(sourceFile.exists()) { "Source file does not exist: ${sourceFile.path}" }

        destinationDir.mkdirs()

        // Use random UUID-based filename for encrypted file (more secure, no metadata leakage)
        val randomFileName = java.util.UUID.randomUUID().toString()
        val encryptedFile = File(destinationDir, randomFileName + ENCRYPTED_FILE_EXTENSION)

        // 1. Generate FEK (File Encryption Key)
        val fek = generateRandomKey(32)

        // 2. Wrap FEK using Master Key
        val masterKey = securityManager.getActiveMasterKey()
        val (iv, encFek) = securityManager.wrapFEK(fek, masterKey)

        // 3. Prepare Metadata (store original filename, not the temp/rotated filename)
        val metadata = FileMetadata(
            filename = originalFileName ?: sourceFile.name,
            timestamp = System.currentTimeMillis()
        )
        val metadataJson = Json.encodeToString(metadata)
        val metadataBytes = metadataJson.toByteArray(Charsets.UTF_8)
        
        // Encrypt Metadata (using MasterKey + its own IV, reusing general encrypt method)
        // We use the securityManager.encrypt which generates its own IV and tags
        // But wait, SecurityManager.encrypt uses getFileEncryptionKey/activeMasterKey
        // We should add a method to SecurityManager to encrypt with a specific key or use public encrypt
        // Ideally we should keep header structure simple.
        // Step 2 says: "META Var bytes Encrypted Metadata JSON".
        // I'll manually encrypt it here using MasterKey to be self-contained in header logic if needed,
        // or add a helper in SecurityManager.
        // Let's use a helper in SecurityManager: encryptWithMasterKey(bytes) -> bytes
        // But SecurityManager.encrypt() already does (IV + Encrypted + Tag).
        // I need to ensure SecurityManager uses the *activeMasterKey*.
        // I will update SecurityManager to expose `encrypt(data, key)` or check `encrypt` impl.
        // The previous `encrypt` used `getFileEncryptionKey`. I removed that method.
        // I'll add `encryptData(data: ByteArray, key: SecretKey): ByteArray` to SecurityManager later?
        // Or implement it here locally. 
        // AES-GCM for Metadata seems appropriate.
        val encMetadata = encryptDataGcm(metadataBytes, masterKey)

        // 4. Create Header
        val header = FileHeader(
            version = FileHeader.VERSION_1,
            iv = iv,
            encryptedFek = encFek,
            metaLen = encMetadata.size,
            meta = encMetadata
        )

        // 5. Write Header + Encrypt Body
        FileOutputStream(encryptedFile).use { output ->
            // Write Header
            header.writeHeader(output)

            // Encrypt Body using FEK (AES-CTR for streaming)
            // Use IV from header (padded to 16 bytes)
            val bodyIv = iv.copyOf(16) // Pad with zeros
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, fek, IvParameterSpec(bodyIv))

            FileInputStream(sourceFile).use { input ->
                CipherOutputStream(output, cipher).use { cipherOut ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        cipherOut.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        // Securely delete the source file
        secureDelete(sourceFile)

        encryptedFile
    }

    /**
     * Decrypts an encrypted file to a temporary location for viewing.
     */
    suspend fun decryptFile(encryptedFile: File): File = withContext(Dispatchers.IO) {
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}_${encryptedFile.name.removeSuffix(ENCRYPTED_FILE_EXTENSION)}")

        FileInputStream(encryptedFile).use { input ->
            // 1. Read Header
            val header = FileHeader.readHeader(input)

            // 2. Unwrap FEK
            val masterKey = securityManager.getActiveMasterKey()
            val fek = securityManager.unwrapFEK(header.encryptedFek, header.iv, masterKey)

            // 3. Decrypt Body
            val bodyIv = header.iv.copyOf(16)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, fek, IvParameterSpec(bodyIv))

            // Input stream is already positioned at body start
            CipherInputStream(input, cipher).use { cipherIn ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        tempFile
    }

    /**
     * Gets a streaming decrypted InputStream.
     */
    fun getStreamingDecryptedInputStream(encryptedFile: File): java.io.InputStream {
        require(encryptedFile.exists()) { "Encrypted file does not exist: ${encryptedFile.path}" }

        val fileInputStream = FileInputStream(encryptedFile)

        try {
            // 1. Read Header
            val header = FileHeader.readHeader(fileInputStream)

            // 2. Unwrap FEK
            val masterKey = securityManager.getActiveMasterKey()
            val fek = securityManager.unwrapFEK(header.encryptedFek, header.iv, masterKey)

            // 3. Setup Decryption Stream
            val bodyIv = header.iv.copyOf(16)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, fek, IvParameterSpec(bodyIv))

            return CipherInputStream(fileInputStream, cipher)
        } catch (e: Exception) {
            try { fileInputStream.close() } catch (_: Exception) {}
            throw e
        }
    }

    /**
     * Re-wraps all encrypted files with a new Master Key.
     * This is used when the password changes.
     */
    suspend fun reWrapAllFiles(oldMasterKey: SecretKey, newMasterKey: SecretKey) = withContext(Dispatchers.IO) {
        val dir = getSecureStorageDir()
        dir.listFiles()?.forEach { file ->
            if (isEncrypted(file)) {
                try {
                    reWrapFile(file, oldMasterKey, newMasterKey)
                } catch (e: Exception) {
                    android.util.Log.e("SecureFileManager", "Failed to re-wrap file: ${file.name}", e)
                }
            }
        }
    }

    private suspend fun reWrapFile(file: File, oldKey: SecretKey, newKey: SecretKey) {
        val raf = RandomAccessFile(file, "rw")
        val channel = raf.channel
        
        try {
            // 1. Read existing header to get FEK and Body Start Position
            // We use a separate stream to not mess with channel position logic if buffering happens
            channel.position(0)
            val input = Channels.newInputStream(channel)
            val header = FileHeader.readHeader(input)
            val headerEndPos = channel.position()
            
            // 2. Unwrap FEK with OLD key
            val fek = securityManager.unwrapFEK(header.encryptedFek, header.iv, oldKey)
            
            // 3. Decrypt Metadata with OLD key
            val metadata = decryptMetadata(header.meta, oldKey) 
                ?: throw IllegalStateException("Failed to decrypt metadata for ${file.name}")
            
            // 4. Wrap FEK with NEW key
            val (newIv, newEncFek) = securityManager.wrapFEK(fek, newKey)
            
            // 5. Encrypt Metadata with NEW key
            val metadataBytes = Json.encodeToString(metadata).toByteArray(Charsets.UTF_8)
            val newEncMetadata = encryptDataGcm(metadataBytes, newKey)
            
            // 6. Create New Header
            val newHeader = FileHeader(
                version = FileHeader.VERSION_1,
                iv = newIv,
                encryptedFek = newEncFek,
                metaLen = newEncMetadata.size,
                meta = newEncMetadata
            )
            
            // 7. Check size match
            val oldHeaderSize = headerEndPos
            val buffer = ByteArrayOutputStream()
            newHeader.writeHeader(buffer)
            val newHeaderBytes = buffer.toByteArray()
            
            if (newHeaderBytes.size.toLong() == oldHeaderSize) {
                // Perfect fit, overwrite in place
                channel.position(0)
                channel.write(java.nio.ByteBuffer.wrap(newHeaderBytes))
            } else {
                // Size mismatch - must rewrite file
                raf.close()
                reWrapFileSlow(file, newHeader, headerEndPos)
                return
            }
        } finally {
            try { raf.close() } catch(_:Exception){}
        }
    }
    
    private suspend fun reWrapFileSlow(file: File, newHeader: FileHeader, oldBodyStart: Long) {
        val tempFile = File(file.parentFile, file.name + ".rewrap")
        FileOutputStream(tempFile).use { out ->
            newHeader.writeHeader(out)
            FileInputStream(file).use { input ->
                input.skip(oldBodyStart)
                input.copyTo(out)
            }
        }
        secureDelete(file)
        tempFile.renameTo(file)
    }

    /**
     * Gets a streaming encryption OutputStream for creating new encrypted files.
     * Writes header immediately and returns stream that encrypts body.
     */
    fun getStreamingEncryptionOutputStream(targetFile: File): java.io.OutputStream {
        targetFile.parentFile?.mkdirs()
        
        // 1. Generate FEK
        val fek = generateRandomKey(32)
        
        // 2. Wrap FEK
        val masterKey = securityManager.getActiveMasterKey()
        val (iv, encFek) = securityManager.wrapFEK(fek, masterKey)
        
        // 3. Metadata
        val metadata = FileMetadata(filename = targetFile.name)
        val metadataJson = Json.encodeToString(metadata)
        val encMetadata = encryptDataGcm(metadataJson.toByteArray(Charsets.UTF_8), masterKey)
        
        // 4. Header
        val header = FileHeader(FileHeader.VERSION_1, iv, encFek, encMetadata.size, encMetadata)
        
        // 5. Open stream
        val fileOut = FileOutputStream(targetFile)
        try {
            header.writeHeader(fileOut)
            
            // 6. Body Cipher Stream
            val bodyIv = iv.copyOf(16)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, fek, IvParameterSpec(bodyIv))
            
            return CipherOutputStream(fileOut, cipher)
        } catch (e: Exception) {
            fileOut.close()
            targetFile.delete()
            throw e
        }
    }

    /**
     * Encrypts data in place (replaces original file).
     */
    suspend fun encryptFileInPlace(file: File): File = withContext(Dispatchers.IO) {
        // Implementation similar to encryptFile but with temp destination then rename
        val tempEncrypted = File(file.parentFile, file.name + ".temp")
        encryptFileToTarget(file, tempEncrypted)
        secureDelete(file)
        tempEncrypted.renameTo(File(file.parentFile, file.name + ENCRYPTED_FILE_EXTENSION))
        File(file.parentFile, file.name + ENCRYPTED_FILE_EXTENSION)
    }

    private suspend fun encryptFileToTarget(source: File, target: File) {
        val fek = generateRandomKey(32)
        val masterKey = securityManager.getActiveMasterKey()
        val (iv, encFek) = securityManager.wrapFEK(fek, masterKey)

        val metadata = FileMetadata(filename = source.name)
        val encMetadata = encryptDataGcm(Json.encodeToString(metadata).toByteArray(), masterKey)

        val header = FileHeader(FileHeader.VERSION_1, iv, encFek, encMetadata.size, encMetadata)

        FileOutputStream(target).use { output ->
            header.writeHeader(output)
            val bodyIv = iv.copyOf(16)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, fek, IvParameterSpec(bodyIv))
            
            FileInputStream(source).use { input ->
                CipherOutputStream(output, cipher).use { cipherOut ->
                    input.copyTo(cipherOut)
                }
            }
        }
    }

    /**
     * Securely deletes a file.
     */
    suspend fun secureDelete(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false
        try {
            val length = file.length()
            if (length > 0) {
                // Single pass random overwrite is usually sufficient for flash storage/SSD
                // and avoids excessive wear
                overwriteFileRandom(file, length)
            }
            file.delete()
        } catch (e: Exception) {
            file.delete()
        }
    }

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
            fos.fd.sync()
        }
    }
    
    // Helper to generate random AES key
    private fun generateRandomKey(size: Int): SecretKey {
        val key = ByteArray(size)
        SecureRandom().nextBytes(key)
        return SecretKeySpec(key, "AES")
    }

    // Helper for GCM encryption (IV + Data + Tag)
    private fun encryptDataGcm(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }
    
    // Decrypts metadata
    fun decryptMetadata(encryptedMetadata: ByteArray, key: SecretKey): FileMetadata? {
        try {
            val iv = encryptedMetadata.copyOfRange(0, 12)
            val ciphertext = encryptedMetadata.copyOfRange(12, encryptedMetadata.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            return Json.decodeFromString(String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Validates if a file is a valid encrypted file for the current user (MasterKey).
     * Returns the decrypted metadata if valid, or null if invalid/not encrypted.
     */
    suspend fun validateAndGetMetadata(file: File): FileMetadata? = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { input ->
                val header = FileHeader.readHeader(input)
                val masterKey = securityManager.getActiveMasterKey()
                
                // Validate Key by trying to unwrap
                // If this fails, it throws exception
                securityManager.unwrapFEK(header.encryptedFek, header.iv, masterKey)
                
                // Decrypt Metadata
                return@use decryptMetadata(header.meta, masterKey)
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    /**
     * Checks if a file is encrypted.
     */
    fun isEncrypted(file: File): Boolean {
        return file.name.endsWith(ENCRYPTED_FILE_EXTENSION)
    }

    /**
     * Gets original filename (from filename or metadata if implemented).
     * For now, simplistic check.
     */
    fun getOriginalFileName(encryptedFile: File): String {
        return encryptedFile.name.removeSuffix(ENCRYPTED_FILE_EXTENSION)
    }
    
    // Thumbnail generation methods...
    suspend fun generateImageThumbnail(encryptedFile: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val stream = getStreamingDecryptedInputStream(encryptedFile)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            stream.close()

            val stream2 = getStreamingDecryptedInputStream(encryptedFile)
            options.inJustDecodeBounds = false
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            val bitmap = BitmapFactory.decodeStream(stream2, null, options)
            stream2.close()

            if (bitmap == null) return@withContext null

            val thumb = ThumbnailUtils.extractThumbnail(bitmap, bitmap.width/4, bitmap.height/4)
            val out = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            if (thumb != bitmap) thumb.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun generateVideoThumbnail(encryptedFile: File): ByteArray? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = decryptFile(encryptedFile)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(1_000_000) ?: return@withContext null
            retriever.release()
            
            val thumb = ThumbnailUtils.extractThumbnail(bitmap, bitmap.width/4, bitmap.height/4)
            val out = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            if (thumb != bitmap) thumb.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            tempFile?.let { secureDelete(it) }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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

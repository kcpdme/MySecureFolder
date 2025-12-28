package com.kcpd.myfolder.ui.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Progress state for share/zip operations.
 */
sealed class ShareProgress {
    data class Decrypting(val current: Int, val total: Int, val fileName: String) : ShareProgress()
    data class Ready(val intent: Intent, val tempFiles: List<File>) : ShareProgress()
    data class Error(val message: String) : ShareProgress()
}

/**
 * Progress state for zipping operation.
 */
sealed class ZipProgress {
    data class Decrypting(val current: Int, val total: Int, val fileName: String) : ZipProgress()
    data class Zipping(val current: Int, val total: Int, val fileName: String) : ZipProgress()
    data class Completed(val zipFile: File, val totalFiles: Int) : ZipProgress()
    data class Error(val message: String) : ZipProgress()
}

object FolderActions {

    /**
     * Decrypts a single file and creates a share intent.
     * Returns the intent and temp file for cleanup.
     */
    suspend fun createDecryptedShareIntent(
        context: Context,
        mediaRepository: MediaRepository,
        mediaFile: MediaFile
    ): Pair<Intent, File> = withContext(Dispatchers.IO) {
        val decryptedFile = mediaRepository.decryptForViewing(mediaFile)
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            decryptedFile
        )
        
        val mimeType = getMimeType(mediaFile.fileName)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        Pair(Intent.createChooser(shareIntent, "Share via"), decryptedFile)
    }

    /**
     * Decrypts multiple files and creates share intent with progress reporting.
     */
    fun createDecryptedMultipleShareIntent(
        context: Context,
        mediaRepository: MediaRepository,
        files: List<MediaFile>
    ): Flow<ShareProgress> = flow {
        val tempFiles = mutableListOf<File>()
        
        try {
            val total = files.size
            
            // Decrypt all files
            files.forEachIndexed { index, mediaFile ->
                emit(ShareProgress.Decrypting(index + 1, total, mediaFile.fileName))
                val decryptedFile = mediaRepository.decryptForViewing(mediaFile)
                tempFiles.add(decryptedFile)
            }
            
            // Create URIs for all decrypted files
            val uris = ArrayList<Uri>()
            var commonMimeType: String? = null
            
            tempFiles.forEachIndexed { index, file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                uris.add(uri)
                
                val mimeType = getMimeType(files[index].fileName)
                if (commonMimeType == null) {
                    commonMimeType = mimeType
                } else if (commonMimeType != mimeType) {
                    commonMimeType = "*/*"
                }
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType ?: "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            emit(ShareProgress.Ready(
                Intent.createChooser(shareIntent, "Share files"),
                tempFiles
            ))
            
        } catch (e: Exception) {
            // Clean up on error
            tempFiles.forEach { file ->
                try { if (file.exists()) file.delete() } catch (_: Exception) {}
            }
            emit(ShareProgress.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Creates a ZIP file from encrypted media files with decryption and progress reporting.
     * Returns a Flow that emits progress updates.
     */
    fun createDecryptedZip(
        context: Context,
        mediaRepository: MediaRepository,
        files: List<MediaFile>,
        zipFileName: String? = null
    ): Flow<ZipProgress> = flow {
        val tempDecryptedFiles = mutableListOf<File>()
        
        try {
            val total = files.size
            
            // Step 1: Decrypt all files to temp location
            files.forEachIndexed { index, mediaFile ->
                emit(ZipProgress.Decrypting(index + 1, total, mediaFile.fileName))
                val decryptedFile = mediaRepository.decryptForViewing(mediaFile)
                tempDecryptedFiles.add(decryptedFile)
            }
            
            // Step 2: Create ZIP file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val finalZipName = zipFileName ?: "MyFolder_Export_$timestamp"
            val cacheDir = File(context.cacheDir, "shared_zips").apply { mkdirs() }
            val zipFile = File(cacheDir, "$finalZipName.zip")
            
            // Delete existing zip if present
            if (zipFile.exists()) {
                zipFile.delete()
            }
            
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                tempDecryptedFiles.forEachIndexed { index, decryptedFile ->
                    val originalName = files[index].fileName
                    emit(ZipProgress.Zipping(index + 1, total, originalName))
                    addFileToZip(zipOut, decryptedFile, originalName)
                }
            }
            
            // Step 3: Clean up temp decrypted files
            tempDecryptedFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    android.util.Log.w("FolderActions", "Failed to delete temp file: ${file.name}")
                }
            }
            
            emit(ZipProgress.Completed(zipFile, total))
            
        } catch (e: Exception) {
            // Clean up on error
            tempDecryptedFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            emit(ZipProgress.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Creates a share intent for the completed ZIP file.
     */
    fun createZipShareIntent(context: Context, zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, "Share ZIP file")
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            fis.copyTo(zipOut, 8192)
            zipOut.closeEntry()
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                "txt" -> "text/plain"
                "md" -> "text/markdown"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
    }

    /**
     * Cleans up temporary files after a delay.
     */
    suspend fun cleanupTempFiles(files: List<File>, delayMs: Long = 60000) {
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.delay(delayMs)
            files.forEach { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                        android.util.Log.d("FolderActions", "Cleaned up temp file: ${file.name}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FolderActions", "Failed to cleanup: ${file.name}")
                }
            }
        }
    }
}

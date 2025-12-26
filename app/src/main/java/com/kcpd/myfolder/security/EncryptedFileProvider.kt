package com.kcpd.myfolder.security

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Encrypted FileProvider that streams decrypted content through pipes.
 * Based on Tella's implementation - avoids materializing decrypted files on disk.
 *
 * This provider creates on-the-fly decryption streams using ParcelFileDescriptor pipes,
 * which means:
 * - No temporary decrypted files on disk (security)
 * - No full file loading into memory (performance)
 * - Lower battery consumption (streaming vs batch decryption)
 * - Works with Android's standard file APIs
 */
class EncryptedFileProvider : ContentProvider() {

    companion object {
        private const val TAG = "EncryptedFileProvider"
        private const val BUFFER_SIZE = 8192 // 8KB buffer for streaming

        /**
         * Generates a content:// URI for an encrypted file.
         */
        fun getUriForFile(context: android.content.Context, file: File): Uri {
            val authority = "${context.packageName}.encryptedfileprovider"
            return Uri.parse("content://$authority/${file.absolutePath}")
        }
    }

    private val securityManager: SecurityManager by lazy {
        context?.let { ctx ->
            val app = ctx.applicationContext as? com.kcpd.myfolder.MyFolderApplication
            app?.securityManager ?: throw IllegalStateException("Application not initialized")
        } ?: throw IllegalStateException("Context not available")
    }

    private val secureFileManager: SecureFileManager by lazy {
        context?.let { ctx ->
            val app = ctx.applicationContext as? com.kcpd.myfolder.MyFolderApplication
            app?.secureFileManager ?: throw IllegalStateException("Application not initialized")
        } ?: throw IllegalStateException("Context not available")
    }

    override fun onCreate(): Boolean {
        // Just return true - actual initialization happens lazily when needed
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d(TAG, "openFile: uri=$uri, mode=$mode")

        val file = getFileFromUri(uri)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.absolutePath}")
        }

        return when (mode) {
            "r" -> openReadPipe(file)
            "w", "wt", "wa" -> openWritePipe(file)
            else -> throw IllegalArgumentException("Unsupported mode: $mode")
        }
    }

    /**
     * Creates a pipe for reading encrypted files.
     * The read end is returned to the caller, while the write end is fed by a background thread
     * that decrypts the file on-the-fly.
     */
    private fun openReadPipe(encryptedFile: File): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        // Start background thread to decrypt and write to pipe
        Thread(ReadRunnable(encryptedFile, writeEnd)).start()

        return readEnd
    }

    /**
     * Creates a pipe for writing encrypted files.
     * The write end is returned to the caller, while the read end is consumed by a background thread
     * that encrypts data on-the-fly.
     */
    private fun openWritePipe(targetFile: File): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        // Start background thread to read from pipe and encrypt
        Thread(WriteRunnable(targetFile, readEnd)).start()

        return writeEnd
    }

    /**
     * Background thread that reads encrypted file and writes decrypted data to pipe.
     */
    private inner class ReadRunnable(
        private val encryptedFile: File,
        private val writeEnd: ParcelFileDescriptor
    ) : Runnable {
        override fun run() {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { output ->
                    // Get streaming decryption input
                    secureFileManager.getStreamingDecryptedInputStream(encryptedFile).use { input ->
                        // Stream data in chunks
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
                Log.d(TAG, "Read pipe completed successfully for: ${encryptedFile.name}")
            } catch (e: IOException) {
                Log.e(TAG, "Error in read pipe for ${encryptedFile.name}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in read pipe", e)
            }
        }
    }

    /**
     * Background thread that reads from pipe and writes encrypted data to file.
     */
    private inner class WriteRunnable(
        private val targetFile: File,
        private val readEnd: ParcelFileDescriptor
    ) : Runnable {
        override fun run() {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { input ->
                    secureFileManager.getStreamingEncryptionOutputStream(targetFile).use { output ->
                        // Stream data in chunks
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
                Log.d(TAG, "Write pipe completed successfully for: ${targetFile.name}")
            } catch (e: IOException) {
                Log.e(TAG, "Error in write pipe for ${targetFile.name}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in write pipe", e)
            }
        }
    }

    /**
     * Extracts the file path from the content URI.
     */
    private fun getFileFromUri(uri: Uri): File {
        // URI format: content://authority/absolute/file/path
        val path = uri.path?.substring(1) // Remove leading '/'
            ?: throw IllegalArgumentException("Invalid URI: $uri")
        return File(path)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = getFileFromUri(uri)
        if (!file.exists()) {
            return null
        }

        val columns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )

        val cursor = MatrixCursor(columns)
        val row = arrayOfNulls<Any>(columns.size)

        columns.forEachIndexed { index, column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> {
                    row[index] = secureFileManager.getOriginalFileName(file)
                }
                OpenableColumns.SIZE -> {
                    // Return encrypted file size (we can't know decrypted size without decrypting)
                    row[index] = file.length()
                }
            }
        }

        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val file = getFileFromUri(uri)
        val fileName = secureFileManager.getOriginalFileName(file)

        // Determine MIME type from extension
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Delete not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update not supported")
    }
}

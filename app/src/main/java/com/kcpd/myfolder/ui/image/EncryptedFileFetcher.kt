package com.kcpd.myfolder.ui.image

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.security.SecureFileManager
import okio.buffer
import okio.source
import java.io.File

/**
 * Custom Coil Fetcher that decrypts encrypted media files before loading them.
 * Uses InputStream for efficient memory usage instead of creating temporary decrypted files.
 */
class EncryptedFileFetcher(
    private val data: MediaFile,
    private val options: Options,
    private val secureFileManager: SecureFileManager
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        android.util.Log.d("EncryptedFileFetcher", "═══════════════════════════════════════")
        android.util.Log.d("EncryptedFileFetcher", "Fetching: ${data.fileName}")
        android.util.Log.d("EncryptedFileFetcher", "  Encrypted path: ${data.filePath}")
        android.util.Log.d("EncryptedFileFetcher", "  MIME type from DB: ${data.mimeType ?: "NULL"}")
        android.util.Log.d("EncryptedFileFetcher", "  Media type: ${data.mediaType}")

        // Get the encrypted file
        val encryptedFile = File(data.filePath)

        if (!encryptedFile.exists()) {
            android.util.Log.e("EncryptedFileFetcher", "ERROR: Encrypted file does NOT exist: ${data.filePath}")
            throw IllegalStateException("Encrypted file does not exist: ${data.filePath}")
        }

        android.util.Log.d("EncryptedFileFetcher", "  Encrypted file size: ${encryptedFile.length()} bytes")

        // Get STREAMING decrypted InputStream from SecureFileManager
        // This uses pipe-based streaming for better performance and lower memory usage
        val decryptedStream = secureFileManager.getStreamingDecryptedInputStream(encryptedFile)

        android.util.Log.d("EncryptedFileFetcher", "  Streaming decryption started")
        android.util.Log.d("EncryptedFileFetcher", "═══════════════════════════════════════")

        // Convert InputStream to BufferedSource for Coil
        val bufferedSource = decryptedStream.source().buffer()

        // Return the decrypted stream as an ImageSource
        return SourceResult(
            source = ImageSource(
                source = bufferedSource,
                context = options.context
            ),
            mimeType = data.mimeType,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val secureFileManager: SecureFileManager) : Fetcher.Factory<MediaFile> {
        override fun create(
            data: MediaFile,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return EncryptedFileFetcher(data, options, secureFileManager)
        }
    }
}

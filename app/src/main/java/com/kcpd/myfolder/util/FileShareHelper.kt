package com.kcpd.myfolder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.security.EncryptedFileProvider
import java.io.File

/**
 * Helper for sharing encrypted files using streaming decryption.
 * Uses EncryptedFileProvider to provide on-the-fly decryption via content:// URIs.
 */
object FileShareHelper {

    /**
     * Creates a share intent for an encrypted media file.
     * The file will be decrypted on-the-fly as the receiving app reads it.
     *
     * @param context Android context
     * @param mediaFile The encrypted media file to share
     * @param chooserTitle Title for the app chooser dialog
     * @return Share Intent ready to be launched
     */
    fun createShareIntent(
        context: Context,
        mediaFile: MediaFile,
        chooserTitle: String = "Share via"
    ): Intent {
        val encryptedFile = File(mediaFile.filePath)
        val uri = EncryptedFileProvider.getUriForFile(context, encryptedFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mediaFile.mimeType ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, chooserTitle)
    }

    /**
     * Creates a share intent for multiple encrypted media files.
     * All files will be decrypted on-the-fly as the receiving app reads them.
     *
     * @param context Android context
     * @param mediaFiles List of encrypted media files to share
     * @param chooserTitle Title for the app chooser dialog
     * @return Share Intent ready to be launched
     */
    fun createMultipleShareIntent(
        context: Context,
        mediaFiles: List<MediaFile>,
        chooserTitle: String = "Share via"
    ): Intent {
        val uris = ArrayList<Uri>()
        var commonMimeType: String? = null

        mediaFiles.forEach { mediaFile ->
            val encryptedFile = File(mediaFile.filePath)
            val uri = EncryptedFileProvider.getUriForFile(context, encryptedFile)
            uris.add(uri)

            // Determine common MIME type
            if (commonMimeType == null) {
                commonMimeType = mediaFile.mimeType
            } else if (commonMimeType != mediaFile.mimeType) {
                commonMimeType = "*/*" // Mixed types
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimeType ?: "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, chooserTitle)
    }

    /**
     * Creates a view intent for an encrypted media file.
     * The file will be decrypted on-the-fly as the viewing app reads it.
     *
     * @param context Android context
     * @param mediaFile The encrypted media file to view
     * @param chooserTitle Title for the app chooser dialog
     * @return View Intent ready to be launched
     */
    fun createViewIntent(
        context: Context,
        mediaFile: MediaFile,
        chooserTitle: String = "Open with"
    ): Intent {
        val encryptedFile = File(mediaFile.filePath)
        val uri = EncryptedFileProvider.getUriForFile(context, encryptedFile)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mediaFile.mimeType ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(viewIntent, chooserTitle)
    }

    /**
     * Creates an edit intent for an encrypted image file.
     * The file will be decrypted on-the-fly for the editing app.
     *
     * @param context Android context
     * @param mediaFile The encrypted image file to edit
     * @param chooserTitle Title for the app chooser dialog
     * @return Edit Intent ready to be launched
     */
    fun createEditIntent(
        context: Context,
        mediaFile: MediaFile,
        chooserTitle: String = "Edit with"
    ): Intent {
        val encryptedFile = File(mediaFile.filePath)
        val uri = EncryptedFileProvider.getUriForFile(context, encryptedFile)

        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mediaFile.mimeType ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        return Intent.createChooser(editIntent, chooserTitle)
    }
}

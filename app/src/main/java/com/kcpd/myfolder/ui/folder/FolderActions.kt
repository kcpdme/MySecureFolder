package com.kcpd.myfolder.ui.folder

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.UserFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FolderActions {

    fun shareAsZip(
        context: Context,
        items: List<Any>, // Mix of MediaFile and UserFolder
        zipFileName: String
    ) {
        try {
            val cacheDir = File(context.cacheDir, "shared_zips").apply { mkdirs() }
            val zipFile = File(cacheDir, "$zipFileName.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                items.forEach { item ->
                    when (item) {
                        is MediaFile -> {
                            addFileToZip(zipOut, File(item.filePath), item.fileName)
                        }
                        is UserFolder -> {
                            // For folders, we would need to recursively add all files
                            // This is a simplified version
                        }
                    }
                }
            }

            // Share the ZIP file
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

            context.startActivity(Intent.createChooser(shareIntent, "Share ZIP file"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            fis.copyTo(zipOut, 1024)
            zipOut.closeEntry()
        }
    }

    fun shareMultipleFiles(context: Context, files: List<MediaFile>) {
        try {
            val uris = files.map { mediaFile ->
                val file = File(mediaFile.filePath)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share files"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

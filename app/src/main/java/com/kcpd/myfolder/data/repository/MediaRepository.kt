package com.kcpd.myfolder.data.repository

import android.content.Context
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val mediaDir: File
        get() = File(context.filesDir, "media").apply { mkdirs() }

    init {
        migrateLegacyFiles()
        loadMediaFiles()
    }

    private fun migrateLegacyFiles() {
        // Migrate old files from media/ to categorized subdirectories
        val oldFiles = mediaDir.listFiles()?.filter { it.isFile } ?: return

        oldFiles.forEach { file ->
            val mediaType = getMediaTypeFromExtension(file.extension.lowercase())
            if (mediaType != null) {
                val category = FolderCategory.fromMediaType(mediaType)
                val categoryDir = getCategoryDirectory(category)
                val newFile = File(categoryDir, file.name)
                if (!newFile.exists()) {
                    file.renameTo(newFile)
                }
            }
        }
    }

    private fun loadMediaFiles() {
        val files = mutableListOf<MediaFile>()

        // Load from all category subdirectories
        FolderCategory.entries.forEach { category ->
            val categoryDir = getCategoryDirectory(category)
            categoryDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val mediaType = getMediaTypeFromExtension(file.extension.lowercase())
                    if (mediaType == category.mediaType) {
                        files.add(
                            MediaFile(
                                id = file.nameWithoutExtension,
                                fileName = file.name,
                                filePath = file.absolutePath,
                                mediaType = mediaType,
                                size = file.length(),
                                createdAt = Date(file.lastModified())
                            )
                        )
                    }
                }
            }
        }

        _mediaFiles.value = files.sortedByDescending { it.createdAt }
    }

    private fun getMediaTypeFromExtension(extension: String): MediaType? {
        return when (extension) {
            "jpg", "jpeg", "png" -> MediaType.PHOTO
            "mp4", "mov" -> MediaType.VIDEO
            "mp3", "m4a", "aac" -> MediaType.AUDIO
            "txt" -> MediaType.NOTE
            else -> null
        }
    }

    fun getCategoryDirectory(category: FolderCategory): File {
        return File(mediaDir, category.path).apply { mkdirs() }
    }

    fun getFilesForCategory(category: FolderCategory): StateFlow<List<MediaFile>> {
        return mediaFiles.map { files ->
            files.filter { it.mediaType == category.mediaType }
        }.stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun addMediaFile(file: File, mediaType: MediaType): MediaFile {
        val mediaFile = MediaFile(
            id = UUID.randomUUID().toString(),
            fileName = file.name,
            filePath = file.absolutePath,
            mediaType = mediaType,
            size = file.length(),
            createdAt = Date()
        )
        _mediaFiles.value = listOf(mediaFile) + _mediaFiles.value
        return mediaFile
    }

    fun saveNote(fileName: String, content: String): MediaFile {
        val category = FolderCategory.NOTES
        val categoryDir = getCategoryDirectory(category)
        val file = File(categoryDir, fileName)
        file.writeText(content)

        val mediaFile = MediaFile(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            filePath = file.absolutePath,
            mediaType = MediaType.NOTE,
            size = file.length(),
            createdAt = Date(),
            textContent = content
        )
        _mediaFiles.value = listOf(mediaFile) + _mediaFiles.value
        return mediaFile
    }

    fun loadNoteContent(mediaFile: MediaFile): String {
        return if (mediaFile.mediaType == MediaType.NOTE) {
            File(mediaFile.filePath).readText()
        } else {
            ""
        }
    }

    fun deleteMediaFile(mediaFile: MediaFile): Boolean {
        val file = File(mediaFile.filePath)
        val deleted = file.delete()
        if (deleted) {
            _mediaFiles.value = _mediaFiles.value.filter { it.id != mediaFile.id }
        }
        return deleted
    }

    fun updateMediaFile(mediaFile: MediaFile) {
        _mediaFiles.value = _mediaFiles.value.map {
            if (it.id == mediaFile.id) mediaFile else it
        }
    }

    fun getMediaDirectory(): File = mediaDir

    fun getFileCountForCategory(category: FolderCategory): StateFlow<Int> {
        return mediaFiles.map { files ->
            files.count { it.mediaType == category.mediaType }
        }.stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    }
}

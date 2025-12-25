package com.kcpd.myfolder.data.repository

import android.content.Context
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        loadMediaFiles()
    }

    private fun loadMediaFiles() {
        val files = mutableListOf<MediaFile>()
        mediaDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val mediaType = when (file.extension.lowercase()) {
                    "jpg", "jpeg", "png" -> MediaType.PHOTO
                    "mp4", "mov" -> MediaType.VIDEO
                    "mp3", "m4a", "aac" -> MediaType.AUDIO
                    else -> return@forEach
                }
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
        _mediaFiles.value = files.sortedByDescending { it.createdAt }
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
}

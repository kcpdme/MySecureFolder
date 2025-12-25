package com.kcpd.myfolder.data.model

import java.util.Date

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO
}

data class MediaFile(
    val id: String,
    val fileName: String,
    val filePath: String,
    val mediaType: MediaType,
    val thumbnailPath: String? = null,
    val duration: Long? = null,
    val size: Long,
    val createdAt: Date,
    val isUploaded: Boolean = false,
    val s3Url: String? = null
)

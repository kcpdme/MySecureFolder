package com.kcpd.myfolder.data.model

import java.util.Date

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO,
    NOTE
}

data class MediaFile(
    val id: String,
    val fileName: String,
    val filePath: String,
    val mediaType: MediaType,
    val thumbnailPath: String? = null, // DEPRECATED: Use thumbnail instead
    val thumbnail: ByteArray? = null, // Thumbnail byte array for fast grid loading
    val duration: Long? = null,
    val size: Long,
    val createdAt: Date,
    val isUploaded: Boolean = false,
    val s3Url: String? = null,
    val textContent: String? = null,
    val folderId: String? = null, // ID of the folder this file belongs to
    val mimeType: String? = null // MIME type for proper file handling
) {
    // Override equals/hashCode to exclude ByteArray for proper comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaFile

        if (id != other.id) return false
        if (fileName != other.fileName) return false
        if (filePath != other.filePath) return false
        if (mediaType != other.mediaType) return false
        if (thumbnailPath != other.thumbnailPath) return false
        if (thumbnail != null) {
            if (other.thumbnail == null) return false
            if (!thumbnail.contentEquals(other.thumbnail)) return false
        } else if (other.thumbnail != null) return false
        if (duration != other.duration) return false
        if (size != other.size) return false
        if (createdAt != other.createdAt) return false
        if (isUploaded != other.isUploaded) return false
        if (s3Url != other.s3Url) return false
        if (textContent != other.textContent) return false
        if (folderId != other.folderId) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + (thumbnailPath?.hashCode() ?: 0)
        result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
        result = 31 * result + (duration?.hashCode() ?: 0)
        result = 31 * result + size.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + isUploaded.hashCode()
        result = 31 * result + (s3Url?.hashCode() ?: 0)
        result = 31 * result + (textContent?.hashCode() ?: 0)
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}

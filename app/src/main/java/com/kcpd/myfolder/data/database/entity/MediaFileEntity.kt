package com.kcpd.myfolder.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Room entity for encrypted media file metadata.
 * The actual file content is encrypted on disk, this stores only metadata.
 */
@Entity(
    tableName = "media_files",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["mediaType"]),
        Index(value = ["createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MediaFileEntity(
    @PrimaryKey
    val id: String,

    /** Original user-visible filename (encrypted in database) */
    val originalFileName: String,

    /** Actual encrypted filename on disk */
    val encryptedFileName: String,

    /** Absolute path to encrypted file */
    val encryptedFilePath: String,

    /** Media type: PHOTO, VIDEO, AUDIO, NOTE */
    val mediaType: String,

    /** Path to encrypted thumbnail (if applicable) - DEPRECATED, use thumbnail instead */
    val encryptedThumbnailPath: String?,

    /** Thumbnail as byte array (for photos/videos) - stored directly in DB for fast grid loading */
    val thumbnail: ByteArray?,

    /** Duration in milliseconds (for audio/video) */
    val duration: Long?,

    /** File size in bytes (of encrypted file) */
    val size: Long,

    /** Creation timestamp in milliseconds */
    val createdAt: Long,

    /** Associated folder ID (nullable for root files) */
    val folderId: String?,

    /** SHA-256 hash of original file for integrity verification */
    val verificationHash: String?,

    /** Original file size before encryption */
    val originalSize: Long,

    /** MIME type of the original file */
    val mimeType: String?
)

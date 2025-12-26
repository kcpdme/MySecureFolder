package com.kcpd.myfolder.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Room entity for encrypted folder metadata.
 * Folder names and metadata are encrypted in the database.
 */
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["parentFolderId"]),
        Index(value = ["categoryPath"]),
        Index(value = ["createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentFolderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,

    /** Folder name (stored encrypted) */
    val name: String,

    /** Folder color as hex string */
    val colorHex: String,

    /** Parent folder ID for nested folders */
    val parentFolderId: String?,

    /** Category path: "photos", "videos", "recordings", "notes" */
    val categoryPath: String,

    /** Creation timestamp in milliseconds */
    val createdAt: Long
)

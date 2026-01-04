package com.kcpd.myfolder.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a pending upload task in the queue.
 * 
 * This entity is the source of truth for upload state - survives:
 * - App going to background
 * - App being killed
 * - Device reboot
 * 
 * WorkManager reads this table to retry failed/pending uploads.
 */
@Entity(
    tableName = "upload_queue",
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["status"]),
        Index(value = ["remoteId"])
    ]
)
data class UploadQueueEntity(
    @PrimaryKey
    val id: String,  // Composite: "${fileId}_${remoteId}"
    
    // File information
    val fileId: String,
    val fileName: String,
    val filePath: String,  // Path to encrypted file
    val fileSize: Long,
    val mediaType: String,  // PHOTO, VIDEO, AUDIO, OTHER - needed for folder path
    val folderId: String? = null,  // User's folder ID if file is in a subfolder
    
    // Remote information
    val remoteId: String,
    val remoteName: String,
    val remoteType: String,  // "google_drive", "s3", "webdav"
    
    // Upload state
    val status: String,  // PENDING, IN_PROGRESS, SUCCESS, FAILED
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val uploadedUrl: String? = null,
    
    // Retry tracking
    val attemptCount: Int = 0,
    val maxAttempts: Int = 5,
    val lastAttemptAt: Long? = null,
    val nextRetryAt: Long? = null,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
        
        fun createId(fileId: String, remoteId: String): String = "${fileId}_${remoteId}"
    }
    
    val isPending: Boolean get() = status == STATUS_PENDING
    val isInProgress: Boolean get() = status == STATUS_IN_PROGRESS
    val isSuccess: Boolean get() = status == STATUS_SUCCESS
    val isFailed: Boolean get() = status == STATUS_FAILED
    val isComplete: Boolean get() = isSuccess || isFailed
    val canRetry: Boolean get() = isFailed && attemptCount < maxAttempts
}

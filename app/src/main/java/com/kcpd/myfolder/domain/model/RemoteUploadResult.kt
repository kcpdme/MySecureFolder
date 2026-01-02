package com.kcpd.myfolder.domain.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Result of uploading a file to a specific remote.
 * Tracks the status, progress, and any errors for a single remote upload operation.
 */
@Serializable
data class RemoteUploadResult(
    val remoteId: String,
    val remoteName: String,
    @Serializable(with = ColorSerializer::class)
    val remoteColor: Color,
    val status: UploadStatus,
    val progress: Float = 0f,           // 0.0 to 1.0
    val errorMessage: String? = null,
    val uploadedUrl: String? = null,
    val startedAt: Long? = null,        // Timestamp when upload started
    val completedAt: Long? = null       // Timestamp when upload completed/failed
) {
    /**
     * Check if this upload is in a terminal state (success or failed)
     */
    val isComplete: Boolean
        get() = status == UploadStatus.SUCCESS || status == UploadStatus.FAILED

    /**
     * Check if this upload succeeded
     */
    val isSuccess: Boolean
        get() = status == UploadStatus.SUCCESS

    /**
     * Check if this upload is currently active
     */
    val isActive: Boolean
        get() = status == UploadStatus.IN_PROGRESS

    /**
     * Get duration of upload in milliseconds (if completed)
     */
    val durationMs: Long?
        get() = if (startedAt != null && completedAt != null) {
            completedAt - startedAt
        } else null
}

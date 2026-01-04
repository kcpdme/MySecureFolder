package com.kcpd.myfolder.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the complete upload state for a single file across all configured remotes.
 * Tracks individual progress and status for each remote this file is being uploaded to.
 */
@Serializable
data class FileUploadState(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val remoteResults: Map<String, RemoteUploadResult> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()  // Timestamp when this upload was added
) {
    /**
     * Get list of all remote results
     */
    val allResults: List<RemoteUploadResult>
        get() = remoteResults.values.toList()

    /**
     * Check if all remotes have completed (success or failed)
     */
    val isComplete: Boolean
        get() = remoteResults.isNotEmpty() && remoteResults.values.all { it.isComplete }

    /**
     * Check if at least one remote upload succeeded
     */
    val hasAnySuccess: Boolean
        get() = remoteResults.values.any { it.isSuccess }

    /**
     * Check if all remotes succeeded
     */
    val allSucceeded: Boolean
        get() = remoteResults.isNotEmpty() && remoteResults.values.all { it.isSuccess }

    /**
     * Check if all remotes failed
     */
    val allFailed: Boolean
        get() = remoteResults.isNotEmpty() && remoteResults.values.all { it.status == UploadStatus.FAILED }

    /**
     * Get count of successful uploads
     */
    val successCount: Int
        get() = remoteResults.values.count { it.isSuccess }

    /**
     * Get count of failed uploads
     */
    val failedCount: Int
        get() = remoteResults.values.count { it.status == UploadStatus.FAILED }

    /**
     * Get count of in-progress uploads
     */
    val activeCount: Int
        get() = remoteResults.values.count { it.isActive }

    /**
     * Get count of queued uploads
     */
    val queuedCount: Int
        get() = remoteResults.values.count { it.status == UploadStatus.QUEUED }

    /**
     * Get overall progress (average across all remotes)
     */
    val overallProgress: Float
        get() = if (remoteResults.isEmpty()) 0f
        else remoteResults.values.map { it.progress }.average().toFloat()

    /**
     * Get human-readable status summary
     */
    val statusSummary: String
        get() = when {
            allSucceeded -> "Uploaded to all remotes"
            allFailed -> "Failed on all remotes"
            isComplete && hasAnySuccess -> "Uploaded to $successCount/${remoteResults.size} remotes"
            activeCount > 0 -> "Uploading to $activeCount remotes"
            queuedCount > 0 -> "Queued for ${remoteResults.size} remotes"
            else -> "Pending"
        }
}

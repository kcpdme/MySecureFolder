package com.kcpd.myfolder.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the current status of an upload to a specific remote
 */
@Serializable
enum class UploadStatus {
    /**
     * Upload is waiting in queue to start
     */
    QUEUED,

    /**
     * Upload is currently in progress
     */
    IN_PROGRESS,

    /**
     * Upload completed successfully
     */
    SUCCESS,

    /**
     * Upload failed with an error
     */
    FAILED
}

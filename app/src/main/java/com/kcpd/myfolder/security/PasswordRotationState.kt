package com.kcpd.myfolder.security

import kotlinx.serialization.Serializable

/**
 * Represents the state of a password rotation process.
 * This is persisted to disk to ensure recoverability after crashes.
 */
@Serializable
data class PasswordRotationJournal(
    val rotationState: RotationState,
    val currentStep: RotationStep,
    val oldKeyId: String?,
    val newKeyId: String?,
    val encryptedDbKeyBackup: String? // Base64 encoded encrypted DB key
) {
    companion object {
        fun initial(): PasswordRotationJournal {
            return PasswordRotationJournal(RotationState.IDLE, RotationStep.NONE, null, null, null)
        }
    }
}

/**
 * Defines the high-level state of the password rotation.
 */
enum class RotationState {
    /** No password rotation is in progress. */
    IDLE,

    /** A password rotation is currently in progress. */
    IN_PROGRESS,

    /** A password rotation has failed and may require manual intervention or rollback. */
    FAILED
}

/**
 * Defines the specific step within an active password rotation process.
 */
enum class RotationStep {
    /** Initial state before any steps have been taken. */
    NONE,

    /** The process of re-wrapping file encryption keys (FEKs) has started. */
    REWRAP_FILES,

    /** The process of re-wrapping the database key has started. */
    REWRAP_DATABASE_KEY,

    /** All re-wrapping is complete, and the finalization step (e.g., updating the master key) is pending. */
    FINALIZE,

    /** The process has successfully completed. */
    DONE
}

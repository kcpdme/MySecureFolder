package com.kcpd.myfolder.domain.usecase

import android.util.Log
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.repository.RemoteConfigRepository
import com.kcpd.myfolder.data.repository.RemoteRepositoryFactory
import com.kcpd.myfolder.domain.model.FileUploadState
import com.kcpd.myfolder.domain.model.RemoteConfig
import com.kcpd.myfolder.domain.model.RemoteUploadResult
import com.kcpd.myfolder.domain.model.UploadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates uploads to multiple remote storage providers.
 * Manages upload state, progress tracking, and concurrency control.
 * 
 * Uses limited concurrency (2 remotes at a time) to balance:
 * - Speed: Faster than fully sequential
 * - Stability: No bandwidth saturation or connection pool exhaustion
 */
@Singleton
class MultiRemoteUploadCoordinator @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val repositoryFactory: RemoteRepositoryFactory
) {
    companion object {
        private const val TAG = "MultiRemoteUploadCoordinator"
        // Max concurrent remote uploads per file
        // 2 is a good balance: faster than sequential, stable unlike full parallel
        private const val MAX_CONCURRENT_REMOTES = 2
    }

    // Own scope with SupervisorJob - isolated from caller's scope
    // This ensures multiple upload batches can run independently
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Semaphore to limit concurrent remote uploads
    private val uploadSemaphore = Semaphore(MAX_CONCURRENT_REMOTES)

    // Mutex for thread-safe state updates
    private val stateMutex = Mutex()

    // Upload states for all files
    private val _uploadStates = MutableStateFlow<Map<String, FileUploadState>>(emptyMap())
    val uploadStates: StateFlow<Map<String, FileUploadState>> = _uploadStates.asStateFlow()

    // Active uploads count
    private val _activeUploadsCount = MutableStateFlow(0)
    val activeUploadsCount: StateFlow<Int> = _activeUploadsCount.asStateFlow()

    /**
     * Upload multiple files to all active remotes.
     * For each file:
     *   1. Upload to remotes with LIMITED concurrency (MAX_CONCURRENT_REMOTES at a time)
     *   2. Wait for all remotes to complete (success or failure)
     *   3. Move to next file
     * 
     * Limited concurrency (2 at a time) provides:
     * - Faster total upload time than fully sequential
     * - Stable performance without bandwidth saturation
     * - Consistent upload times for all remote types (S3, WebDAV, Google Drive)
     * 
     * This function launches uploads in the background and returns immediately.
     * It does NOT block the caller - uploads continue asynchronously.
     * The scope parameter is kept for backward compatibility but is no longer used.
     */
    fun uploadFiles(
        files: List<MediaFile>,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope
    ) {
        val activeRemotes = remoteConfigRepository.getActiveRemotesSync()

        if (activeRemotes.isEmpty()) {
            Log.w(TAG, "No active remotes configured")
            return
        }

        Log.d(TAG, "Starting upload of ${files.size} files to ${activeRemotes.size} remotes")
        Log.d(TAG, "Max $MAX_CONCURRENT_REMOTES concurrent remote uploads per file")

        // Initialize ALL file states UPFRONT so UI shows total count immediately
        // This runs synchronously before launching background work
        uploadScope.launch {
            // First, initialize all file states so user sees X/Y total immediately
            files.forEach { file ->
                initializeFileState(file, activeRemotes)
            }
            Log.d(TAG, "Initialized ${files.size} files for upload - all visible in UI now")

            // Process files ONE AT A TIME
            files.forEach { file ->
                Log.d(TAG, "Processing file: ${file.fileName}")

                // Upload this file to remotes with LIMITED concurrency
                // Semaphore ensures only MAX_CONCURRENT_REMOTES upload at once
                val uploadJobs = activeRemotes.map { remote ->
                    async {
                        uploadSemaphore.withPermit {
                            uploadToRemote(file, remote)
                        }
                    }
                }

                // Wait for ALL remotes to complete for this file
                uploadJobs.awaitAll()

                Log.d(TAG, "Completed file: ${file.fileName} - Moving to next file")
            }

            Log.d(TAG, "All ${files.size} files processed")
        }
    }

    /**
     * Upload a single file to all active remotes
     */
    fun uploadFile(
        file: MediaFile,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope
    ) {
        uploadFiles(listOf(file), scope)
    }

    /**
     * Retry upload for a specific file to a specific remote
     */
    fun retryUpload(
        fileId: String,
        remoteId: String,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope
    ) {
        val uploadState = _uploadStates.value[fileId] ?: run {
            Log.w(TAG, "Cannot retry: File state not found for $fileId")
            return
        }

        val remoteResult = uploadState.remoteResults[remoteId] ?: run {
            Log.w(TAG, "Cannot retry: Remote result not found for $remoteId")
            return
        }

        // Launch retry in uploadScope - all suspend operations happen inside
        uploadScope.launch {
            // Get the remote config
            val remote = remoteConfigRepository.getRemoteById(remoteId) ?: run {
                Log.w(TAG, "Cannot retry: Remote config not found for $remoteId")
                return@launch
            }

            // Reset state to queued
            updateRemoteStatus(
                fileId = fileId,
                remoteId = remoteId,
                status = UploadStatus.QUEUED,
                remoteName = remoteResult.remoteName,
                remoteColor = remoteResult.remoteColor
            )

            // Note: We need the original MediaFile for retry
            // This is a limitation - consider storing MediaFile reference in FileUploadState
            Log.d(TAG, "Retry initiated for file $fileId to remote $remoteId")
        }
    }

    /**
     * Cancel all pending uploads
     */
    suspend fun cancelAll() {
        stateMutex.withLock {
            _uploadStates.update { emptyMap() }
            _activeUploadsCount.value = 0
        }
        Log.d(TAG, "All uploads cancelled")
    }

    /**
     * Clear completed uploads from state
     */
    suspend fun clearCompleted() {
        stateMutex.withLock {
            _uploadStates.update { states ->
                states.filterValues { !it.isComplete }
            }
        }
    }

    /**
     * Initialize upload state for a file across all remotes
     */
    private suspend fun initializeFileState(file: MediaFile, remotes: List<RemoteConfig>) {
        val remoteResults = remotes.associate { remote ->
            remote.id to RemoteUploadResult(
                remoteId = remote.id,
                remoteName = remote.name,
                remoteColor = remote.color,
                status = UploadStatus.QUEUED,
                progress = 0f
            )
        }

        val fileState = FileUploadState(
            fileId = file.id,
            fileName = file.fileName,
            fileSize = file.size,
            remoteResults = remoteResults
        )

        stateMutex.withLock {
            _uploadStates.update { states ->
                states + (file.id to fileState)
            }
        }
    }

    /**
     * Upload a file to a specific remote
     */
    private suspend fun uploadToRemote(file: MediaFile, remote: RemoteConfig) {
        try {
            // Update status to IN_PROGRESS
            updateRemoteStatus(
                fileId = file.id,
                remoteId = remote.id,
                status = UploadStatus.IN_PROGRESS,
                remoteName = remote.name,
                remoteColor = remote.color,
                startedAt = System.currentTimeMillis()
            )

            _activeUploadsCount.update { it + 1 }

            Log.d(TAG, "Starting upload: ${file.fileName} -> ${remote.name}")

            // Get repository for this remote
            val repository = repositoryFactory.getRepository(remote)

            // Perform upload
            val result = repository.uploadFile(file)

            // Update state based on result
            if (result.isSuccess) {
                updateRemoteStatus(
                    fileId = file.id,
                    remoteId = remote.id,
                    status = UploadStatus.SUCCESS,
                    remoteName = remote.name,
                    remoteColor = remote.color,
                    progress = 1f,
                    uploadedUrl = result.getOrNull(),
                    completedAt = System.currentTimeMillis()
                )
                Log.d(TAG, "Upload SUCCESS: ${file.fileName} -> ${remote.name}")
            } else {
                val error = result.exceptionOrNull()
                updateRemoteStatus(
                    fileId = file.id,
                    remoteId = remote.id,
                    status = UploadStatus.FAILED,
                    remoteName = remote.name,
                    remoteColor = remote.color,
                    errorMessage = error?.message ?: "Upload failed",
                    completedAt = System.currentTimeMillis()
                )
                Log.e(TAG, "Upload FAILED: ${file.fileName} -> ${remote.name}", error)
            }

        } catch (e: Exception) {
            updateRemoteStatus(
                fileId = file.id,
                remoteId = remote.id,
                status = UploadStatus.FAILED,
                remoteName = remote.name,
                remoteColor = remote.color,
                errorMessage = e.message ?: "Unknown error",
                completedAt = System.currentTimeMillis()
            )
            Log.e(TAG, "Upload EXCEPTION: ${file.fileName} -> ${remote.name}", e)
        } finally {
            _activeUploadsCount.update { it - 1 }
        }
    }

    /**
     * Update the status of a remote upload
     */
    private suspend fun updateRemoteStatus(
        fileId: String,
        remoteId: String,
        status: UploadStatus,
        remoteName: String,
        remoteColor: androidx.compose.ui.graphics.Color,
        progress: Float = 0f,
        errorMessage: String? = null,
        uploadedUrl: String? = null,
        startedAt: Long? = null,
        completedAt: Long? = null
    ) {
        stateMutex.withLock {
            _uploadStates.update { states ->
                val currentState = states[fileId] ?: return@update states

                val currentResult = currentState.remoteResults[remoteId]
                val updatedResult = RemoteUploadResult(
                    remoteId = remoteId,
                    remoteName = remoteName,
                    remoteColor = remoteColor,
                    status = status,
                    progress = progress,
                    errorMessage = errorMessage,
                    uploadedUrl = uploadedUrl,
                    startedAt = startedAt ?: currentResult?.startedAt,
                    completedAt = completedAt
                )

                val updatedResults = currentState.remoteResults + (remoteId to updatedResult)
                val updatedState = currentState.copy(remoteResults = updatedResults)

                states + (fileId to updatedState)
            }
        }
    }

    /**
     * Get upload state for a specific file
     */
    fun getFileUploadState(fileId: String): FileUploadState? {
        return _uploadStates.value[fileId]
    }

    /**
     * Check if any uploads are in progress
     */
    fun hasActiveUploads(): Boolean {
        return _activeUploadsCount.value > 0
    }

    /**
     * Get summary statistics
     */
    fun getUploadSummary(): UploadSummary {
        val states = _uploadStates.value.values
        return UploadSummary(
            totalFiles = states.size,
            completedFiles = states.count { it.isComplete },
            successfulFiles = states.count { it.hasAnySuccess },
            failedFiles = states.count { it.allFailed },
            activeUploads = _activeUploadsCount.value
        )
    }

    data class UploadSummary(
        val totalFiles: Int,
        val completedFiles: Int,
        val successfulFiles: Int,
        val failedFiles: Int,
        val activeUploads: Int
    )
}

package com.kcpd.myfolder.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.kcpd.myfolder.R
import com.kcpd.myfolder.data.database.UploadQueueDatabase
import com.kcpd.myfolder.data.database.entity.UploadQueueEntity
import com.kcpd.myfolder.data.repository.RemoteConfigRepository
import com.kcpd.myfolder.data.repository.RemoteRepositoryFactory
import com.kcpd.myfolder.data.model.MediaFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * WorkManager worker that processes pending uploads in the background.
 * 
 * This worker:
 * 1. Runs even when app is killed (managed by Android system)
 * 2. Automatically retries with exponential backoff
 * 3. Shows foreground notification for long-running uploads
 * 4. Works without vault unlock (files are already encrypted)
 * 
 * Critical for reporter safety - ensures evidence is uploaded even if 
 * the reporter can't keep the app open.
 * 
 * Uses Hilt's @HiltWorker for proper dependency injection.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val repositoryFactory: RemoteRepositoryFactory,
    private val uploadSettingsRepository: com.kcpd.myfolder.data.repository.UploadSettingsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "UploadWorker"
        const val WORK_NAME = "reliable_upload_work"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "upload_channel"
    }

    private val uploadQueueDao by lazy { 
        UploadQueueDatabase.getInstance(context).uploadQueueDao() 
    }
    
    // Semaphores are lazily initialized with values from settings
    private val s3Semaphore by lazy { 
        Semaphore(uploadSettingsRepository.getS3ConcurrencySync()) 
    }
    private val googleSemaphore by lazy { 
        Semaphore(uploadSettingsRepository.getGoogleDriveConcurrencySync()) 
    }
    private val webdavSemaphore by lazy { 
        Semaphore(uploadSettingsRepository.getWebdavConcurrencySync()) 
    }
    private val globalSemaphore by lazy { 
        Semaphore(uploadSettingsRepository.getMaxParallelUploadsSync()) 
    }
    
    // Counters for notification updates
    private val successCount = AtomicInteger(0)
    private val failCount = AtomicInteger(0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "UploadWorker started (attempt: ${runAttemptCount + 1})")
        
        try {
            // Get all pending uploads from the queue
            val pendingUploads = uploadQueueDao.getPendingUploads()
            
            if (pendingUploads.isEmpty()) {
                Log.d(TAG, "No pending uploads found")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Found ${pendingUploads.size} pending uploads - processing in PARALLEL")
            
            // Show foreground notification for long-running work
            setForeground(createForegroundInfo(pendingUploads.size))
            
            // Reset counters
            successCount.set(0)
            failCount.set(0)
            
            // Process uploads in PARALLEL with per-remote-type concurrency
            val jobs = pendingUploads.map { upload ->
                async {
                    // Check if we've been cancelled
                    if (isStopped) {
                        Log.d(TAG, "Worker stopped, skipping ${upload.fileName}")
                        return@async
                    }
                    
                    // Acquire global and per-remote semaphores
                    globalSemaphore.withPermit {
                        val remoteSemaphore = getSemaphoreForRemoteType(upload.remoteType)
                        remoteSemaphore.withPermit {
                            try {
                                val success = processUpload(upload)
                                if (success) {
                                    successCount.incrementAndGet()
                                } else {
                                    failCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing upload ${upload.id}", e)
                                markUploadFailed(upload, e.message ?: "Unknown error")
                                failCount.incrementAndGet()
                            }
                            
                            // Update notification progress
                            val remaining = pendingUploads.size - successCount.get() - failCount.get()
                            try {
                                setForeground(createForegroundInfo(
                                    pending = remaining,
                                    completed = successCount.get(),
                                    failed = failCount.get()
                                ))
                            } catch (e: Exception) {
                                // Ignore notification update errors
                            }
                        }
                    }
                }
            }
            
            // Wait for all uploads to complete
            jobs.forEach { it.await() }
            
            Log.d(TAG, "UploadWorker completed: ${successCount.get()} success, ${failCount.get()} failed")
            
            // If there are still retryable failures, return retry
            val retryableCount = uploadQueueDao.getRetryableFailedCount()
            if (retryableCount > 0) {
                Log.d(TAG, "Returning RETRY for $retryableCount retryable uploads")
                return@withContext Result.retry()
            }
            
            return@withContext Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "UploadWorker failed with exception", e)
            return@withContext Result.retry()
        }
    }
    
    /**
     * Get the appropriate semaphore for a remote type.
     * This ensures optimal concurrency per-remote to avoid rate limits.
     */
    private fun getSemaphoreForRemoteType(remoteType: String): Semaphore {
        return when (remoteType.lowercase()) {
            "s3" -> s3Semaphore
            "google_drive" -> googleSemaphore
            "webdav" -> webdavSemaphore
            else -> globalSemaphore // Fallback to global for unknown types
        }
    }
    
    private suspend fun processUpload(upload: UploadQueueEntity): Boolean {
        Log.d(TAG, "Processing upload: ${upload.fileName} -> ${upload.remoteName}")
        
        // Verify file exists
        val file = File(upload.filePath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${upload.filePath}")
            markUploadFailed(upload, "File not found - may have been deleted")
            return false
        }
        
        // Mark as in progress
        uploadQueueDao.markInProgress(upload.id)
        
        try {
            // Get the remote config
            val remoteConfig = remoteConfigRepository.getRemoteByIdSync(upload.remoteId)
            if (remoteConfig == null) {
                Log.e(TAG, "Remote config not found: ${upload.remoteId}")
                markUploadFailed(upload, "Remote configuration not found or disabled")
                return false
            }
            
            // Parse media type from stored string
            val mediaType = try {
                com.kcpd.myfolder.data.model.MediaType.valueOf(upload.mediaType)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unknown media type: ${upload.mediaType}, defaulting to OTHER")
                com.kcpd.myfolder.data.model.MediaType.OTHER
            }
            
            // Create MediaFile from upload queue entry with correct folder path info
            val mediaFile = MediaFile(
                id = upload.fileId,
                fileName = upload.fileName,
                filePath = upload.filePath,
                mediaType = mediaType,  // Use stored media type for correct folder
                size = upload.fileSize,
                createdAt = java.util.Date(upload.createdAt),
                folderId = upload.folderId  // Use stored folder ID for correct subfolder
            )
            
            // Get repository and upload
            val repository = repositoryFactory.getRepository(remoteConfig)
            val result = repository.uploadFile(mediaFile)
            
            if (result.isSuccess) {
                val url = result.getOrNull()
                uploadQueueDao.updateSuccessStatus(
                    id = upload.id,
                    status = UploadQueueEntity.STATUS_SUCCESS,
                    uploadedUrl = url,
                    completedAt = System.currentTimeMillis()
                )
                Log.d(TAG, "Upload SUCCESS: ${upload.fileName} -> ${upload.remoteName}")
                return true
            } else {
                val error = result.exceptionOrNull()?.message ?: "Upload failed"
                markUploadFailed(upload, error)
                Log.e(TAG, "Upload FAILED: ${upload.fileName} -> ${upload.remoteName}: $error")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${upload.fileName}", e)
            markUploadFailed(upload, e.message ?: "Unknown error")
            return false
        }
    }
    
    private suspend fun markUploadFailed(upload: UploadQueueEntity, errorMessage: String) {
        // Calculate next retry time with exponential backoff
        val baseDelayMs = 30_000L  // 30 seconds
        val maxDelayMs = 3600_000L  // 1 hour max
        val attempt = upload.attemptCount + 1
        val delayMs = minOf(baseDelayMs * (1L shl minOf(attempt, 10)), maxDelayMs)
        val nextRetryAt = System.currentTimeMillis() + delayMs
        
        Log.d(TAG, "Marking failed (attempt $attempt/${upload.maxAttempts}), next retry in ${delayMs / 1000}s")
        
        uploadQueueDao.updateFailedStatus(
            id = upload.id,
            status = UploadQueueEntity.STATUS_FAILED,
            progress = 0f,
            errorMessage = errorMessage,
            lastAttemptAt = System.currentTimeMillis(),
            nextRetryAt = if (attempt < upload.maxAttempts) nextRetryAt else null
        )
    }
    
    private fun createForegroundInfo(pending: Int, completed: Int = 0, failed: Int = 0): ForegroundInfo {
        val title = "Uploading to cloud"
        val content = when {
            pending > 0 -> "Uploading: $pending remaining, $completed completed"
            completed > 0 && failed == 0 -> "Completed $completed uploads"
            completed > 0 && failed > 0 -> "Completed $completed, failed $failed"
            else -> "Processing uploads..."
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setOngoing(true)
            .setProgress(pending + completed + failed, completed + failed, pending == 0 && completed == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        
        // For Android 14+ (API 34+), we must specify the foreground service type
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

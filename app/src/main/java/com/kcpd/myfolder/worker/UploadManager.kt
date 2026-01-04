package com.kcpd.myfolder.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kcpd.myfolder.data.database.UploadQueueDatabase
import com.kcpd.myfolder.data.database.dao.UploadQueueDao
import com.kcpd.myfolder.data.database.entity.UploadQueueEntity
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.domain.model.RemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for reliable background uploads using WorkManager.
 * 
 * This class:
 * 1. Queues uploads to the database (source of truth)
 * 2. Triggers WorkManager to process the queue
 * 3. Ensures uploads survive app death, background, and reboot
 * 4. Provides progress observation via Flow
 * 
 * Usage:
 *   uploadManager.queueUpload(file, remote)  // Returns immediately
 *   uploadManager.queuedUploadsFlow.collect { ... }  // Observe progress
 */
@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UploadManager"
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val uploadQueueDao = UploadQueueDatabase.getInstance(context).uploadQueueDao()
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Queue a file for upload to a specific remote.
     * The upload will be processed by WorkManager even if app is killed.
     */
    suspend fun queueUpload(file: MediaFile, remote: RemoteConfig) {
        val queueId = UploadQueueEntity.createId(file.id, remote.id)
        
        val entity = UploadQueueEntity(
            id = queueId,
            fileId = file.id,
            fileName = file.fileName,
            filePath = file.filePath,
            fileSize = file.size,
            mediaType = file.mediaType.name,  // Store media type for correct folder path
            folderId = file.folderId,  // Store folder ID for correct subfolder path
            remoteId = remote.id,
            remoteName = remote.name,
            remoteType = when (remote) {
                is RemoteConfig.S3Remote -> "s3"
                is RemoteConfig.GoogleDriveRemote -> "google_drive"
                is RemoteConfig.WebDavRemote -> "webdav"
            },
            status = UploadQueueEntity.STATUS_PENDING
        )
        
        uploadQueueDao.insert(entity)
        Log.d(TAG, "Queued upload: ${file.fileName} -> ${remote.name}")
        
        // Trigger WorkManager
        scheduleUploadWork()
    }
    
    /**
     * Queue a file for upload to multiple remotes.
     */
    suspend fun queueUploads(file: MediaFile, remotes: List<RemoteConfig>) {
        remotes.forEach { remote ->
            queueUpload(file, remote)
        }
    }
    
    /**
     * Queue multiple files for upload to multiple remotes.
     */
    suspend fun queueBatchUploads(files: List<MediaFile>, remotes: List<RemoteConfig>) {
        val entities = files.flatMap { file ->
            remotes.map { remote ->
                UploadQueueEntity(
                    id = UploadQueueEntity.createId(file.id, remote.id),
                    fileId = file.id,
                    fileName = file.fileName,
                    filePath = file.filePath,
                    fileSize = file.size,
                    mediaType = file.mediaType.name,  // Store for correct folder path
                    folderId = file.folderId,  // Store for correct subfolder path
                    remoteId = remote.id,
                    remoteName = remote.name,
                    remoteType = when (remote) {
                        is RemoteConfig.S3Remote -> "s3"
                        is RemoteConfig.GoogleDriveRemote -> "google_drive"
                        is RemoteConfig.WebDavRemote -> "webdav"
                    },
                    status = UploadQueueEntity.STATUS_PENDING
                )
            }
        }
        
        uploadQueueDao.insertAll(entities)
        Log.d(TAG, "Queued ${entities.size} uploads (${files.size} files x ${remotes.size} remotes)")
        
        // Trigger WorkManager
        scheduleUploadWork()
    }
    
    /**
     * Schedule the upload work.
     * Uses KEEP policy - if work is already running, don't interrupt it.
     */
    private fun scheduleUploadWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag("upload")
            .build()
        
        workManager.enqueueUniqueWork(
            UploadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,  // Don't replace running work
            uploadRequest
        )
        
        Log.d(TAG, "Upload work scheduled")
    }
    
    /**
     * Retry all failed uploads.
     */
    suspend fun retryAllFailed() {
        val failedUploads = uploadQueueDao.getByStatus(UploadQueueEntity.STATUS_FAILED)
        
        if (failedUploads.isEmpty()) {
            Log.d(TAG, "No failed uploads to retry")
            return
        }
        
        // Reset failed uploads to pending
        failedUploads.forEach { upload ->
            uploadQueueDao.updateStatus(upload.id, UploadQueueEntity.STATUS_PENDING, 0f)
        }
        
        Log.d(TAG, "Reset ${failedUploads.size} failed uploads to pending")
        
        // Trigger WorkManager with REPLACE to force immediate execution
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .addTag("upload")
            .addTag("retry")
            .build()
        
        workManager.enqueueUniqueWork(
            UploadWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,  // Replace to force retry
            uploadRequest
        )
    }
    
    /**
     * Cancel all pending uploads.
     */
    suspend fun cancelAllPending() {
        workManager.cancelUniqueWork(UploadWorker.WORK_NAME)
        uploadQueueDao.deleteAll()
        Log.d(TAG, "Cancelled all pending uploads")
    }
    
    /**
     * Clear completed uploads from the queue.
     */
    suspend fun clearCompleted() {
        uploadQueueDao.deleteCompleted()
        Log.d(TAG, "Cleared completed uploads")
    }
    
    /**
     * Observe all queued uploads.
     */
    fun getQueuedUploadsFlow(): Flow<List<UploadQueueEntity>> {
        return uploadQueueDao.getAllAsFlow()
    }
    
    /**
     * Observe uploads for a specific file.
     */
    fun getFileUploadsFlow(fileId: String): Flow<List<UploadQueueEntity>> {
        return uploadQueueDao.getByFileIdAsFlow(fileId)
    }
    
    /**
     * Observe pending upload count.
     */
    fun getPendingCountFlow(): Flow<Int> {
        return uploadQueueDao.getPendingCountAsFlow()
    }
    
    /**
     * Observe WorkManager work state.
     */
    fun getWorkStateFlow(): Flow<Boolean> {
        return workManager.getWorkInfosForUniqueWorkFlow(UploadWorker.WORK_NAME)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING }
            }
    }
    
    /**
     * Get current queue statistics.
     */
    suspend fun getQueueStats(): UploadQueueDao.UploadStats {
        return uploadQueueDao.getStats()
    }
    
    /**
     * Create notification channel for upload progress.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UploadWorker.CHANNEL_ID,
                "Upload Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of cloud uploads"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

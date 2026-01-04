package com.kcpd.myfolder.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kcpd.myfolder.data.database.entity.UploadQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for upload queue operations.
 * Used by both in-app uploads and WorkManager background uploads.
 */
@Dao
interface UploadQueueDao {
    
    // ==================== INSERT ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: UploadQueueEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(uploads: List<UploadQueueEntity>)
    
    // ==================== UPDATE ====================
    
    @Update
    suspend fun update(upload: UploadQueueEntity)
    
    @Query("UPDATE upload_queue SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, progress: Float)
    
    @Query("""
        UPDATE upload_queue 
        SET status = :status, 
            progress = :progress, 
            errorMessage = :errorMessage,
            attemptCount = attemptCount + 1,
            lastAttemptAt = :lastAttemptAt,
            nextRetryAt = :nextRetryAt
        WHERE id = :id
    """)
    suspend fun updateFailedStatus(
        id: String, 
        status: String, 
        progress: Float, 
        errorMessage: String?,
        lastAttemptAt: Long,
        nextRetryAt: Long?
    )
    
    @Query("""
        UPDATE upload_queue 
        SET status = :status, 
            progress = 1.0, 
            uploadedUrl = :uploadedUrl,
            completedAt = :completedAt
        WHERE id = :id
    """)
    suspend fun updateSuccessStatus(
        id: String, 
        status: String, 
        uploadedUrl: String?,
        completedAt: Long
    )
    
    @Query("UPDATE upload_queue SET status = 'IN_PROGRESS' WHERE id = :id")
    suspend fun markInProgress(id: String)
    
    // ==================== QUERY ====================
    
    @Query("SELECT * FROM upload_queue WHERE id = :id")
    suspend fun getById(id: String): UploadQueueEntity?
    
    @Query("SELECT * FROM upload_queue WHERE fileId = :fileId")
    suspend fun getByFileId(fileId: String): List<UploadQueueEntity>
    
    @Query("SELECT * FROM upload_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<UploadQueueEntity>
    
    @Query("""
        SELECT * FROM upload_queue 
        WHERE status IN ('PENDING', 'FAILED') 
        AND (nextRetryAt IS NULL OR nextRetryAt <= :currentTime)
        AND attemptCount < maxAttempts
        ORDER BY createdAt ASC
    """)
    suspend fun getPendingUploads(currentTime: Long = System.currentTimeMillis()): List<UploadQueueEntity>
    
    @Query("SELECT * FROM upload_queue WHERE status = 'IN_PROGRESS'")
    suspend fun getInProgressUploads(): List<UploadQueueEntity>
    
    @Query("SELECT * FROM upload_queue ORDER BY createdAt DESC")
    fun getAllAsFlow(): Flow<List<UploadQueueEntity>>
    
    @Query("SELECT * FROM upload_queue WHERE fileId = :fileId")
    fun getByFileIdAsFlow(fileId: String): Flow<List<UploadQueueEntity>>
    
    @Query("SELECT COUNT(*) FROM upload_queue WHERE status IN ('PENDING', 'IN_PROGRESS')")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT COUNT(*) FROM upload_queue WHERE status IN ('PENDING', 'IN_PROGRESS')")
    fun getPendingCountAsFlow(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM upload_queue WHERE status = 'FAILED' AND attemptCount < maxAttempts")
    suspend fun getRetryableFailedCount(): Int
    
    // ==================== DELETE ====================
    
    @Query("DELETE FROM upload_queue WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM upload_queue WHERE fileId = :fileId")
    suspend fun deleteByFileId(fileId: String)
    
    @Query("DELETE FROM upload_queue WHERE status = 'SUCCESS'")
    suspend fun deleteCompleted()
    
    @Query("DELETE FROM upload_queue WHERE status = 'FAILED' AND attemptCount >= maxAttempts")
    suspend fun deleteExhaustedFailures()
    
    @Query("DELETE FROM upload_queue")
    suspend fun deleteAll()
    
    // ==================== AGGREGATE ====================
    
    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) as inProgress,
            SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success,
            SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed
        FROM upload_queue
    """)
    suspend fun getStats(): UploadStats
    
    data class UploadStats(
        val total: Int,
        val pending: Int,
        val inProgress: Int,
        val success: Int,
        val failed: Int
    )
}

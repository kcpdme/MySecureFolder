package com.kcpd.myfolder.data.database.dao

import androidx.room.*
import com.kcpd.myfolder.data.database.entity.MediaFileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for media file operations.
 */
@Dao
interface MediaFileDao {

    @Query("SELECT * FROM media_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files ORDER BY createdAt DESC")
    suspend fun getAllFilesOnce(): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE mediaType = :mediaType ORDER BY createdAt DESC")
    fun getFilesByType(mediaType: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getFilesByFolder(folderId: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE folderId IS NULL AND mediaType = :mediaType ORDER BY createdAt DESC")
    fun getRootFilesByType(mediaType: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE id = :id")
    suspend fun getFileById(id: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE isUploaded = 0 ORDER BY createdAt DESC")
    fun getUnuploadedFiles(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE originalFileName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchFiles(query: String): Flow<List<MediaFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: MediaFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<MediaFileEntity>)

    @Update
    suspend fun updateFile(file: MediaFileEntity)

    @Delete
    suspend fun deleteFile(file: MediaFileEntity)

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM media_files WHERE folderId = :folderId")
    suspend fun deleteFilesByFolder(folderId: String)

    @Query("UPDATE media_files SET isUploaded = 1, s3Url = :s3Url WHERE id = :id")
    suspend fun markAsUploaded(id: String, s3Url: String)

    @Query("UPDATE media_files SET isUploaded = 0, s3Url = NULL WHERE id = :id")
    suspend fun markAsNotUploaded(id: String)

    @Query("UPDATE media_files SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?)

    @Query("SELECT COUNT(*) FROM media_files")
    suspend fun getFileCount(): Int

    @Query("SELECT COUNT(*) FROM media_files WHERE mediaType = :mediaType")
    suspend fun getFileCountByType(mediaType: String): Int

    @Query("SELECT SUM(originalSize) FROM media_files")
    suspend fun getTotalStorageSize(): Long?
}

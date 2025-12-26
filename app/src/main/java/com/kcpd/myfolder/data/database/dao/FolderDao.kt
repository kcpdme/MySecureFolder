package com.kcpd.myfolder.data.database.dao

import androidx.room.*
import com.kcpd.myfolder.data.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for folder operations.
 */
@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE categoryPath = :categoryPath ORDER BY createdAt DESC")
    fun getFoldersByCategory(categoryPath: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL AND categoryPath = :categoryPath ORDER BY createdAt DESC")
    fun getRootFoldersByCategory(categoryPath: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY createdAt DESC")
    fun getSubfolders(parentId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchFolders(query: String): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("DELETE FROM folders WHERE parentFolderId = :parentId")
    suspend fun deleteSubfolders(parentId: String)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int

    @Query("SELECT COUNT(*) FROM folders WHERE categoryPath = :categoryPath")
    suspend fun getFolderCountByCategory(categoryPath: String): Int
}

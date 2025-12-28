package com.kcpd.myfolder.data.repository

import android.content.Context
import com.kcpd.myfolder.data.database.dao.FolderDao
import com.kcpd.myfolder.data.database.entity.FolderEntity
import com.kcpd.myfolder.data.model.FolderColor
import com.kcpd.myfolder.data.model.UserFolder
import com.kcpd.myfolder.security.SecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for legacy JSON deserialization.
 */
@Serializable
data class UserFolderData(
    val id: String,
    val name: String,
    val colorName: String,
    val parentFolderId: String? = null,
    val categoryPath: String,
    val createdAtMillis: Long
)

/**
 * FolderRepository that uses encrypted Room database.
 * Handles migration from legacy JSON-based storage.
 */
@Singleton
class FolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderDao: FolderDao,
    private val securityManager: SecurityManager
) {
    private val _folders = MutableStateFlow<List<UserFolder>>(emptyList())
    val folders: StateFlow<List<UserFolder>> = _folders.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val legacyFoldersFile = File(context.filesDir, "user_folders.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        scope.launch {
            try {
                // Migrate legacy folders if needed
                migrateLegacyFolders()

                // Load folders from database
                folderDao.getAllFolders().collect { entities ->
                    _folders.value = entities.map { it.toUserFolder() }
                }
            } catch (e: Exception) {
                android.util.Log.e("FolderRepository", "Failed to initialize repository", e)
            }
        }
    }

    /**
     * Migrates folders from legacy JSON file to encrypted database.
     */
    private suspend fun migrateLegacyFolders() {
        if (!legacyFoldersFile.exists()) return

        try {
            val jsonString = legacyFoldersFile.readText()
            val folderDataList = json.decodeFromString<List<UserFolderData>>(jsonString)

            // Insert into encrypted database
            val entities = folderDataList.map { data ->
                FolderEntity(
                    id = data.id,
                    name = data.name, // Will be encrypted by Room/SQLCipher
                    colorHex = FolderColor.valueOf(data.colorName).colorHex,
                    parentFolderId = data.parentFolderId,
                    categoryPath = data.categoryPath,
                    createdAt = data.createdAtMillis
                )
            }

            folderDao.insertFolders(entities)

            // Delete legacy file after successful migration
            legacyFoldersFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createFolder(
        name: String,
        color: FolderColor,
        categoryPath: String,
        parentFolderId: String? = null
    ): UserFolder {
        val folder = UserFolder(
            name = name,
            color = color,
            categoryPath = categoryPath,
            parentFolderId = parentFolderId
        )

        val entity = folder.toEntity()
        folderDao.insertFolder(entity)

        return folder
    }

    suspend fun updateFolder(folder: UserFolder) {
        folderDao.updateFolder(folder.toEntity())
    }

    suspend fun deleteFolder(folderId: String) {
        // Room will cascade delete subfolders due to foreign key constraint
        folderDao.deleteFolderById(folderId)
    }

    fun getFoldersForCategory(categoryPath: String, parentFolderId: String? = null): List<UserFolder> {
        return _folders.value.filter {
            it.categoryPath == categoryPath && it.parentFolderId == parentFolderId
        }
    }

    fun getFolderById(folderId: String): UserFolder? {
        return _folders.value.find { it.id == folderId }
    }

    /**
     * Converts FolderEntity to UserFolder.
     */
    private fun FolderEntity.toUserFolder(): UserFolder {
        return UserFolder(
            id = id,
            name = name,
            color = FolderColor.entries.find { it.colorHex == colorHex } ?: FolderColor.BLUE,
            parentFolderId = parentFolderId,
            categoryPath = categoryPath,
            createdAt = Date(createdAt)
        )
    }

    /**
     * Converts UserFolder to FolderEntity.
     */
    private fun UserFolder.toEntity(): FolderEntity {
        return FolderEntity(
            id = id,
            name = name,
            colorHex = color.colorHex,
            parentFolderId = parentFolderId,
            categoryPath = categoryPath,
            createdAt = createdAt.time
        )
    }
}

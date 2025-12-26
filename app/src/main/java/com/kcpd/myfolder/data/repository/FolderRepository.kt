package com.kcpd.myfolder.data.repository

import android.content.Context
import com.kcpd.myfolder.data.model.FolderColor
import com.kcpd.myfolder.data.model.UserFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UserFolderData(
    val id: String,
    val name: String,
    val colorName: String,
    val parentFolderId: String? = null,
    val categoryPath: String,
    val createdAtMillis: Long
)

@Singleton
class FolderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _folders = MutableStateFlow<List<UserFolder>>(emptyList())
    val folders: StateFlow<List<UserFolder>> = _folders.asStateFlow()

    private val foldersFile: File
        get() = File(context.filesDir, "user_folders.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        loadFolders()
    }

    private fun loadFolders() {
        try {
            if (foldersFile.exists()) {
                val jsonString = foldersFile.readText()
                val folderDataList = json.decodeFromString<List<UserFolderData>>(jsonString)
                _folders.value = folderDataList.map { data ->
                    UserFolder(
                        id = data.id,
                        name = data.name,
                        color = FolderColor.valueOf(data.colorName),
                        parentFolderId = data.parentFolderId,
                        categoryPath = data.categoryPath,
                        createdAt = Date(data.createdAtMillis)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _folders.value = emptyList()
        }
    }

    private fun saveFolders() {
        try {
            val folderDataList = _folders.value.map { folder ->
                UserFolderData(
                    id = folder.id,
                    name = folder.name,
                    colorName = folder.color.name,
                    parentFolderId = folder.parentFolderId,
                    categoryPath = folder.categoryPath,
                    createdAtMillis = folder.createdAt.time
                )
            }
            val jsonString = json.encodeToString(folderDataList)
            foldersFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createFolder(name: String, color: FolderColor, categoryPath: String, parentFolderId: String? = null): UserFolder {
        val folder = UserFolder(
            name = name,
            color = color,
            categoryPath = categoryPath,
            parentFolderId = parentFolderId
        )
        _folders.value = _folders.value + folder
        saveFolders()
        return folder
    }

    fun updateFolder(folder: UserFolder) {
        _folders.value = _folders.value.map {
            if (it.id == folder.id) folder else it
        }
        saveFolders()
    }

    fun deleteFolder(folderId: String) {
        // Delete folder and all its subfolders
        val foldersToDelete = mutableSetOf(folderId)
        var foundNew = true

        while (foundNew) {
            foundNew = false
            _folders.value.forEach { folder ->
                if (folder.parentFolderId in foldersToDelete && folder.id !in foldersToDelete) {
                    foldersToDelete.add(folder.id)
                    foundNew = true
                }
            }
        }

        _folders.value = _folders.value.filter { it.id !in foldersToDelete }
        saveFolders()
    }

    fun getFoldersForCategory(categoryPath: String, parentFolderId: String? = null): List<UserFolder> {
        return _folders.value.filter {
            it.categoryPath == categoryPath && it.parentFolderId == parentFolderId
        }
    }

    fun getFolderById(folderId: String): UserFolder? {
        return _folders.value.find { it.id == folderId }
    }
}

package com.kcpd.myfolder.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.security.SecureFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureFileManager: SecureFileManager
) : RemoteStorageRepository {

    private var driveService: Drive? = null
    private var signedInAccount: GoogleSignInAccount? = null
    private val folderIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        // Check if already signed in
        signedInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (signedInAccount != null) {
            initializeDriveService(signedInAccount!!)
        }
    }

    fun setSignedInAccount(account: GoogleSignInAccount?) {
        signedInAccount = account
        if (account != null) {
            initializeDriveService(account)
        } else {
            driveService = null
        }
    }

    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("MyFolder")
        .build()
        
        Log.d("GoogleDriveRepository", "Drive service initialized for user: ${account.email}")
    }

    override suspend fun uploadFile(mediaFile: MediaFile): Result<String> = withContext(Dispatchers.IO) {
        if (driveService == null) {
            return@withContext Result.failure(Exception("Not signed in to Google Drive"))
        }

        var tempDecryptedFile: File? = null
        try {
            // Decrypt file
            val encryptedFile = File(mediaFile.filePath)
            tempDecryptedFile = secureFileManager.decryptFile(encryptedFile)
            
            // Create folder structure: MyFolderPrivate/Category
            val rootFolderId = getOrCreateFolder("MyFolderPrivate")
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            val parentFolderId = getOrCreateFolder(category.displayName, rootFolderId)
            
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = mediaFile.fileName
            fileMetadata.parents = listOf(parentFolderId)
            
            val mediaContent = FileContent(getContentType(mediaFile.fileName), tempDecryptedFile)
            
            val uploadedFile = driveService!!.files().create(fileMetadata, mediaContent)
                .setFields("id, webContentLink, webViewLink")
                .execute()
                
            Log.d("GoogleDriveRepository", "File uploaded: ${uploadedFile.id}")
            
            // Return the ID or a link. The S3 implementation returns a URL.
            // For Drive, we might return "drive://<id>" or the webViewLink.
            // Let's return the ID for now, prefixed.
            Result.success("drive://${uploadedFile.id}")

        } catch (e: Exception) {
            Log.e("GoogleDriveRepository", "Upload failed", e)
            Result.failure(e)
        } finally {
            tempDecryptedFile?.delete()
        }
    }

    override suspend fun verifyFileExists(mediaFile: MediaFile): Result<String?> = withContext(Dispatchers.IO) {
        if (driveService == null) {
            return@withContext Result.failure(Exception("Not signed in to Google Drive"))
        }

        // Verify session is valid (double check)
        try {
            // Simple call to check if credential is valid
             driveService!!.about().get().setFields("user").execute()
        } catch (e: Exception) {
            Log.e("GoogleDriveRepository", "Session invalid", e)
            return@withContext Result.failure(Exception("Session expired or invalid. Please sign in again."))
        }
        
        // 1. If s3Url contains the Drive ID, check by ID first
        if (mediaFile.s3Url?.startsWith("drive://") == true) {
            val driveId = mediaFile.s3Url.removePrefix("drive://")
            try {
                val file = driveService!!.files().get(driveId)
                    .setFields("id, trashed, parents")
                    .execute()
                    
                if (file.trashed != true) {
                    val category = FolderCategory.fromMediaType(mediaFile.mediaType)
                    val rootId = getFolderId("MyFolderPrivate")
                    val expectedParentId = getFolderId(category.displayName, rootId)

                    if (file.parents != null && file.parents.contains(expectedParentId)) {
                         // Verified by ID
                         return@withContext Result.success(mediaFile.s3Url)
                    }
                }
            } catch (e: Exception) {
                // Not found by ID, fall through to name search
                Log.w("GoogleDriveRepository", "File check by ID failed: $driveId. Trying name search.", e)
            }
        }

        // 2. Fallback: Search by Name in the expected folder
        // This handles cases where local DB has S3 URL (or lost ID) but file is on Drive
        try {
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            val rootId = getFolderId("MyFolderPrivate")
            val parentId = getFolderId(category.displayName, rootId)
            
            // Search for file with name in parentId
            // Note: Google Drive names are not unique, but we assume the first one is correct for restoration
            val query = "name = '${mediaFile.fileName}' and '$parentId' in parents and trashed = false"
            val list = driveService!!.files().list()
                .setQ(query)
                .setFields("files(id)")
                .execute()
            
            if (list.files.isNotEmpty()) {
                val foundId = list.files[0].id
                val foundUrl = "drive://$foundId"
                Log.d("GoogleDriveRepository", "File recovered by name: ${mediaFile.fileName} -> $foundId")
                return@withContext Result.success(foundUrl)
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveRepository", "Name search failed for ${mediaFile.fileName}", e)
            return@withContext Result.failure(e)
        }

        Result.success(null)
    }

    override suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, String?> = withContext(Dispatchers.IO) {
        // Naive implementation: check one by one.
        val results = mutableMapOf<String, String?>()
        mediaFiles.forEachIndexed { index, file ->
            // Add small delay to avoid rate limiting
            if (index > 0 && index % 5 == 0) {
                kotlinx.coroutines.delay(200)
            }
            
            val result = verifyFileExists(file)
            result.onSuccess { url ->
                results[file.id] = url
            }.onFailure { e ->
                // On error (e.g. network), do NOT add to results map.
                // This prevents marking files as missing when they might just be inaccessible.
                Log.w("GoogleDriveRepository", "Failed to verify file ${file.fileName}", e)
            }
        }
        results
    }

    private fun getFolderId(name: String, parentId: String? = null): String {
        val key = "${parentId ?: "root"}/$name"
        folderIdCache[key]?.let { return it }
        
        val id = getOrCreateFolder(name, parentId)
        folderIdCache[key] = id
        return id
    }

    private fun getOrCreateFolder(folderName: String, parentId: String? = null): String {
        // Search for folder
        val queryBuilder = StringBuilder("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false")
        if (parentId != null) {
            queryBuilder.append(" and '$parentId' in parents")
        }
        
        val fileList = driveService!!.files().list()
            .setQ(queryBuilder.toString())
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            
        if (fileList.files.isNotEmpty()) {
            return fileList.files[0].id
        }
        
        // Create folder
        val folderMetadata = com.google.api.services.drive.model.File()
        folderMetadata.name = folderName
        folderMetadata.mimeType = "application/vnd.google-apps.folder"
        if (parentId != null) {
            folderMetadata.parents = listOf(parentId)
        }
        
        val folder = driveService!!.files().create(folderMetadata)
            .setFields("id")
            .execute()
            
        return folder.id
    }

    private fun getContentType(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

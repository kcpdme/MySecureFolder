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
    private val secureFileManager: SecureFileManager,
    private val folderRepository: FolderRepository
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

        try {
            // Use encrypted file directly
            val fileToUpload = File(mediaFile.filePath)
            if (!fileToUpload.exists()) {
                throw java.io.FileNotFoundException("Encrypted file not found: ${mediaFile.filePath}")
            }

            // Create folder structure: MyFolderPrivate/Category/[FolderPath]
            val rootFolderId = getOrCreateFolder("MyFolderPrivate")
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            var parentFolderId = getOrCreateFolder(category.displayName, rootFolderId)

            // Build user folder path if file is in a folder
            val userFolderPath = buildFolderPathList(mediaFile.folderId)
            if (userFolderPath.isNotEmpty()) {
                // Create nested folders in Google Drive
                for (folderName in userFolderPath) {
                    parentFolderId = getOrCreateFolder(folderName, parentFolderId)
                }
                Log.d("GoogleDriveRepository", "Created folder hierarchy: ${userFolderPath.joinToString("/")}")
            }

            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = mediaFile.fileName
            fileMetadata.parents = listOf(parentFolderId)
            
            val mediaContent = FileContent("application/octet-stream", fileToUpload)
            
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
        }
    }


    /**
     * Build the folder path hierarchy as a list of folder names.
     * Returns list like ["FolderA", "FolderB", "FolderC"] for nested folders.
     */
    private fun buildFolderPathList(folderId: String?): List<String> {
        if (folderId == null) return emptyList()

        val folderNames = mutableListOf<String>()
        var currentFolderId: String? = folderId

        // Traverse up the folder hierarchy
        while (currentFolderId != null) {
            val folder = folderRepository.getFolderById(currentFolderId)
            if (folder != null) {
                folderNames.add(0, folder.name) // Add to beginning to maintain order
                currentFolderId = folder.parentFolderId
            } else {
                break
            }
        }

        return folderNames
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

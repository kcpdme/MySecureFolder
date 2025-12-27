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
            
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            val parentFolderId = getOrCreateFolder(category.displayName)
            
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

    override suspend fun verifyFileExists(mediaFile: MediaFile): Result<Boolean> = withContext(Dispatchers.IO) {
        if (driveService == null) {
            return@withContext Result.failure(Exception("Not signed in to Google Drive"))
        }
        
        // If s3Url contains the Drive ID, we can check by ID. 
        // But mediaFile.s3Url stores the URL. If it starts with "drive://", we extract ID.
        val driveId = mediaFile.s3Url?.removePrefix("drive://") ?: return@withContext Result.success(false)
        
        try {
            driveService!!.files().get(driveId).setFields("id, trashed").execute()
            // If we get here and it's not trashed, it exists.
            // Note: get() might throw 404 if not found.
             val file = driveService!!.files().get(driveId)
                .setFields("trashed")
                .execute()
                
            if (file.trashed == true) {
                Result.success(false)
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.w("GoogleDriveRepository", "File check failed or not found: $driveId", e)
            Result.success(false)
        }
    }

    override suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        // Naive implementation: check one by one.
        // Batch requests could be used for optimization.
        val results = mutableMapOf<String, Boolean>()
        mediaFiles.forEach { file ->
            val result = verifyFileExists(file)
            results[file.id] = result.getOrDefault(false)
        }
        results
    }

    private fun getOrCreateFolder(folderName: String): String {
        // Search for folder
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
        val fileList = driveService!!.files().list()
            .setQ(query)
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

package com.kcpd.myfolder.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

    // CRITICAL: Synchronization lock to prevent race condition when creating folders
    // Without this, parallel uploads can create duplicate folders with the same name
    private val folderCreationLock = Any()

    /**
     * Clears the folder ID cache. Call this when folders may have been deleted externally
     * (e.g., user deleted MyFolderPrivate from Google Drive web UI).
     */
    fun clearFolderCache() {
        folderIdCache.clear()
        Log.d("GoogleDriveRepository", "Folder ID cache cleared")
    }

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
            return@withContext Result.failure(
                UserFacingException(
                    "Google Drive is not ready. Open Settings → Google Drive and sign in again."
                )
            )
        }

        try {
            Log.d("GoogleDriveRepository", "═══════════════════════════════════════")
            Log.d("GoogleDriveRepository", "Starting Google Drive upload")
            Log.d("GoogleDriveRepository", "  Original filename: ${mediaFile.fileName}")
            Log.d("GoogleDriveRepository", "  Local file path: ${mediaFile.filePath}")

            // Use encrypted file directly
            val fileToUpload = File(mediaFile.filePath)
            if (!fileToUpload.exists()) {
                throw java.io.FileNotFoundException("Encrypted file not found: ${mediaFile.filePath}")
            }

            Log.d("GoogleDriveRepository", "  Encrypted filename: ${fileToUpload.name}")
            Log.d("GoogleDriveRepository", "  File size: ${fileToUpload.length()} bytes")
            Log.d("GoogleDriveRepository", "  MediaType: ${mediaFile.mediaType}")

            // Create folder structure: MyFolderPrivate/category/[FolderPath]
            // Use category.path (lowercase) for consistency with local storage directory structure
            val rootFolderId = getOrCreateFolder("MyFolderPrivate")
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)

            Log.d("GoogleDriveRepository", "  Category name: ${category.name}")
            Log.d("GoogleDriveRepository", "  Category path: ${category.path} (used for folder)")
            Log.d("GoogleDriveRepository", "  Category displayName: ${category.displayName} (NOT used)")

            var parentFolderId = getOrCreateFolder(category.path, rootFolderId)

            // Build user folder path if file is in a folder
            val userFolderPath = buildFolderPathList(mediaFile.folderId)
            if (userFolderPath.isNotEmpty()) {
                Log.d("GoogleDriveRepository", "  User folder ID: ${mediaFile.folderId}")
                Log.d("GoogleDriveRepository", "  User folder path: ${userFolderPath.joinToString("/")}")

                // Create nested folders in Google Drive
                for (folderName in userFolderPath) {
                    parentFolderId = getOrCreateFolder(folderName, parentFolderId)
                }
                Log.d("GoogleDriveRepository", "  Created folder hierarchy: ${userFolderPath.joinToString("/")}")
            } else {
                Log.d("GoogleDriveRepository", "  No user subfolder (root level file)")
            }

            // SECURITY: Use encrypted filename (UUID) to prevent metadata leakage on Google Drive
            // The original filename is already encrypted INSIDE the file's metadata
            // Using the encrypted filename (UUID.enc) instead of original name prevents:
            // - Filename-based content identification by Drive admins/attackers
            // - Metadata leakage to Google's servers
            // - Pattern analysis of file types and naming conventions
            val encryptedFileName = fileToUpload.name  // e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"

            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = encryptedFileName  // Use encrypted filename, not original
            fileMetadata.parents = listOf(parentFolderId)

            // Build full Google Drive path for logging
            val fullPath = buildString {
                append("MyFolderPrivate/")
                append(category.path)
                append("/")
                if (userFolderPath.isNotEmpty()) {
                    append(userFolderPath.joinToString("/"))
                    append("/")
                }
                append(encryptedFileName)
            }
            Log.d("GoogleDriveRepository", "  Final Google Drive path: $fullPath")

            val mediaContent = FileContent("application/octet-stream", fileToUpload)

            val uploadedFile = driveService!!.files().create(fileMetadata, mediaContent)
                .setFields("id, webContentLink, webViewLink")
                .execute()

            Log.d("GoogleDriveRepository", "  Upload successful! File ID: ${uploadedFile.id}")
            Log.d("GoogleDriveRepository", "═══════════════════════════════════════")
            
            // Return the ID or a link. The S3 implementation returns a URL.
            // For Drive, we might return "drive://<id>" or the webViewLink.
            // Let's return the ID for now, prefixed.
            Result.success("drive://${uploadedFile.id}")

        } catch (e: Exception) {
            Log.e("GoogleDriveRepository", "Upload failed", e)
            Result.failure(mapToUserFacingError(e))
        }
    }

    private fun mapToUserFacingError(error: Exception): Exception {
        val message = when (error) {
            is java.io.FileNotFoundException ->
                "Local file not found. If you just switched storage providers, reopen the folder screen and try again."

            is UserRecoverableAuthIOException ->
                "Google Drive authorization is required. Open Settings → Google Drive and sign in again."

            is GoogleJsonResponseException -> {
                when (error.statusCode) {
                    401 -> "Google Drive session expired. Please sign in again."
                    403 -> "Google Drive access denied. Check permissions and that Drive API access is enabled for this app."
                    404 -> "Google Drive folder not found. Please try again."
                    else -> "Google Drive error (${error.statusCode}). Please try again."
                }
            }

            is UnknownHostException -> "Can't reach Google Drive. Check your internet connection."
            is SocketTimeoutException -> "Google Drive connection timed out. Please try again."

            else -> error.message?.takeIf { it.isNotBlank() } ?: "Google Drive upload failed."
        }

        return if (error is UserFacingException) error else UserFacingException(message, error)
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

    private fun getOrCreateFolder(folderName: String, parentId: String? = null): String {
        // CRITICAL: Check cache first before taking the lock
        // This avoids lock contention for folders that are already created
        val cacheKey = "${parentId ?: "root"}/$folderName"
        folderIdCache[cacheKey]?.let { return it }

        // CRITICAL: Synchronize folder creation to prevent race condition
        // Without this, parallel uploads can both search, find nothing, and both create the same folder
        return synchronized(folderCreationLock) {
            // Double-check cache inside lock (another thread may have created it while we waited)
            folderIdCache[cacheKey]?.let { return it }

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
                val folderId = fileList.files[0].id
                folderIdCache[cacheKey] = folderId
                Log.d("GoogleDriveRepository", "  Found existing folder '$folderName': $folderId")
                return folderId
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

            folderIdCache[cacheKey] = folder.id
            Log.d("GoogleDriveRepository", "  Created new folder '$folderName': ${folder.id}")

            folder.id
        }
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

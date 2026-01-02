package com.kcpd.myfolder.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kcpd.myfolder.data.model.S3Config
import com.kcpd.myfolder.domain.model.RemoteConfig
import com.kcpd.myfolder.security.SecureFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.minio.MinioClient
import io.minio.PutObjectArgs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating RemoteStorageRepository instances based on RemoteConfig.
 * Manages the lifecycle and caching of repository instances to avoid recreating them
 * for every upload operation.
 */
@Singleton
class RemoteRepositoryFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureFileManager: SecureFileManager,
    private val folderRepository: FolderRepository,
    private val s3SessionManager: dagger.Lazy<S3SessionManager>
) {
    // Cache of repository instances to avoid recreating them
    private val repositoryCache = mutableMapOf<String, RemoteStorageRepository>()

    /**
     * Get or create a repository instance for the given remote configuration
     */
    fun getRepository(remoteConfig: RemoteConfig): RemoteStorageRepository {
        // Check cache first
        return repositoryCache.getOrPut(remoteConfig.id) {
            createRepository(remoteConfig)
        }
    }

    /**
     * Create a new repository instance based on remote type
     */
    private fun createRepository(remoteConfig: RemoteConfig): RemoteStorageRepository {
        return when (remoteConfig) {
            is RemoteConfig.S3Remote -> createS3Repository(remoteConfig)
            is RemoteConfig.GoogleDriveRemote -> createGoogleDriveRepository(remoteConfig)
        }
    }

    /**
     * Create an S3Repository instance with the given configuration
     */
    private fun createS3Repository(config: RemoteConfig.S3Remote): RemoteStorageRepository {
        return S3RepositoryInstance(
            context = context,
            config = S3Config(
                endpoint = config.endpoint,
                accessKey = config.accessKey,
                secretKey = config.secretKey,
                bucketName = config.bucketName,
                region = config.region
            ),
            secureFileManager = secureFileManager,
            folderRepository = folderRepository,
            s3SessionManager = s3SessionManager
        )
    }

    /**
     * Create a GoogleDriveRepository instance with the given configuration
     */
    private fun createGoogleDriveRepository(config: RemoteConfig.GoogleDriveRemote): RemoteStorageRepository {
        return GoogleDriveRepositoryInstance(
            context = context,
            accountEmail = config.accountEmail,
            secureFileManager = secureFileManager,
            folderRepository = folderRepository
        )
    }

    /**
     * Clear cached repositories (call when remote configurations change)
     */
    fun clearCache() {
        repositoryCache.clear()
    }

    /**
     * Clear specific repository from cache
     */
    fun clearRepository(remoteId: String) {
        repositoryCache.remove(remoteId)
    }

    /**
     * Update cached repository if configuration changed
     */
    fun updateRepository(remoteConfig: RemoteConfig) {
        repositoryCache[remoteConfig.id] = createRepository(remoteConfig)
    }
}

/**
 * S3Repository instance that doesn't use singleton DataStore.
 * Each instance has its own configuration passed via constructor.
 */
class S3RepositoryInstance(
    private val context: Context,
    private val config: S3Config,
    private val secureFileManager: SecureFileManager,
    private val folderRepository: FolderRepository,
    private val s3SessionManager: dagger.Lazy<S3SessionManager>
) : RemoteStorageRepository {

    override suspend fun uploadFile(mediaFile: com.kcpd.myfolder.data.model.MediaFile): Result<String> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileToUpload = java.io.File(mediaFile.filePath)
                if (!fileToUpload.exists()) {
                    throw java.io.FileNotFoundException("Encrypted file not found: ${mediaFile.filePath}")
                }

                var lastException: Exception? = null
                val maxRetries = 3

                for (attempt in 1..maxRetries) {
                    try {
                        // Create MinIO client for this specific config
                        val minioClient = MinioClient.builder()
                            .endpoint(config.endpoint)
                            .credentials(config.accessKey, config.secretKey)
                            .region(config.region)
                            .build()

                        // Build folder path hierarchy
                        val folderPath = buildFolderPath(mediaFile.folderId)
                        val category = com.kcpd.myfolder.data.model.FolderCategory.fromMediaType(mediaFile.mediaType)

                        val objectName = if (folderPath.isNotEmpty()) {
                            "MyFolderPrivate/${category.path}/$folderPath/${fileToUpload.name}"
                        } else {
                            "MyFolderPrivate/${category.path}/${fileToUpload.name}"
                        }

                        minioClient.putObject(
                            PutObjectArgs.builder()
                                .bucket(config.bucketName)
                                .`object`(objectName)
                                .stream(fileToUpload.inputStream(), fileToUpload.length(), -1)
                                .contentType("application/octet-stream")
                                .build()
                        )

                        val url = "s3://${config.bucketName}/$objectName"
                        android.util.Log.d("S3RepositoryInstance", "Upload successful: $url")
                        return@withContext Result.success(url)

                    } catch (e: Exception) {
                        lastException = e
                        if (attempt < maxRetries) {
                            kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }
                }

                Result.failure(lastException ?: Exception("Upload failed"))

            } catch (e: Exception) {
                android.util.Log.e("S3RepositoryInstance", "Upload failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Build the folder path hierarchy for S3 object key.
     */
    private fun buildFolderPath(folderId: String?): String {
        if (folderId == null) return ""

        val folderNames = mutableListOf<String>()
        var currentFolderId: String? = folderId

        while (currentFolderId != null) {
            val folder = folderRepository.getFolderById(currentFolderId)
            if (folder != null) {
                folderNames.add(0, folder.name)
                currentFolderId = folder.parentFolderId
            } else {
                break
            }
        }

        return folderNames.joinToString("/")
    }
}

/**
 * GoogleDriveRepository instance for a specific account.
 * Each instance manages its own Drive service and folder cache.
 */
class GoogleDriveRepositoryInstance(
    private val context: Context,
    private val accountEmail: String,
    private val secureFileManager: SecureFileManager,
    private val folderRepository: FolderRepository
) : RemoteStorageRepository {

    private var driveService: Drive? = null
    private val folderIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val folderCreationLock = Any()

    init {
        // Initialize Drive service for this specific account
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account?.email == accountEmail) {
            initializeDriveService(account)
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
    }

    override suspend fun uploadFile(mediaFile: com.kcpd.myfolder.data.model.MediaFile): Result<String> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val drive = driveService ?: return@withContext Result.failure(
                    IllegalStateException("Google Drive not initialized for $accountEmail")
                )

                val fileToUpload = java.io.File(mediaFile.filePath)
                if (!fileToUpload.exists()) {
                    throw java.io.FileNotFoundException("Encrypted file not found: ${mediaFile.filePath}")
                }

                // Build folder path hierarchy
                val userFolderPath = buildFolderPath(mediaFile.folderId)
                val category = com.kcpd.myfolder.data.model.FolderCategory.fromMediaType(mediaFile.mediaType)

                // Build folder hierarchy: MyFolderPrivate/{category}/{userFolderPath}
                val folderPath = buildList {
                    add("MyFolderPrivate")
                    add(category.path)
                    if (userFolderPath.isNotEmpty()) {
                        addAll(userFolderPath.split("/"))
                    }
                }

                // Get or create folder hierarchy
                val parentFolderId = getOrCreateFolderHierarchy(drive, folderPath)

                // Upload file
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileToUpload.name
                    parents = listOf(parentFolderId)
                }

                val mediaContent = FileContent("application/octet-stream", fileToUpload)
                val file = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()

                val url = "drive://${file.id}"
                android.util.Log.d("GoogleDriveInstance", "Upload successful: $url")
                Result.success(url)

            } catch (e: Exception) {
                android.util.Log.e("GoogleDriveInstance", "Upload failed", e)
                Result.failure(e)
            }
        }
    }

    private fun getOrCreateFolderHierarchy(drive: Drive, pathComponents: List<String>): String {
        var currentParentId = "root"

        for (folderName in pathComponents) {
            val cacheKey = "$currentParentId/$folderName"

            currentParentId = folderIdCache.getOrPut(cacheKey) {
                synchronized(folderCreationLock) {
                    // Double-check after acquiring lock
                    folderIdCache[cacheKey] ?: run {
                        // Search for existing folder
                        val query = "name='$folderName' and '$currentParentId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
                        val result = drive.files().list()
                            .setQ(query)
                            .setSpaces("drive")
                            .setFields("files(id, name)")
                            .execute()

                        if (result.files.isNotEmpty()) {
                            result.files[0].id
                        } else {
                            // Create new folder
                            val folderMetadata = com.google.api.services.drive.model.File().apply {
                                name = folderName
                                mimeType = "application/vnd.google-apps.folder"
                                parents = listOf(currentParentId)
                            }
                            drive.files().create(folderMetadata)
                                .setFields("id")
                                .execute()
                                .id
                        }
                    }
                }
            }
        }

        return currentParentId
    }

    /**
     * Build the folder path hierarchy.
     */
    private fun buildFolderPath(folderId: String?): String {
        if (folderId == null) return ""

        val folderNames = mutableListOf<String>()
        var currentFolderId: String? = folderId

        while (currentFolderId != null) {
            val folder = folderRepository.getFolderById(currentFolderId)
            if (folder != null) {
                folderNames.add(0, folder.name)
                currentFolderId = folder.parentFolderId
            } else {
                break
            }
        }

        return folderNames.joinToString("/")
    }
}

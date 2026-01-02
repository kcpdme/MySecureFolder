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
            is RemoteConfig.WebDavRemote -> createWebDavRepository(remoteConfig)
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
     * Create a WebDAV repository instance with the given configuration
     */
    private fun createWebDavRepository(config: RemoteConfig.WebDavRemote): RemoteStorageRepository {
        return WebDavRepositoryInstance(
            config = config,
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
 *
 * Optimizations:
 * - Uses custom OkHttpClient with proper connection pool settings
 * - Shorter keep-alive to avoid stale connection errors on B2/R2
 * - retryOnConnectionFailure for resilience on mobile networks
 */
class S3RepositoryInstance(
    private val context: Context,
    private val config: S3Config,
    private val secureFileManager: SecureFileManager,
    private val folderRepository: FolderRepository,
    private val s3SessionManager: dagger.Lazy<S3SessionManager>
) : RemoteStorageRepository {

    companion object {
        private const val TAG = "S3RepositoryInstance"
    }

    // Track if client has been initialized for logging
    @Volatile
    private var clientInitialized = false

    // Custom OkHttpClient optimized for S3/B2/R2 uploads
    // Fixes: stale connection reuse causing "unexpected end of stream" errors
    private val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            // Retry on connection failure - handles stale connections gracefully
            .retryOnConnectionFailure(true)
            // Connection pool: max 5 idle connections, 30s keep-alive
            // Shorter keep-alive prevents stale connection issues with B2/R2
            .connectionPool(okhttp3.ConnectionPool(5, 30, java.util.concurrent.TimeUnit.SECONDS))
            // Timeouts optimized for large file uploads
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // Cache the MinIO client to avoid recreating it for every upload
    // This eliminates the manifest parsing overhead on each upload
    private val minioClient: MinioClient by lazy {
        android.util.Log.d(TAG, "Creating MinIO client for endpoint: ${config.endpoint} (bucket: ${config.bucketName})")
        val startTime = System.currentTimeMillis()
        val client = MinioClient.builder()
            .endpoint(config.endpoint)
            .credentials(config.accessKey, config.secretKey)
            .region(config.region)
            .httpClient(okHttpClient)
            .build()
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.d(TAG, "MinIO client created in ${elapsed}ms for ${config.bucketName}")
        clientInitialized = true
        client
    }

    override suspend fun uploadFile(mediaFile: com.kcpd.myfolder.data.model.MediaFile): Result<String> {
        // Log whether we're using cached client or creating new one
        if (clientInitialized) {
            android.util.Log.d(TAG, "Using CACHED MinIO client for ${config.bucketName}")
        } else {
            android.util.Log.d(TAG, "First upload - will initialize MinIO client for ${config.bucketName}")
        }
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
                        // Use cached MinIO client (created lazily on first use)
                        // Build folder path hierarchy
                        val folderPath = buildFolderPath(mediaFile.folderId)
                        val category = com.kcpd.myfolder.data.model.FolderCategory.fromMediaType(mediaFile.mediaType)

                        val objectName = if (folderPath.isNotEmpty()) {
                            "MyFolderPrivate/${category.path}/$folderPath/${fileToUpload.name}"
                        } else {
                            "MyFolderPrivate/${category.path}/${fileToUpload.name}"
                        }

                        // Optimize multipart upload settings for better performance
                        // Use larger part size (10MB) for faster uploads to R2/B2
                        // partSize of -1 uses default (5MB), but we can increase for better throughput
                        val partSize = 10L * 1024 * 1024 // 10MB parts for better performance

                        // Use use{} to ensure stream is properly closed after upload
                        fileToUpload.inputStream().use { inputStream ->
                            minioClient.putObject(
                                PutObjectArgs.builder()
                                    .bucket(config.bucketName)
                                    .`object`(objectName)
                                    .stream(inputStream, fileToUpload.length(), partSize)
                                    .contentType("application/octet-stream")
                                    .build()
                            )
                        }

                        val url = "s3://${config.bucketName}/$objectName"
                        android.util.Log.d(TAG, "Upload successful: $url")
                        return@withContext Result.success(url)

                    } catch (e: Exception) {
                        lastException = e
                        android.util.Log.w("S3RepositoryInstance", "Upload attempt $attempt failed", e)
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

/**
 * WebDAV repository instance for services like Koofr, Icedrive, Nextcloud, etc.
 * Uses sardine-android library (OkHttp-based WebDAV client).
 *
 * Optimizations:
 * - Uses preemptive Basic authentication to avoid 401 retry cycles
 * - Global folder cache shared across all instances (persists for app lifetime)
 * - Uses MKCOL directly instead of exists() check to reduce requests
 * - Streaming upload for large files (no memory issues with 100MB+ files)
 */
class WebDavRepositoryInstance(
    private val config: RemoteConfig.WebDavRemote,
    private val folderRepository: FolderRepository
) : RemoteStorageRepository {

    companion object {
        private const val TAG = "WebDavRepository"

        // Global folder cache shared across ALL WebDavRepositoryInstance instances
        // This persists for the app's lifetime, avoiding redundant folder checks
        // Key format: "serverUrl/path/" -> true
        private val globalFolderCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    }

    // Track if client has been initialized for logging
    @Volatile
    private var clientInitialized = false

    // Create OkHttpClient with preemptive Basic authentication
    // This avoids the 401 -> retry cycle that causes slowness
    private val okHttpClient: okhttp3.OkHttpClient by lazy {
        val credentials = okhttp3.Credentials.basic(config.username, config.password)
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Add Authorization header preemptively to avoid 401 challenge
                val request = chain.request().newBuilder()
                    .header("Authorization", credentials)
                    .build()
                chain.proceed(request)
            }
            .retryOnConnectionFailure(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            // Extended write timeout for large files (100MB+ at slow speeds)
            .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // Cache the Sardine WebDAV client with custom OkHttpClient
    private val sardineClient: com.thegrizzlylabs.sardineandroid.Sardine by lazy {
        android.util.Log.d(TAG, "Creating WebDAV client for: ${config.serverUrl}")
        val startTime = System.currentTimeMillis()
        val client = com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine(okHttpClient)
        // Note: setCredentials not needed since we're using preemptive auth via interceptor
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.d(TAG, "WebDAV client created in ${elapsed}ms for ${config.name}")
        clientInitialized = true
        client
    }

    override suspend fun uploadFile(mediaFile: com.kcpd.myfolder.data.model.MediaFile): Result<String> {
        // Log whether we're using cached client or creating new one
        if (clientInitialized) {
            android.util.Log.d(TAG, "Using CACHED WebDAV client for ${config.name}")
        } else {
            android.util.Log.d(TAG, "First upload - will initialize WebDAV client for ${config.name}")
        }

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
                        // Build folder path hierarchy
                        val userFolderPath = buildFolderPath(mediaFile.folderId)
                        val category = com.kcpd.myfolder.data.model.FolderCategory.fromMediaType(mediaFile.mediaType)

                        // Build full path: serverUrl/basePath/MyFolderPrivate/{category}/{userFolderPath}
                        val baseUrl = config.serverUrl.trimEnd('/')
                        val basePath = config.basePath.trim('/').let { if (it.isNotEmpty()) "/$it" else "" }

                        val folderComponents = mutableListOf("MyFolderPrivate", category.path)
                        if (userFolderPath.isNotEmpty()) {
                            folderComponents.addAll(userFolderPath.split("/"))
                        }

                        // Ensure all folders exist (WebDAV requires explicit folder creation)
                        // Optimization: Use global cache to skip folder checks across sessions
                        var currentPath = "$baseUrl$basePath"
                        for (folder in folderComponents) {
                            currentPath = "$currentPath/$folder"
                            val folderUrl = "$currentPath/"

                            // Skip if we already know this folder exists (global cache)
                            if (globalFolderCache.containsKey(folderUrl)) {
                                continue
                            }

                            try {
                                // Try to create the directory directly (MKCOL)
                                // This is faster than exists() check + create
                                // If folder exists, server returns 405 (Method Not Allowed) or similar
                                sardineClient.createDirectory(folderUrl)
                                android.util.Log.d(TAG, "Created WebDAV folder: $folderUrl")
                                globalFolderCache[folderUrl] = true
                            } catch (e: com.thegrizzlylabs.sardineandroid.impl.SardineException) {
                                // 405 = Method Not Allowed (folder exists)
                                // 409 = Conflict (intermediate folder missing - shouldn't happen with our sequential creation)
                                // 301/302 = Redirect (folder exists)
                                val statusCode = e.statusCode
                                if (statusCode == 405 || statusCode == 409 || statusCode == 301 || statusCode == 302) {
                                    // Folder already exists, cache it
                                    globalFolderCache[folderUrl] = true
                                    android.util.Log.d(TAG, "Folder already exists: $folderUrl (status: $statusCode)")
                                } else {
                                    android.util.Log.w(TAG, "Folder creation failed for $folderUrl: ${e.message} (status: $statusCode)")
                                    // Continue anyway - folder might exist despite error
                                    globalFolderCache[folderUrl] = true
                                }
                            } catch (e: Exception) {
                                // Other errors - assume folder exists and continue
                                android.util.Log.d(TAG, "Folder check/create for $folderUrl: ${e.message}")
                                globalFolderCache[folderUrl] = true
                            }
                        }

                        // Upload the file using streaming (efficient for large files 100MB+)
                        val fileUrl = "$currentPath/${fileToUpload.name}"

                        // sardine-android put(url, File, contentType) streams the file internally
                        // It uses OkHttp's RequestBody.create(file, contentType) which is memory-efficient
                        sardineClient.put(fileUrl, fileToUpload, "application/octet-stream")

                        val url = "webdav://${config.name}/${folderComponents.joinToString("/")}/${fileToUpload.name}"
                        android.util.Log.d(TAG, "Upload successful: $url")
                        return@withContext Result.success(url)

                    } catch (e: Exception) {
                        lastException = e
                        android.util.Log.w(TAG, "Upload attempt $attempt failed", e)
                        if (attempt < maxRetries) {
                            kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }
                }

                Result.failure(lastException ?: Exception("Upload failed"))

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Upload failed", e)
                Result.failure(e)
            }
        }
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

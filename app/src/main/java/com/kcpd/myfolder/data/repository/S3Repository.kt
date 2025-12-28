package com.kcpd.myfolder.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.S3Config
import dagger.hilt.android.qualifiers.ApplicationContext
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "s3_config")

@Singleton
class S3Repository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: dagger.Lazy<S3SessionManager>, // Lazy to avoid circular dependency
    private val secureFileManager: com.kcpd.myfolder.security.SecureFileManager,
    private val folderRepository: FolderRepository
) : RemoteStorageRepository {
    private val endpointKey = stringPreferencesKey("endpoint")
    private val accessKeyKey = stringPreferencesKey("access_key")
    private val secretKeyKey = stringPreferencesKey("secret_key")
    private val bucketNameKey = stringPreferencesKey("bucket_name")
    private val regionKey = stringPreferencesKey("region")

    val s3Config: Flow<S3Config?> = context.dataStore.data.map { preferences ->
        val endpoint = preferences[endpointKey]
        val accessKey = preferences[accessKeyKey]
        val secretKey = preferences[secretKeyKey]
        val bucketName = preferences[bucketNameKey]

        if (endpoint != null && accessKey != null && secretKey != null && bucketName != null) {
            S3Config(
                endpoint = endpoint,
                accessKey = accessKey,
                secretKey = secretKey,
                bucketName = bucketName,
                region = preferences[regionKey] ?: "us-east-1"
            )
        } else {
            null
        }
    }

    suspend fun saveS3Config(config: S3Config) {
        context.dataStore.edit { preferences ->
            preferences[endpointKey] = config.endpoint
            preferences[accessKeyKey] = config.accessKey
            preferences[secretKeyKey] = config.secretKey
            preferences[bucketNameKey] = config.bucketName
            preferences[regionKey] = config.region
        }
    }

    override suspend fun uploadFile(mediaFile: MediaFile): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Use encrypted file directly
            val fileToUpload = File(mediaFile.filePath)
            if (!fileToUpload.exists()) {
                throw java.io.FileNotFoundException("Encrypted file not found: ${mediaFile.filePath}")
            }
            Log.d("S3Repository", "Uploading encrypted file: ${mediaFile.fileName}, size: ${fileToUpload.length()} bytes")

            var lastException: Exception? = null
            val maxRetries = 3

            // Retry loop for upload
            for (attempt in 1..maxRetries) {
                try {
                    val minioClient: MinioClient
                    val config: S3Config

                    // Attempt 1: Try to use cached session
                    // Attempts > 1: Force new client to handle potential stale connections (Broken pipe)
                    val useCached = attempt == 1

                    var cachedClient: MinioClient? = null
                    var cachedConfig: S3Config? = null

                    if (useCached) {
                        cachedClient = withContext(Dispatchers.Main) {
                            sessionManager.get().getClient()
                        }
                        cachedConfig = withContext(Dispatchers.Main) {
                            sessionManager.get().getConfig()
                        }
                    }

                    if (useCached && cachedClient != null && cachedConfig != null) {
                        Log.d("S3Repository", "Attempt $attempt: Using cached S3 session")
                        minioClient = cachedClient
                        config = cachedConfig
                    } else {
                        Log.d("S3Repository", "Attempt $attempt: Creating new MinIO client")
                        val currentConfig = s3Config.first()
                        if (currentConfig == null) {
                            // Gracefully handle missing config without crashing
                            Log.e("S3Repository", "S3 configuration not found")
                            return@withContext Result.failure(Exception("S3 configuration not found. Please configure S3 settings first."))
                        }
                        config = currentConfig

                        minioClient = MinioClient.builder()
                            .endpoint(config.endpoint)
                            .credentials(config.accessKey, config.secretKey)
                            .region(config.region)
                            .build()
                    }

                    // Step 2: Upload the encrypted file to S3 with uniform path structure
                    val category = FolderCategory.fromMediaType(mediaFile.mediaType)

                    // SECURITY: Use encrypted filename (UUID) to prevent metadata leakage on S3
                    // The original filename is already encrypted INSIDE the file's metadata
                    // Using the encrypted filename (UUID.enc) instead of original name prevents:
                    // - Filename-based content identification by S3 admins/attackers
                    // - Metadata leakage to cloud storage providers
                    // - Pattern analysis of file types and naming conventions
                    val encryptedFileName = fileToUpload.name  // e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"

                    // Build folder path if file is in a folder
                    val folderPath = buildFolderPath(mediaFile.folderId)
                    val objectName = if (folderPath.isNotEmpty()) {
                        "MyFolderPrivate/${category.displayName}/$folderPath/$encryptedFileName"
                    } else {
                        "MyFolderPrivate/${category.displayName}/$encryptedFileName"
                    }

                    Log.d("S3Repository", "Uploading to S3 path: $objectName")

                    minioClient.putObject(
                        PutObjectArgs.builder()
                            .bucket(config.bucketName)
                            .`object`(objectName)
                            .stream(fileToUpload.inputStream(), fileToUpload.length(), -1)
                            .contentType("application/octet-stream")
                            .build()
                    )

                    val url = "${config.endpoint}/${config.bucketName}/$objectName"
                    Log.d("S3Repository", "File uploaded successfully on attempt $attempt: $url")

                    return@withContext Result.success(url)

                } catch (e: Exception) {
                    Log.e("S3Repository", "Upload attempt $attempt failed", e)
                    lastException = e
                    if (attempt < maxRetries) {
                        // Exponential backoff: 1s, 2s
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                }
            }

            throw lastException ?: Exception("Upload failed after $maxRetries attempts")

        } catch (e: Exception) {
            Log.e("S3Repository", "Upload failed", e)
            Result.failure(e)
        }
    }


    /**
     * Build the folder path hierarchy for S3 object key.
     * Returns path like "FolderA/FolderB/FolderC" for nested folders.
     */
    private fun buildFolderPath(folderId: String?): String {
        if (folderId == null) return ""

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

        return folderNames.joinToString("/")
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

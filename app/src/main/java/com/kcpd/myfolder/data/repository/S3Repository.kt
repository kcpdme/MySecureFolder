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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
            Log.d("S3Repository", "═══════════════════════════════════════")
            Log.d("S3Repository", "Starting S3/MinIO upload")
            Log.d("S3Repository", "  Original filename: ${mediaFile.fileName}")
            Log.d("S3Repository", "  Local file path: ${mediaFile.filePath}")

            // Step 1: Use encrypted file directly
            val fileToUpload = File(mediaFile.filePath)
            if (!fileToUpload.exists()) {
                throw java.io.FileNotFoundException("Encrypted file not found: ${mediaFile.filePath}")
            }

            Log.d("S3Repository", "  Encrypted filename: ${fileToUpload.name}")
            Log.d("S3Repository", "  File size: ${fileToUpload.length()} bytes")
            Log.d("S3Repository", "  MediaType: ${mediaFile.mediaType}")

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
                    // Use category.path (lowercase) for consistency with local storage directory structure
                    val category = FolderCategory.fromMediaType(mediaFile.mediaType)

                    Log.d("S3Repository", "  Category name: ${category.name}")
                    Log.d("S3Repository", "  Category path: ${category.path} (used for object key)")
                    Log.d("S3Repository", "  Category displayName: ${category.displayName} (NOT used)")

                    // SECURITY: Use encrypted filename (UUID) to prevent metadata leakage on S3
                    // The original filename is already encrypted INSIDE the file's metadata
                    // Using the encrypted filename (UUID.enc) instead of original name prevents:
                    // - Filename-based content identification by S3 admins/attackers
                    // - Metadata leakage to cloud storage providers
                    // - Pattern analysis of file types and naming conventions
                    val encryptedFileName = fileToUpload.name  // e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"

                    // Build folder path if file is in a folder
                    val folderPath = buildFolderPath(mediaFile.folderId)
                    if (folderPath.isNotEmpty()) {
                        Log.d("S3Repository", "  User folder ID: ${mediaFile.folderId}")
                        Log.d("S3Repository", "  User folder path: $folderPath")
                    } else {
                        Log.d("S3Repository", "  No user subfolder (root level file)")
                    }

                    val objectName = if (folderPath.isNotEmpty()) {
                        "MyFolderPrivate/${category.path}/$folderPath/$encryptedFileName"
                    } else {
                        "MyFolderPrivate/${category.path}/$encryptedFileName"
                    }

                    Log.d("S3Repository", "  Final S3 object key: $objectName")

                    minioClient.putObject(
                        PutObjectArgs.builder()
                            .bucket(config.bucketName)
                            .`object`(objectName)
                            .stream(fileToUpload.inputStream(), fileToUpload.length(), -1)
                            .contentType("application/octet-stream")
                            .build()
                    )

                    val url = "${config.endpoint}/${config.bucketName}/$objectName"
                    Log.d("S3Repository", "  Upload successful! URL: $url")
                    Log.d("S3Repository", "═══════════════════════════════════════")

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
            Result.failure(mapToUserFacingError(e))
        }
    }

    private fun mapToUserFacingError(error: Exception): Exception {
        val message: String = when (error) {
            is java.io.FileNotFoundException ->
                "Local file not found. Please try again."

            is UnknownHostException ->
                "Can't reach S3 endpoint. Check your internet connection and the endpoint URL."

            is SocketTimeoutException ->
                "S3 connection timed out. Please try again."

            else -> {
                val raw = error.message ?: ""
                when {
                    raw.contains("AccessDenied", ignoreCase = true) -> "S3 access denied. Check Access Key/Secret Key and bucket policy."
                    raw.contains("SignatureDoesNotMatch", ignoreCase = true) -> "S3 signature mismatch. Verify access key/secret key and region."
                    raw.contains("InvalidAccessKeyId", ignoreCase = true) -> "Invalid S3 access key. Please re-check S3 settings."
                    raw.contains("NoSuchBucket", ignoreCase = true) -> "S3 bucket not found. Please check the bucket name."
                    raw.contains("SSL", ignoreCase = true) || raw.contains("handshake", ignoreCase = true) ->
                        "S3 TLS/SSL error. If using https, ensure the certificate is valid (or switch to http for local MinIO)."
                    else -> raw.takeIf { it.isNotBlank() } ?: "S3 upload failed."
                }
            }
        }

        return if (error is UserFacingException) error else UserFacingException(message, error)
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

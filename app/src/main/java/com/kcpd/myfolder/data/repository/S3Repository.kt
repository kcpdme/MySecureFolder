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
    private val secureFileManager: com.kcpd.myfolder.security.SecureFileManager
) {
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

    suspend fun uploadFile(mediaFile: MediaFile): Result<String> = withContext(Dispatchers.IO) {
        var tempDecryptedFile: File? = null
        try {
            // Get session manager on main thread first to avoid lifecycle issues
            val cachedClient = withContext(Dispatchers.Main) {
                sessionManager.get().getClient()
            }
            val cachedConfig = withContext(Dispatchers.Main) {
                sessionManager.get().getConfig()
            }

            // Now do all IO operations (already on IO dispatcher from outer withContext)
            val minioClient: MinioClient
            val config: S3Config

            if (cachedClient != null && cachedConfig != null) {
                // Use cached session
                Log.d("S3Repository", "Using cached S3 session")
                minioClient = cachedClient
                config = cachedConfig
            } else {
                // No cached session - create new client
                Log.d("S3Repository", "No cached session, creating new MinIO client")
                config = s3Config.first() ?: return@withContext Result.failure(
                    Exception("S3 configuration not found. Please configure S3 settings first.")
                )

                minioClient = MinioClient.builder()
                    .endpoint(config.endpoint)
                    .credentials(config.accessKey, config.secretKey)
                    .region(config.region)
                    .build()
            }

            // Step 1: Decrypt the encrypted file to a temporary file
            val encryptedFile = File(mediaFile.filePath)
            Log.d("S3Repository", "Decrypting file for upload: ${mediaFile.fileName}")
            tempDecryptedFile = secureFileManager.decryptFile(encryptedFile)
            Log.d("S3Repository", "Decrypted to temp file: ${tempDecryptedFile.absolutePath}, size: ${tempDecryptedFile.length()} bytes")

            // Step 2: Upload the decrypted file to S3
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            val objectName = "${category.path}/${mediaFile.fileName}"

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(config.bucketName)
                    .`object`(objectName)
                    .stream(tempDecryptedFile.inputStream(), tempDecryptedFile.length(), -1)
                    .contentType(getContentType(mediaFile.fileName))
                    .build()
            )

            val url = "${config.endpoint}/${config.bucketName}/$objectName"
            Log.d("S3Repository", "File uploaded successfully: $url")

            Result.success(url)
        } catch (e: Exception) {
            Log.e("S3Repository", "Upload failed", e)
            Result.failure(e)
        } finally {
            // Step 3: Always clean up the temporary decrypted file
            tempDecryptedFile?.let { tempFile ->
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    Log.d("S3Repository", "Temp file deleted: $deleted (${tempFile.absolutePath})")
                }
            }
        }
    }

    /**
     * Verify if a file exists on S3.
     * Returns true if file exists, false if deleted or not found.
     */
    suspend fun verifyFileExists(mediaFile: MediaFile): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Get S3 client (must get session manager on main thread first)
            val cachedClient = withContext(Dispatchers.Main) {
                sessionManager.get().getClient()
            }
            val cachedConfig = withContext(Dispatchers.Main) {
                sessionManager.get().getConfig()
            }

            // Now do S3 operations (already on IO dispatcher)
            val minioClient: MinioClient
            val config: S3Config

            if (cachedClient != null && cachedConfig != null) {
                minioClient = cachedClient
                config = cachedConfig
            } else {
                config = s3Config.first() ?: return@withContext Result.failure(
                    Exception("S3 configuration not found")
                )

                minioClient = MinioClient.builder()
                    .endpoint(config.endpoint)
                    .credentials(config.accessKey, config.secretKey)
                    .region(config.region)
                    .build()
            }

            // Check if file exists on S3
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            val objectName = "${category.path}/${mediaFile.fileName}"

            try {
                minioClient.statObject(
                    StatObjectArgs.builder()
                        .bucket(config.bucketName)
                        .`object`(objectName)
                        .build()
                )
                // File exists
                Log.d("S3Repository", "File exists on S3: ${mediaFile.fileName}")
                Result.success(true)
            } catch (e: Exception) {
                // File not found (deleted from S3)
                Log.w("S3Repository", "File not found on S3: ${mediaFile.fileName}")
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e("S3Repository", "Error verifying file existence", e)
            Result.failure(e)
        }
    }

    /**
     * Verify multiple files exist on S3.
     * Returns map of fileId -> exists status.
     */
    suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()

        mediaFiles.forEach { mediaFile ->
            val result = verifyFileExists(mediaFile)
            result.onSuccess { exists ->
                results[mediaFile.id] = exists
            }.onFailure {
                // On error, assume file doesn't exist to be safe
                results[mediaFile.id] = false
            }
        }

        results
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

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
    private val sessionManager: dagger.Lazy<S3SessionManager> // Lazy to avoid circular dependency
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
        try {
            // Try to use cached session first
            val cachedClient = sessionManager.get().getClient()
            val cachedConfig = sessionManager.get().getConfig()

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

            val file = File(mediaFile.filePath)
            val category = FolderCategory.fromMediaType(mediaFile.mediaType)
            val objectName = "${category.path}/${mediaFile.fileName}"

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(config.bucketName)
                    .`object`(objectName)
                    .stream(file.inputStream(), file.length(), -1)
                    .contentType(getContentType(mediaFile.fileName))
                    .build()
            )

            val url = "${config.endpoint}/${config.bucketName}/$objectName"
            Log.d("S3Repository", "File uploaded successfully: $url")
            Result.success(url)
        } catch (e: Exception) {
            Log.e("S3Repository", "Upload failed", e)
            Result.failure(e)
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

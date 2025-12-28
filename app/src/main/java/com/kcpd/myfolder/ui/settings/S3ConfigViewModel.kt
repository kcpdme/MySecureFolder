package com.kcpd.myfolder.ui.settings

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.S3Config
import com.kcpd.myfolder.data.repository.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class S3ConfigViewModel @Inject constructor(
    application: Application,
    private val s3Repository: S3Repository
) : AndroidViewModel(application) {

    val s3Config: Flow<S3Config?> = s3Repository.s3Config

    private val _testingConnection = MutableStateFlow(false)
    val testingConnection: StateFlow<Boolean> = _testingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    sealed class ConnectionTestResult {
        object Success : ConnectionTestResult()
        data class Error(val message: String) : ConnectionTestResult()
    }

    fun saveConfig(
        endpoint: String,
        accessKey: String,
        secretKey: String,
        bucketName: String,
        region: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val config = S3Config(
                endpoint = endpoint.trim(),
                accessKey = accessKey.trim(),
                secretKey = secretKey.trim(),
                bucketName = bucketName.trim(),
                region = region.trim()
            )

            // Test connection before saving
            _testingConnection.value = true
            val testResult = testConnection(config)
            _testingConnection.value = false

            when (testResult) {
                is ConnectionTestResult.Success -> {
                    s3Repository.saveS3Config(config)
                    _connectionTestResult.value = ConnectionTestResult.Success
                    Toast.makeText(
                        getApplication(),
                        "S3 configuration saved and verified",
                        Toast.LENGTH_SHORT
                    ).show()
                    onSuccess()
                }
                is ConnectionTestResult.Error -> {
                    _connectionTestResult.value = testResult
                    Toast.makeText(
                        getApplication(),
                        "Connection failed: ${testResult.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    suspend fun testConnection(config: S3Config): ConnectionTestResult = withContext(Dispatchers.IO) {
        try {
            val minioClient = io.minio.MinioClient.builder()
                .endpoint(config.endpoint)
                .credentials(config.accessKey, config.secretKey)
                .region(config.region)
                .build()

            val bucketExists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder()
                    .bucket(config.bucketName)
                    .build()
            )

            if (bucketExists) {
                ConnectionTestResult.Success
            } else {
                ConnectionTestResult.Error("Bucket '${config.bucketName}' not found or not accessible")
            }
        } catch (e: Exception) {
            android.util.Log.e("S3ConfigViewModel", "Connection test failed", e)
            ConnectionTestResult.Error(e.message ?: "Unknown error")
        }
    }

    fun clearTestResult() {
        _connectionTestResult.value = null
    }
}

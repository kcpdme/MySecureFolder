package com.kcpd.myfolder.data.repository

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kcpd.myfolder.data.model.S3Config
import com.kcpd.myfolder.security.VaultManager
import io.minio.MinioClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3 Session Manager - Manages cached MinIO client connections
 *
 * Pattern:
 * 1. Establish MinIO client when vault unlocks and S3 is configured
 * 2. Cache the authenticated client in memory during session
 * 3. Auto-clear session when app backgrounds or vault locks
 * 4. Re-establish on app foreground if vault is unlocked
 *
 * Benefits:
 * - Reduced network overhead (reuse connection)
 * - Faster uploads (no repeated authentication)
 * - Automatic cleanup on security events
 * - Connection health monitoring
 */
@Singleton
class S3SessionManager @Inject constructor(
    private val s3Repository: S3Repository,
    private val vaultManager: VaultManager,
    application: Application
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "S3SessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var cachedClient: MinioClient? = null
    private var cachedConfig: S3Config? = null

    init {
        // Observe app lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Observe vault state changes
        scope.launch {
            vaultManager.vaultState.collect { vaultState ->
                when (vaultState) {
                    is VaultManager.VaultState.Unlocked -> {
                        // Vault unlocked - try to establish S3 session
                        establishSession()
                    }
                    is VaultManager.VaultState.Locked -> {
                        // Vault locked - clear S3 session
                        clearSession()
                    }
                }
            }
        }
    }

    sealed class SessionState {
        object Disconnected : SessionState()
        object Connecting : SessionState()
        data class Connected(
            val endpoint: String,
            val bucketName: String,
            val connectedAt: Long = System.currentTimeMillis()
        ) : SessionState()
        data class Error(val message: String) : SessionState()
    }

    /**
     * Establishes S3 session by creating and caching MinIO client
     */
    suspend fun establishSession() {
        if (!vaultManager.isUnlocked()) {
            Log.d(TAG, "Cannot establish S3 session: vault is locked")
            return
        }

        _sessionState.value = SessionState.Connecting

        try {
            val config = s3Repository.s3Config.first()

            if (config == null) {
                Log.d(TAG, "S3 not configured, session not established")
                _sessionState.value = SessionState.Disconnected
                return
            }

            // Build MinIO client
            val client = MinioClient.builder()
                .endpoint(config.endpoint)
                .credentials(config.accessKey, config.secretKey)
                .region(config.region)
                .build()

            // Test connection by checking if bucket exists
            val bucketExists = try {
                client.bucketExists(
                    io.minio.BucketExistsArgs.builder()
                        .bucket(config.bucketName)
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify bucket existence", e)
                false
            }

            if (!bucketExists) {
                Log.w(TAG, "Bucket '${config.bucketName}' does not exist or is not accessible")
                _sessionState.value = SessionState.Error("Bucket not accessible")
                return
            }

            // Cache the client and config
            cachedClient = client
            cachedConfig = config

            _sessionState.value = SessionState.Connected(
                endpoint = config.endpoint,
                bucketName = config.bucketName
            )

            Log.d(TAG, "S3 session established: ${config.endpoint}/${config.bucketName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish S3 session", e)
            _sessionState.value = SessionState.Error(e.message ?: "Connection failed")
            cachedClient = null
            cachedConfig = null
        }
    }

    /**
     * Clears the cached S3 session
     */
    fun clearSession() {
        Log.d(TAG, "Clearing S3 session")
        cachedClient = null
        cachedConfig = null
        _sessionState.value = SessionState.Disconnected
    }

    /**
     * Gets the cached MinIO client if session is active
     * Returns null if session is not established
     */
    fun getClient(): MinioClient? {
        return if (_sessionState.value is SessionState.Connected) {
            cachedClient
        } else {
            null
        }
    }

    /**
     * Gets the cached S3 config if session is active
     * Returns null if session is not established
     */
    fun getConfig(): S3Config? {
        return if (_sessionState.value is SessionState.Connected) {
            cachedConfig
        } else {
            null
        }
    }

    /**
     * Checks if S3 session is currently active
     */
    fun isSessionActive(): Boolean {
        return _sessionState.value is SessionState.Connected
    }

    // Lifecycle callbacks

    override fun onStop(owner: LifecycleOwner) {
        // App going to background
        // IMPORTANT: Do NOT clear session while uploads are in progress
        // This allows background uploads to complete
        // The session will be cleared when vault locks or app is killed
        Log.d(TAG, "App backgrounded - session preserved for background uploads")
        
        // Note: For true security on app background, users should enable
        // "Lock vault on background" in settings, which will trigger vault lock
        // and THAT will clear the session properly
    }

    override fun onStart(owner: LifecycleOwner) {
        // App returning to foreground - re-establish if vault is unlocked
        if (vaultManager.isUnlocked()) {
            Log.d(TAG, "App foregrounded with unlocked vault, establishing S3 session")
            scope.launch {
                establishSession()
            }
        }
    }
}

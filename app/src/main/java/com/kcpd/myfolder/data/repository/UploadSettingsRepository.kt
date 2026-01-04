package com.kcpd.myfolder.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uploadSettingsDataStore by preferencesDataStore(name = "upload_settings")

/**
 * Repository for managing upload-related settings.
 * Stores preferences like per-remote-type concurrency in DataStore.
 * 
 * Unified settings used by both:
 * - MultiRemoteUploadCoordinator (foreground uploads)
 * - UploadWorker (background WorkManager uploads)
 */
@Singleton
class UploadSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Legacy key (kept for backward compatibility)
        private val KEY_UPLOAD_CONCURRENCY = intPreferencesKey("upload_concurrency")
        
        // Per-remote-type concurrency keys
        private val KEY_S3_CONCURRENCY = intPreferencesKey("s3_concurrency")
        private val KEY_GOOGLE_DRIVE_CONCURRENCY = intPreferencesKey("google_drive_concurrency")
        private val KEY_WEBDAV_CONCURRENCY = intPreferencesKey("webdav_concurrency")
        private val KEY_MAX_PARALLEL_UPLOADS = intPreferencesKey("max_parallel_uploads")
        
        // Default values (optimized based on API characteristics)
        const val DEFAULT_CONCURRENCY = 2  // Legacy default
        const val DEFAULT_S3_CONCURRENCY = 3          // S3/MinIO: Fast, handles concurrency well
        const val DEFAULT_GOOGLE_DRIVE_CONCURRENCY = 1 // Google Drive: Rate limited, be conservative
        const val DEFAULT_WEBDAV_CONCURRENCY = 2       // WebDAV: Moderate, depends on server
        const val DEFAULT_MAX_PARALLEL = 4             // Maximum total concurrent uploads
        
        // Valid ranges
        const val MIN_CONCURRENCY = 1
        const val MAX_CONCURRENCY = 5
        const val MIN_MAX_PARALLEL = 2
        const val MAX_MAX_PARALLEL = 8
        
        // Preset options for UI
        val CONCURRENCY_OPTIONS = listOf(1, 2, 3, 4, 5)
        val MAX_PARALLEL_OPTIONS = listOf(2, 3, 4, 5, 6, 8)
    }

    // ==================== S3/MinIO Concurrency ====================
    
    val s3Concurrency: Flow<Int> = context.uploadSettingsDataStore.data.map { preferences ->
        preferences[KEY_S3_CONCURRENCY] ?: DEFAULT_S3_CONCURRENCY
    }
    
    fun getS3ConcurrencySync(): Int = runBlocking {
        s3Concurrency.first()
    }
    
    suspend fun setS3Concurrency(value: Int) {
        val valid = value.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
        context.uploadSettingsDataStore.edit { it[KEY_S3_CONCURRENCY] = valid }
    }
    
    // ==================== Google Drive Concurrency ====================
    
    val googleDriveConcurrency: Flow<Int> = context.uploadSettingsDataStore.data.map { preferences ->
        preferences[KEY_GOOGLE_DRIVE_CONCURRENCY] ?: DEFAULT_GOOGLE_DRIVE_CONCURRENCY
    }
    
    fun getGoogleDriveConcurrencySync(): Int = runBlocking {
        googleDriveConcurrency.first()
    }
    
    suspend fun setGoogleDriveConcurrency(value: Int) {
        val valid = value.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
        context.uploadSettingsDataStore.edit { it[KEY_GOOGLE_DRIVE_CONCURRENCY] = valid }
    }
    
    // ==================== WebDAV Concurrency ====================
    
    val webdavConcurrency: Flow<Int> = context.uploadSettingsDataStore.data.map { preferences ->
        preferences[KEY_WEBDAV_CONCURRENCY] ?: DEFAULT_WEBDAV_CONCURRENCY
    }
    
    fun getWebdavConcurrencySync(): Int = runBlocking {
        webdavConcurrency.first()
    }
    
    suspend fun setWebdavConcurrency(value: Int) {
        val valid = value.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
        context.uploadSettingsDataStore.edit { it[KEY_WEBDAV_CONCURRENCY] = valid }
    }
    
    // ==================== Max Parallel Uploads ====================
    
    val maxParallelUploads: Flow<Int> = context.uploadSettingsDataStore.data.map { preferences ->
        preferences[KEY_MAX_PARALLEL_UPLOADS] ?: DEFAULT_MAX_PARALLEL
    }
    
    fun getMaxParallelUploadsSync(): Int = runBlocking {
        maxParallelUploads.first()
    }
    
    suspend fun setMaxParallelUploads(value: Int) {
        val valid = value.coerceIn(MIN_MAX_PARALLEL, MAX_MAX_PARALLEL)
        context.uploadSettingsDataStore.edit { it[KEY_MAX_PARALLEL_UPLOADS] = valid }
    }
    
    // ==================== Helper to get concurrency by remote type ====================
    
    /**
     * Get concurrency for a specific remote type.
     * Used by both UploadWorker and MultiRemoteUploadCoordinator.
     */
    fun getConcurrencyForRemoteType(remoteType: String): Int {
        return when (remoteType.lowercase()) {
            "s3" -> getS3ConcurrencySync()
            "google_drive" -> getGoogleDriveConcurrencySync()
            "webdav" -> getWebdavConcurrencySync()
            else -> DEFAULT_CONCURRENCY
        }
    }
    
    // ==================== Legacy Support ====================
    
    /**
     * Legacy flow - now maps to max parallel uploads
     */
    val uploadConcurrency: Flow<Int> = maxParallelUploads
    
    suspend fun getUploadConcurrencySync(): Int = maxParallelUploads.first()
    
    suspend fun setUploadConcurrency(concurrency: Int) {
        setMaxParallelUploads(concurrency)
    }
    
    // ==================== Display Helpers ====================
    
    fun getConcurrencyDisplayText(value: Int): String {
        return when (value) {
            1 -> "1 (Conservative)"
            2 -> "2 (Balanced)"
            3 -> "3 (Fast)"
            4 -> "4 (Faster)"
            5 -> "5 (Maximum)"
            else -> "$value"
        }
    }
    
    fun getMaxParallelDisplayText(value: Int): String {
        return when (value) {
            2 -> "2 (Conservative)"
            3 -> "3"
            4 -> "4 (Balanced - Default)"
            5 -> "5"
            6 -> "6 (Fast)"
            8 -> "8 (Maximum)"
            else -> "$value"
        }
    }
    
    fun getRemoteTypeDisplayName(remoteType: String): String {
        return when (remoteType.lowercase()) {
            "s3" -> "S3 / MinIO"
            "google_drive" -> "Google Drive"
            "webdav" -> "WebDAV"
            else -> remoteType
        }
    }
}

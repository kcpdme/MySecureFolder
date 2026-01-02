package com.kcpd.myfolder.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uploadSettingsDataStore by preferencesDataStore(name = "upload_settings")

/**
 * Repository for managing upload-related settings.
 * Stores preferences like connection concurrency in DataStore.
 */
@Singleton
class UploadSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_UPLOAD_CONCURRENCY = intPreferencesKey("upload_concurrency")
        
        // Default and valid range for concurrent uploads
        const val DEFAULT_CONCURRENCY = 2
        const val MIN_CONCURRENCY = 1
        const val MAX_CONCURRENCY = 5
        
        // Preset options for UI
        val CONCURRENCY_OPTIONS = listOf(1, 2, 3, 4, 5)
    }

    /**
     * Flow of the current upload concurrency setting
     */
    val uploadConcurrency: Flow<Int> = context.uploadSettingsDataStore.data.map { preferences ->
        preferences[KEY_UPLOAD_CONCURRENCY] ?: DEFAULT_CONCURRENCY
    }

    /**
     * Get the current upload concurrency synchronously
     */
    suspend fun getUploadConcurrencySync(): Int {
        return uploadConcurrency.first()
    }

    /**
     * Set the upload concurrency value
     * @param concurrency Number of concurrent uploads (1-5)
     */
    suspend fun setUploadConcurrency(concurrency: Int) {
        val validConcurrency = concurrency.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
        context.uploadSettingsDataStore.edit { preferences ->
            preferences[KEY_UPLOAD_CONCURRENCY] = validConcurrency
        }
    }

    /**
     * Get display text for a concurrency value
     */
    fun getConcurrencyDisplayText(value: Int): String {
        return when (value) {
            1 -> "1 (Sequential - Most stable)"
            2 -> "2 (Balanced - Default)"
            3 -> "3 (Faster)"
            4 -> "4 (Fast)"
            5 -> "5 (Maximum speed)"
            else -> "$value"
        }
    }
}

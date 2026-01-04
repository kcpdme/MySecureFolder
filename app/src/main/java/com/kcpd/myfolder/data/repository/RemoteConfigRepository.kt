package com.kcpd.myfolder.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kcpd.myfolder.domain.model.RemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing remote storage configurations.
 * Handles CRUD operations and persistence via DataStore.
 */
@Singleton
class RemoteConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.remoteConfigDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "remote_config_preferences"
    )

    private val dataStore = context.remoteConfigDataStore
    
    // Scope for maintaining cached state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val REMOTES_KEY = stringPreferencesKey("remotes_list")
        
        @Volatile
        private var INSTANCE: RemoteConfigRepository? = null
        
        /**
         * Get singleton instance for non-DI contexts (like WorkManager).
         * DI-injected instances are preferred when available.
         */
        fun getInstance(context: Context): RemoteConfigRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteConfigRepository(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    init {
        // Set singleton instance for non-DI access
        INSTANCE = this
    }
    
    /**
     * Get a specific remote by ID synchronously (uses cached data).
     * Safe for WorkManager use.
     */
    fun getRemoteByIdSync(id: String): RemoteConfig? {
        return _cachedRemotes.value.find { it.id == id }
    }
    
    // Cached list of all remotes - stays up to date automatically
    private val _cachedRemotes = getAllRemotesFlow()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Get all configured remotes as a Flow
     */
    fun getAllRemotesFlow(): Flow<List<RemoteConfig>> {
        return dataStore.data.map { preferences ->
            val jsonString = preferences[REMOTES_KEY] ?: "[]"
            try {
                json.decodeFromString<List<RemoteConfig>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Get all configured remotes (suspend function for one-time read)
     */
    suspend fun getAllRemotes(): List<RemoteConfig> {
        return getAllRemotesFlow().first()
    }

    /**
     * Get only active remotes as a Flow
     */
    fun getActiveRemotesFlow(): Flow<List<RemoteConfig>> {
        return getAllRemotesFlow().map { remotes ->
            remotes.filter { it.isActive }
        }
    }

    /**
     * Get only active remotes (suspend function)
     */
    suspend fun getActiveRemotes(): List<RemoteConfig> {
        return getActiveRemotesFlow().first()
    }
    
    /**
     * Get only active remotes synchronously (uses cached data)
     * Safe to call from any thread - returns cached value
     */
    fun getActiveRemotesSync(): List<RemoteConfig> {
        return _cachedRemotes.value.filter { it.isActive }
    }

    /**
     * Get a specific remote by ID
     */
    suspend fun getRemoteById(id: String): RemoteConfig? {
        return getAllRemotes().find { it.id == id }
    }

    /**
     * Add a new remote configuration
     */
    suspend fun addRemote(remote: RemoteConfig) {
        dataStore.edit { preferences ->
            val currentRemotes = getAllRemotes().toMutableList()
            currentRemotes.add(remote)
            preferences[REMOTES_KEY] = json.encodeToString(currentRemotes)
        }
    }

    /**
     * Update an existing remote configuration
     */
    suspend fun updateRemote(remote: RemoteConfig) {
        dataStore.edit { preferences ->
            val currentRemotes = getAllRemotes().toMutableList()
            val index = currentRemotes.indexOfFirst { it.id == remote.id }
            if (index != -1) {
                currentRemotes[index] = remote
                preferences[REMOTES_KEY] = json.encodeToString(currentRemotes)
            }
        }
    }

    /**
     * Delete a remote configuration
     */
    suspend fun deleteRemote(remoteId: String) {
        dataStore.edit { preferences ->
            val currentRemotes = getAllRemotes().toMutableList()
            currentRemotes.removeAll { it.id == remoteId }
            preferences[REMOTES_KEY] = json.encodeToString(currentRemotes)
        }
    }

    /**
     * Toggle active state of a remote
     */
    suspend fun toggleRemoteActive(remoteId: String) {
        dataStore.edit { preferences ->
            val currentRemotes = getAllRemotes().toMutableList()
            val index = currentRemotes.indexOfFirst { it.id == remoteId }
            if (index != -1) {
                val remote = currentRemotes[index]
                currentRemotes[index] = when (remote) {
                    is RemoteConfig.S3Remote -> remote.copy(isActive = !remote.isActive)
                    is RemoteConfig.GoogleDriveRemote -> remote.copy(isActive = !remote.isActive)
                    is RemoteConfig.WebDavRemote -> remote.copy(isActive = !remote.isActive)
                }
                preferences[REMOTES_KEY] = json.encodeToString(currentRemotes)
            }
        }
    }

    /**
     * Check if any active remotes are configured
     */
    suspend fun hasActiveRemotes(): Boolean {
        return getActiveRemotes().isNotEmpty()
    }

    /**
     * Get count of active remotes
     */
    suspend fun getActiveRemoteCount(): Int {
        return getActiveRemotes().size
    }

    /**
     * Clear all remote configurations (for testing/reset)
     */
    suspend fun clearAllRemotes() {
        dataStore.edit { preferences ->
            preferences.remove(REMOTES_KEY)
        }
    }

    /**
     * Import remotes from JSON string (for backup/restore)
     */
    suspend fun importRemotes(jsonString: String) {
        try {
            val remotes = json.decodeFromString<List<RemoteConfig>>(jsonString)
            dataStore.edit { preferences ->
                preferences[REMOTES_KEY] = json.encodeToString(remotes)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid remote configuration JSON", e)
        }
    }

    /**
     * Export remotes to JSON string (for backup/restore)
     */
    suspend fun exportRemotes(): String {
        return json.encodeToString(getAllRemotes())
    }
}

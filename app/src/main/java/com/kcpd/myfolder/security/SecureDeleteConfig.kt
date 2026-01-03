package com.kcpd.myfolder.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secureDeleteDataStore by preferencesDataStore(name = "secure_delete_config")

/**
 * Secure delete level options.
 * 
 * - QUICK: Single-pass random overwrite (default, good for SSDs/flash)
 * - DOD: DoD 5220.22-M 3-pass (zeros, ones, random)
 * - GUTMANN: 35-pass (maximum security, very slow, mainly theoretical)
 */
enum class SecureDeleteLevel(val passes: Int, val displayName: String) {
    QUICK(1, "Quick (1 pass)"),
    DOD(3, "DoD 5220.22-M (3 passes)"),
    GUTMANN(35, "Gutmann (35 passes)");

    companion object {
        fun fromString(value: String?): SecureDeleteLevel {
            return entries.find { it.name == value } ?: QUICK
        }
    }
}

/**
 * Repository for secure delete configuration.
 */
@Singleton
class SecureDeleteConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val levelKey = stringPreferencesKey("secure_delete_level")

    val secureDeleteLevel: Flow<SecureDeleteLevel> = context.secureDeleteDataStore.data.map { prefs ->
        SecureDeleteLevel.fromString(prefs[levelKey])
    }

    suspend fun setSecureDeleteLevel(level: SecureDeleteLevel) {
        context.secureDeleteDataStore.edit { prefs ->
            prefs[levelKey] = level.name
        }
    }
}

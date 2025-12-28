package com.kcpd.myfolder.security

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault Manager - Tella-inspired session-based access control.
 *
 * Pattern:
 * 1. User unlocks vault once with password
 * 2. Vault stays unlocked during app session
 * 3. Files decrypt on-demand (streaming, no batch decryption)
 * 4. Vault auto-locks when app goes to background
 * 5. User must re-enter password when app resumes
 *
 * Benefits:
 * - No need to decrypt hundreds of files upfront
 * - Each file decrypts only when accessed
 * - Session-based security (auto-lock on background)
 * - Configurable lock timeout
 */
@Singleton
class VaultManager @Inject constructor(
    private val passwordManager: PasswordManager,
    private val securityManager: SecurityManager,
    private val secureFileManager: SecureFileManager,
    private val application: Application
) : DefaultLifecycleObserver {

    companion object {
        private const val DEFAULT_LOCK_TIMEOUT_MS = 60_000L // 1 minute
        private const val IMMEDIATE_LOCK = 0L
        private const val NEVER_LOCK = -1L
        private const val PREFS_NAME = "vault_prefs"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }

    private val _vaultState = MutableStateFlow<VaultState>(VaultState.Locked)
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    private var lockTimeoutMs: Long = prefs.getLong(KEY_LOCK_TIMEOUT, DEFAULT_LOCK_TIMEOUT_MS)
    private var backgroundTimestamp: Long = 0

    init {
        // Observe app lifecycle for auto-lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Vault state - either Locked or Unlocked with session info
     */
    sealed class VaultState {
        object Locked : VaultState()
        data class Unlocked(
            val unlockedAt: Long = System.currentTimeMillis(),
            val autoLockEnabled: Boolean = true
        ) : VaultState()
    }

    /**
     * Unlocks the vault with password verification.
     * After unlocking, files can be accessed on-demand with streaming decryption.
     *
     * @param password User's password
     * @return true if password correct and vault unlocked, false otherwise
     */
    suspend fun unlock(password: String): Boolean {
        if (!passwordManager.isPasswordSet()) {
            // No password set - should not happen in normal flow
            return false
        }

        val isValid = passwordManager.verifyPassword(password)
        if (isValid) {
            _vaultState.value = VaultState.Unlocked(
                unlockedAt = System.currentTimeMillis(),
                autoLockEnabled = lockTimeoutMs != NEVER_LOCK
            )
        }
        return isValid
    }

    /**
     * Unlocks the vault after biometric verification.
     * This should only be called after successful biometric authentication.
     */
    fun unlockWithBiometric() {
        if (!isBiometricEnabled()) {
            android.util.Log.w("VaultManager", "Attempted biometric unlock but biometric not enabled")
            return
        }

        if (passwordManager.unlockWithBiometrics()) {
            _vaultState.value = VaultState.Unlocked(
                unlockedAt = System.currentTimeMillis(),
                autoLockEnabled = lockTimeoutMs != NEVER_LOCK
            )
        } else {
            android.util.Log.e("VaultManager", "Biometric unlock failed to load keys")
        }
    }

    /**
     * Changes the master password.
     * Re-encrypts file headers AND database with new key.
     *
     * Process:
     * 1. Verify old password
     * 2. Derive new Master Key (seed words remain unchanged)
     * 3. Re-wrap all file encryption keys (FEKs) with new Master Key
     * 4. Re-key the database with new database key (derived from new Master Key)
     * 5. Update stored credentials
     *
     * @param oldPass Current password
     * @param newPass New password
     * @return true if successful, false otherwise
     */
    suspend fun changePassword(oldPass: String, newPass: String): Boolean {
        // 1. Verify old password and ensure Old Master Key is loaded
        if (!passwordManager.verifyPassword(oldPass)) {
            android.util.Log.w("VaultManager", "Password change failed: incorrect old password")
            return false
        }

        try {
            val oldMasterKey = securityManager.getActiveMasterKey()

            // 2. Get Seed Words (they do NOT change)
            val seedWords = securityManager.loadStoredSeedWords() ?: run {
                android.util.Log.e("VaultManager", "Password change failed: seed words not found")
                return false
            }

            // 3. Derive New Master Key
            val newMasterKey = securityManager.deriveMasterKey(newPass, seedWords)

            android.util.Log.i("VaultManager", "Starting password change: re-wrapping files and re-keying database")

            // 4. Re-wrap all file headers (unwrap FEK with old key, re-wrap with new key)
            // This updates the encrypted FEK in each file's header without re-encrypting file bodies
            secureFileManager.reWrapAllFiles(oldMasterKey, newMasterKey)

            // 5. Re-key the database (change database encryption key)
            // This uses SQLCipher's PRAGMA rekey to re-encrypt the entire database
            withContext(Dispatchers.IO) {
                securityManager.rekeyDatabase(
                    context = application,
                    oldMasterKey = oldMasterKey,
                    newMasterKey = newMasterKey
                )
            }

            // 6. Update Credentials (store new Master Key encrypted by Keystore)
            securityManager.storeCredentials(newMasterKey, seedWords)
            securityManager.setActiveMasterKey(newMasterKey)

            android.util.Log.i("VaultManager", "Password changed successfully")
            return true
        } catch (e: Exception) {
            android.util.Log.e("VaultManager", "Failed to change password: ${e.message}", e)
            return false
        }
    }

    /**
     * Locks the vault immediately.
     * All file access will require unlocking again.
     */
    fun lock() {
        _vaultState.value = VaultState.Locked
    }

    /**
     * Checks if vault is currently unlocked.
     */
    fun isUnlocked(): Boolean {
        return _vaultState.value is VaultState.Unlocked
    }

    /**
     * Checks if vault is locked.
     */
    fun isLocked(): Boolean {
        return _vaultState.value is VaultState.Locked
    }

    /**
     * Sets the auto-lock timeout.
     *
     * @param timeoutMs Milliseconds before auto-lock (0 = immediate, -1 = never)
     */
    fun setLockTimeout(timeoutMs: Long) {
        lockTimeoutMs = timeoutMs
        prefs.edit().putLong(KEY_LOCK_TIMEOUT, timeoutMs).apply()
    }

    /**
     * Gets current lock timeout setting.
     */
    fun getLockTimeout(): Long = lockTimeoutMs

    /**
     * Enables or disables biometric authentication.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Checks if biometric authentication is enabled.
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Configures common lock timeout presets.
     */
    enum class LockTimeoutPreset(val milliseconds: Long, val displayName: String) {
        IMMEDIATE(IMMEDIATE_LOCK, "Immediately"),
        THIRTY_SECONDS(30_000L, "30 seconds"),
        ONE_MINUTE(60_000L, "1 minute"),
        FIVE_MINUTES(300_000L, "5 minutes"),
        FIFTEEN_MINUTES(900_000L, "15 minutes"),
        NEVER(NEVER_LOCK, "Never");

        companion object {
            fun fromMilliseconds(ms: Long): LockTimeoutPreset {
                return values().find { it.milliseconds == ms } ?: ONE_MINUTE
            }
        }
    }

    /**
     * Sets lock timeout using preset.
     */
    fun setLockTimeout(preset: LockTimeoutPreset) {
        setLockTimeout(preset.milliseconds)
    }

    // Lifecycle callbacks for auto-lock

    override fun onStop(owner: LifecycleOwner) {
        // App going to background
        backgroundTimestamp = System.currentTimeMillis()

        // Immediate lock if configured
        if (lockTimeoutMs == IMMEDIATE_LOCK && isUnlocked()) {
            lock()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // App returning to foreground
        if (!isUnlocked()) {
            return // Already locked
        }

        if (lockTimeoutMs == NEVER_LOCK) {
            return // Never auto-lock
        }

        // Check if timeout exceeded
        val timeInBackground = System.currentTimeMillis() - backgroundTimestamp
        if (timeInBackground > lockTimeoutMs) {
            lock()
        }
    }

    /**
     * Requires vault to be unlocked before executing action.
     * Throws SecurityException if vault is locked.
     *
     * Usage:
     * ```
     * vaultManager.requireUnlocked {
     *     // Access encrypted files
     * }
     * ```
     */
    inline fun <T> requireUnlocked(action: () -> T): T {
        if (isLocked()) {
            throw SecurityException("Vault is locked. Please unlock first.")
        }
        return action()
    }

    /**
     * Executes action only if vault is unlocked.
     * Returns null if vault is locked.
     *
     * Usage:
     * ```
     * val data = vaultManager.ifUnlocked {
     *     // Access encrypted files
     * }
     * ```
     */
    inline fun <T> ifUnlocked(action: () -> T): T? {
        return if (isUnlocked()) action() else null
    }
}

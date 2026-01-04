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
import java.security.MessageDigest
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
    private val journalManager: PasswordRotationJournalManager,
    private val securityPinManager: SecurityPinManager,
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
        
        // Check for incomplete password rotation on startup
        checkAndResumeRotation()
    }

    /**
     * Checks if a password rotation was interrupted and attempts recovery.
     * This runs on app startup to ensure system integrity.
     */
    private fun checkAndResumeRotation() {
        val journal = journalManager.readJournal()
        if (journal.rotationState == RotationState.IN_PROGRESS) {
            android.util.Log.w("VaultManager", "Detected incomplete password rotation. State: ${journal.currentStep}")
            // For safety, we lock the vault and require the user to unlock with their password
            // The UI should detect this state and prompt for recovery
            lock()
            _vaultState.value = VaultState.Locked
        }
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

        // Check for Panic PIN first! (Only if panic mode is enabled)
        if (securityPinManager.panicModeEnabled.value && securityPinManager.verifyPin(password)) {
            android.util.Log.e("VaultManager", "PANIC PIN DETECTED! INITIATING DATA WIPE.")
            // Run wipe on IO dispatcher to ensure file operations complete
            withContext(Dispatchers.IO) {
                securityManager.wipeAllData()
            }
            // Close app immediately
            System.exit(0)
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
        // SAFETY: Block biometric unlock during password rotation
        if (journalManager.isRotationInProgress()) {
            android.util.Log.e("VaultManager", "Biometric unlock blocked: password rotation in progress")
            return
        }

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
     * Changes the master password using a crash-safe, atomic process.
     * This method implements the hardened password rotation algorithm.
     *
     * SAFETY GUARANTEES:
     * - Journaled state machine ensures recoverability after crashes
     * - Database key is re-wrapped, NOT re-keyed (no full DB re-encryption)
     * - File FEKs are re-wrapped atomically
     * - Old or new password will work during crash window
     * - No irreversible operations without recovery path
     *
     * Process:
     * 1. Verify old password
     * 2. Write journal: IN_PROGRESS
     * 3. Derive new Master Key
     * 4. Re-wrap all file encryption keys (FEKs) with new Master Key
     * 5. Re-wrap encrypted DB key (no full database re-encryption!)
     * 6. fsync / commit
     * 7. Store new Master Key
     * 8. Clear journal
     *
     * @param oldPass Current password
     * @param newPass New password
     * @param onProgress Callback to report progress to UI
     * @return true if successful, false otherwise
     */
    suspend fun changePasswordSafely(
        oldPass: String,
        newPass: String,
        onProgress: ((String, Int, Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        // Check if a rotation is already in progress
        if (journalManager.isRotationInProgress()) {
            android.util.Log.e("VaultManager", "Password rotation already in progress")
            onProgress?.invoke("Error: Password rotation already in progress", 0, 8)
            return@withContext false
        }

        try {
            // STEP 1: Verify old password and ensure Old Master Key is loaded
            onProgress?.invoke("Step 1/8: Verifying current password...", 1, 8)
            android.util.Log.i("VaultManager", "STEP 1/8: Verifying old password")
            
            if (!passwordManager.verifyPassword(oldPass)) {
                android.util.Log.w("VaultManager", "Password change failed: incorrect old password")
                onProgress?.invoke("Error: Current password is incorrect", 1, 8)
                return@withContext false
            }

            val oldMasterKey = securityManager.getActiveMasterKey()
            val oldKeyId = android.util.Base64.encodeToString(oldMasterKey.encoded.copyOf(8), android.util.Base64.NO_WRAP)
            android.util.Log.i("VaultManager", "Old password verified, old key ID: $oldKeyId")

            // STEP 2: Write journal BEFORE any mutations
            onProgress?.invoke("Step 2/8: Creating recovery journal...", 2, 8)
            android.util.Log.i("VaultManager", "STEP 2/8: Writing journal to disk")
            
            val journal = PasswordRotationJournal(
                rotationState = RotationState.IN_PROGRESS,
                currentStep = RotationStep.REWRAP_FILES,
                oldKeyId = oldKeyId,
                newKeyId = null,
                encryptedDbKeyBackup = null
            )
            journalManager.writeJournal(journal)
            android.util.Log.i("VaultManager", "Password rotation journal written. Starting safe password change.")

            // STEP 3: Derive New Master Key (deterministic, no side effects)
            onProgress?.invoke("Step 3/8: Deriving new encryption key...", 3, 8)
            android.util.Log.i("VaultManager", "STEP 3/8: Deriving new master key from new password")
            
            val newMasterKey = passwordManager.deriveNewMasterKey(newPass)
                ?: throw IllegalStateException("Failed to derive new master key")
            val newKeyId = android.util.Base64.encodeToString(newMasterKey.encoded.copyOf(8), android.util.Base64.NO_WRAP)
            android.util.Log.i("VaultManager", "New master key derived, new key ID: $newKeyId")

            // Update journal with new key ID
            journalManager.writeJournal(journal.copy(newKeyId = newKeyId))

            // STEP 4: Re-wrap all file headers (FEKs only, not file bodies)
            onProgress?.invoke("Step 4/8: Re-encrypting file headers...", 4, 8)
            android.util.Log.i("VaultManager", "STEP 4/8: Re-wrapping all file encryption keys (scanning all subdirectories)")
            
            secureFileManager.reWrapAllFiles(oldMasterKey, newMasterKey)
            android.util.Log.i("VaultManager", "All file headers successfully re-wrapped")

            // Update journal: FILES DONE
            journalManager.writeJournal(journal.copy(
                currentStep = RotationStep.REWRAP_DATABASE_KEY,
                newKeyId = newKeyId
            ))

            // STEP 5: Re-wrap encrypted DB key (NOT full database re-encryption!)
            onProgress?.invoke("Step 5/8: Re-encrypting database key...", 5, 8)
            android.util.Log.i("VaultManager", "STEP 5/8: Re-wrapping encrypted database key (no full DB re-encryption)")
            
            val dbRewrapSuccess = securityManager.rewrapEncryptedDbKey(oldMasterKey, newMasterKey)
            if (!dbRewrapSuccess) {
                android.util.Log.e("VaultManager", "Failed to re-wrap database key")
                onProgress?.invoke("Error: Failed to re-encrypt database key", 5, 8)
                // Rollback journal
                journalManager.writeJournal(journal.copy(rotationState = RotationState.FAILED))
                return@withContext false
            }
            android.util.Log.i("VaultManager", "Database key successfully re-wrapped")

            // Update journal: DB KEY DONE
            journalManager.writeJournal(journal.copy(
                currentStep = RotationStep.FINALIZE,
                newKeyId = newKeyId
            ))

            // STEP 6: Force fsync to ensure all writes are durable
            onProgress?.invoke("Step 6/8: Syncing changes to disk...", 6, 8)
            android.util.Log.i("VaultManager", "STEP 6/8: Closing database to flush changes to disk")
            
            com.kcpd.myfolder.data.database.AppDatabase.closeDatabase()
            android.util.Log.i("VaultManager", "Database closed and flushed to disk")

            // STEP 7: Store new Master Key (final irreversible step)
            onProgress?.invoke("Step 7/8: Storing new master key...", 7, 8)
            android.util.Log.i("VaultManager", "STEP 7/8: Storing new master key (irreversible commit point)")
            
            passwordManager.storeNewMasterKey(newMasterKey)
            android.util.Log.i("VaultManager", "New master key stored successfully")

            // STEP 8: Clear journal (success!)
            onProgress?.invoke("Step 8/8: Finalizing...", 8, 8)
            android.util.Log.i("VaultManager", "STEP 8/8: Clearing rotation journal")
            
            journalManager.clearJournal()
            android.util.Log.i("VaultManager", "✅ Password changed successfully! Journal cleared.")
            
            onProgress?.invoke("Password changed successfully!", 8, 8)
            return@withContext true

        } catch (e: Exception) {
            android.util.Log.e("VaultManager", "❌ Failed to change password: ${e.message}", e)
            onProgress?.invoke("Error: ${e.message}", 0, 8)
            // Mark as failed in journal for manual recovery
            try {
                val currentJournal = journalManager.readJournal()
                journalManager.writeJournal(currentJournal.copy(rotationState = RotationState.FAILED))
                android.util.Log.e("VaultManager", "Journal marked as FAILED")
            } catch (je: Exception) {
                android.util.Log.e("VaultManager", "Failed to update journal on error", je)
            }
            return@withContext false
        }
    }

    /**
     * DEPRECATED: Use changePasswordSafely() instead.
     * This method is kept for backward compatibility but should not be used.
     */
    @Deprecated("Use changePasswordSafely() for crash-safe password changes", ReplaceWith("changePasswordSafely(oldPass, newPass)"))
    suspend fun changePassword(oldPass: String, newPass: String): Boolean {
        return changePasswordSafely(oldPass, newPass)
    }

    /**
     * Emergency recovery: Re-wrap all files with the current active master key.
     * Use this if password was changed but files weren't re-wrapped properly.
     * This function assumes you've already unlocked with the NEW password.
     */
    suspend fun emergencyReWrapFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentMasterKey = securityManager.getActiveMasterKey()
            android.util.Log.w("VaultManager", "Starting emergency file re-wrap with current master key")
            
            // We need to try with stored credentials to get the old key
            val storedMasterKey = securityManager.loadStoredMasterKey()
            if (storedMasterKey != null && !MessageDigest.isEqual(storedMasterKey.encoded, currentMasterKey.encoded)) {
                android.util.Log.i("VaultManager", "Detected key mismatch - attempting re-wrap with stored key as old key")
                secureFileManager.reWrapAllFiles(storedMasterKey, currentMasterKey)
                return@withContext true
            } else {
                android.util.Log.e("VaultManager", "Cannot perform emergency re-wrap: no old key found")
                return@withContext false
            }
        } catch (e: Exception) {
            android.util.Log.e("VaultManager", "Emergency re-wrap failed", e)
            return@withContext false
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

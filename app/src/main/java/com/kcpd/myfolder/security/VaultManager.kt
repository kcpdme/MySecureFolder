package com.kcpd.myfolder.security

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    application: Application
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
     * The actual password verification was already done during initial setup.
     */
    fun unlockWithBiometric() {
        if (!isBiometricEnabled()) {
            android.util.Log.w("VaultManager", "Attempted biometric unlock but biometric not enabled")
            return
        }

        _vaultState.value = VaultState.Unlocked(
            unlockedAt = System.currentTimeMillis(),
            autoLockEnabled = lockTimeoutMs != NEVER_LOCK
        )
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

package com.kcpd.myfolder.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the unified Security PIN feature.
 * 
 * The Security PIN is a numeric PIN that serves multiple purposes:
 * 1. Calculator Camouflage unlock - Type PIN + = on calculator to unlock vault
 * 2. Panic Wipe trigger - Entering this PIN at vault unlock triggers data wipe
 * 
 * Security:
 * - PIN is stored as SHA-256 hash
 * - PIN must be numeric (for calculator compatibility)
 * - Minimum 4 digits for security
 * - Can be reset using vault password
 */
@Singleton
class SecurityPinManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "security_pin_prefs"
        private const val KEY_PIN_HASH = "security_pin_hash"
        private const val KEY_PANIC_MODE_ENABLED = "panic_mode_enabled"
        private const val KEY_CAMOUFLAGE_ENABLED = "camouflage_enabled"
        
        const val MIN_PIN_LENGTH = 4
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _pinSet = MutableStateFlow(isPinSet())
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()
    
    private val _panicModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_PANIC_MODE_ENABLED, false))
    val panicModeEnabled: StateFlow<Boolean> = _panicModeEnabled.asStateFlow()
    
    private val _camouflageEnabled = MutableStateFlow(prefs.getBoolean(KEY_CAMOUFLAGE_ENABLED, false))
    val camouflageEnabled: StateFlow<Boolean> = _camouflageEnabled.asStateFlow()
    
    /**
     * Sets the Security PIN.
     * This PIN is used for both Calculator unlock and Panic wipe.
     * 
     * @param pin Numeric PIN (minimum 4 digits)
     * @throws IllegalArgumentException if PIN is too short or not numeric
     */
    fun setPin(pin: String) {
        require(pin.length >= MIN_PIN_LENGTH) { "PIN must be at least $MIN_PIN_LENGTH digits" }
        require(pin.all { it.isDigit() }) { "PIN must be numeric only" }
        
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
        _pinSet.value = true
        android.util.Log.i("SecurityPinManager", "Security PIN set successfully")
    }
    
    /**
     * Verifies if the provided PIN matches the stored Security PIN.
     * 
     * @param pin PIN to verify
     * @return true if PIN matches, false otherwise
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val candidateHash = hashPin(pin)
        return storedHash == candidateHash
    }
    
    /**
     * Checks if a Security PIN has been set.
     */
    fun isPinSet(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null) != null
    }
    
    /**
     * Clears the Security PIN and disables all features that depend on it.
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PANIC_MODE_ENABLED, false)
            .putBoolean(KEY_CAMOUFLAGE_ENABLED, false)
            .apply()
        _pinSet.value = false
        _panicModeEnabled.value = false
        _camouflageEnabled.value = false
    }
    
    /**
     * Enables or disables Panic Mode.
     * When enabled, entering the Security PIN at vault unlock triggers data wipe.
     * 
     * @throws IllegalStateException if PIN is not set
     */
    fun setPanicModeEnabled(enabled: Boolean) {
        if (enabled && !isPinSet()) {
            throw IllegalStateException("Security PIN must be set before enabling Panic Mode")
        }
        prefs.edit().putBoolean(KEY_PANIC_MODE_ENABLED, enabled).apply()
        _panicModeEnabled.value = enabled
    }
    
    /**
     * Enables or disables Calculator Camouflage.
     * When enabled, app appears as calculator and unlocks with Security PIN + =.
     * 
     * @throws IllegalStateException if PIN is not set
     */
    fun setCamouflageEnabled(enabled: Boolean) {
        if (enabled && !isPinSet()) {
            throw IllegalStateException("Security PIN must be set before enabling Camouflage")
        }
        prefs.edit().putBoolean(KEY_CAMOUFLAGE_ENABLED, enabled).apply()
        _camouflageEnabled.value = enabled
    }
    
    /**
     * Gets the stored PIN for display (requires prior authentication).
     * Returns null if PIN not set.
     * 
     * NOTE: This returns null - we don't store the actual PIN, only the hash.
     * To "view" the PIN, user must remember it or reset it.
     */
    fun canViewPin(): Boolean {
        // We can't actually view the PIN since we only store the hash
        // But we can allow user to reset it
        return false
    }
    
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

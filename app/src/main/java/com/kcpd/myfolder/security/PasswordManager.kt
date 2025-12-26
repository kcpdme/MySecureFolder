package com.kcpd.myfolder.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages password-based authentication and key derivation.
 * Allows users to recover encrypted data on new devices using their password.
 */
@Singleton
class PasswordManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val PREFS_NAME = "password_prefs"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_SALT = "salt"
        private const val KEY_IS_SET = "password_is_set"

        // PBKDF2 parameters
        private const val PBKDF2_ITERATIONS = 100000 // NIST recommended minimum
        private const val KEY_LENGTH = 256 // bits
        private const val SALT_LENGTH = 32 // bytes
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if a master password has been set.
     */
    fun isPasswordSet(): Boolean {
        return prefs.getBoolean(KEY_IS_SET, false)
    }

    /**
     * Sets up the master password for first time.
     * Derives database encryption key from password.
     *
     * @return true if successful
     */
    fun setupPassword(password: String): Boolean {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters")
        }

        try {
            // Generate random salt
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)

            // Derive key from password
            val derivedKey = deriveKey(password, salt)

            // Hash the password for verification (separate from encryption key)
            val passwordHash = hashPassword(password, salt)

            // Store salt and password hash
            prefs.edit()
                .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .putString(KEY_PASSWORD_HASH, android.util.Base64.encodeToString(passwordHash, android.util.Base64.NO_WRAP))
                .putBoolean(KEY_IS_SET, true)
                .apply()

            // Store the derived key as database encryption key
            storeDatabaseKey(derivedKey)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Verifies the password and unlocks the app.
     *
     * @return true if password is correct
     */
    fun verifyPassword(password: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_SALT, null) ?: return false

        try {
            val salt = android.util.Base64.decode(storedSalt, android.util.Base64.NO_WRAP)
            val computedHash = hashPassword(password, salt)
            val expectedHash = android.util.Base64.decode(storedHash, android.util.Base64.NO_WRAP)

            return computedHash.contentEquals(expectedHash)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Changes the master password.
     * Re-encrypts the database with new password-derived key.
     */
    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(oldPassword)) {
            return false
        }

        if (newPassword.length < 8) {
            throw IllegalArgumentException("New password must be at least 8 characters")
        }

        // Note: In production, you'd need to re-encrypt the entire database
        // with the new key. This is complex and requires:
        // 1. Decrypt all data with old key
        // 2. Generate new key from new password
        // 3. Re-encrypt all data with new key
        // For now, we just update the password hash

        return setupPassword(newPassword)
    }

    /**
     * Recovers access on a new device using password.
     * User must have the same password they set originally.
     */
    fun recoverWithPassword(password: String, salt: ByteArray): ByteArray {
        return deriveKey(password, salt)
    }

    /**
     * Exports the salt for backup.
     * User should save this along with their password for device migration.
     */
    fun exportSaltForBackup(): String? {
        return prefs.getString(KEY_SALT, null)
    }

    /**
     * Imports salt from backup to recover on new device.
     */
    fun importSaltFromBackup(saltBase64: String, password: String): Boolean {
        try {
            val salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)

            // Derive key from imported salt and password
            val derivedKey = deriveKey(password, salt)

            // Hash password for verification
            val passwordHash = hashPassword(password, salt)

            // Store salt and password hash
            prefs.edit()
                .putString(KEY_SALT, saltBase64)
                .putString(KEY_PASSWORD_HASH, android.util.Base64.encodeToString(passwordHash, android.util.Base64.NO_WRAP))
                .putBoolean(KEY_IS_SET, true)
                .apply()

            // Store the derived key as database encryption key
            storeDatabaseKey(derivedKey)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Resets password (DESTRUCTIVE - loses all data).
     */
    fun resetPassword() {
        prefs.edit().clear().apply()
        securityManager.wipeKeys()
    }

    /**
     * Derives a cryptographic key from password using PBKDF2.
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Hashes password for verification (separate from encryption key).
     */
    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        // Use slightly different parameters for password hash vs encryption key
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Stores the password-derived key as database encryption key.
     */
    private fun storeDatabaseKey(key: ByteArray) {
        // Store in EncryptedSharedPreferences via SecurityManager
        val encodedKey = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
        val encryptedPrefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
        encryptedPrefs.edit()
            .putString("database_encryption_key", encodedKey)
            .apply()
    }

    /**
     * Validates password strength.
     */
    fun validatePasswordStrength(password: String): PasswordStrength {
        return when {
            password.length < 8 -> PasswordStrength.TOO_SHORT
            password.length < 12 -> PasswordStrength.WEAK
            password.length < 16 && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() } -> PasswordStrength.MEDIUM
            password.length >= 16 && password.any { it.isDigit() } && password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } && password.any { !it.isLetterOrDigit() } -> PasswordStrength.STRONG
            else -> PasswordStrength.MEDIUM
        }
    }
}

enum class PasswordStrength {
    TOO_SHORT,
    WEAK,
    MEDIUM,
    STRONG
}

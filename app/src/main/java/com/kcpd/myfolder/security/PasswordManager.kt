package com.kcpd.myfolder.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
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
        private const val KEY_BACKUP_CODE_VERSION = "backup_code_version"

        // PBKDF2 parameters
        private const val PBKDF2_ITERATIONS = 100000 // NIST recommended minimum
        private const val KEY_LENGTH = 256 // bits
        private const val SALT_LENGTH = 32 // bytes

        // Backup code format version
        private const val BACKUP_CODE_VERSION = 1
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
     * Derives database and file encryption keys from password.
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

            // Derive master key from password using PBKDF2
            val masterKey = deriveKey(password, salt)

            // Derive file encryption key from master key using HKDF
            val fileKey = securityManager.deriveFileEncryptionKey(masterKey)

            // Derive database encryption key from master key using HKDF
            val dbKey = securityManager.deriveDatabaseEncryptionKey(masterKey)

            // Hash the master key for verification
            val passwordVerifier = hashMasterKey(masterKey)

            // Store salt, password verifier, and version
            prefs.edit()
                .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .putString(KEY_PASSWORD_HASH, android.util.Base64.encodeToString(passwordVerifier, android.util.Base64.NO_WRAP))
                .putBoolean(KEY_IS_SET, true)
                .putInt(KEY_BACKUP_CODE_VERSION, BACKUP_CODE_VERSION)
                .apply()

            // Store the derived keys
            storeFileEncryptionKey(fileKey)
            storeDatabaseKey(dbKey)

            // Set active keys in memory
            securityManager.setFileEncryptionKey(fileKey)
            securityManager.setDatabaseKey(dbKey)

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
            
            // Derive master key
            val masterKey = deriveKey(password, salt)
            
            // Compute verifier
            val computedVerifier = hashMasterKey(masterKey)
            val expectedVerifier = android.util.Base64.decode(storedHash, android.util.Base64.NO_WRAP)

            if (computedVerifier.contentEquals(expectedVerifier)) {
                // Password correct! Derive and set keys in memory.
                val fileKey = securityManager.deriveFileEncryptionKey(masterKey)
                val dbKey = securityManager.deriveDatabaseEncryptionKey(masterKey)
                
                securityManager.setFileEncryptionKey(fileKey)
                securityManager.setDatabaseKey(dbKey)
                return true
            }
            return false
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

            // Derive master key from imported salt and password
            val masterKey = deriveKey(password, salt)

            // Derive file and database keys
            val fileKey = securityManager.deriveFileEncryptionKey(masterKey)
            val dbKey = securityManager.deriveDatabaseEncryptionKey(masterKey)

            // Create verifier
            val passwordVerifier = hashMasterKey(masterKey)

            // Store salt and password verifier
            prefs.edit()
                .putString(KEY_SALT, saltBase64)
                .putString(KEY_PASSWORD_HASH, android.util.Base64.encodeToString(passwordVerifier, android.util.Base64.NO_WRAP))
                .putBoolean(KEY_IS_SET, true)
                .putInt(KEY_BACKUP_CODE_VERSION, BACKUP_CODE_VERSION)
                .apply()

            // Store the derived keys
            storeFileEncryptionKey(fileKey)
            storeDatabaseKey(dbKey)

            // Set active keys
            securityManager.setFileEncryptionKey(fileKey)
            securityManager.setDatabaseKey(dbKey)

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
     * Calculates SHA-256 hash of the master key for verification.
     */
    private fun hashMasterKey(masterKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(masterKey)
    }

    /**
     * Stores the password-derived key as database encryption key.
     */
    private fun storeDatabaseKey(key: ByteArray) {
        securityManager.storeDatabaseKey(key)
    }

    /**
     * Stores the password-derived file encryption key.
     */
    private fun storeFileEncryptionKey(key: SecretKey) {
        securityManager.storeFileEncryptionKey(key)
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

    /**
     * Gets the backup code for vault recovery.
     * User should save this securely - it's needed for password recovery if keystore fails.
     *
     * @return Backup code string (Base64-encoded JSON), or null if not set
     */
    fun getBackupCode(): String? {
        val salt = prefs.getString(KEY_SALT, null) ?: return null
        val version = prefs.getInt(KEY_BACKUP_CODE_VERSION, BACKUP_CODE_VERSION)

        val backupData = mapOf(
            "version" to version,
            "salt" to salt,
            "created" to System.currentTimeMillis()
        )

        // Convert to JSON and encode
        val json = backupData.entries.joinToString(",", "{", "}") { (k, v) ->
            when (v) {
                is String -> "\"$k\":\"$v\""
                else -> "\"$k\":$v"
            }
        }

        return android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    /**
     * Recovers encryption keys from password and backup code.
     * Used when Android Keystore is broken or on a new device.
     *
     * @param password User's password
     * @param backupCode Backup code obtained from getBackupCode()
     * @return KeyRecoveryResult indicating success or failure
     */
    fun recoverFromBackupCode(password: String, backupCode: String): KeyRecoveryResult {
        return try {
            // Decode backup code
            val json = String(android.util.Base64.decode(backupCode, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))

            // Parse JSON manually (simple key-value extraction)
            val salt64 = json.substringAfter("\"salt\":\"").substringBefore("\"")
            val salt = android.util.Base64.decode(salt64, android.util.Base64.NO_WRAP)

            // Derive master key from password
            val masterKey = deriveKey(password, salt)

            // Verify password first (if we have a stored hash)
            val storedHash = prefs.getString(KEY_PASSWORD_HASH, null)
            if (storedHash != null) {
                val computedVerifier = hashMasterKey(masterKey)
                val expectedVerifier = android.util.Base64.decode(storedHash, android.util.Base64.NO_WRAP)
                if (!computedVerifier.contentEquals(expectedVerifier)) {
                    return KeyRecoveryResult.WrongPassword
                }
            }

            // Derive file and database keys
            val fileKey = securityManager.deriveFileEncryptionKey(masterKey)
            val dbKey = securityManager.deriveDatabaseEncryptionKey(masterKey)

            // Store the recovered keys
            storeFileEncryptionKey(fileKey)
            storeDatabaseKey(dbKey)

            // Store salt and password verifier if not already set
            if (!isPasswordSet()) {
                val passwordVerifier = hashMasterKey(masterKey)
                prefs.edit()
                    .putString(KEY_SALT, salt64)
                    .putString(KEY_PASSWORD_HASH, android.util.Base64.encodeToString(passwordVerifier, android.util.Base64.NO_WRAP))
                    .putBoolean(KEY_IS_SET, true)
                    .putInt(KEY_BACKUP_CODE_VERSION, BACKUP_CODE_VERSION)
                    .apply()
            }

            // Set active keys in memory
            securityManager.setFileEncryptionKey(fileKey)
            securityManager.setDatabaseKey(dbKey)

            KeyRecoveryResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            KeyRecoveryResult.InvalidBackupCode
        }
    }

    /**
     * Attempts to recover keys from password only (when keystore fails but backup code not provided).
     * Uses stored salt.
     *
     * @param password User's password
     * @return KeyRecoveryResult indicating success or failure
     */
    fun recoverFromPassword(password: String): KeyRecoveryResult {
        return try {
            // Get stored salt
            val salt64 = prefs.getString(KEY_SALT, null) ?: return KeyRecoveryResult.NoBackupData
            val salt = android.util.Base64.decode(salt64, android.util.Base64.NO_WRAP)

            // Verify password
            if (!verifyPassword(password)) {
                return KeyRecoveryResult.WrongPassword
            }

            // Derive master key from password
            val masterKey = deriveKey(password, salt)

            // Derive file and database keys
            val fileKey = securityManager.deriveFileEncryptionKey(masterKey)
            val dbKey = securityManager.deriveDatabaseEncryptionKey(masterKey)

            // Store the recovered keys
            storeFileEncryptionKey(fileKey)
            storeDatabaseKey(dbKey)

            KeyRecoveryResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            KeyRecoveryResult.RecoveryFailed(e.message ?: "Unknown error")
        }
    }
}

enum class PasswordStrength {
    TOO_SHORT,
    WEAK,
    MEDIUM,
    STRONG
}

sealed class KeyRecoveryResult {
    object Success : KeyRecoveryResult()
    object WrongPassword : KeyRecoveryResult()
    object InvalidBackupCode : KeyRecoveryResult()
    object NoBackupData : KeyRecoveryResult()
    data class RecoveryFailed(val error: String) : KeyRecoveryResult()
}

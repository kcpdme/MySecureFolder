package com.kcpd.myfolder.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption keys and security operations for the app.
 * Uses Android Keystore for secure key storage and AES-256-GCM for encryption.
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ENCRYPTED_PREFS_NAME = "secure_prefs"
        private const val KEY_ALIAS = "myfolder_master_key"
        private const val DATABASE_KEY_PREF = "database_encryption_key"
        private const val FILE_KEY_PREF = "file_encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DATABASE_KEY_LENGTH = 32 // 256 bits

        // HKDF context strings for key derivation
        private const val HKDF_CONTEXT_FILE = "myfolder-file-encryption-v1"
        private const val HKDF_CONTEXT_DATABASE = "myfolder-database-encryption-v1"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Active file encryption key (in-memory)
    private var activeFileEncryptionKey: SecretKey? = null
    // Active database encryption key (in-memory)
    private var activeDatabaseKey: ByteArray? = null

    /**
     * Sets the active file encryption key (after password verification).
     */
    fun setFileEncryptionKey(key: SecretKey) {
        activeFileEncryptionKey = key
    }

    /**
     * Sets the active database encryption key (after password verification).
     */
    fun setDatabaseKey(key: ByteArray) {
        activeDatabaseKey = key
    }

    /**
     * Gets or generates the database encryption key.
     * This key is used by SQLCipher to encrypt the Room database.
     */
    fun getDatabaseKey(): ByteArray {
        // 1. Use active in-memory key if set
        activeDatabaseKey?.let { return it }

        // 2. Check if key already exists in encrypted preferences
        try {
            val existingKey = encryptedPrefs.getString(DATABASE_KEY_PREF, null)
            if (existingKey != null) {
                val key = android.util.Base64.decode(existingKey, android.util.Base64.NO_WRAP)
                activeDatabaseKey = key
                return key
            }
        } catch (e: Exception) {
            // Keystore broken
            e.printStackTrace()
        }

        // 3. Generate new random key (Only if not password protected yet? 
        // Or if we are setting up for first time?)
        // If we are here, it means no active key and no stored key (or stored key unreadable).
        // For fresh install, generating random key is fine.
        // For recovery, this method shouldn't be called until password is verified.
        
        val key = ByteArray(DATABASE_KEY_LENGTH)
        SecureRandom().nextBytes(key)

        // Store it encrypted (might fail if keystore broken)
        try {
            val encodedKey = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
            encryptedPrefs.edit().putString(DATABASE_KEY_PREF, encodedKey).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return key
    }

    /**
     * Stores the database encryption key.
     */
    fun storeDatabaseKey(key: ByteArray) {
        val encodedKey = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
        encryptedPrefs.edit().putString(DATABASE_KEY_PREF, encodedKey).apply()
    }

    /**
     * Gets the file encryption key to use.
     * Priorities:
     * 1. Active in-memory key (from password derivation)
     * 2. Stored key in EncryptedSharedPreferences (if available)
     * 3. Legacy Keystore key (fallback)
     */
    fun getFileEncryptionKey(): SecretKey {
        // 1. Use active in-memory key if set (best for recovery)
        activeFileEncryptionKey?.let { return it }

        // 2. Try to get from EncryptedSharedPreferences
        try {
            val encodedKey = encryptedPrefs.getString(FILE_KEY_PREF, null)
            if (encodedKey != null) {
                val keyBytes = android.util.Base64.decode(encodedKey, android.util.Base64.NO_WRAP)
                val key = SecretKeySpec(keyBytes, "AES")
                activeFileEncryptionKey = key // Cache it
                return key
            }
        } catch (e: Exception) {
            // Keystore might be broken or key invalidated
            e.printStackTrace()
        }

        // 3. Fallback to Legacy Keystore key (if it exists)
        // This is needed for migration or if we haven't switched yet
        return getLegacyKeystoreKey()
    }

    private fun getLegacyKeystoreKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        // If no key exists at all, we should probably generate one via PasswordManager
        // But for compatibility, we might need to generate a legacy one?
        // NO - new keys should only come from PasswordManager.
        throw IllegalStateException("No encryption key available. Please set up password.")
    }

    /**
     * Encrypts data using AES-256-GCM.
     * Returns: IV (12 bytes) + Encrypted data + Auth tag (16 bytes)
     */
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = getFileEncryptionKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv // 12 bytes for GCM
        val encryptedData = cipher.doFinal(data)

        // Combine IV + encrypted data
        return iv + encryptedData
    }

    /**
     * Decrypts data encrypted with encrypt().
     * Expects: IV (12 bytes) + Encrypted data + Auth tag (16 bytes)
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = getFileEncryptionKey()

        // Extract IV (first 12 bytes)
        val iv = encryptedData.copyOfRange(0, IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(IV_LENGTH, encryptedData.size)

        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a string to Base64-encoded encrypted data.
     */
    fun encryptString(plaintext: String): String {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded encrypted string.
     */
    fun decryptString(encryptedBase64: String): String {
        val encrypted = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
        val decrypted = decrypt(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Checks if the app has been initialized with encryption keys.
     */
    fun isInitialized(): Boolean {
        return encryptedPrefs.contains(DATABASE_KEY_PREF)
    }

    /**
     * Wipes all security keys (for logout/reset).
     * WARNING: This will make all encrypted data unrecoverable.
     */
    fun wipeKeys() {
        // Clear encrypted preferences
        encryptedPrefs.edit().clear().apply()

        // Remove key from Android Keystore
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // Key might not exist
        }
    }

    /**
     * Derives a file encryption key from the master key using HKDF.
     * This allows password-based recovery of file encryption keys.
     *
     * @param masterKey The master key derived from user password via PBKDF2
     * @return SecretKey for file encryption
     */
    fun deriveFileEncryptionKey(masterKey: ByteArray): SecretKey {
        val derivedKey = hkdf(masterKey, HKDF_CONTEXT_FILE.toByteArray(), 32)
        return SecretKeySpec(derivedKey, "AES")
    }

    /**
     * Derives a database encryption key from the master key using HKDF.
     *
     * @param masterKey The master key derived from user password via PBKDF2
     * @return ByteArray for SQLCipher database encryption
     */
    fun deriveDatabaseEncryptionKey(masterKey: ByteArray): ByteArray {
        return hkdf(masterKey, HKDF_CONTEXT_DATABASE.toByteArray(), 32)
    }

    /**
     * Stores the password-derived file encryption key.
     * Key is stored encrypted in EncryptedSharedPreferences.
     *
     * @param fileKey The file encryption key to store
     */
    fun storeFileEncryptionKey(fileKey: SecretKey) {
        val encodedKey = android.util.Base64.encodeToString(fileKey.encoded, android.util.Base64.NO_WRAP)
        encryptedPrefs.edit()
            .putString(FILE_KEY_PREF, encodedKey)
            .apply()
    }

    /**
     * Gets the stored file encryption key (Legacy/Helper method).
     * @return SecretKey for file encryption, or null if not available
     */
    fun getStoredFileEncryptionKey(): SecretKey? {
        return try {
            val encodedKey = encryptedPrefs.getString(FILE_KEY_PREF, null) ?: return null
            val keyBytes = android.util.Base64.decode(encodedKey, android.util.Base64.NO_WRAP)
            SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the file encryption key exists in storage.
     *
     * @return true if file key is stored
     */
    fun hasFileEncryptionKey(): Boolean {
        return try {
            encryptedPrefs.contains(FILE_KEY_PREF)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * HKDF (HMAC-based Key Derivation Function) implementation.
     * Used to derive multiple keys from a single master key.
     *
     * Based on RFC 5869: https://tools.ietf.org/html/rfc5869
     *
     * @param ikm Input keying material (master key)
     * @param info Context and application specific information
     * @param length Desired output length in bytes
     * @return Derived key
     */
    private fun hkdf(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
        // We use a fixed salt (all zeros) since our IKM is already high-entropy (from PBKDF2)
        val salt = ByteArray(32) // 32 bytes of zeros
        val prk = hmacSha256(salt, ikm)

        // HKDF-Expand: OKM = HMAC-Hash(PRK, T(0) | info | 0x01)
        val okm = ByteArray(length)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        var t = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()

            val copyLength = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, copyLength)
            offset += copyLength
            counter++
        }

        return okm
    }

    /**
     * Computes HMAC-SHA256.
     *
     * @param key HMAC key
     * @param data Data to authenticate
     * @return HMAC output
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

package com.kcpd.myfolder.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DATABASE_KEY_LENGTH = 32 // 256 bits
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

    /**
     * Gets or generates the database encryption key.
     * This key is used by SQLCipher to encrypt the Room database.
     */
    fun getDatabaseKey(): ByteArray {
        // Check if key already exists in encrypted preferences
        val existingKey = encryptedPrefs.getString(DATABASE_KEY_PREF, null)
        if (existingKey != null) {
            return android.util.Base64.decode(existingKey, android.util.Base64.NO_WRAP)
        }

        // Generate new random key
        val key = ByteArray(DATABASE_KEY_LENGTH)
        SecureRandom().nextBytes(key)

        // Store it encrypted
        val encodedKey = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
        encryptedPrefs.edit().putString(DATABASE_KEY_PREF, encodedKey).apply()

        return key
    }

    /**
     * Gets or creates the AES key for file encryption from Android Keystore.
     */
    private fun getOrCreateFileEncryptionKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        // Try to get existing key
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        // Generate new key in Keystore
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Can be enabled for extra security
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts data using AES-256-GCM.
     * Returns: IV (12 bytes) + Encrypted data + Auth tag (16 bytes)
     */
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = getOrCreateFileEncryptionKey()
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
        val key = getOrCreateFileEncryptionKey()

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
}

package com.kcpd.myfolder.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption keys and security operations for the app.
 * Implements the "Hybrid" security model using Argon2id for key derivation
 * and Envelope Encryption for file security.
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ENCRYPTED_PREFS_NAME = "secure_prefs"
        private const val KEY_STORED_MASTER_KEY = "stored_master_key" // Encrypted by Keystore
        private const val KEY_STORED_SEED_WORDS = "stored_seed_words" // Encrypted by Keystore
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val MASTER_KEY_LENGTH = 32 // 256 bits

        // HKDF context string for database key derivation
        private const val HKDF_CONTEXT_DATABASE = "myfolder-database-encryption-v1"
    }

    private val argon2 = Argon2Kt()

    private val masterKeyAlias: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // EncryptedSharedPreferences uses Android Keystore to encrypt values
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Active Master Key (held in memory while unlocked)
    private var activeMasterKey: SecretKey? = null

    /**
     * Sets the active master key (after successful authentication/recovery).
     */
    fun setActiveMasterKey(key: SecretKey) {
        activeMasterKey = key
    }

    /**
     * Gets the active master key.
     * @throws IllegalStateException if the vault is locked.
     */
    fun getActiveMasterKey(): SecretKey {
        return activeMasterKey ?: throw IllegalStateException("Vault is locked. Master key not available.")
    }

    /**
     * Checks if the vault is unlocked (Master Key is in memory).
     */
    fun isUnlocked(): Boolean {
        return activeMasterKey != null
    }

    /**
     * Derives the Master Key from Password and Seed Words using Argon2id.
     * 
     * @param password User's password
     * @param seedWords List of 12 BIP39 seed words
     * @return 32-byte Master Key
     */
    fun deriveMasterKey(password: String, seedWords: List<String>): SecretKey {
        // Salt = SHA-256(SeedWords)
        // The Seed Words act as the high-entropy global salt.
        val seedString = seedWords.joinToString(" ")
        val salt = MessageDigest.getInstance("SHA-256").digest(seedString.toByteArray(Charsets.UTF_8))

        // Argon2id Derivation
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.toCharArray(),
            salt = salt,
            tCostInIterations = 3, // Recommended balance for mobile
            mCostInKibibytes = 64 * 1024 // 64 MB
        )

        return SecretKeySpec(result.rawHashAsByteArray(), "AES")
    }

    /**
     * Wraps (Encrypts) a File Encryption Key (FEK) using the Master Key.
     *
     * @param fek The random File Encryption Key
     * @param masterKey The Master Key
     * @return Pair of (IV, EncryptedFEK)
     */
    fun wrapFEK(fek: SecretKey, masterKey: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        // Generate random IV for this wrapping
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec)
        
        val encryptedBytes = cipher.doFinal(fek.encoded)
        
        return Pair(iv, encryptedBytes)
    }

    /**
     * Unwraps (Decrypts) a File Encryption Key (FEK).
     *
     * @param encryptedFek The encrypted FEK bytes
     * @param iv The IV used for encryption
     * @param masterKey The Master Key
     * @return The original FEK
     */
    fun unwrapFEK(encryptedFek: ByteArray, iv: ByteArray, masterKey: SecretKey): SecretKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        
        val decryptedBytes = cipher.doFinal(encryptedFek)
        
        return SecretKeySpec(decryptedBytes, "AES")
    }

    /**
     * Stores the Master Key and Seed Words in EncryptedSharedPreferences (KeyStore backed).
     * This allows convenience unlock via Biometrics/PIN without re-entering password.
     */
    fun storeCredentials(masterKey: SecretKey, seedWords: List<String>) {
        val masterKeyBase64 = android.util.Base64.encodeToString(masterKey.encoded, android.util.Base64.NO_WRAP)
        val seedWordsString = seedWords.joinToString(" ")
        
        encryptedPrefs.edit()
            .putString(KEY_STORED_MASTER_KEY, masterKeyBase64)
            .putString(KEY_STORED_SEED_WORDS, seedWordsString)
            .apply()
    }

    /**
     * Tries to load the Master Key from secure storage (e.g., after Biometric auth).
     * @return The Master Key if available, null otherwise.
     */
    fun loadStoredMasterKey(): SecretKey? {
        val base64 = encryptedPrefs.getString(KEY_STORED_MASTER_KEY, null) ?: return null
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            SecretKeySpec(bytes, "AES")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads the stored seed words.
     */
    fun loadStoredSeedWords(): List<String>? {
        val seedString = encryptedPrefs.getString(KEY_STORED_SEED_WORDS, null) ?: return null
        return seedString.split(" ")
    }

    /**
     * Checks if credentials are stored (i.e., app is set up).
     */
    fun isSetup(): Boolean {
        return encryptedPrefs.contains(KEY_STORED_MASTER_KEY)
    }

    /**
     * Wipes all stored keys from memory and storage.
     */
    fun wipeKeys() {
        activeMasterKey = null
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Derives a database encryption key from the Master Key.
     * We use HKDF to derive a sub-key so the Master Key itself isn't used for DB.
     */
    fun deriveDatabaseKey(masterKey: SecretKey): ByteArray {
        // Use HKDF to derive DB key
        return hkdf(masterKey.encoded, HKDF_CONTEXT_DATABASE.toByteArray(), 32)
    }

    /**
     * HKDF implementation for internal key derivation.
     */
    private fun hkdf(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val salt = ByteArray(32) // Fixed zero salt as IKM is already high entropy
        val prk = hmacSha256(salt, ikm)

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

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

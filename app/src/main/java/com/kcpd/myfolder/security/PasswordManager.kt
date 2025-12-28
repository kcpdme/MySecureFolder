package com.kcpd.myfolder.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages password-based authentication and key derivation using the "Hybrid" model.
 * Handles BIP39 Seed Word generation and recovery manually.
 */
@Singleton
class PasswordManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val ENTROPY_BITS = 128
        private const val CHECKSUM_BITS = 4 // ENTROPY_BITS / 32
        private const val WORD_COUNT = 12
    }

    private val wordList: List<String> by lazy {
        try {
            val resId = context.resources.getIdentifier("bip39_english", "raw", context.packageName)
            if (resId == 0) throw IllegalStateException("bip39_english.txt not found in raw resources")
            
            context.resources.openRawResource(resId).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readLines()
            }
        } catch (e: Exception) {
            android.util.Log.e("PasswordManager", "Failed to load wordlist", e)
            emptyList()
        }
    }

    /**
     * Checks if the vault is set up (Master Key and Seed Words stored).
     */
    fun isPasswordSet(): Boolean {
        return securityManager.isSetup()
    }

    /**
     * Generates a new set of 12 BIP39 seed words.
     */
    fun generateSeedWords(): List<String> {
        val entropy = ByteArray(ENTROPY_BITS / 8)
        SecureRandom().nextBytes(entropy)
        return entropyToMnemonic(entropy)
    }

    /**
     * Validates a list of seed words.
     */
    fun validateSeedWords(words: List<String>): Boolean {
        if (words.size != WORD_COUNT) return false
        if (wordList.isEmpty()) return false // Should not happen
        if (!words.all { it in wordList }) return false
        return verifyChecksum(words)
    }

    /**
     * Sets up the vault with a password and seed words.
     * Derives Master Key and stores credentials securely.
     */
    suspend fun setupPassword(password: String, seedWords: List<String>): Boolean = withContext(Dispatchers.Default) {
        if (password.length < 8) throw IllegalArgumentException("Password must be at least 8 characters")
        if (!validateSeedWords(seedWords)) throw IllegalArgumentException("Invalid seed words")

        try {
            // Derive Master Key using Argon2id
            val masterKey = securityManager.deriveMasterKey(password, seedWords)

            // Store credentials (encrypted by Android Keystore)
            securityManager.storeCredentials(masterKey, seedWords)

            // Set active key
            securityManager.setActiveMasterKey(masterKey)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Verifies the password and unlocks the vault.
     * Uses the stored seed words to re-derive the key and compare with stored key.
     */
    suspend fun verifyPassword(password: String): Boolean = withContext(Dispatchers.Default) {
        try {
            // Load stored credentials
            val storedMasterKey = securityManager.loadStoredMasterKey() ?: return@withContext false
            val storedSeedWords = securityManager.loadStoredSeedWords() ?: return@withContext false

            // Derive candidate key
            val candidateKey = securityManager.deriveMasterKey(password, storedSeedWords)

            // Compare keys
            if (MessageDigest.isEqual(candidateKey.encoded, storedMasterKey.encoded)) {
                // Success - set active key
                securityManager.setActiveMasterKey(storedMasterKey)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Unlocks the vault using stored credentials (e.g. after Biometric auth).
     * No password required if Keystore unlocks successfully.
     */
    fun unlockWithBiometrics(): Boolean {
        return try {
            val storedMasterKey = securityManager.loadStoredMasterKey() ?: return false
            securityManager.setActiveMasterKey(storedMasterKey)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Recovers the vault using Password and Seed Words.
     * Used when Keystore is broken or on a new device.
     */
    suspend fun recoverVault(password: String, seedWords: List<String>): Boolean = withContext(Dispatchers.Default) {
        if (!validateSeedWords(seedWords)) return@withContext false

        try {
            // Re-derive Master Key (Deterministic)
            val masterKey = securityManager.deriveMasterKey(password, seedWords)

            // Store new credentials (re-encrypts with current Keystore)
            securityManager.storeCredentials(masterKey, seedWords)

            // Set active key
            securityManager.setActiveMasterKey(masterKey)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Derives a new master key from a new password and existing seed words.
     * This DOES NOT store or activate the new key.
     * It is intended for use by VaultManager during a safe password rotation.
     */
    internal suspend fun deriveNewMasterKey(newPassword: String): SecretKey? = withContext(Dispatchers.Default) {
        if (newPassword.length < 8) throw IllegalArgumentException("New password too short")
        val seedWords = securityManager.loadStoredSeedWords() ?: return@withContext null
        securityManager.deriveMasterKey(newPassword, seedWords)
    }

    /**
     * Stores the new master key and updates the active key.
     * This is the final step of a password change, controlled by VaultManager.
     */
    internal fun storeNewMasterKey(newMasterKey: SecretKey) {
        val seedWords = securityManager.loadStoredSeedWords()
            ?: throw IllegalStateException("Cannot store new master key without seed words.")
        securityManager.storeCredentials(newMasterKey, seedWords)
        securityManager.setActiveMasterKey(newMasterKey)
    }

    /**
     * Gets the stored seed words for backup/display.
     */
    fun getSeedWords(): List<String>? {
        return securityManager.loadStoredSeedWords()
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

    // --- Panic PIN Implementation ---

    /**
     * Sets the Panic PIN.
     * We store a simple SHA-256 hash of the PIN since it's just for matching,
     * and it's stored in EncryptedSharedPreferences.
     */
    fun setPanicPin(pin: String) {
        if (pin.length < 4) throw IllegalArgumentException("Panic PIN must be at least 4 digits")
        val hash = hashPin(pin)
        securityManager.storePanicPinHash(hash)
    }

    /**
     * Checks if the provided PIN matches the stored Panic PIN.
     */
    fun verifyPanicPin(pin: String): Boolean {
        val storedHash = securityManager.getPanicPinHash() ?: return false
        val candidateHash = hashPin(pin)
        return storedHash == candidateHash
    }

    /**
     * Checks if a Panic PIN is set.
     */
    fun isPanicPinSet(): Boolean {
        return securityManager.getPanicPinHash() != null
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // --- BIP39 Implementation ---

    private fun entropyToMnemonic(entropy: ByteArray): List<String> {
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = getBits(hash, CHECKSUM_BITS)
        
        val entropyBits = bytesToBits(entropy)
        val combinedBits = entropyBits + checksumBits
        
        return combinedBits.chunked(11).map { wordList[bitsToInt(it)] }
    }

    private fun verifyChecksum(words: List<String>): Boolean {
        val indices = words.map { wordList.indexOf(it) }
        val combinedBits = indices.flatMap { intToBits(it, 11) }
        
        val entropyBits = combinedBits.take(ENTROPY_BITS)
        val checksumBits = combinedBits.takeLast(CHECKSUM_BITS)
        
        val entropy = bitsToBytes(entropyBits)
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val expectedChecksum = getBits(hash, CHECKSUM_BITS)
        
        return checksumBits == expectedChecksum
    }

    private fun bytesToBits(bytes: ByteArray): List<Boolean> {
        val bits = mutableListOf<Boolean>()
        for (byte in bytes) {
            for (i in 7 downTo 0) {
                bits.add((byte.toInt() shr i and 1) == 1)
            }
        }
        return bits
    }

    private fun bitsToBytes(bits: List<Boolean>): ByteArray {
        val bytes = ByteArray(bits.size / 8)
        for (i in bytes.indices) {
            var byte = 0
            for (j in 0 until 8) {
                if (bits[i * 8 + j]) {
                    byte = byte or (1 shl (7 - j))
                }
            }
            bytes[i] = byte.toByte()
        }
        return bytes
    }

    private fun intToBits(value: Int, length: Int): List<Boolean> {
        val bits = mutableListOf<Boolean>()
        for (i in length - 1 downTo 0) {
            bits.add((value shr i and 1) == 1)
        }
        return bits
    }

    private fun bitsToInt(bits: List<Boolean>): Int {
        var value = 0
        for (bit in bits) {
            value = (value shl 1) or (if (bit) 1 else 0)
        }
        return value
    }

    private fun getBits(bytes: ByteArray, count: Int): List<Boolean> {
        return bytesToBits(bytes).take(count)
    }
}

enum class PasswordStrength {
    TOO_SHORT,
    WEAK,
    MEDIUM,
    STRONG
}

package com.kcpd.myfolder.security

import android.content.Context
import cash.z.ecc.android.bip39.Mnemonics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages password-based authentication and key derivation using the "Hybrid" model.
 * Handles BIP39 Seed Word generation and recovery.
 */
@Singleton
class PasswordManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    companion object {
        // ENTROPY_BITS = 128 for 12 words
        private const val ENTROPY_BITS = 128
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
        val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordList.ENGLISH)
        // Generate 128 bits of entropy -> 12 words
        val mnemonic = mnemonicCode.make(ENTROPY_BITS)
        return String(mnemonic.chars).split(" ")
    }

    /**
     * Validates a list of seed words.
     */
    fun validateSeedWords(words: List<String>): Boolean {
        if (words.size != 12) return false
        try {
            val mnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordList.ENGLISH)
            mnemonicCode.validate(Mnemonics.Mnemonic(words.joinToString(" ").toCharArray()))
            return true
        } catch (e: Exception) {
            return false
        }
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
     * Changes the password.
     * Note: Seed words do not change.
     * Requires re-wrapping all file encryption keys (implemented in caller or separate use case).
     * This method only updates the Master Key and storage.
     * 
     * @return The new Master Key, which the caller must use to re-wrap FEKs.
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): SecretKey? = withContext(Dispatchers.Default) {
        if (!verifyPassword(oldPassword)) return@withContext null
        if (newPassword.length < 8) throw IllegalArgumentException("New password too short")

        val seedWords = securityManager.loadStoredSeedWords() ?: return@withContext null
        
        // Derive NEW Master Key
        val newMasterKey = securityManager.deriveMasterKey(newPassword, seedWords)
        
        // Update storage
        securityManager.storeCredentials(newMasterKey, seedWords)
        securityManager.setActiveMasterKey(newMasterKey)
        
        newMasterKey
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
}

enum class PasswordStrength {
    TOO_SHORT,
    WEAK,
    MEDIUM,
    STRONG
}

package com.kcpd.myfolder.ui.auth

import androidx.lifecycle.ViewModel
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.PasswordStrength
import com.kcpd.myfolder.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PasswordSetupViewModel @Inject constructor(
    private val passwordManager: PasswordManager,
    private val vaultManager: VaultManager
) : ViewModel() {

    fun isPasswordSet(): Boolean {
        return passwordManager.isPasswordSet()
    }

    suspend fun setupPassword(password: String): Boolean {
        // Generate new seed words for fresh setup
        val seedWords = passwordManager.generateSeedWords()
        return passwordManager.setupPassword(password, seedWords)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        return vaultManager.changePassword(currentPassword, newPassword)
    }

    fun getPasswordStrength(password: String): PasswordStrength {
        return passwordManager.validatePasswordStrength(password)
    }

    // Deprecated in new design, but keeping for compatibility if needed? 
    // New design uses Seed Words.
    fun exportSaltForBackup(): String? {
        // We can export Seed Words instead? 
        // SecurityManager.loadStoredSeedWords() needs to be called.
        // But logic is in PasswordManager/SecurityManager.
        // For now returning null or dummy as salt backup is replaced by seed words.
        return null
    }

    suspend fun recoverFromBackup(password: String, backupCode: String): Boolean {
        // Treat backupCode as space-separated seed words
        val seedWords = backupCode.trim().split("\\s+".toRegex())
        if (seedWords.size != 12) return false
        return passwordManager.recoverVault(password, seedWords)
    }
}

package com.kcpd.myfolder.ui.auth

import androidx.lifecycle.ViewModel
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.PasswordStrength
import com.kcpd.myfolder.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PasswordSetupViewModel @Inject constructor(
    private val passwordManager: PasswordManager,
    private val vaultManager: VaultManager
) : ViewModel() {

    private val _passwordChangeProgress = MutableStateFlow<PasswordChangeProgress?>(null)
    val passwordChangeProgress: StateFlow<PasswordChangeProgress?> = _passwordChangeProgress.asStateFlow()

    data class PasswordChangeProgress(
        val message: String,
        val currentStep: Int,
        val totalSteps: Int
    )

    fun isPasswordSet(): Boolean {
        return passwordManager.isPasswordSet()
    }

    /**
     * Generate new seed words without setting up the password yet.
     * Used in the seed generation screen.
     */
    fun generateSeedWords(): List<String> {
        return passwordManager.generateSeedWords()
    }

    /**
     * Setup password with pre-generated seed words.
     * Called after user has backed up their seed words.
     */
    suspend fun setupPasswordWithSeed(password: String, seedWords: List<String>): Boolean {
        return passwordManager.setupPassword(password, seedWords)
    }

    suspend fun setupPassword(password: String): Boolean {
        // Generate new seed words for fresh setup
        val seedWords = passwordManager.generateSeedWords()
        return passwordManager.setupPassword(password, seedWords)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        return vaultManager.changePasswordSafely(currentPassword, newPassword) { message, step, total ->
            _passwordChangeProgress.value = PasswordChangeProgress(message, step, total)
        }
    }

    fun clearProgress() {
        _passwordChangeProgress.value = null
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

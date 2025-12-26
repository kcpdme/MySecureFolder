package com.kcpd.myfolder.ui.auth

import androidx.lifecycle.ViewModel
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.PasswordStrength
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PasswordSetupViewModel @Inject constructor(
    private val passwordManager: PasswordManager
) : ViewModel() {

    fun isPasswordSet(): Boolean {
        return passwordManager.isPasswordSet()
    }

    fun setupPassword(password: String): Boolean {
        return passwordManager.setupPassword(password)
    }

    fun getPasswordStrength(password: String): PasswordStrength {
        return passwordManager.validatePasswordStrength(password)
    }

    fun exportSaltForBackup(): String? {
        return passwordManager.exportSaltForBackup()
    }
}

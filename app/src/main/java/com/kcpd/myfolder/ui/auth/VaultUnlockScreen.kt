package com.kcpd.myfolder.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.security.BiometricManager
import com.kcpd.myfolder.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultUnlockViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUnlockUiState())
    val uiState: StateFlow<VaultUnlockUiState> = _uiState.asStateFlow()

    data class VaultUnlockUiState(
        val password: String = "",
        val isPasswordVisible: Boolean = false,
        val isUnlocking: Boolean = false,
        val error: String? = null,
        val isUnlocked: Boolean = false
    )

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            error = null
        )
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            isPasswordVisible = !_uiState.value.isPasswordVisible
        )
    }

    fun unlock() {
        val password = _uiState.value.password

        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your password")
            return
        }

        _uiState.value = _uiState.value.copy(isUnlocking = true, error = null)

        viewModelScope.launch {
            val success = vaultManager.unlock(password)

            if (success) {
                _uiState.value = _uiState.value.copy(
                    isUnlocking = false,
                    isUnlocked = true,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isUnlocking = false,
                    error = "Incorrect password",
                    password = "" // Clear password on failure
                )
            }
        }
    }

    fun getLockTimeoutDisplay(): String {
        val preset = VaultManager.LockTimeoutPreset.fromMilliseconds(
            vaultManager.getLockTimeout()
        )
        return "Auto-lock: ${preset.displayName}"
    }

    fun isBiometricEnabled(): Boolean {
        val vaultEnabled = vaultManager.isBiometricEnabled()
        val canUse = biometricManager.canUseBiometric()
        android.util.Log.d("VaultUnlockViewModel", "Biometric check: vaultEnabled=$vaultEnabled, canUse=$canUse")
        return vaultEnabled && canUse
    }

    fun unlockWithBiometric(activity: FragmentActivity) {
        android.util.Log.d("VaultUnlockViewModel", "unlockWithBiometric() called")
        biometricManager.authenticate(
            activity = activity,
            title = "Unlock Vault",
            subtitle = "Use biometric to unlock",
            onSuccess = {
                android.util.Log.d("VaultUnlockViewModel", "Biometric authentication succeeded")
                // Biometric authentication succeeded - unlock vault
                vaultManager.unlockWithBiometric()
                _uiState.value = _uiState.value.copy(
                    isUnlocking = false,
                    isUnlocked = true,
                    error = null
                )
            },
            onError = { error ->
                android.util.Log.e("VaultUnlockViewModel", "Biometric authentication error: $error")
                _uiState.value = _uiState.value.copy(
                    error = error,
                    isUnlocking = false
                )
            },
            onCancel = {
                android.util.Log.d("VaultUnlockViewModel", "Biometric authentication cancelled")
                // User cancelled, they can still use password
            }
        )
    }
}

@Composable
fun VaultUnlockScreen(
    onUnlocked: () -> Unit,
    viewModel: VaultUnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    val isBiometricEnabled = viewModel.isBiometricEnabled()

    // Navigate away when unlocked
    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            onUnlocked()
        }
    }

    // Auto-focus password field (only if biometric not enabled)
    LaunchedEffect(Unit) {
        android.util.Log.d("VaultUnlockScreen", "Screen launched, isBiometricEnabled=$isBiometricEnabled")
        if (!isBiometricEnabled) {
            android.util.Log.d("VaultUnlockScreen", "Requesting focus on password field")
            focusRequester.requestFocus()
        }
    }

    // Auto-show biometric prompt on launch if enabled
    LaunchedEffect(isBiometricEnabled) {
        android.util.Log.d("VaultUnlockScreen", "Auto-biometric effect triggered: enabled=$isBiometricEnabled, activity=$activity")
        if (isBiometricEnabled && activity != null) {
            android.util.Log.d("VaultUnlockScreen", "Triggering auto-biometric unlock")
            viewModel.unlockWithBiometric(activity)
        } else {
            if (!isBiometricEnabled) android.util.Log.d("VaultUnlockScreen", "Biometric not enabled, skipping auto-unlock")
            if (activity == null) android.util.Log.e("VaultUnlockScreen", "Activity is null, cannot show biometric prompt")
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Vault Locked",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Vault Locked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Enter your password to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Password field
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                visualTransformation = if (uiState.isPasswordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        viewModel.unlock()
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible)
                                Icons.Default.Visibility
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = if (uiState.isPasswordVisible)
                                "Hide password"
                            else
                                "Show password"
                        )
                    }
                },
                isError = uiState.error != null,
                supportingText = uiState.error?.let { { Text(it) } },
                enabled = !uiState.isUnlocking,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Unlock button
            Button(
                onClick = viewModel::unlock,
                enabled = !uiState.isUnlocking && uiState.password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState.isUnlocking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Unlock Vault")
                }
            }

            // Biometric button (if enabled)
            if (isBiometricEnabled && activity != null) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.unlockWithBiometric(activity) },
                    enabled = !uiState.isUnlocking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Biometric")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lock timeout info
            Text(
                text = viewModel.getLockTimeoutDisplay(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

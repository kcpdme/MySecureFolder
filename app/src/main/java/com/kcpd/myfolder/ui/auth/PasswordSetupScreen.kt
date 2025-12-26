package com.kcpd.myfolder.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kcpd.myfolder.security.PasswordStrength

/**
 * Screen for setting up master password on first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSetupScreen(
    onPasswordSet: () -> Unit,
    viewModel: PasswordSetupViewModel = hiltViewModel()
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val passwordStrength = viewModel.getPasswordStrength(password)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Your Data") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create Master Password",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Your password encrypts all media files and metadata. " +
                        "Choose a strong password and keep it safe!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Master Password") },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Password strength indicator
            if (password.isNotEmpty()) {
                PasswordStrengthIndicator(strength = passwordStrength)
            }

            // Confirm password field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = null
                },
                label = { Text("Confirm Password") },
                visualTransformation = if (confirmVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (confirmVisible) "Hide password" else "Show password"
                        )
                    }
                },
                isError = errorMessage != null,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Warning card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ Important",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "• If you forget this password, your data CANNOT be recovered\n" +
                                "• Save your password in a secure password manager\n" +
                                "• Backup your salt code for device migration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Set password button
            Button(
                onClick = {
                    when {
                        password.length < 8 -> {
                            errorMessage = "Password must be at least 8 characters"
                        }
                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match"
                        }
                        passwordStrength == PasswordStrength.TOO_SHORT -> {
                            errorMessage = "Password is too short"
                        }
                        else -> {
                            if (viewModel.setupPassword(password)) {
                                onPasswordSet()
                            } else {
                                errorMessage = "Failed to setup password. Please try again."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotEmpty() && confirmPassword.isNotEmpty()
            ) {
                Text("Create Password & Encrypt Data")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Restore button
            TextButton(
                onClick = { showRestoreDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Existing User? Restore from Backup")
            }
        }
    }
    
    if (showRestoreDialog) {
        RestoreBackupDialog(
            onDismiss = { showRestoreDialog = false },
            onRestore = { code, pwd ->
                if (viewModel.recoverFromBackup(pwd, code)) {
                    showRestoreDialog = false
                    onPasswordSet()
                } else {
                    // Show error in dialog? 
                    // For simplicity, we just close and show error on main screen or toast
                    // ideally pass error back
                    errorMessage = "Restore failed. Invalid code or password."
                }
            }
        )
    }
}

@Composable
fun RestoreBackupDialog(
    onDismiss: () -> Unit,
    onRestore: (String, String) -> Unit
) {
    var backupCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    // Validate backup code format (should be base64)
    val isValidBackupCodeFormat = backupCode.isBlank() || backupCode.matches(Regex("^[A-Za-z0-9+/=]+$"))
    val backupCodeError = when {
        backupCode.isNotBlank() && !isValidBackupCodeFormat -> "Invalid recovery code format"
        backupCode.isNotBlank() && backupCode.length < 20 -> "Recovery code seems too short"
        else -> null
    }

    val passwordError = when {
        password.isNotEmpty() && password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }

    val canRestore = backupCode.isNotBlank() &&
                     password.length >= 8 &&
                     isValidBackupCodeFormat &&
                     !isRestoring

    AlertDialog(
        onDismissRequest = if (!isRestoring) onDismiss else { {} },
        title = { Text("Restore from Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Enter your recovery code and password to restore access.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = backupCode,
                    onValueChange = {
                        backupCode = it
                        errorMessage = null
                    },
                    label = { Text("Recovery Code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    isError = backupCodeError != null,
                    supportingText = backupCodeError?.let { { Text(it) } },
                    enabled = !isRestoring
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                    enabled = !isRestoring
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isRestoring = true
                    errorMessage = null
                    // Validate and provide specific error feedback
                    if (backupCode.length < 20) {
                        errorMessage = "Recovery code is too short. Please enter the full code from settings."
                        isRestoring = false
                    } else if (!isValidBackupCodeFormat) {
                        errorMessage = "Invalid recovery code format. Code should contain only letters, numbers, and +/= characters."
                        isRestoring = false
                    } else {
                        onRestore(backupCode, password)
                        isRestoring = false
                    }
                },
                enabled = canRestore
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRestoring
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PasswordStrengthIndicator(strength: PasswordStrength) {
    val (color, label) = when (strength) {
        PasswordStrength.TOO_SHORT -> MaterialTheme.colorScheme.error to "Too Short"
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error to "Weak"
        PasswordStrength.MEDIUM -> Color(0xFFFFA500) to "Medium"
        PasswordStrength.STRONG -> Color(0xFF4CAF50) to "Strong"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Password Strength:",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }

    LinearProgressIndicator(
        progress = when (strength) {
            PasswordStrength.TOO_SHORT -> 0.1f
            PasswordStrength.WEAK -> 0.33f
            PasswordStrength.MEDIUM -> 0.66f
            PasswordStrength.STRONG -> 1.0f
        },
        modifier = Modifier.fillMaxWidth(),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

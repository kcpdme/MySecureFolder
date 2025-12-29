package com.kcpd.myfolder.ui.auth

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordChangeScreen(
    navController: NavController,
    viewModel: PasswordSetupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showProgressSheet by remember { mutableStateOf(false) }
    var showRestartingScreen by remember { mutableStateOf(false) }
    var passwordChangeSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val passwordChangeProgress by viewModel.passwordChangeProgress.collectAsState()
    val newPasswordStrength = viewModel.getPasswordStrength(newPassword)

    // Show progress bottom sheet when password change is in progress
    LaunchedEffect(passwordChangeProgress) {
        showProgressSheet = passwordChangeProgress != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Password") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Change Your Master Password",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Your data will be re-encrypted with the new password",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Current Password
            OutlinedTextField(
                value = currentPassword,
                onValueChange = {
                    currentPassword = it
                    errorMessage = null
                },
                label = { Text("Current Password") },
                visualTransformation = if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { currentVisible = !currentVisible }) {
                        Icon(
                            if (currentVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )
            )

            // New Password
            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    errorMessage = null
                },
                label = { Text("New Password") },
                visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newVisible = !newVisible }) {
                        Icon(
                            if (newVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )
            )

            // Password strength indicator
            if (newPassword.isNotEmpty()) {
                PasswordStrengthIndicator(newPasswordStrength)
            }

            // Confirm Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = null
                },
                label = { Text("Confirm New Password") },
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                )
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    when {
                        currentPassword.isEmpty() -> {
                            errorMessage = "Please enter your current password"
                        }
                        newPassword.length < 8 -> {
                            errorMessage = "New password must be at least 8 characters"
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = "Passwords do not match"
                        }
                        else -> {
                            scope.launch {
                                val result = viewModel.changePassword(currentPassword, newPassword)
                                if (result) {
                                    // Mark success - user will click Done to trigger restart
                                    passwordChangeSuccess = true
                                } else {
                                    // Clear progress and show error
                                    viewModel.clearProgress()
                                    showProgressSheet = false
                                    errorMessage = "Current password is incorrect"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentPassword.isNotEmpty() &&
                         newPassword.isNotEmpty() &&
                         confirmPassword.isNotEmpty()
            ) {
                Text("Change Password")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ Important: Crash-Safe Password Change",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Changing your password will:\n" +
                               "• Re-wrap all file encryption keys (headers only)\n" +
                               "• Re-wrap the database encryption key (no full re-encryption)\n" +
                               "• Keep your 12 seed words unchanged\n\n" +
                               "This process is crash-safe and atomic. If interrupted, you can recover with either your old or new password. " +
                               "The process may take a few moments depending on the number of files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        // Progress Bottom Sheet
        if (showProgressSheet && passwordChangeProgress != null) {
            ModalBottomSheet(
                onDismissRequest = { /* Don't allow dismissal during password change */ },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Changing Password",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    LinearProgressIndicator(
                        progress = passwordChangeProgress!!.currentStep.toFloat() / passwordChangeProgress!!.totalSteps.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = passwordChangeProgress!!.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "Step ${passwordChangeProgress!!.currentStep} of ${passwordChangeProgress!!.totalSteps}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (passwordChangeProgress!!.currentStep == passwordChangeProgress!!.totalSteps && passwordChangeSuccess) {
                        Button(
                            onClick = {
                                viewModel.clearProgress()
                                showProgressSheet = false
                                showRestartingScreen = true
                                
                                // Restart the app after a short delay
                                scope.launch {
                                    delay(1500) // Show message for 1.5 seconds
                                    
                                    val activity = context as? Activity
                                    activity?.let {
                                        val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        it.startActivity(intent)
                                        it.finishAffinity()
                                        Runtime.getRuntime().exit(0)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    
    // Restarting overlay - shown after successful password change
    if (showRestartingScreen) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Password Changed Successfully!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Restarting app...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

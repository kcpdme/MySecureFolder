package com.kcpd.myfolder.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kcpd.myfolder.security.PasswordStrength
import kotlinx.coroutines.launch

/**
 * Screen for setting up master password on first launch.
 * Uses scrollable layout to handle keyboard visibility.
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
    var showRestoreScreen by remember { mutableStateOf(false) }
    var showSeedGenerationScreen by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf("") }
    var generatedSeedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val passwordStrength = viewModel.getPasswordStrength(password)

    when {
        showSeedGenerationScreen -> {
            SeedGenerationScreen(
                password = pendingPassword,
                onSeedGenerated = { seedWords ->
                    generatedSeedWords = seedWords
                },
                onComplete = {
                    // Now actually setup the password with the generated seed
                    scope.launch {
                        if (viewModel.setupPasswordWithSeed(pendingPassword, generatedSeedWords)) {
                            onPasswordSet()
                        } else {
                            showSeedGenerationScreen = false
                            errorMessage = "Failed to setup password. Please try again."
                        }
                    }
                },
                generateSeedWords = { viewModel.generateSeedWords() }
            )
        }
        showRestoreScreen -> {
            RestoreBackupScreen(
                onBack = { showRestoreScreen = false },
                onRestore = { code, pwd ->
                    scope.launch {
                        if (viewModel.recoverFromBackup(pwd, code)) {
                            showRestoreScreen = false
                            onPasswordSet()
                        } else {
                            errorMessage = "Restore failed. Invalid code or password."
                        }
                    }
                }
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .imePadding() // Handle keyboard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Spacer(modifier = Modifier.height(48.dp))

                // Lock icon
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Create Master Password",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your password encrypts all media files and metadata.\nChoose a strong password and keep it safe!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

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
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next
                    )
                )

                // Password strength indicator
                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordStrengthIndicator(strength = passwordStrength)
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Warning card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ Important",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "• If you forget this password, your data CANNOT be recovered\n" +
                                    "• Save your password in a secure password manager\n" +
                                    "• Backup your seed words for device migration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

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
                                // Save password for later and show seed generation
                                pendingPassword = password
                                showSeedGenerationScreen = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = password.isNotEmpty() && confirmPassword.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Continue",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Restore button
                OutlinedButton(
                    onClick = { showRestoreScreen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Existing User? Restore from Backup",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
}

/**
 * Full screen for restoring from backup - replaces the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    onBack: () -> Unit,
    onRestore: (String, String) -> Unit
) {
    var seedWordsInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Validate seed words (roughly 12 words)
    val words = seedWordsInput.trim().split("\\s+".toRegex())
    val isValidSeedWords = seedWordsInput.isBlank() || words.size == 12
    val seedWordsError = when {
        seedWordsInput.isNotBlank() && !isValidSeedWords -> "Must be exactly 12 words"
        else -> null
    }

    val passwordError = when {
        password.isNotEmpty() && password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }

    val canRestore = seedWordsInput.isNotBlank() &&
            password.length >= 8 &&
            isValidSeedWords &&
            !isRestoring

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top bar
            TopAppBar(
                title = { Text("Restore from Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isRestoring) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Restore icon
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome Back!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter your 12 seed words and password to restore access to your encrypted data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Error message
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
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Seed words input
                OutlinedTextField(
                    value = seedWordsInput,
                    onValueChange = {
                        seedWordsInput = it
                        errorMessage = null
                    },
                    label = { Text("Recovery Seed Words") },
                    placeholder = { Text("word1 word2 word3 ... word12") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    singleLine = false,
                    maxLines = 4,
                    isError = seedWordsError != null,
                    supportingText = {
                        if (seedWordsError != null) {
                            Text(seedWordsError, color = MaterialTheme.colorScheme.error)
                        } else if (seedWordsInput.isNotBlank()) {
                            Text("${words.size}/12 words entered")
                        }
                    },
                    enabled = !isRestoring,
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password input
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Your Password") },
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
                    supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    enabled = !isRestoring,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Restore button
                Button(
                    onClick = {
                        isRestoring = true
                        errorMessage = null
                        if (!isValidSeedWords) {
                            errorMessage = "Please enter exactly 12 words separated by spaces."
                            isRestoring = false
                        } else {
                            onRestore(seedWordsInput, password)
                            isRestoring = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = canRestore,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        "Restore My Data",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Help card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "💡 Need Help?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Seed words were shown when you first created your password\n" +
                                    "• They should be 12 English words separated by spaces\n" +
                                    "• The password is the same one you used before",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun PasswordStrengthIndicator(strength: PasswordStrength) {
    val (color, label) = when (strength) {
        PasswordStrength.TOO_SHORT -> MaterialTheme.colorScheme.error to "Too Short"
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error to "Weak"
        PasswordStrength.MEDIUM -> Color(0xFFFFA500) to "Medium"
        PasswordStrength.STRONG -> Color(0xFF4CAF50) to "Strong"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password Strength:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }

        LinearProgressIndicator(
            progress = {
                when (strength) {
                    PasswordStrength.TOO_SHORT -> 0.1f
                    PasswordStrength.WEAK -> 0.33f
                    PasswordStrength.MEDIUM -> 0.66f
                    PasswordStrength.STRONG -> 1.0f
                }
            },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

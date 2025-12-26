package com.kcpd.myfolder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val passwordManager: PasswordManager
) : ViewModel() {

    private val _lockTimeout = MutableStateFlow(vaultManager.getLockTimeout())
    val lockTimeout: StateFlow<Long> = _lockTimeout.asStateFlow()

    fun setLockTimeout(preset: VaultManager.LockTimeoutPreset) {
        viewModelScope.launch {
            vaultManager.setLockTimeout(preset)
            _lockTimeout.value = preset.milliseconds
        }
    }

    fun lockVault() {
        vaultManager.lock()
    }

    fun getLockTimeoutPreset(): VaultManager.LockTimeoutPreset {
        return VaultManager.LockTimeoutPreset.fromMilliseconds(_lockTimeout.value)
    }

    fun getBackupCode(): String? {
        return passwordManager.getBackupCode()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showLockTimeoutDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    val lockTimeout by viewModel.lockTimeout.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        ) {
            // Security Section
            Text(
                text = "Security",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Change Password",
                description = "Change your master encryption password",
                onClick = {
                    navController.navigate("password_change")
                }
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.Timer,
                title = "Auto-Lock Timeout",
                description = VaultManager.LockTimeoutPreset.fromMilliseconds(lockTimeout).displayName,
                onClick = {
                    showLockTimeoutDialog = true
                }
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.LockClock,
                title = "Lock Vault Now",
                description = "Immediately lock the vault",
                onClick = {
                    viewModel.lockVault()
                    navController.navigate("vault_unlock") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )

            HorizontalDivider()

            // Backup & Recovery
            Text(
                text = "Backup & Recovery",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                icon = Icons.Default.Key,
                title = "Recovery Code",
                description = "View backup code for device migration",
                onClick = {
                    showBackupDialog = true
                }
            )

            HorizontalDivider()

            // Storage Section
            Text(
                text = "Cloud Storage",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                icon = Icons.Default.Cloud,
                title = "S3/Minio Configuration",
                description = "Configure cloud storage for backups",
                onClick = {
                    navController.navigate("s3_config")
                }
            )
        }
    }

    // Lock timeout dialog
    if (showLockTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text("Auto-Lock Timeout") },
            text = {
                Column {
                    Text(
                        text = "Choose when to automatically lock the vault after the app goes to background:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    VaultManager.LockTimeoutPreset.values().forEach { preset ->
                        val isSelected = preset.milliseconds == lockTimeout
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLockTimeout(preset)
                                    showLockTimeoutDialog = false
                                },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setLockTimeout(preset)
                                        showLockTimeoutDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Backup Code Dialog
    if (showBackupDialog) {
        val backupCode = viewModel.getBackupCode()
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Recovery Code") },
            text = {
                Column {
                    Text(
                        text = "Save this code securely. You will need it to recover your data if you lose your device or reinstall the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (backupCode != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = backupCode,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text("Error: Could not generate backup code. Is password set?")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (backupCode != null) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Recovery Code", backupCode)
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

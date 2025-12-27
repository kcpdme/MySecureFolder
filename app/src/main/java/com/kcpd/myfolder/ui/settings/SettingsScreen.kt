package com.kcpd.myfolder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kcpd.myfolder.security.BiometricManager
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val passwordManager: PasswordManager,
    private val biometricManager: BiometricManager,
    private val mediaRepository: com.kcpd.myfolder.data.repository.MediaRepository,
    private val remoteRepositoryManager: com.kcpd.myfolder.data.repository.RemoteRepositoryManager,
    private val googleDriveRepository: com.kcpd.myfolder.data.repository.GoogleDriveRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _lockTimeout = MutableStateFlow(vaultManager.getLockTimeout())
    val lockTimeout: StateFlow<Long> = _lockTimeout.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(vaultManager.isBiometricEnabled())
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _storageInfo = MutableStateFlow<Map<String, Long>>(emptyMap())
    val storageInfo: StateFlow<Map<String, Long>> = _storageInfo.asStateFlow()

    val activeRemoteType = remoteRepositoryManager.activeRemoteType.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        com.kcpd.myfolder.data.model.RemoteType.S3_MINIO
    )

    private val _googleAccountEmail = MutableStateFlow<String?>(null)
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    init {
        // Check for existing signed-in account using Application Context
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            _googleAccountEmail.value = account.email
            googleDriveRepository.setSignedInAccount(account)
        }
    }

    fun setRemoteType(type: com.kcpd.myfolder.data.model.RemoteType) {
        viewModelScope.launch {
            // Always sign out of Google when switching remotes to ensure a fresh session/clean state
            signOutGoogleSuspend(context)
            remoteRepositoryManager.setRemoteType(type)
        }
    }

    fun handleGoogleSignInResult(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) {
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            googleDriveRepository.setSignedInAccount(account)
            _googleAccountEmail.value = account.email
        } catch (e: com.google.android.gms.common.api.ApiException) {
            android.util.Log.w("SettingsViewModel", "signInResult:failed code=" + e.statusCode)
            _googleAccountEmail.value = null
        }
    }

    fun checkGoogleSignIn(context: android.content.Context) {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        _googleAccountEmail.value = account?.email
        googleDriveRepository.setSignedInAccount(account)
    }
    
    fun signOutGoogle(context: android.content.Context) {
        viewModelScope.launch {
            signOutGoogleSuspend(context)
        }
    }

    private suspend fun signOutGoogleSuspend(context: android.content.Context) = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        ).requestEmail().build()
        
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
        client.signOut().addOnCompleteListener {
            _googleAccountEmail.value = null
            googleDriveRepository.setSignedInAccount(null)
            if (cont.isActive) {
                cont.resume(Unit) {}
            }
        }
    }

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

    fun setBiometricEnabled(enabled: Boolean) {
        vaultManager.setBiometricEnabled(enabled)
        _biometricEnabled.value = enabled
    }

    fun isBiometricAvailable(): Boolean {
        return biometricManager.canUseBiometric()
    }

    fun getBiometricAvailabilityMessage(): String {
        return biometricManager.checkBiometricAvailability().message
    }

    fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                _storageInfo.value = mediaRepository.analyzeStorageUsage()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to load storage info", e)
            }
        }
    }

    fun cleanupOrphanedFiles(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val deletedCount = mediaRepository.cleanupOrphanedFiles()
                onComplete(deletedCount)
                // Refresh storage info after cleanup
                _storageInfo.value = mediaRepository.analyzeStorageUsage()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to cleanup orphaned files", e)
                onComplete(0)
            }
        }
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
    var showStorageDialog by remember { mutableStateOf(false) }
    val lockTimeout by viewModel.lockTimeout.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val isBiometricAvailable = viewModel.isBiometricAvailable()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeRemoteType by viewModel.activeRemoteType.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        viewModel.handleGoogleSignInResult(task)
    }

    LaunchedEffect(Unit) {
        viewModel.checkGoogleSignIn(context)
    }

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
                .verticalScroll(rememberScrollState())
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

            // Biometric Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isBiometricAvailable) {
                        viewModel.setBiometricEnabled(!biometricEnabled)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = if (isBiometricAvailable)
                            androidx.compose.ui.graphics.Color.White
                        else
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.38f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biometric Unlock",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isBiometricAvailable)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = if (isBiometricAvailable)
                                "Use fingerprint or face to unlock"
                            else
                                viewModel.getBiometricAvailabilityMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled && isBiometricAvailable,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        enabled = isBiometricAvailable
                    )
                }
            }

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
                text = "Storage",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Storage Usage",
                description = "View storage breakdown by file type",
                onClick = {
                    viewModel.loadStorageInfo()
                    showStorageDialog = true
                }
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.Delete,
                title = "Clean Orphaned Files",
                description = "Remove unencrypted leftovers (SECURITY FIX)",
                onClick = {
                    viewModel.cleanupOrphanedFiles { deletedCount ->
                        val message = if (deletedCount > 0) {
                            "Deleted $deletedCount orphaned files"
                        } else {
                            "No orphaned files found"
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )

            HorizontalDivider()

            // Cloud Storage Section
            Text(
                text = "Cloud Storage",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            // Remote Type Selector
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Storage Provider",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.setRemoteType(com.kcpd.myfolder.data.model.RemoteType.S3_MINIO) }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = activeRemoteType == com.kcpd.myfolder.data.model.RemoteType.S3_MINIO,
                            onClick = { viewModel.setRemoteType(com.kcpd.myfolder.data.model.RemoteType.S3_MINIO) }
                        )
                        Text(text = "S3 / MinIO", modifier = Modifier.padding(start = 8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.setRemoteType(com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = activeRemoteType == com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE,
                            onClick = { viewModel.setRemoteType(com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) }
                        )
                        Text(text = "Google Drive", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (activeRemoteType == com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (googleAccountEmail != null) {
                            Text(text = "Signed in as: $googleAccountEmail")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.signOutGoogle(context) }) {
                                Text("Sign Out")
                            }
                        } else {
                            Button(onClick = {
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                                )
                                .requestEmail()
                                .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
                                .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_APPDATA))
                                .build()

                                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(client.signInIntent)
                            }) {
                                Text("Sign In with Google")
                            }
                            Text(
                                text = "Note: Requires Google Cloud Console setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
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

    // Storage Info Dialog
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Storage Usage") },
            text = {
                Column {
                    if (storageInfo.isEmpty()) {
                        Text("Loading storage information...")
                    } else {
                        val totalSize = storageInfo.values.sum()
                        Text(
                            text = "Total: ${formatBytes(totalSize)}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        storageInfo.forEach { (category, size) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatBytes(size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Note: Cache is automatically cleaned on app startup. Large 'Data' size is normal - it contains your encrypted files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
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
                tint = androidx.compose.ui.graphics.Color.White
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
                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

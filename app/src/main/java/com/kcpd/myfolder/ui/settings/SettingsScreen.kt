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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.kcpd.myfolder.security.BiometricManager
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.SecureDeleteConfigRepository
import com.kcpd.myfolder.security.SecureDeleteLevel
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
    private val remoteConfigRepository: com.kcpd.myfolder.data.repository.RemoteConfigRepository,
    private val remoteRepositoryFactory: com.kcpd.myfolder.data.repository.RemoteRepositoryFactory,
    private val uploadSettingsRepository: com.kcpd.myfolder.data.repository.UploadSettingsRepository,
    private val securityManager: com.kcpd.myfolder.security.SecurityManager,
    private val secureDeleteConfigRepository: SecureDeleteConfigRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _lockTimeout = MutableStateFlow(vaultManager.getLockTimeout())
    val lockTimeout: StateFlow<Long> = _lockTimeout.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(vaultManager.isBiometricEnabled())
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _storageInfo = MutableStateFlow<Map<String, Long>>(emptyMap())
    val storageInfo: StateFlow<Map<String, Long>> = _storageInfo.asStateFlow()
    
    // Upload concurrency setting
    val uploadConcurrency = uploadSettingsRepository.uploadConcurrency.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.kcpd.myfolder.data.repository.UploadSettingsRepository.DEFAULT_CONCURRENCY
    )
    
    // Secure delete level setting
    val secureDeleteLevel = secureDeleteConfigRepository.secureDeleteLevel.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SecureDeleteLevel.QUICK
    )
    
    fun setSecureDeleteLevel(level: SecureDeleteLevel) {
        viewModelScope.launch {
            secureDeleteConfigRepository.setSecureDeleteLevel(level)
        }
    }

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
    
    /**
     * Set the upload concurrency (number of parallel uploads)
     */
    fun setUploadConcurrency(value: Int) {
        viewModelScope.launch {
            uploadSettingsRepository.setUploadConcurrency(value)
        }
    }
    
    /**
     * Get display text for a concurrency value
     */
    fun getConcurrencyDisplayText(value: Int): String {
        return uploadSettingsRepository.getConcurrencyDisplayText(value)
    }

    fun setRemoteType(type: com.kcpd.myfolder.data.model.RemoteType) {
        viewModelScope.launch {
            // Only sign out when switching AWAY from Google Drive.
            // Signing out while selecting Google Drive leaves the repository uninitialized until app restart.
            if (type != com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) {
                signOutGoogleSuspend(context)
            }

            remoteRepositoryManager.setRemoteType(type)

            // Ensure Drive service is initialized immediately when switching to Google Drive
            if (type == com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) {
                checkGoogleSignIn(context)
            }
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

    fun getSeedWords(): List<String>? {
        return passwordManager.getSeedWords()
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

    fun recoverOrphanedFiles(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val recoveredCount = mediaRepository.recoverOrphanedFiles()
                onComplete(recoveredCount)
                // Refresh storage info after recovery
                _storageInfo.value = mediaRepository.analyzeStorageUsage()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to recover orphaned files", e)
                onComplete(0)
            }
        }
    }

    fun resetDatabase(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Delete corrupted database
                securityManager.deleteDatabase(context)
                
                // 2. Re-initialize empty database (by accessing it via repository)
                // The repository will automatically create a new DB when accessed
                
                // 3. Scan and recover files into the new database
                recoverOrphanedFiles(onComplete)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to reset database", e)
                onComplete(0)
            }
        }
    }

    fun setPanicPin(pin: String) {
        passwordManager.setPanicPin(pin)
    }

    fun isPanicPinSet(): Boolean {
        return passwordManager.isPanicPinSet()
    }

    /**
     * Export all cloud remote configurations to JSON string
     */
    fun exportRemoteConfigs(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val json = remoteConfigRepository.exportRemotes()
                onResult(json)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to export remote configs", e)
                onResult(null)
            }
        }
    }

    /**
     * Import cloud remote configurations from JSON string
     * @param merge If true, adds to existing configs. If false, replaces all.
     */
    fun importRemoteConfigs(jsonString: String, merge: Boolean = true, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                if (merge) {
                    // Parse the incoming configs
                    val json = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }
                    val importedRemotes = json.decodeFromString<List<com.kcpd.myfolder.domain.model.RemoteConfig>>(jsonString)
                    
                    // Get existing configs
                    val existingRemotes = remoteConfigRepository.getAllRemotes()
                    val existingIds = existingRemotes.map { it.id }.toSet()
                    
                    // Add only new configs (by ID)
                    var addedCount = 0
                    for (remote in importedRemotes) {
                        if (remote.id !in existingIds) {
                            remoteConfigRepository.addRemote(remote)
                            addedCount++
                        }
                    }
                    
                    // Clear repository cache so new configs get fresh instances
                    // This is important for Google Drive which needs to re-check sign-in state
                    remoteRepositoryFactory.clearCache()
                    
                    onResult(true, "Imported $addedCount new remote(s). ${importedRemotes.size - addedCount} already existed.")
                } else {
                    // Replace all
                    remoteConfigRepository.importRemotes(jsonString)
                    
                    // Clear repository cache so new configs get fresh instances
                    remoteRepositoryFactory.clearCache()
                    
                    val count = remoteConfigRepository.getAllRemotes().size
                    onResult(true, "Imported $count remote configuration(s)")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to import remote configs", e)
                onResult(false, "Import failed: ${e.message}")
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
    var showPanicPinDialog by remember { mutableStateOf(false) }
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

    // File picker for importing remote configurations
    val importFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val jsonContent = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                inputStream?.close()
                
                if (jsonContent != null) {
                    viewModel.importRemoteConfigs(jsonContent, merge = true) { success, message ->
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Could not read file", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Import failed: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
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

            SettingsItem(
                icon = Icons.Default.Warning,
                title = "Panic Wipe Setup",
                description = if (viewModel.isPanicPinSet()) "Panic PIN is set (enters wipe mode)" else "Set a Panic PIN to instantly wipe data",
                onClick = {
                    showPanicPinDialog = true
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
            
            // Secure Delete Level Setting
            val secureDeleteLevel by viewModel.secureDeleteLevel.collectAsState()
            var showSecureDeleteDialog by remember { mutableStateOf(false) }
            
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Secure Delete Level",
                description = secureDeleteLevel.displayName,
                onClick = {
                    showSecureDeleteDialog = true
                }
            )
            
            // Secure Delete Level dialog
            if (showSecureDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showSecureDeleteDialog = false },
                    title = { Text("Secure Delete Level") },
                    text = {
                        Column {
                            Text(
                                text = "Choose how thoroughly files are overwritten before deletion. More passes = slower but more secure.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ On SSDs/flash, multiple passes have diminishing returns due to wear leveling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            SecureDeleteLevel.entries.forEach { level ->
                                val isSelected = level == secureDeleteLevel
                                val description = when (level) {
                                    SecureDeleteLevel.QUICK -> "1 random pass (fast, good for SSDs)"
                                    SecureDeleteLevel.DOD -> "3 passes: zeros → ones → random"
                                    SecureDeleteLevel.GUTMANN -> "35 passes (very slow, for magnetic drives)"
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setSecureDeleteLevel(level)
                                            showSecureDeleteDialog = false
                                        },
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setSecureDeleteLevel(level)
                                                showSecureDeleteDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = level.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSecureDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

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
                title = "Recovery Seed Words",
                description = "View your 12-word recovery phrase",
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
                icon = Icons.Default.Restore,
                title = "Recover Database & Files",
                description = "Scan storage and rebuild database if corrupted",
                onClick = {
                    viewModel.recoverOrphanedFiles { recoveredCount ->
                        val message = if (recoveredCount > 0) {
                            "Recovered $recoveredCount files into database"
                        } else {
                            "No missing files found to recover"
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "Reset Database (Fix Corruption)",
                description = "Delete corrupted database and recover files. Use this if app crashes.",
                onClick = {
                    viewModel.resetDatabase { recoveredCount ->
                        val message = "Database reset. Recovered $recoveredCount files."
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

            SettingsItem(
                icon = Icons.Default.Cloud,
                title = "Cloud Remotes",
                description = "Configure multiple upload destinations",
                onClick = {
                    navController.navigate("remote_management")
                }
            )

            HorizontalDivider()
            
            // Upload Concurrency Setting
            val uploadConcurrency by viewModel.uploadConcurrency.collectAsState()
            var showConcurrencyDialog by remember { mutableStateOf(false) }
            
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "Upload Concurrency",
                description = "Parallel uploads: ${viewModel.getConcurrencyDisplayText(uploadConcurrency)}",
                onClick = {
                    showConcurrencyDialog = true
                }
            )
            
            // Concurrency selection dialog
            if (showConcurrencyDialog) {
                AlertDialog(
                    onDismissRequest = { showConcurrencyDialog = false },
                    title = { Text("Upload Concurrency") },
                    text = {
                        Column {
                            Text(
                                text = "Choose how many files upload simultaneously. Higher values are faster but may be unstable on slow connections.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            com.kcpd.myfolder.data.repository.UploadSettingsRepository.CONCURRENCY_OPTIONS.forEach { value ->
                                val isSelected = value == uploadConcurrency
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setUploadConcurrency(value)
                                            showConcurrencyDialog = false
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
                                                viewModel.setUploadConcurrency(value)
                                                showConcurrencyDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = viewModel.getConcurrencyDisplayText(value),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showConcurrencyDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.Upload,
                title = "Export Remote Configs",
                description = "Save cloud configurations to a file",
                onClick = {
                    viewModel.exportRemoteConfigs { json ->
                        if (json != null) {
                            // Save to Downloads folder
                            try {
                                val fileName = "myfolder_remotes_${System.currentTimeMillis()}.json"
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                                )
                                val file = java.io.File(downloadsDir, fileName)
                                file.writeText(json)
                                android.widget.Toast.makeText(
                                    context,
                                    "Exported to Downloads/$fileName",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Export failed: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Export failed",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.Download,
                title = "Import Remote Configs",
                description = "Load cloud configurations from a file",
                onClick = {
                    // Launch file picker for JSON
                    importFileLauncher.launch(arrayOf("application/json", "*/*"))
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

    // Seed Words Dialog
    if (showBackupDialog) {
        val seedWords = viewModel.getSeedWords()
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Recovery Seed Words",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ These 12 words are the ONLY way to recover your data. Keep them safe and never share!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (seedWords != null && seedWords.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Display in 2 columns, 6 rows for better readability
                                for (row in 0 until 6) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (col in 0 until 2) {
                                            val index = row * 2 + col
                                            if (index < seedWords.size) {
                                                Surface(
                                                    modifier = Modifier.weight(1f),
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.surface
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${index + 1}.",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.width(24.dp)
                                                        )
                                                        Text(
                                                            text = seedWords[index],
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Error: Could not retrieve seed words. Is the vault unlocked?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBackupDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                if (seedWords != null) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Seed Words", seedWords.joinToString(" "))
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
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

    // Panic PIN Dialog
    if (showPanicPinDialog) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPanicPinDialog = false },
            title = { Text("Set Panic PIN") },
            text = {
                Column {
                    Text(
                        text = "Entering this PIN on the lock screen will INSTANTLY wipe all data and close the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { 
                            if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                                pin = it
                                error = ""
                            }
                        },
                        label = { Text("Panic PIN (4-8 digits)") },
                        singleLine = true,
                        isError = error.isNotEmpty(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        )
                    )
                    if (error.isNotEmpty()) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin.length >= 4) {
                        viewModel.setPanicPin(pin)
                        showPanicPinDialog = false
                        android.widget.Toast.makeText(context, "Panic PIN set", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        error = "PIN must be at least 4 digits"
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicPinDialog = false }) {
                    Text("Cancel")
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

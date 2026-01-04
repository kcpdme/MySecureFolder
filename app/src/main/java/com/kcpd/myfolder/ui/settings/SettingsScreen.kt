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
    private val camouflageManager: com.kcpd.myfolder.security.CamouflageManager,
    private val securityPinManager: com.kcpd.myfolder.security.SecurityPinManager,
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
    
    // Security PIN Management (unified for Camouflage and Panic Mode)
    val securityPinSet = securityPinManager.pinSet
    val panicModeEnabled = securityPinManager.panicModeEnabled
    val camouflageEnabled = securityPinManager.camouflageEnabled
    
    fun setSecurityPin(pin: String) {
        securityPinManager.setPin(pin)
    }
    
    fun verifySecurityPin(pin: String): Boolean {
        return securityPinManager.verifyPin(pin)
    }
    
    fun isSecurityPinSet(): Boolean {
        return securityPinManager.isPinSet()
    }
    
    fun clearSecurityPin() {
        securityPinManager.clearPin()
    }
    
    fun getMinPinLength(): Int {
        return com.kcpd.myfolder.security.SecurityPinManager.MIN_PIN_LENGTH
    }
    
    fun setPanicModeEnabled(enabled: Boolean) {
        securityPinManager.setPanicModeEnabled(enabled)
    }
    
    fun setCamouflageEnabled(enabled: Boolean) {
        securityPinManager.setCamouflageEnabled(enabled)
        // Also handle launcher icon switching
        camouflageManager.setStealthModeEnabled(enabled)
    }
    
    suspend fun verifyVaultPassword(password: String): Boolean {
        return passwordManager.verifyPassword(password)
    }
    
    // Upload concurrency settings - per remote type
    val s3Concurrency = uploadSettingsRepository.s3Concurrency.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.kcpd.myfolder.data.repository.UploadSettingsRepository.DEFAULT_S3_CONCURRENCY
    )
    
    val googleDriveConcurrency = uploadSettingsRepository.googleDriveConcurrency.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.kcpd.myfolder.data.repository.UploadSettingsRepository.DEFAULT_GOOGLE_DRIVE_CONCURRENCY
    )
    
    val webdavConcurrency = uploadSettingsRepository.webdavConcurrency.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.kcpd.myfolder.data.repository.UploadSettingsRepository.DEFAULT_WEBDAV_CONCURRENCY
    )
    
    val maxParallelUploads = uploadSettingsRepository.maxParallelUploads.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.kcpd.myfolder.data.repository.UploadSettingsRepository.DEFAULT_MAX_PARALLEL
    )
    
    // Legacy - maps to maxParallelUploads now
    val uploadConcurrency = maxParallelUploads
    
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
     * Set concurrency for a specific remote type
     */
    fun setS3Concurrency(value: Int) {
        viewModelScope.launch {
            uploadSettingsRepository.setS3Concurrency(value)
        }
    }
    
    fun setGoogleDriveConcurrency(value: Int) {
        viewModelScope.launch {
            uploadSettingsRepository.setGoogleDriveConcurrency(value)
        }
    }
    
    fun setWebdavConcurrency(value: Int) {
        viewModelScope.launch {
            uploadSettingsRepository.setWebdavConcurrency(value)
        }
    }
    
    fun setMaxParallelUploads(value: Int) {
        viewModelScope.launch {
            uploadSettingsRepository.setMaxParallelUploads(value)
        }
    }
    
    /**
     * Set the upload concurrency (legacy - now maps to max parallel)
     */
    fun setUploadConcurrency(value: Int) {
        viewModelScope.launch {
            uploadSettingsRepository.setMaxParallelUploads(value)
        }
    }
    
    /**
     * Get display text for a concurrency value
     */
    fun getConcurrencyDisplayText(value: Int): String {
        return uploadSettingsRepository.getConcurrencyDisplayText(value)
    }
    
    fun getMaxParallelDisplayText(value: Int): String {
        return uploadSettingsRepository.getMaxParallelDisplayText(value)
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
            
            // ================== SECURITY PIN SECTION ==================
            val securityPinSet by viewModel.securityPinSet.collectAsState()
            val panicModeEnabled by viewModel.panicModeEnabled.collectAsState()
            val camouflageEnabled by viewModel.camouflageEnabled.collectAsState()
            var showSecurityPinDialog by remember { mutableStateOf(false) }
            var showChangePinDialog by remember { mutableStateOf(false) }
            
            // Security PIN Setup/Change
            SettingsItem(
                icon = Icons.Default.Pin,
                title = "Security PIN",
                description = if (securityPinSet) 
                    "PIN is set • Used for Calculator unlock and Panic wipe" 
                else 
                    "Set a numeric PIN for advanced security features",
                onClick = {
                    if (securityPinSet) {
                        showChangePinDialog = true
                    } else {
                        showSecurityPinDialog = true
                    }
                }
            )
            
            // Show toggle options only if PIN is set
            if (securityPinSet) {
                HorizontalDivider()
                
                // Calculator Camouflage Toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setCamouflageEnabled(!camouflageEnabled) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (camouflageEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                androidx.compose.ui.graphics.Color.White
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Calculator Camouflage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (camouflageEnabled)
                                    "Active • Type PIN + = on calculator to unlock"
                                else
                                    "Hide app as a working calculator",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = camouflageEnabled,
                            onCheckedChange = { viewModel.setCamouflageEnabled(it) }
                        )
                    }
                }
                
                HorizontalDivider()
                
                // Panic Wipe Toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setPanicModeEnabled(!panicModeEnabled) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (panicModeEnabled)
                                MaterialTheme.colorScheme.error
                            else
                                androidx.compose.ui.graphics.Color.White
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Panic Wipe Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (panicModeEnabled)
                                    "Active • Entering PIN at unlock wipes all data"
                                else
                                    "Enter Security PIN at vault unlock to wipe data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = panicModeEnabled,
                            onCheckedChange = { viewModel.setPanicModeEnabled(it) }
                        )
                    }
                }
            }
            
            // Set Security PIN Dialog
            if (showSecurityPinDialog) {
                var newPin by remember { mutableStateOf("") }
                var confirmPin by remember { mutableStateOf("") }
                var pinError by remember { mutableStateOf<String?>(null) }
                val minPinLength = viewModel.getMinPinLength()
                
                AlertDialog(
                    onDismissRequest = { showSecurityPinDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.Pin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("Set Security PIN") },
                    text = {
                        Column {
                            Text(
                                text = "Set a numeric PIN (min $minPinLength digits) to enable:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("• Calculator Camouflage - Hide app as calculator", style = MaterialTheme.typography.bodySmall)
                            Text("• Panic Wipe - Instantly delete all data", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { 
                                    if (it.all { char -> char.isDigit() }) {
                                        newPin = it
                                        pinError = null
                                    }
                                },
                                label = { Text("Security PIN") },
                                placeholder = { Text("Enter at least $minPinLength digits") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                ),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = { 
                                    if (it.all { char -> char.isDigit() }) {
                                        confirmPin = it
                                        pinError = null
                                    }
                                },
                                label = { Text("Confirm PIN") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                ),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (pinError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pinError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                when {
                                    newPin.length < minPinLength -> {
                                        pinError = "PIN must be at least $minPinLength digits"
                                    }
                                    newPin != confirmPin -> {
                                        pinError = "PINs do not match"
                                    }
                                    else -> {
                                        try {
                                            viewModel.setSecurityPin(newPin)
                                            showSecurityPinDialog = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "Security PIN set successfully",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            pinError = e.message ?: "Failed to set PIN"
                                        }
                                    }
                                }
                            },
                            enabled = newPin.length >= minPinLength && confirmPin.isNotEmpty()
                        ) {
                            Text("Set PIN")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSecurityPinDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Change Security PIN Dialog
            if (showChangePinDialog) {
                var currentPin by remember { mutableStateOf("") }
                var newPin by remember { mutableStateOf("") }
                var confirmPin by remember { mutableStateOf("") }
                var pinError by remember { mutableStateOf<String?>(null) }
                var useVaultPassword by remember { mutableStateOf(false) }
                var vaultPassword by remember { mutableStateOf("") }
                var isVerifying by remember { mutableStateOf(false) }
                val minPinLength = viewModel.getMinPinLength()
                val coroutineScope = rememberCoroutineScope()
                
                AlertDialog(
                    onDismissRequest = { showChangePinDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.Pin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("Change Security PIN") },
                    text = {
                        Column {
                            if (!useVaultPassword) {
                                OutlinedTextField(
                                    value = currentPin,
                                    onValueChange = { 
                                        if (it.all { char -> char.isDigit() }) {
                                            currentPin = it
                                            pinError = null
                                        }
                                    },
                                    label = { Text("Current PIN") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                    ),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                TextButton(
                                    onClick = { useVaultPassword = true },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Forgot PIN? Use vault password", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                OutlinedTextField(
                                    value = vaultPassword,
                                    onValueChange = { 
                                        vaultPassword = it
                                        pinError = null
                                    },
                                    label = { Text("Vault Password") },
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                TextButton(
                                    onClick = { useVaultPassword = false },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Use current PIN instead", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { 
                                    if (it.all { char -> char.isDigit() }) {
                                        newPin = it
                                        pinError = null
                                    }
                                },
                                label = { Text("New PIN") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                ),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = { 
                                    if (it.all { char -> char.isDigit() }) {
                                        confirmPin = it
                                        pinError = null
                                    }
                                },
                                label = { Text("Confirm New PIN") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                ),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (pinError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pinError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isVerifying = true
                                    when {
                                        !useVaultPassword && !viewModel.verifySecurityPin(currentPin) -> {
                                            pinError = "Current PIN is incorrect"
                                        }
                                        useVaultPassword && !viewModel.verifyVaultPassword(vaultPassword) -> {
                                            pinError = "Vault password is incorrect"
                                        }
                                        newPin.length < minPinLength -> {
                                            pinError = "New PIN must be at least $minPinLength digits"
                                        }
                                        newPin != confirmPin -> {
                                            pinError = "New PINs do not match"
                                        }
                                        else -> {
                                            try {
                                                viewModel.setSecurityPin(newPin)
                                                showChangePinDialog = false
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Security PIN changed successfully",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (e: Exception) {
                                                pinError = e.message ?: "Failed to change PIN"
                                            }
                                        }
                                    }
                                    isVerifying = false
                                }
                            },
                            enabled = !isVerifying && newPin.length >= minPinLength && confirmPin.isNotEmpty() && 
                                ((!useVaultPassword && currentPin.isNotEmpty()) || (useVaultPassword && vaultPassword.isNotEmpty()))
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Change PIN")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChangePinDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

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
            
            // Upload Speed Settings - Per Remote Type
            val s3Concurrency by viewModel.s3Concurrency.collectAsState()
            val googleDriveConcurrency by viewModel.googleDriveConcurrency.collectAsState()
            val webdavConcurrency by viewModel.webdavConcurrency.collectAsState()
            val maxParallelUploads by viewModel.maxParallelUploads.collectAsState()
            var showUploadSpeedDialog by remember { mutableStateOf(false) }
            
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "Upload Speed Settings",
                description = "Max: $maxParallelUploads parallel • S3: $s3Concurrency • GDrive: $googleDriveConcurrency • WebDAV: $webdavConcurrency",
                onClick = {
                    showUploadSpeedDialog = true
                }
            )
            
            // Upload Speed Settings dialog
            if (showUploadSpeedDialog) {
                AlertDialog(
                    onDismissRequest = { showUploadSpeedDialog = false },
                    title = { Text("Upload Speed Settings") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Configure concurrent uploads per remote type. Higher values = faster but may cause rate limit errors.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Max Parallel Uploads
                            Text(
                                text = "Max Parallel Uploads: $maxParallelUploads",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total concurrent uploads across all remotes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("2", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = maxParallelUploads.toFloat(),
                                    onValueChange = { viewModel.setMaxParallelUploads(it.toInt()) },
                                    valueRange = 2f..8f,
                                    steps = 5,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text("8", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            // S3/MinIO Concurrency
                            Text(
                                text = "S3 / MinIO: $s3Concurrency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fast local servers. Higher values work well.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("1", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = s3Concurrency.toFloat(),
                                    onValueChange = { viewModel.setS3Concurrency(it.toInt()) },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text("5", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Google Drive Concurrency
                            Text(
                                text = "Google Drive: $googleDriveConcurrency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "⚠️ Rate limited by Google. Keep low to avoid quota errors.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("1", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = googleDriveConcurrency.toFloat(),
                                    onValueChange = { viewModel.setGoogleDriveConcurrency(it.toInt()) },
                                    valueRange = 1f..3f,
                                    steps = 1,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text("3", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // WebDAV Concurrency
                            Text(
                                text = "WebDAV: $webdavConcurrency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Depends on server. Moderate values recommended.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("1", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = webdavConcurrency.toFloat(),
                                    onValueChange = { viewModel.setWebdavConcurrency(it.toInt()) },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text("5", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showUploadSpeedDialog = false }) {
                            Text("Done")
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

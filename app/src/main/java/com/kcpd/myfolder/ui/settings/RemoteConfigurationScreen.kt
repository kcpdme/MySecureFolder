package com.kcpd.myfolder.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.kcpd.myfolder.data.repository.RemoteConfigRepository
import com.kcpd.myfolder.domain.model.RemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteConfigurationViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val remoteRepositoryFactory: com.kcpd.myfolder.data.repository.RemoteRepositoryFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val remoteType: String = savedStateHandle["remoteType"] ?: "s3"

    // Common fields
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _selectedColor = MutableStateFlow(RemoteConfig.AVAILABLE_COLORS[0])
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    // S3 fields
    private val _s3Endpoint = MutableStateFlow("")
    val s3Endpoint: StateFlow<String> = _s3Endpoint.asStateFlow()

    private val _s3AccessKey = MutableStateFlow("")
    val s3AccessKey: StateFlow<String> = _s3AccessKey.asStateFlow()

    private val _s3SecretKey = MutableStateFlow("")
    val s3SecretKey: StateFlow<String> = _s3SecretKey.asStateFlow()

    private val _s3BucketName = MutableStateFlow("")
    val s3BucketName: StateFlow<String> = _s3BucketName.asStateFlow()

    private val _s3Region = MutableStateFlow("us-east-1")
    val s3Region: StateFlow<String> = _s3Region.asStateFlow()

    // Google Drive fields
    private val _googleAccountEmail = MutableStateFlow("")
    val googleAccountEmail: StateFlow<String> = _googleAccountEmail.asStateFlow()

    // WebDAV fields
    private val _webdavServerUrl = MutableStateFlow("")
    val webdavServerUrl: StateFlow<String> = _webdavServerUrl.asStateFlow()

    private val _webdavUsername = MutableStateFlow("")
    val webdavUsername: StateFlow<String> = _webdavUsername.asStateFlow()

    private val _webdavPassword = MutableStateFlow("")
    val webdavPassword: StateFlow<String> = _webdavPassword.asStateFlow()

    private val _webdavBasePath = MutableStateFlow("")
    val webdavBasePath: StateFlow<String> = _webdavBasePath.asStateFlow()

    // UI state
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun isGoogleDrive(): Boolean = remoteType == "google_drive"
    fun isS3(): Boolean = remoteType == "s3"
    fun isWebDav(): Boolean = remoteType == "webdav"

    fun updateName(value: String) {
        _name.value = value
    }

    fun updateColor(color: Color) {
        _selectedColor.value = color
    }

    fun updateS3Endpoint(value: String) {
        _s3Endpoint.value = value
    }

    fun updateS3AccessKey(value: String) {
        _s3AccessKey.value = value
    }

    fun updateS3SecretKey(value: String) {
        _s3SecretKey.value = value
    }

    fun updateS3BucketName(value: String) {
        _s3BucketName.value = value
    }

    fun updateS3Region(value: String) {
        _s3Region.value = value
    }

    fun updateGoogleAccountEmail(value: String) {
        _googleAccountEmail.value = value
    }

    fun updateWebdavServerUrl(value: String) {
        _webdavServerUrl.value = value
    }

    fun updateWebdavUsername(value: String) {
        _webdavUsername.value = value
    }

    fun updateWebdavPassword(value: String) {
        _webdavPassword.value = value
    }

    fun updateWebdavBasePath(value: String) {
        _webdavBasePath.value = value
    }

    fun testConnection() {
        if (isGoogleDrive()) {
            _testResult.value = "Test connection is not available for Google Drive"
            return
        }

        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null

            try {
                when {
                    isS3() -> testS3Connection()
                    isWebDav() -> testWebDavConnection()
                }
            } finally {
                _isTesting.value = false
            }
        }
    }

    private suspend fun testS3Connection() {
        try {
            // Validation
            if (_s3Endpoint.value.isBlank() || _s3AccessKey.value.isBlank() ||
                _s3SecretKey.value.isBlank() || _s3BucketName.value.isBlank()) {
                _testResult.value = "Please fill in all S3 fields first"
                return
            }

            // Test S3 connection
            val minioClient = io.minio.MinioClient.builder()
                .endpoint(_s3Endpoint.value)
                .credentials(_s3AccessKey.value, _s3SecretKey.value)
                .region(_s3Region.value)
                .build()

            // Check if bucket exists
            val bucketExists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder()
                    .bucket(_s3BucketName.value)
                    .build()
            )

            _testResult.value = if (bucketExists) {
                "✓ Connection successful! Bucket '${_s3BucketName.value}' is accessible."
            } else {
                "✗ Bucket '${_s3BucketName.value}' not found. Please check the bucket name."
            }
        } catch (e: Exception) {
            _testResult.value = "✗ Connection failed: ${e.message ?: "Unknown error"}"
            android.util.Log.e("RemoteConfiguration", "Test S3 connection failed", e)
        }
    }

    private suspend fun testWebDavConnection() {
        try {
            // Validation
            if (_webdavServerUrl.value.isBlank() || _webdavUsername.value.isBlank() ||
                _webdavPassword.value.isBlank()) {
                _testResult.value = "Please fill in server URL, username, and password"
                return
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val sardine = com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine()
                sardine.setCredentials(_webdavUsername.value, _webdavPassword.value)

                val testUrl = _webdavServerUrl.value.trimEnd('/') + 
                    (_webdavBasePath.value.let { if (it.isNotBlank()) "/${it.trim('/')}" else "" }) + "/"

                // Try to list the directory to test connection
                val resources = sardine.list(testUrl)
                _testResult.value = "✓ Connection successful! Found ${resources.size} items."
            }
        } catch (e: Exception) {
            _testResult.value = "✗ Connection failed: ${e.message ?: "Unknown error"}"
            android.util.Log.e("RemoteConfiguration", "Test WebDAV connection failed", e)
        }
    }

    fun saveRemote(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null

            try {
                // Validation
                if (_name.value.isBlank()) {
                    _errorMessage.value = "Please enter a name"
                    _isSaving.value = false
                    return@launch
                }

                val remote = if (isS3()) {
                    if (_s3Endpoint.value.isBlank()) {
                        _errorMessage.value = "Please enter S3 endpoint"
                        _isSaving.value = false
                        return@launch
                    }
                    if (_s3AccessKey.value.isBlank()) {
                        _errorMessage.value = "Please enter access key"
                        _isSaving.value = false
                        return@launch
                    }
                    if (_s3SecretKey.value.isBlank()) {
                        _errorMessage.value = "Please enter secret key"
                        _isSaving.value = false
                        return@launch
                    }
                    if (_s3BucketName.value.isBlank()) {
                        _errorMessage.value = "Please enter bucket name"
                        _isSaving.value = false
                        return@launch
                    }

                    RemoteConfig.S3Remote(
                        id = java.util.UUID.randomUUID().toString(),
                        name = _name.value,
                        color = _selectedColor.value,
                        isActive = true,
                        endpoint = _s3Endpoint.value,
                        accessKey = _s3AccessKey.value,
                        secretKey = _s3SecretKey.value,
                        bucketName = _s3BucketName.value,
                        region = _s3Region.value
                    )
                } else if (isGoogleDrive()) {
                    if (_googleAccountEmail.value.isBlank()) {
                        _errorMessage.value = "Please sign in with Google"
                        _isSaving.value = false
                        return@launch
                    }

                    RemoteConfig.GoogleDriveRemote(
                        id = java.util.UUID.randomUUID().toString(),
                        name = _name.value,
                        color = _selectedColor.value,
                        isActive = true,
                        accountEmail = _googleAccountEmail.value
                    )
                } else {
                    // WebDAV
                    if (_webdavServerUrl.value.isBlank()) {
                        _errorMessage.value = "Please enter WebDAV server URL"
                        _isSaving.value = false
                        return@launch
                    }
                    if (_webdavUsername.value.isBlank()) {
                        _errorMessage.value = "Please enter username"
                        _isSaving.value = false
                        return@launch
                    }
                    if (_webdavPassword.value.isBlank()) {
                        _errorMessage.value = "Please enter password"
                        _isSaving.value = false
                        return@launch
                    }

                    RemoteConfig.WebDavRemote(
                        id = java.util.UUID.randomUUID().toString(),
                        name = _name.value,
                        color = _selectedColor.value,
                        isActive = true,
                        serverUrl = _webdavServerUrl.value,
                        username = _webdavUsername.value,
                        password = _webdavPassword.value,
                        basePath = _webdavBasePath.value
                    )
                }

                remoteConfigRepository.addRemote(remote)
                
                // Clear repository cache so the new/updated remote gets a fresh instance
                // This is especially important for Google Drive to pick up the signed-in account
                remoteRepositoryFactory.clearCache()
                
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save remote"
            } finally {
                _isSaving.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteConfigurationScreen(
    navController: NavController,
    viewModel: RemoteConfigurationViewModel = hiltViewModel()
) {
    val name by viewModel.name.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Google Sign-In launcher
    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.updateGoogleAccountEmail(account.email ?: "")
        } catch (e: com.google.android.gms.common.api.ApiException) {
            android.util.Log.w("RemoteConfigurationScreen", "signInResult:failed code=" + e.statusCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            viewModel.isS3() -> "Configure S3 Remote"
                            viewModel.isGoogleDrive() -> "Configure Google Drive"
                            viewModel.isWebDav() -> "Configure WebDAV Remote"
                            else -> "Configure Remote"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveRemote {
                                navController.navigate("remote_management") {
                                    popUpTo("remote_management") { inclusive = true }
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error message
            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Remote Name") },
                placeholder = { 
                    Text(
                        when {
                            viewModel.isS3() -> "My S3 Backup"
                            viewModel.isGoogleDrive() -> "My Google Drive"
                            viewModel.isWebDav() -> "My WebDAV Storage"
                            else -> "My Remote"
                        }
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Label, contentDescription = null)
                }
            )

            // Color picker
            Text(
                text = "Color",
                style = MaterialTheme.typography.labelLarge
            )
            RemoteColorPicker(
                selectedColor = selectedColor,
                onColorSelected = { viewModel.updateColor(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Type-specific configuration
            when {
                viewModel.isS3() -> S3ConfigurationFields(
                    viewModel = viewModel,
                    isTesting = isTesting,
                    testResult = testResult,
                    onTestClick = { viewModel.testConnection() }
                )
                viewModel.isGoogleDrive() -> GoogleDriveConfigurationFields(
                    viewModel = viewModel,
                    onSignInClick = {
                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                        )
                            .requestEmail()
                            .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
                            .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_APPDATA))
                            .build()

                        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)

                        // Sign out silently to force account picker
                        client.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(client.signInIntent)
                        }
                    }
                )
                viewModel.isWebDav() -> WebDavConfigurationFields(
                    viewModel = viewModel,
                    isTesting = isTesting,
                    testResult = testResult,
                    onTestClick = { viewModel.testConnection() }
                )
            }
        }
    }
}

@Composable
fun RemoteColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(120.dp)
    ) {
        items(RemoteConfig.AVAILABLE_COLORS) { color ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color, CircleShape)
                    .then(
                        if (color == selectedColor) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun S3ConfigurationFields(
    viewModel: RemoteConfigurationViewModel,
    isTesting: Boolean,
    testResult: String?,
    onTestClick: () -> Unit
) {
    val endpoint by viewModel.s3Endpoint.collectAsState()
    val accessKey by viewModel.s3AccessKey.collectAsState()
    val secretKey by viewModel.s3SecretKey.collectAsState()
    val bucketName by viewModel.s3BucketName.collectAsState()
    val region by viewModel.s3Region.collectAsState()

    var secretKeyVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "S3 / MinIO Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = endpoint,
            onValueChange = { viewModel.updateS3Endpoint(it) },
            label = { Text("Endpoint") },
            placeholder = { Text("s3.amazonaws.com or play.min.io") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Cloud, contentDescription = null)
            }
        )

        OutlinedTextField(
            value = accessKey,
            onValueChange = { viewModel.updateS3AccessKey(it) },
            label = { Text("Access Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null)
            }
        )

        OutlinedTextField(
            value = secretKey,
            onValueChange = { viewModel.updateS3SecretKey(it) },
            label = { Text("Secret Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (secretKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { secretKeyVisible = !secretKeyVisible }) {
                    Icon(
                        imageVector = if (secretKeyVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (secretKeyVisible) "Hide" else "Show"
                    )
                }
            }
        )

        OutlinedTextField(
            value = bucketName,
            onValueChange = { viewModel.updateS3BucketName(it) },
            label = { Text("Bucket Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Folder, contentDescription = null)
            }
        )

        OutlinedTextField(
            value = region,
            onValueChange = { viewModel.updateS3Region(it) },
            label = { Text("Region") },
            placeholder = { Text("us-east-1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Public, contentDescription = null)
            }
        )

        // Test Connection Button
        Button(
            onClick = onTestClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing Connection...")
            } else {
                Icon(Icons.Default.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection")
            }
        }

        // Test Result
        testResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.startsWith("✓")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (result.startsWith("✓")) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = if (result.startsWith("✓")) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.startsWith("✓")) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleDriveConfigurationFields(
    viewModel: RemoteConfigurationViewModel,
    onSignInClick: () -> Unit
) {
    val accountEmail by viewModel.googleAccountEmail.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Google Drive Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        if (accountEmail.isBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sign in required",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Please sign in with your Google account to configure this remote.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Signed in",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Signed in as",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = accountEmail,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebDavConfigurationFields(
    viewModel: RemoteConfigurationViewModel,
    isTesting: Boolean,
    testResult: String?,
    onTestClick: () -> Unit
) {
    val serverUrl by viewModel.webdavServerUrl.collectAsState()
    val username by viewModel.webdavUsername.collectAsState()
    val password by viewModel.webdavPassword.collectAsState()
    val basePath by viewModel.webdavBasePath.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "WebDAV Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        // Preset hints card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Common WebDAV URLs:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "• Koofr: https://app.koofr.net/dav/Koofr",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• Icedrive: https://webdav.icedrive.io",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• Nextcloud: https://your-server.com/remote.php/dav/files/username",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { viewModel.updateWebdavServerUrl(it) },
            label = { Text("Server URL *") },
            placeholder = { Text("https://app.koofr.net/dav/Koofr") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
            supportingText = { Text("Full WebDAV endpoint URL") }
        )

        OutlinedTextField(
            value = username,
            onValueChange = { viewModel.updateWebdavUsername(it) },
            label = { Text("Username *") },
            placeholder = { Text("your-email@example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.updateWebdavPassword(it) },
            label = { Text("Password / App Password *") },
            placeholder = { Text("App-specific password recommended") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            supportingText = { Text("Use app-specific password for services like Koofr") }
        )

        OutlinedTextField(
            value = basePath,
            onValueChange = { viewModel.updateWebdavBasePath(it) },
            label = { Text("Base Path (optional)") },
            placeholder = { Text("MyFolder") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
            supportingText = { Text("Subfolder within your WebDAV storage") }
        )

        // Test connection button
        Button(
            onClick = onTestClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing...")
            } else {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection")
            }
        }

        // Test result
        if (testResult != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (testResult.startsWith("✓")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = testResult,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

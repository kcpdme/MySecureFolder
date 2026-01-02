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

enum class RemoteTypeSelection {
    S3, GOOGLE_DRIVE
}

@HiltViewModel
class AddEditRemoteViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val remoteId: String? = savedStateHandle["remoteId"]
    val isEditMode = remoteId != null

    // Common fields
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _selectedColor = MutableStateFlow(RemoteConfig.AVAILABLE_COLORS[0])
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _remoteType = MutableStateFlow(RemoteTypeSelection.S3)
    val remoteType: StateFlow<RemoteTypeSelection> = _remoteType.asStateFlow()

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

    // UI state
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (isEditMode && remoteId != null) {
            loadRemote(remoteId)
        }
    }

    private fun loadRemote(id: String) {
        viewModelScope.launch {
            val remote = remoteConfigRepository.getRemoteById(id)
            if (remote != null) {
                _name.value = remote.name
                _selectedColor.value = remote.color

                when (remote) {
                    is RemoteConfig.S3Remote -> {
                        _remoteType.value = RemoteTypeSelection.S3
                        _s3Endpoint.value = remote.endpoint
                        _s3AccessKey.value = remote.accessKey
                        _s3SecretKey.value = remote.secretKey
                        _s3BucketName.value = remote.bucketName
                        _s3Region.value = remote.region
                    }
                    is RemoteConfig.GoogleDriveRemote -> {
                        _remoteType.value = RemoteTypeSelection.GOOGLE_DRIVE
                        _googleAccountEmail.value = remote.accountEmail
                    }
                }
            }
        }
    }

    fun updateName(value: String) {
        _name.value = value
    }

    fun updateColor(color: Color) {
        _selectedColor.value = color
    }

    fun updateRemoteType(type: RemoteTypeSelection) {
        _remoteType.value = type
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

    fun testConnection() {
        if (_remoteType.value != RemoteTypeSelection.S3) {
            _testResult.value = "Test connection is only available for S3 remotes"
            return
        }

        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null

            try {
                // Validation
                if (_s3Endpoint.value.isBlank() || _s3AccessKey.value.isBlank() ||
                    _s3SecretKey.value.isBlank() || _s3BucketName.value.isBlank()) {
                    _testResult.value = "Please fill in all S3 fields first"
                    _isTesting.value = false
                    return@launch
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
                android.util.Log.e("AddEditRemoteScreen", "Test connection failed", e)
            } finally {
                _isTesting.value = false
            }
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

                val remote = when (_remoteType.value) {
                    RemoteTypeSelection.S3 -> {
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
                            id = remoteId ?: java.util.UUID.randomUUID().toString(),
                            name = _name.value,
                            color = _selectedColor.value,
                            isActive = true,
                            endpoint = _s3Endpoint.value,
                            accessKey = _s3AccessKey.value,
                            secretKey = _s3SecretKey.value,
                            bucketName = _s3BucketName.value,
                            region = _s3Region.value
                        )
                    }
                    RemoteTypeSelection.GOOGLE_DRIVE -> {
                        if (_googleAccountEmail.value.isBlank()) {
                            _errorMessage.value = "Please sign in with Google"
                            _isSaving.value = false
                            return@launch
                        }

                        RemoteConfig.GoogleDriveRemote(
                            id = remoteId ?: java.util.UUID.randomUUID().toString(),
                            name = _name.value,
                            color = _selectedColor.value,
                            isActive = true,
                            accountEmail = _googleAccountEmail.value
                        )
                    }
                }

                if (isEditMode) {
                    remoteConfigRepository.updateRemote(remote)
                } else {
                    remoteConfigRepository.addRemote(remote)
                }

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
fun AddEditRemoteScreen(
    navController: NavController,
    viewModel: AddEditRemoteViewModel = hiltViewModel()
) {
    val name by viewModel.name.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val remoteType by viewModel.remoteType.collectAsState()
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
            android.util.Log.w("AddEditRemoteScreen", "signInResult:failed code=" + e.statusCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Remote" else "Add Remote") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveRemote {
                                navController.navigateUp()
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Color picker
            Text(
                text = "Color",
                style = MaterialTheme.typography.labelLarge
            )
            ColorPicker(
                selectedColor = selectedColor,
                onColorSelected = { viewModel.updateColor(it) }
            )

            // Remote type selector (only for new remotes)
            if (!viewModel.isEditMode) {
                Text(
                    text = "Remote Type",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = remoteType == RemoteTypeSelection.S3,
                        onClick = { viewModel.updateRemoteType(RemoteTypeSelection.S3) },
                        label = { Text("S3 / MinIO") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = remoteType == RemoteTypeSelection.GOOGLE_DRIVE,
                        onClick = { viewModel.updateRemoteType(RemoteTypeSelection.GOOGLE_DRIVE) },
                        label = { Text("Google Drive") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Divider()

            // Type-specific fields
            when (remoteType) {
                RemoteTypeSelection.S3 -> S3ConfigFields(
                    viewModel = viewModel,
                    isTesting = isTesting,
                    testResult = testResult,
                    onTestClick = { viewModel.testConnection() }
                )
                RemoteTypeSelection.GOOGLE_DRIVE -> GoogleDriveConfigFields(
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
                        googleSignInLauncher.launch(client.signInIntent)
                    }
                )
            }
        }
    }
}

@Composable
fun ColorPicker(
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
fun S3ConfigFields(
    viewModel: AddEditRemoteViewModel,
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
            text = "S3 Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = endpoint,
            onValueChange = { viewModel.updateS3Endpoint(it) },
            label = { Text("Endpoint") },
            placeholder = { Text("s3.amazonaws.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = accessKey,
            onValueChange = { viewModel.updateS3AccessKey(it) },
            label = { Text("Access Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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
            singleLine = true
        )

        OutlinedTextField(
            value = region,
            onValueChange = { viewModel.updateS3Region(it) },
            label = { Text("Region") },
            placeholder = { Text("us-east-1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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
fun GoogleDriveConfigFields(
    viewModel: AddEditRemoteViewModel,
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
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sign in required",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Please sign in with your Google account to configure this remote.",
                        style = MaterialTheme.typography.bodySmall
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
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Signed in as",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = accountEmail,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Signed in",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

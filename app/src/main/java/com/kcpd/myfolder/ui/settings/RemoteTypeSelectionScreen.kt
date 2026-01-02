package com.kcpd.myfolder.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
class RemoteTypeSelectionViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    private val _hasGoogleDriveRemote = MutableStateFlow(false)
    val hasGoogleDriveRemote: StateFlow<Boolean> = _hasGoogleDriveRemote.asStateFlow()

    private val _selectedType = MutableStateFlow<RemoteTypeSelection?>(null)
    val selectedType: StateFlow<RemoteTypeSelection?> = _selectedType.asStateFlow()

    init {
        checkForExistingGoogleDriveRemote()
    }

    private fun checkForExistingGoogleDriveRemote() {
        viewModelScope.launch {
            remoteConfigRepository.getAllRemotesFlow().collect { remotes ->
                _hasGoogleDriveRemote.value = remotes.any { it is RemoteConfig.GoogleDriveRemote }
            }
        }
    }

    fun selectType(type: RemoteTypeSelection) {
        _selectedType.value = type
    }

    fun isTypeSelected(type: RemoteTypeSelection): Boolean {
        return _selectedType.value == type
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteTypeSelectionScreen(
    navController: NavController,
    viewModel: RemoteTypeSelectionViewModel = hiltViewModel()
) {
    val hasGoogleDriveRemote by viewModel.hasGoogleDriveRemote.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Remote") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            selectedType?.let { type ->
                                when (type) {
                                    RemoteTypeSelection.S3 -> {
                                        navController.navigate("configure_remote/s3") {
                                            popUpTo("select_remote_type") { inclusive = true }
                                        }
                                    }
                                    RemoteTypeSelection.GOOGLE_DRIVE -> {
                                        navController.navigate("configure_remote/google_drive") {
                                            popUpTo("select_remote_type") { inclusive = true }
                                        }
                                    }
                                    RemoteTypeSelection.WEBDAV -> {
                                        navController.navigate("configure_remote/webdav") {
                                            popUpTo("select_remote_type") { inclusive = true }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = selectedType != null
                    ) {
                        Text("Next")
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Choose Remote Type",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Select where you want to store your encrypted files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // S3/MinIO Card
            RemoteTypeCard(
                icon = Icons.Default.Storage,
                title = "S3 / MinIO",
                description = "Self-hosted or cloud object storage. Supports multiple accounts and buckets.",
                benefits = listOf(
                    "Unlimited remotes",
                    "Self-hosted option",
                    "AWS S3 compatible"
                ),
                isSelected = viewModel.isTypeSelected(RemoteTypeSelection.S3),
                isEnabled = true,
                onClick = { viewModel.selectType(RemoteTypeSelection.S3) }
            )

            // Google Drive Card
            RemoteTypeCard(
                icon = Icons.Default.CloudQueue,
                title = "Google Drive",
                description = if (hasGoogleDriveRemote) {
                    "Remote already configured. Only one Google Drive account is supported."
                } else {
                    "Store files in your Google Drive. Easy setup with Google account."
                },
                benefits = if (!hasGoogleDriveRemote) {
                    listOf(
                        "Easy Google sign-in",
                        "15GB free storage",
                        "Familiar interface"
                    )
                } else {
                    emptyList()
                },
                isSelected = viewModel.isTypeSelected(RemoteTypeSelection.GOOGLE_DRIVE),
                isEnabled = !hasGoogleDriveRemote,
                disabledMessage = if (hasGoogleDriveRemote) "Already configured" else null,
                onClick = {
                    if (!hasGoogleDriveRemote) {
                        viewModel.selectType(RemoteTypeSelection.GOOGLE_DRIVE)
                    }
                }
            )

            // WebDAV Card
            RemoteTypeCard(
                icon = Icons.Default.Folder,
                title = "WebDAV",
                description = "Connect to WebDAV servers like Koofr, Icedrive, Nextcloud, and more.",
                benefits = listOf(
                    "Unlimited remotes",
                    "Many providers supported",
                    "Standard protocol"
                ),
                isSelected = viewModel.isTypeSelected(RemoteTypeSelection.WEBDAV),
                isEnabled = true,
                onClick = { viewModel.selectType(RemoteTypeSelection.WEBDAV) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom hint
            if (selectedType != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Tap Next to configure your remote settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteTypeCard(
    icon: ImageVector,
    title: String,
    description: String,
    benefits: List<String>,
    isSelected: Boolean,
    isEnabled: Boolean,
    disabledMessage: String? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        !isEnabled -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )

                if (disabledMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = disabledMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (benefits.isNotEmpty() && isEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        benefits.forEach { benefit ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = benefit,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

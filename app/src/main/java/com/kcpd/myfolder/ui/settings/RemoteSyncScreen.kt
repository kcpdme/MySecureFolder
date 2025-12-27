package com.kcpd.myfolder.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.data.repository.MediaRepository
import com.kcpd.myfolder.data.repository.RemoteRepositoryManager
import com.kcpd.myfolder.data.repository.RemoteStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteSyncViewModel @Inject constructor(
    application: Application,
    private val mediaRepository: MediaRepository,
    private val remoteStorageRepository: RemoteStorageRepository,
    private val remoteRepositoryManager: RemoteRepositoryManager
) : AndroidViewModel(application) {

    private val _syncStates = MutableStateFlow<Map<FolderCategory, SyncState>>(emptyMap())
    val syncStates: StateFlow<Map<FolderCategory, SyncState>> = _syncStates.asStateFlow()

    val activeRemoteType = remoteRepositoryManager.activeRemoteType

    data class SyncState(
        val isLoading: Boolean = false,
        val isUploading: Boolean = false,
        val uploadProgress: Float = 0f, // 0.0 to 1.0
        val currentUploadIndex: Int = 0,
        val totalFilesToUpload: Int = 0,
        val totalFiles: Int = 0,
        val uploadedFiles: Int = 0,
        val verifiedFiles: Int = 0,
        val deletedFromS3: Int = 0,
        val error: String? = null,
        val lastSynced: Long? = null
    )

    init {
        // Observe remote type changes to reset state
        viewModelScope.launch {
            activeRemoteType.collect {
                // Reset states when remote type changes
                _syncStates.value = emptyMap()
                loadCategoryCounts()
            }
        }
    }

    private fun loadCategoryCounts() {
        viewModelScope.launch {
            val states = mutableMapOf<FolderCategory, SyncState>()
            val type = activeRemoteType.first()

            FolderCategory.entries.forEach { category ->
                if (category != FolderCategory.ALL_FILES) {
                    val files = mediaRepository.getFilesForCategory(category).first()
                    val uploadedCount = files.count { isFileOnRemote(it, type) }

                    states[category] = SyncState(
                        totalFiles = files.size,
                        uploadedFiles = uploadedCount
                    )
                }
            }

            _syncStates.value = states
        }
    }

    private fun isFileOnRemote(file: com.kcpd.myfolder.data.model.MediaFile, type: com.kcpd.myfolder.data.model.RemoteType): Boolean {
        if (!file.isUploaded) return false
        val isDriveUrl = file.s3Url?.startsWith("drive://") == true
        return when (type) {
            com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE -> isDriveUrl
            com.kcpd.myfolder.data.model.RemoteType.S3_MINIO -> !isDriveUrl && file.s3Url != null
        }
    }

    fun syncCategory(category: FolderCategory) {
        viewModelScope.launch {
            // Mark as loading
            updateSyncState(category) { it.copy(isLoading = true, error = null) }

            try {
                // Get all files for this category
                val allFiles = mediaRepository.getFilesForCategory(category).first()
                val type = activeRemoteType.first()
                
                // Verify ALL uploaded files against the active remote to support recovery/status sync
                val filesToVerify = allFiles.filter { it.isUploaded }

                if (filesToVerify.isEmpty()) {
                    updateSyncState(category) {
                        it.copy(
                            isLoading = false,
                            verifiedFiles = 0,
                            deletedFromS3 = 0,
                            lastSynced = System.currentTimeMillis()
                        )
                    }
                    return@launch
                }

                android.util.Log.d("RemoteSyncViewModel", "Syncing ${category.displayName}: Verifying ${filesToVerify.size} files")

                // Verify files against current remote
                val results = remoteStorageRepository.verifyMultipleFiles(filesToVerify)

                var deletedCount = 0
                var verifiedCount = 0
                var recoveredCount = 0

                results.forEach { (fileId, foundUrl) ->
                    if (foundUrl != null) {
                        verifiedCount++
                        // If URL is different (e.g. recovered from GDrive or S3), update it
                        val file = filesToVerify.find { it.id == fileId }
                        if (file != null && file.s3Url != foundUrl) {
                            mediaRepository.markAsUploaded(fileId, foundUrl)
                            recoveredCount++
                        }
                    } else {
                        // Not found on CURRENT remote.
                        // Only mark as "deleted/missing" if it was supposed to be on THIS remote.
                        val file = filesToVerify.find { it.id == fileId }
                        if (file != null && isFileOnRemote(file, type)) {
                            mediaRepository.markAsNotUploaded(fileId)
                            deletedCount++
                        }
                    }
                }

                android.util.Log.d("RemoteSyncViewModel", "${category.displayName}: Verified $verifiedCount, Recovered $recoveredCount, Deleted $deletedCount")

                // Update state with results immediately using calculated counts
                // We use verifiedCount as the new uploadedCount since we verified ALL uploaded files against the current remote
                updateSyncState(category) {
                    it.copy(
                        isLoading = false,
                        verifiedFiles = verifiedCount,
                        deletedFromS3 = deletedCount,
                        uploadedFiles = verifiedCount,
                        lastSynced = System.currentTimeMillis()
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("RemoteSyncViewModel", "Sync failed for ${category.displayName}", e)
                updateSyncState(category) {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Sync failed"
                    )
                }
            }
        }
    }

    fun syncAllCategories() {
        FolderCategory.entries.forEach { category ->
            if (category != FolderCategory.ALL_FILES) {
                syncCategory(category)
            }
        }
    }

    /**
     * Upload all files in a category to S3.
     * Shows progress and marks all as uploaded on success.
     */
    fun uploadAllFiles(category: FolderCategory) {
        viewModelScope.launch {
            // Mark as uploading
            updateSyncState(category) {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    currentUploadIndex = 0,
                    error = null
                )
            }

            try {
                // Get all files in this category
                val allFiles = mediaRepository.getFilesForCategory(category).first()
                val type = activeRemoteType.first()
                val filesToUpload = allFiles.filter { !isFileOnRemote(it, type) }

                if (filesToUpload.isEmpty()) {
                    updateSyncState(category) {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f
                        )
                    }
                    android.util.Log.d("RemoteSyncViewModel", "${category.displayName}: All files already uploaded")
                    return@launch
                }

                android.util.Log.d("RemoteSyncViewModel", "${category.displayName}: Uploading ${filesToUpload.size} files")

                updateSyncState(category) {
                    it.copy(totalFilesToUpload = filesToUpload.size)
                }

                // Upload each file
                var successCount = 0
                filesToUpload.forEachIndexed { index, mediaFile ->
                    val progress = (index + 1).toFloat() / filesToUpload.size

                    updateSyncState(category) {
                        it.copy(
                            currentUploadIndex = index + 1,
                            uploadProgress = progress
                        )
                    }

                    android.util.Log.d("RemoteSyncViewModel", "Uploading ${mediaFile.fileName} (${index + 1}/${filesToUpload.size})")

                    val result = remoteStorageRepository.uploadFile(mediaFile)
                    result.onSuccess { url ->
                        mediaRepository.markAsUploaded(mediaFile.id, url)
                        successCount++
                        android.util.Log.d("RemoteSyncViewModel", "Uploaded: ${mediaFile.fileName}")
                    }.onFailure { error ->
                        android.util.Log.e("RemoteSyncViewModel", "Failed to upload ${mediaFile.fileName}", error)
                    }
                }

                android.util.Log.d("RemoteSyncViewModel", "${category.displayName}: Upload complete - $successCount/${filesToUpload.size} succeeded")

                // Reload counts and update state
                val updatedFiles = mediaRepository.getFilesForCategory(category).first()
                val uploadedCount = updatedFiles.count { isFileOnRemote(it, type) }

                updateSyncState(category) {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 1f,
                        uploadedFiles = uploadedCount,
                        totalFiles = updatedFiles.size
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("RemoteSyncViewModel", "Upload failed for ${category.displayName}", e)
                updateSyncState(category) {
                    it.copy(
                        isUploading = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            }
        }
    }

    private fun updateSyncState(category: FolderCategory, update: (SyncState) -> SyncState) {
        _syncStates.value = _syncStates.value.toMutableMap().apply {
            this[category] = update(this[category] ?: SyncState())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSyncScreen(
    navController: NavController,
    viewModel: RemoteSyncViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val syncStates by viewModel.syncStates.collectAsState()
    val activeRemoteType by viewModel.activeRemoteType.collectAsState(initial = com.kcpd.myfolder.data.model.RemoteType.S3_MINIO)
    val remoteName = if (activeRemoteType == com.kcpd.myfolder.data.model.RemoteType.GOOGLE_DRIVE) "Google Drive" else "S3"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Upload Status") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Sync all button
                    IconButton(
                        onClick = { viewModel.syncAllCategories() }
                    ) {
                        Icon(Icons.Default.Sync, "Sync All")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Verify Upload Status",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check if uploaded files still exist on $remoteName. Files deleted from server will be marked for re-upload.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Category list
            items(
                items = FolderCategory.entries.filter { it != FolderCategory.ALL_FILES },
                key = { it.name }
            ) { category ->
                CategorySyncCard(
                    category = category,
                    syncState = syncStates[category] ?: RemoteSyncViewModel.SyncState(),
                    onSyncClick = { viewModel.syncCategory(category) },
                    onUploadClick = { viewModel.uploadAllFiles(category) },
                    remoteName = remoteName
                )
            }
        }
    }
}

@Composable
fun CategorySyncCard(
    category: FolderCategory,
    syncState: RemoteSyncViewModel.SyncState,
    onSyncClick: () -> Unit,
    onUploadClick: () -> Unit,
    remoteName: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !syncState.isLoading && !syncState.isUploading) { onSyncClick() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Category icon
            Icon(
                imageVector = category.icon,
                contentDescription = category.displayName,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Category info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status text
                if (syncState.isUploading) {
                    Text(
                        text = "Processing ${syncState.currentUploadIndex}/${syncState.totalFilesToUpload}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (syncState.isLoading) {
                    Text(
                        text = "Syncing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (syncState.error != null) {
                    Text(
                        text = "Error: ${syncState.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (syncState.lastSynced != null) {
                    Column {
                        Text(
                            text = "${syncState.uploadedFiles} uploaded • ${syncState.totalFiles} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (syncState.deletedFromS3 > 0) {
                            Text(
                                text = "⚠️ ${syncState.deletedFromS3} deleted from $remoteName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (syncState.verifiedFiles > 0) {
                            Text(
                                text = "✓ ${syncState.verifiedFiles} verified",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${syncState.uploadedFiles} uploaded • ${syncState.totalFiles} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

                // Buttons: Upload and Sync
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Upload all button
                    if (syncState.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onUploadClick,
                            enabled = !syncState.isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload All ${category.displayName}",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Sync/verify button
                    if (syncState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onSyncClick,
                            enabled = !syncState.isUploading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync ${category.displayName}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Progress bar for upload
            if (syncState.isUploading && syncState.uploadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { syncState.uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

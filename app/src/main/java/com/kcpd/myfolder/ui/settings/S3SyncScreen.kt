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
import com.kcpd.myfolder.data.repository.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class S3SyncViewModel @Inject constructor(
    application: Application,
    private val mediaRepository: MediaRepository,
    private val s3Repository: S3Repository
) : AndroidViewModel(application) {

    private val _syncStates = MutableStateFlow<Map<FolderCategory, SyncState>>(emptyMap())
    val syncStates: StateFlow<Map<FolderCategory, SyncState>> = _syncStates.asStateFlow()

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
        // Load initial states
        loadCategoryCounts()
    }

    private fun loadCategoryCounts() {
        viewModelScope.launch {
            val states = mutableMapOf<FolderCategory, SyncState>()

            FolderCategory.entries.forEach { category ->
                if (category != FolderCategory.ALL_FILES) {
                    val files = mediaRepository.getFilesForCategory(category).first()
                    val uploadedCount = files.count { it.isUploaded }

                    states[category] = SyncState(
                        totalFiles = files.size,
                        uploadedFiles = uploadedCount
                    )
                }
            }

            _syncStates.value = states
        }
    }

    fun syncCategory(category: FolderCategory) {
        viewModelScope.launch {
            // Mark as loading
            updateSyncState(category) { it.copy(isLoading = true, error = null) }

            try {
                // Get all files for this category
                val allFiles = mediaRepository.getFilesForCategory(category).first()
                val uploadedFiles = allFiles.filter { it.isUploaded }

                if (uploadedFiles.isEmpty()) {
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

                android.util.Log.d("S3SyncViewModel", "Syncing ${category.displayName}: ${uploadedFiles.size} uploaded files")

                // Verify all uploaded files
                val results = s3Repository.verifyMultipleFiles(uploadedFiles)

                var deletedCount = 0
                var verifiedCount = 0

                results.forEach { (fileId, exists) ->
                    if (exists) {
                        verifiedCount++
                    } else {
                        // File deleted from S3 - mark as not uploaded
                        mediaRepository.markAsNotUploaded(fileId)
                        deletedCount++
                    }
                }

                android.util.Log.d("S3SyncViewModel", "${category.displayName}: Verified $verifiedCount, Deleted $deletedCount")

                // Update state with results
                updateSyncState(category) {
                    it.copy(
                        isLoading = false,
                        verifiedFiles = verifiedCount,
                        deletedFromS3 = deletedCount,
                        uploadedFiles = it.uploadedFiles - deletedCount, // Update uploaded count
                        lastSynced = System.currentTimeMillis()
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("S3SyncViewModel", "Sync failed for ${category.displayName}", e)
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
                val filesToUpload = allFiles.filter { !it.isUploaded }

                if (filesToUpload.isEmpty()) {
                    updateSyncState(category) {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f
                        )
                    }
                    android.util.Log.d("S3SyncViewModel", "${category.displayName}: All files already uploaded")
                    return@launch
                }

                android.util.Log.d("S3SyncViewModel", "${category.displayName}: Uploading ${filesToUpload.size} files")

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

                    android.util.Log.d("S3SyncViewModel", "Uploading ${mediaFile.fileName} (${index + 1}/${filesToUpload.size})")

                    val result = s3Repository.uploadFile(mediaFile)
                    result.onSuccess { url ->
                        mediaRepository.markAsUploaded(mediaFile.id, url)
                        successCount++
                        android.util.Log.d("S3SyncViewModel", "Uploaded: ${mediaFile.fileName}")
                    }.onFailure { error ->
                        android.util.Log.e("S3SyncViewModel", "Failed to upload ${mediaFile.fileName}", error)
                    }
                }

                android.util.Log.d("S3SyncViewModel", "${category.displayName}: Upload complete - $successCount/${filesToUpload.size} succeeded")

                // Reload counts and update state
                val updatedFiles = mediaRepository.getFilesForCategory(category).first()
                val uploadedCount = updatedFiles.count { it.isUploaded }

                updateSyncState(category) {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 1f,
                        uploadedFiles = uploadedCount,
                        totalFiles = updatedFiles.size
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("S3SyncViewModel", "Upload failed for ${category.displayName}", e)
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
fun S3SyncScreen(
    navController: NavController,
    viewModel: S3SyncViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val syncStates by viewModel.syncStates.collectAsState()

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
                            text = "Check if uploaded files still exist on S3. Files deleted from server will be marked for re-upload.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Category list
            items(
                FolderCategory.entries.filter { it != FolderCategory.ALL_FILES }
            ) { category ->
                CategorySyncCard(
                    category = category,
                    syncState = syncStates[category] ?: S3SyncViewModel.SyncState(),
                    onSyncClick = { viewModel.syncCategory(category) },
                    onUploadClick = { viewModel.uploadAllFiles(category) }
                )
            }
        }
    }
}

@Composable
fun CategorySyncCard(
    category: FolderCategory,
    syncState: S3SyncViewModel.SyncState,
    onSyncClick: () -> Unit,
    onUploadClick: () -> Unit
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
                        text = "Uploading ${syncState.currentUploadIndex}/${syncState.totalFilesToUpload}...",
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
                                text = "⚠️ ${syncState.deletedFromS3} deleted from S3",
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

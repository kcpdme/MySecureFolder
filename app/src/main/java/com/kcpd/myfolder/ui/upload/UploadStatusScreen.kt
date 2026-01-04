package com.kcpd.myfolder.ui.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kcpd.myfolder.domain.model.FileUploadState
import com.kcpd.myfolder.domain.model.RemoteUploadResult
import com.kcpd.myfolder.domain.model.UploadStatus

/**
 * Full-screen upload status dialog.
 * Shows upload progress for all files across all remotes with per-remote statistics.
 * 
 * Features:
 * - Per-remote progress bars with status counts
 * - File list with individual upload status per remote
 * - Action buttons: Cancel All, Retry Failed, Clear Completed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatusScreen(
    uploadStates: Map<String, FileUploadState>,
    onDismiss: () -> Unit,
    onRetry: (fileId: String, remoteId: String) -> Unit,
    onClearCompleted: () -> Unit,
    onCancelAllPending: (() -> Unit)? = null,
    onRetryAllFailed: (() -> Unit)? = null,
    pendingQueueCount: Int = 0
) {
    // Sort by createdAt descending (most recently added first)
    val orderedStates = remember(uploadStates) {
        uploadStates.values.sortedByDescending { it.createdAt }
    }

    val totalFiles = orderedStates.size
    val completedFiles = orderedStates.count { it.isComplete }
    val failedFiles = orderedStates.count { it.allFailed }
    val hasCompletedFiles = completedFiles > 0
    val hasFailedFiles = failedFiles > 0
    val hasPendingUploads = pendingQueueCount > 0 || orderedStates.any { !it.isComplete && it.activeCount == 0 }

    // Calculate per-remote statistics
    val perRemoteStats = remember(uploadStates) {
        val allResults = uploadStates.values.flatMap { fileState ->
            fileState.remoteResults.values.map { result ->
                result.remoteName to result
            }
        }
        
        allResults.groupBy { it.first }
            .map { (remoteName, results) ->
                val firstColor = results.firstOrNull()?.second?.remoteColor ?: Color.Gray
                RemoteStats(
                    name = remoteName,
                    completed = results.count { it.second.status == UploadStatus.SUCCESS },
                    uploading = results.count { it.second.status == UploadStatus.IN_PROGRESS },
                    failed = results.count { it.second.status == UploadStatus.FAILED },
                    queued = results.count { it.second.status == UploadStatus.QUEUED },
                    total = results.size,
                    color = firstColor
                )
            }
            .sortedBy { it.name }
    }

    val totalUploadedAcrossRemotes = perRemoteStats.sumOf { it.completed }
    val totalTasksAcrossRemotes = perRemoteStats.sumOf { it.total }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Upload Status",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$totalFiles file${if (totalFiles > 1) "s" else ""} • $totalUploadedAcrossRemotes/$totalTasksAcrossRemotes completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (hasCompletedFiles) {
                            TextButton(onClick = onClearCompleted) {
                                Icon(
                                    Icons.Default.ClearAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
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
            ) {
                // Per-remote statistics
                if (perRemoteStats.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        perRemoteStats.forEach { stats ->
                            RemoteProgressRow(stats)
                        }
                    }
                }

                // Action buttons
                if (hasPendingUploads || hasFailedFiles) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasPendingUploads && onCancelAllPending != null) {
                            OutlinedButton(
                                onClick = onCancelAllPending,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel All")
                            }
                        }
                        
                        if (hasFailedFiles && onRetryAllFailed != null) {
                            OutlinedButton(onClick = onRetryAllFailed) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry Failed ($failedFiles)")
                            }
                        }
                    }
                }

                HorizontalDivider()

                // File list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (orderedStates.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(orderedStates, key = { it.fileId }) { state ->
                                FileUploadCard(
                                    uploadState = state,
                                    onRetry = { remoteId -> onRetry(state.fileId, remoteId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Data class for per-remote statistics
private data class RemoteStats(
    val name: String,
    val completed: Int,
    val uploading: Int,
    val failed: Int,
    val queued: Int,
    val total: Int,
    val color: Color
)

@Composable
private fun RemoteProgressRow(stats: RemoteStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(stats.color, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        
        // Remote name
        Text(
            text = stats.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        
        // Progress bar
        val progress = if (stats.total > 0) stats.completed.toFloat() / stats.total else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(6.dp),
            color = when {
                stats.failed > 0 -> MaterialTheme.colorScheme.error
                stats.completed == stats.total -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(Modifier.width(8.dp))
        
        // Stats summary
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✓${stats.completed}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
            if (stats.uploading > 0) {
                Text("⏳${stats.uploading}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (stats.failed > 0) {
                Text("✗${stats.failed}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (stats.queued > 0) {
                Text("⋯${stats.queued}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("No active uploads", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FileUploadCard(
    uploadState: FileUploadState,
    onRetry: (remoteId: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // File info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uploadState.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(uploadState.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                StatusBadge(uploadState)
            }

            // Status summary
            Text(
                text = uploadState.statusSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            HorizontalDivider()

            // Remote status list
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uploadState.allResults.forEach { result ->
                    RemoteStatusRow(
                        result = result,
                        onRetry = { onRetry(result.remoteId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(uploadState: FileUploadState) {
    val (text, color) = when {
        uploadState.isComplete -> "Done" to Color(0xFF4CAF50)
        uploadState.allFailed -> "Failed" to MaterialTheme.colorScheme.error
        uploadState.activeCount > 0 -> "Uploading" to MaterialTheme.colorScheme.primary
        else -> "Queued" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RemoteStatusRow(
    result: RemoteUploadResult,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(result.remoteColor, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        
        // Remote name
        Text(
            text = result.remoteName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        
        // Status icon and text
        when (result.status) {
            UploadStatus.SUCCESS -> {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
            }
            UploadStatus.IN_PROGRESS -> {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(4.dp))
                Text("${(result.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            UploadStatus.FAILED -> {
                Icon(Icons.Default.Error, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
            UploadStatus.QUEUED -> {
                Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    
    // Error message
    if (result.status == UploadStatus.FAILED && result.errorMessage != null) {
        Text(
            text = result.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

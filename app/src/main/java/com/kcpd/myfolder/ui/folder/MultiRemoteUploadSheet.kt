package com.kcpd.myfolder.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.kcpd.myfolder.domain.model.FileUploadState
import com.kcpd.myfolder.domain.model.RemoteUploadResult
import com.kcpd.myfolder.domain.model.UploadStatus

/**
 * Bottom sheet displaying multi-remote upload progress.
 * Shows each file being uploaded with status indicators for each configured remote.
 * 
 * Enhanced to support WorkManager queue management:
 * - Cancel all pending uploads
 * - Retry all failed uploads
 * - Clear completed uploads
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiRemoteUploadSheet(
    uploadStates: Map<String, FileUploadState>,
    onDismiss: () -> Unit,
    onRetry: (fileId: String, remoteId: String) -> Unit,
    onClearCompleted: () -> Unit,
    onCancelAllPending: (() -> Unit)? = null,
    onRetryAllFailed: (() -> Unit)? = null,
    pendingQueueCount: Int = 0 // From WorkManager queue
) {
    // Sort by createdAt descending (most recently added first)
    // This ensures new uploads appear at the top
    val orderedStates = remember(uploadStates) {
        uploadStates.values.sortedByDescending { it.createdAt }
    }

    val totalFiles = orderedStates.size
    val completedFiles = orderedStates.count { it.isComplete }
    val failedFiles = orderedStates.count { it.allFailed }
    val hasCompletedFiles = orderedStates.any { it.isComplete }
    val hasFailedFiles = failedFiles > 0
    val hasActiveUploads = orderedStates.any { it.activeCount > 0 }
    val hasPendingUploads = pendingQueueCount > 0 || orderedStates.any { !it.isComplete && it.activeCount == 0 }

    // Fullscreen sheet - can be dismissed by swipe or close button
    // User can always bring it back using the upload status icon in toolbar
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Upload Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Calculate file-based counts for display
                    val inProgressFiles = orderedStates.count { it.activeCount > 0 }
                    val uploadingToRemotes = orderedStates.sumOf { it.activeCount }
                    
                    Text(
                        text = when {
                            hasActiveUploads -> {
                                // Show: "3 files uploading (5 tasks), 2 completed"
                                val uploadingPart = if (inProgressFiles > 0) {
                                    "$inProgressFiles file${if (inProgressFiles > 1) "s" else ""} uploading"
                                } else if (pendingQueueCount > 0) {
                                    "$pendingQueueCount queued task${if (pendingQueueCount > 1) "s" else ""}"
                                } else {
                                    "Processing..."
                                }
                                if (completedFiles > 0) {
                                    "$uploadingPart • $completedFiles completed"
                                } else {
                                    uploadingPart
                                }
                            }
                            failedFiles > 0 -> {
                                // Show: "2 completed, 1 failed"
                                "$completedFiles completed • $failedFiles failed"
                            }
                            completedFiles == totalFiles && totalFiles > 0 -> {
                                // All done
                                "All $totalFiles file${if (totalFiles > 1) "s" else ""} uploaded"
                            }
                            else -> "Ready"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel All Pending button
                if (hasPendingUploads && onCancelAllPending != null) {
                    OutlinedButton(
                        onClick = onCancelAllPending,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel All")
                    }
                }
                
                // Retry All Failed button
                if (hasFailedFiles && onRetryAllFailed != null) {
                    OutlinedButton(onClick = onRetryAllFailed) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retry Failed ($failedFiles)")
                    }
                }
                
                // Clear Completed button
                if (hasCompletedFiles) {
                    TextButton(onClick = onClearCompleted) {
                        Text("Clear Completed")
                    }
                }
            }

            HorizontalDivider()

            // Upload list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (orderedStates.isEmpty()) {
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
                            Text(
                                text = "All uploads completed",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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

@Composable
fun FileUploadCard(
    uploadState: FileUploadState,
    onRetry: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File header
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
                        text = formatFileSizeMultiRemote(uploadState.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Overall status badge
                UploadStatusBadge(uploadState)
            }

            // Status summary
            Text(
                text = uploadState.statusSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Divider()

            // Remote status list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uploadState.allResults.forEach { remoteResult ->
                    RemoteUploadRow(
                        result = remoteResult,
                        onRetry = { onRetry(remoteResult.remoteId) }
                    )
                }
            }
        }
    }
}

@Composable
fun UploadStatusBadge(uploadState: FileUploadState) {
    val (icon, color, text) = when {
        uploadState.allSucceeded -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            "Done"
        )
        uploadState.allFailed -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            "Failed"
        )
        uploadState.activeCount > 0 -> Triple(
            Icons.Default.CloudUpload,
            MaterialTheme.colorScheme.tertiary,
            "Uploading"
        )
        else -> Triple(
            Icons.Default.Schedule,
            MaterialTheme.colorScheme.secondary,
            "Queued"
        )
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RemoteUploadRow(
    result: RemoteUploadResult,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator and remote name
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color circle
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(result.remoteColor, CircleShape)
            )

            Text(
                text = result.remoteName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (result.status) {
                UploadStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                UploadStatus.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    if (result.progress > 0f) {
                        Text(
                            text = "${(result.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                UploadStatus.QUEUED -> {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Queued",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                UploadStatus.FAILED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        TextButton(
                            onClick = onRetry,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    // Error message if failed
    if (result.status == UploadStatus.FAILED && result.errorMessage != null) {
        Text(
            text = result.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp)
        )
    }
}

/**
 * Format file size in human-readable format
 */
private fun formatFileSizeMultiRemote(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

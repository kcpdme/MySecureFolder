package com.kcpd.myfolder.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType

/**
 * Dialog showing detailed information about a media file with preview and actions.
 */
@Composable
fun MediaDetailDialog(
    mediaFile: MediaFile,
    onDismiss: () -> Unit,
    onDelete: (MediaFile) -> Unit,
    onUpload: (MediaFile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mediaFile.fileName) },
        text = {
            Column {
                when (mediaFile.mediaType) {
                    MediaType.PHOTO -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(mediaFile)  // Pass MediaFile directly for decryption
                                .crossfade(true)
                                .build(),
                            contentDescription = mediaFile.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    MediaType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    MediaType.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Audio",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    MediaType.NOTE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Note,
                                contentDescription = "Note",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    MediaType.PDF -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "PDF",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Type: ${mediaFile.mediaType.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Size: ${formatFileSize(mediaFile.size)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Created: ${formatDate(mediaFile.createdAt)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onUpload(mediaFile) }) {
                    Icon(Icons.Default.CloudUpload, "Upload")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload")
                }
                TextButton(
                    onClick = { onDelete(mediaFile) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, "Delete")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog for confirming cloud upload of a media file.
 */
@Composable
fun UploadDialog(
    mediaFile: MediaFile,
    onDismiss: () -> Unit,
    onUpload: (MediaFile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudUpload, "Upload") },
        title = { Text("Upload to S3/Minio") },
        text = { Text("Upload ${mediaFile.fileName} to cloud storage?") },
        confirmButton = {
            TextButton(onClick = { onUpload(mediaFile) }) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

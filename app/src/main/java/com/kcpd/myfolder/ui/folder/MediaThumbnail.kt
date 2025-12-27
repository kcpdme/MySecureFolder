package com.kcpd.myfolder.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.MediaType

/**
 * Grid thumbnail component for displaying media files in grid view mode.
 * Shows preview, upload status, and selection state.
 */
@Composable
fun MediaThumbnail(
    mediaFile: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isUploading: Boolean,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    android.util.Log.d("MediaThumbnail", "Rendering thumbnail for: ${mediaFile.fileName}, type: ${mediaFile.mediaType}")

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)  // Small padding between grid items
            .clip(RoundedCornerShape(4.dp))  // Slight rounding like Tella
            .pointerInput(mediaFile.id) {
                detectTapGestures(
                    onTap = {
                        android.util.Log.d("MediaThumbnail", "Thumbnail tapped: ${mediaFile.fileName}")
                        onClick()
                    },
                    onLongPress = {
                        android.util.Log.d("MediaThumbnail", "Thumbnail long pressed: ${mediaFile.fileName}")
                        onLongClick()
                    }
                )
            }
    ) {
        when (mediaFile.mediaType) {
            MediaType.PHOTO -> {
                android.util.Log.d("MediaThumbnail", "Loading photo: ${mediaFile.fileName}, has thumbnail: ${mediaFile.thumbnail != null}")
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        // Use thumbnail if available (fast, no decryption), otherwise fall back to full file
                        .data(mediaFile.thumbnail ?: mediaFile)
                        .crossfade(true)
                        .listener(
                            onStart = {
                                android.util.Log.d("MediaThumbnail", "Image load started: ${mediaFile.fileName}")
                            },
                            onSuccess = { _, _ ->
                                android.util.Log.d("MediaThumbnail", "Image load SUCCESS: ${mediaFile.fileName}")
                            },
                            onError = { _, result ->
                                android.util.Log.e("MediaThumbnail", "Image load ERROR: ${mediaFile.fileName}, error: ${result.throwable.message}", result.throwable)
                            }
                        )
                        .build(),
                    contentDescription = mediaFile.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaType.VIDEO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            // Use thumbnail if available (fast, no decryption), otherwise fall back to full file
                            .data(mediaFile.thumbnail ?: mediaFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = mediaFile.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            MediaType.AUDIO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Audio",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaFile.fileName.removeSuffix(".mp3").removeSuffix(".m4a").removeSuffix(".aac"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2
                        )
                    }
                }
            }
            MediaType.NOTE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Note,
                            contentDescription = "Note",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaFile.fileName.removeSuffix(".txt"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2
                        )
                    }
                }
            }
            MediaType.PDF -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaFile.fileName.removeSuffix(".pdf"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        if (mediaFile.isUploaded) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = "Uploaded",
                tint = Color.White,
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(2.dp)
            )
        }

        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Modern selection indicator - small circular badge
        if (isMultiSelectMode && isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Border animation for selection
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(0.dp)
                    )
            )
        }
    }
}

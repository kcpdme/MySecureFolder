package com.kcpd.myfolder.ui.audio

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kcpd.myfolder.ui.theme.TellaAccent
import com.kcpd.myfolder.ui.theme.TellaPurple
import com.kcpd.myfolder.ui.theme.TellaPurpleLight
import kotlinx.coroutines.delay

/**
 * A compact mini audio player that shows at the bottom of screens
 * when audio is playing in the background. Allows play/pause control,
 * previous/next navigation, and shows the currently playing file name.
 */
@Composable
fun MiniAudioPlayer(
    modifier: Modifier = Modifier,
    onPlayerClick: () -> Unit = {}
) {
    val playbackState by AudioPlaybackManager.playbackState.collectAsState()
    val currentPosition by AudioPlaybackManager.currentPosition.collectAsState()
    val duration by AudioPlaybackManager.duration.collectAsState()
    
    // Update position periodically
    LaunchedEffect(playbackState.isPlaying) {
        while (playbackState.isPlaying) {
            AudioPlaybackManager.updatePosition()
            delay(500)
        }
    }
    
    // Only show when there's a file loaded
    AnimatedVisibility(
        visible = playbackState.currentMediaFile != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        playbackState.currentMediaFile?.let { mediaFile ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TellaPurpleLight,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Column {
                    // Add status bar padding
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerClick() }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Music icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TellaPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Audio",
                                tint = TellaAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        // File name and time
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = mediaFile.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        
                        // Previous button
                        IconButton(
                            onClick = { AudioPlaybackManager.playPrevious() },
                            enabled = AudioPlaybackManager.hasPrevious(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = if (AudioPlaybackManager.hasPrevious()) 
                                    Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Play/Pause button
                        IconButton(
                            onClick = { AudioPlaybackManager.togglePlayPause() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TellaAccent)
                        ) {
                            Icon(
                                if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        
                        // Next button
                        IconButton(
                            onClick = { AudioPlaybackManager.playNext() },
                            enabled = AudioPlaybackManager.hasNext(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = if (AudioPlaybackManager.hasNext()) 
                                    Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Close/Stop button
                        IconButton(
                            onClick = { AudioPlaybackManager.stop() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    
                    // Progress bar at bottom
                    LinearProgressIndicator(
                        progress = { if (duration > 0) currentPosition.toFloat() / duration else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = TellaAccent,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis < 0) return "00:00"
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

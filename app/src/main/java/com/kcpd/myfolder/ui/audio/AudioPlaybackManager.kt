package com.kcpd.myfolder.ui.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kcpd.myfolder.data.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Singleton manager for audio playback across the app.
 * Allows audio to continue playing when navigating away and provides
 * a global way to control playback from anywhere.
 */
object AudioPlaybackManager {
    private const val TAG = "AudioPlaybackManager"
    
    private var exoPlayer: ExoPlayer? = null
    private var currentDecryptedFile: File? = null
    private var appContext: Context? = null
    
    // Playlist management
    private var playlist: List<MediaFile> = emptyList()
    private var currentIndex: Int = -1
    private var decryptCallback: (suspend (MediaFile) -> File?)? = null
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentMediaFile: MediaFile? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentIndex: Int = -1,
        val playlistSize: Int = 0
    )
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                setAudioAttributes(audioAttributes, true)
                setVolume(1.0f)
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = this@apply.duration
                        }
                        // Auto play next when track ends
                        if (playbackState == Player.STATE_ENDED) {
                            playNext()
                        }
                    }
                    
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _playbackState.value = _playbackState.value.copy(isPlaying = playing)
                    }
                })
            }
            Log.d(TAG, "ExoPlayer initialized")
        }
    }
    
    fun setPlaylist(files: List<MediaFile>) {
        playlist = files
        _playbackState.value = _playbackState.value.copy(playlistSize = files.size)
        Log.d(TAG, "Playlist set with ${files.size} files")
    }
    
    fun setDecryptCallback(callback: suspend (MediaFile) -> File?) {
        decryptCallback = callback
    }
    
    fun play(mediaFile: MediaFile, decryptedFile: File, context: Context) {
        initialize(context)
        
        // Update current index in playlist
        val index = playlist.indexOfFirst { it.id == mediaFile.id }
        currentIndex = index
        
        // Clean up previous decrypted file if different
        if (currentDecryptedFile != decryptedFile) {
            cleanupDecryptedFile()
        }
        
        currentDecryptedFile = decryptedFile
        
        _playbackState.value = PlaybackState(
            isLoading = false,
            currentMediaFile = mediaFile,
            currentIndex = currentIndex,
            playlistSize = playlist.size
        )
        
        exoPlayer?.apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(decryptedFile))
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        
        Log.d(TAG, "Playing: ${mediaFile.fileName} (index: $currentIndex)")
    }
    
    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    fun resume() {
        exoPlayer?.play()
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    fun seekForward(milliseconds: Long = 10000) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + milliseconds).coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }
    
    fun seekBackward(milliseconds: Long = 10000) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - milliseconds).coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }
    
    fun hasPrevious(): Boolean = currentIndex > 0
    
    fun hasNext(): Boolean = currentIndex < playlist.size - 1
    
    fun playPrevious() {
        if (hasPrevious()) {
            currentIndex--
            playAtIndex(currentIndex)
        }
    }
    
    fun playNext() {
        if (hasNext()) {
            currentIndex++
            playAtIndex(currentIndex)
        }
    }
    
    private fun playAtIndex(index: Int) {
        if (index >= 0 && index < playlist.size) {
            val mediaFile = playlist[index]
            _playbackState.value = _playbackState.value.copy(
                isLoading = true,
                currentMediaFile = mediaFile,
                currentIndex = index
            )
            
            // We need to decrypt this file - signal that we need decryption
            // The actual decryption should be handled by the UI layer
            Log.d(TAG, "Requesting playback of: ${mediaFile.fileName} at index $index")
        }
    }
    
    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _playbackState.value = PlaybackState()
        _currentPosition.value = 0L
        _duration.value = 0L
        currentIndex = -1
        cleanupDecryptedFile()
        Log.d(TAG, "Playback stopped and cleaned up")
    }
    
    fun updatePosition() {
        exoPlayer?.let { player ->
            _currentPosition.value = player.currentPosition
        }
    }
    
    fun getPlayer(): ExoPlayer? = exoPlayer
    
    private fun cleanupDecryptedFile() {
        currentDecryptedFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleaned up decrypted file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete decrypted file", e)
            }
        }
        currentDecryptedFile = null
    }
    
    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
        playlist = emptyList()
        Log.d(TAG, "AudioPlaybackManager released")
    }
}

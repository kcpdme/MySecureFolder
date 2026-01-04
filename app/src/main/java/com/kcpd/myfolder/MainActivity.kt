package com.kcpd.myfolder

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kcpd.myfolder.security.CamouflageManager
import com.kcpd.myfolder.security.VaultManager
import com.kcpd.myfolder.ui.audio.AudioPlaybackManager
import com.kcpd.myfolder.ui.audio.MiniAudioPlayer
import com.kcpd.myfolder.ui.navigation.MyFolderNavHost
import com.kcpd.myfolder.ui.theme.MyFolderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var vaultManager: VaultManager
    
    @Inject
    lateinit var camouflageManager: CamouflageManager

    // Pending capture action from widget deep link - using StateFlow for proper observation
    private val _pendingCaptureAction = MutableStateFlow<String?>(null)
    private val pendingCaptureAction = _pendingCaptureAction.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Sync launcher icon state with saved preference
        camouflageManager.syncLauncherState()
        
        // Handle deep link intent
        handleDeepLinkIntent(intent)
        
        enableEdgeToEdge()
        setContent {
            MyFolderTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                // Observe pending action as StateFlow
                val pendingAction by pendingCaptureAction.collectAsState()
                
                // Get playback state to determine if mini player should show
                val playbackState by AudioPlaybackManager.playbackState.collectAsState()
                
                // Get vault state to know when vault is unlocked
                val vaultState by vaultManager.vaultState.collectAsState()
                
                // Hide mini player on audio viewer screen (already has full player)
                val isAudioViewerScreen = currentRoute?.startsWith("audio_viewer") == true
                val showMiniPlayer = playbackState.currentMediaFile != null && !isAudioViewerScreen
                
                // Handle pending capture action after vault unlock
                // Wait for: vault unlocked, action pending, nav graph ready (currentRoute not null),
                // and we're on home screen (vault unlock has completed navigation)
                LaunchedEffect(vaultState, pendingAction, currentRoute) {
                    if (vaultState is VaultManager.VaultState.Unlocked 
                        && pendingAction != null 
                        && currentRoute == "home") {
                        val action = pendingAction
                        _pendingCaptureAction.value = null
                        
                        android.util.Log.d("MainActivity", "🚀 Navigating to $action from widget")
                        when (action) {
                            "photo" -> navController.navigate("photo_camera")
                            "video" -> navController.navigate("video_recorder")
                            "audio" -> navController.navigate("audio_recorder")
                        }
                    }
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    MyFolderNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        vaultManager = vaultManager,
                        camouflageManager = camouflageManager
                    )
                    
                    // Mini player at top of screen
                    if (showMiniPlayer) {
                        MiniAudioPlayer(
                            modifier = Modifier.align(Alignment.TopCenter),
                            onPlayerClick = {
                                // Navigate to audio viewer when mini player is clicked
                                playbackState.currentMediaFile?.let { mediaFile ->
                                    navController.navigate("audio_viewer/0?fileId=${mediaFile.id}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "myfolder" && uri.host == "capture") {
                // Extract capture type: photo, video, or audio
                val captureType = uri.pathSegments.firstOrNull()
                if (captureType in listOf("photo", "video", "audio")) {
                    _pendingCaptureAction.value = captureType
                    android.util.Log.d("MainActivity", "📸 Widget deep link: capture/$captureType")
                }
            }
        }
    }
}

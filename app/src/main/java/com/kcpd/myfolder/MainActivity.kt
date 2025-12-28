package com.kcpd.myfolder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kcpd.myfolder.security.VaultManager
import com.kcpd.myfolder.ui.audio.AudioPlaybackManager
import com.kcpd.myfolder.ui.audio.MiniAudioPlayer
import com.kcpd.myfolder.ui.navigation.MyFolderNavHost
import com.kcpd.myfolder.ui.theme.MyFolderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var vaultManager: VaultManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFolderTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                // Get playback state to determine if mini player should show
                val playbackState by AudioPlaybackManager.playbackState.collectAsState()
                
                // Hide mini player on audio viewer screen (already has full player)
                val isAudioViewerScreen = currentRoute?.startsWith("audio_viewer") == true
                val showMiniPlayer = playbackState.currentMediaFile != null && !isAudioViewerScreen
                
                Box(modifier = Modifier.fillMaxSize()) {
                    MyFolderNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        vaultManager = vaultManager
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
}

package com.kcpd.myfolder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kcpd.myfolder.security.VaultManager
import com.kcpd.myfolder.ui.navigation.MyFolderNavHost
import com.kcpd.myfolder.ui.theme.MyFolderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vaultManager: VaultManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFolderTheme {
                MyFolderNavHost(
                    modifier = Modifier.fillMaxSize(),
                    vaultManager = vaultManager
                )
            }
        }
    }
}

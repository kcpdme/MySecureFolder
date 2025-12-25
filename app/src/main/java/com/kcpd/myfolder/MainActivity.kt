package com.kcpd.myfolder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kcpd.myfolder.ui.navigation.MyFolderNavHost
import com.kcpd.myfolder.ui.theme.MyFolderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFolderTheme {
                MyFolderNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

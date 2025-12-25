package com.kcpd.myfolder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kcpd.myfolder.ui.camera.CameraScreen
import com.kcpd.myfolder.ui.gallery.GalleryScreen
import com.kcpd.myfolder.ui.settings.S3ConfigScreen

@Composable
fun MyFolderNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "gallery",
        modifier = modifier
    ) {
        composable("gallery") {
            GalleryScreen(navController = navController)
        }

        composable("camera") {
            CameraScreen(navController = navController)
        }

        composable("s3_config") {
            S3ConfigScreen(navController = navController)
        }
    }
}

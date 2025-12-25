package com.kcpd.myfolder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.ui.camera.CameraScreen
import com.kcpd.myfolder.ui.folder.FolderScreen
import com.kcpd.myfolder.ui.gallery.MediaViewerScreen
import com.kcpd.myfolder.ui.home.HomeScreen
import com.kcpd.myfolder.ui.settings.S3ConfigScreen

@Composable
fun MyFolderNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onFolderClick = { category ->
                    navController.navigate("folder/${category.path}")
                },
                onSettingsClick = {
                    navController.navigate("s3_config")
                }
            )
        }

        composable(
            route = "folder/{category}",
            arguments = listOf(navArgument("category") { type = NavType.StringType })
        ) {
            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = {
                    // Navigate to appropriate capture/creation screen based on category
                    // For now, just navigate to camera - will be refined in later phases
                    navController.navigate("camera")
                }
            )
        }

        composable("camera") {
            CameraScreen(navController = navController)
        }

        composable("s3_config") {
            S3ConfigScreen(navController = navController)
        }

        composable(
            route = "media_viewer/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType })
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            MediaViewerScreen(
                navController = navController,
                initialIndex = index
            )
        }
    }
}

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
import com.kcpd.myfolder.ui.camera.AudioRecorderScreen
import com.kcpd.myfolder.ui.camera.CameraScreen
import com.kcpd.myfolder.ui.camera.PhotoCameraScreen
import com.kcpd.myfolder.ui.camera.VideoRecorderScreen
import com.kcpd.myfolder.ui.folder.FolderScreen
import com.kcpd.myfolder.ui.gallery.MediaViewerScreen
import com.kcpd.myfolder.ui.home.HomeScreen
import com.kcpd.myfolder.ui.note.NoteEditorScreen
import com.kcpd.myfolder.ui.settings.S3ConfigScreen
import com.kcpd.myfolder.ui.viewer.AudioViewerScreen
import com.kcpd.myfolder.ui.viewer.NoteViewerScreen
import com.kcpd.myfolder.ui.viewer.PhotoViewerScreen
import com.kcpd.myfolder.ui.viewer.VideoViewerScreen

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
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category")
            val folderCategory = category?.let { FolderCategory.fromPath(it) }

            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = {
                    // Navigate to appropriate capture/creation screen based on category
                    when (folderCategory?.mediaType) {
                        com.kcpd.myfolder.data.model.MediaType.PHOTO ->
                            navController.navigate("photo_camera")
                        com.kcpd.myfolder.data.model.MediaType.VIDEO ->
                            navController.navigate("video_recorder")
                        com.kcpd.myfolder.data.model.MediaType.AUDIO ->
                            navController.navigate("audio_recorder")
                        com.kcpd.myfolder.data.model.MediaType.NOTE ->
                            navController.navigate("note_editor")
                        null -> navController.navigate("camera")
                    }
                },
                onMediaClick = { index ->
                    // Navigate to the appropriate viewer based on media type
                    when (folderCategory?.mediaType) {
                        com.kcpd.myfolder.data.model.MediaType.PHOTO ->
                            navController.navigate("photo_viewer/$index?category=$category")
                        com.kcpd.myfolder.data.model.MediaType.VIDEO ->
                            navController.navigate("video_viewer/$index?category=$category")
                        com.kcpd.myfolder.data.model.MediaType.AUDIO ->
                            navController.navigate("audio_viewer/$index?category=$category")
                        com.kcpd.myfolder.data.model.MediaType.NOTE ->
                            navController.navigate("note_viewer/$index?category=$category")
                        null -> navController.navigate("media_viewer/$index?category=$category")
                    }
                }
            )
        }

        composable("camera") {
            CameraScreen(navController = navController)
        }

        composable("photo_camera") {
            PhotoCameraScreen(navController = navController)
        }

        composable("video_recorder") {
            VideoRecorderScreen(navController = navController)
        }

        composable("audio_recorder") {
            AudioRecorderScreen(navController = navController)
        }

        composable("note_editor") {
            NoteEditorScreen(navController = navController)
        }

        composable("s3_config") {
            S3ConfigScreen(navController = navController)
        }

        composable(
            route = "media_viewer/{index}?category={category}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            MediaViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category
            )
        }

        composable(
            route = "photo_viewer/{index}?category={category}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            PhotoViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category
            )
        }

        composable(
            route = "video_viewer/{index}?category={category}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            VideoViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category
            )
        }

        composable(
            route = "audio_viewer/{index}?category={category}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            AudioViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category
            )
        }

        composable(
            route = "note_viewer/{index}?category={category}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            NoteViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category
            )
        }
    }
}

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
                    // Use category-specific route names for clarity
                    when (category.path) {
                        "photos" -> navController.navigate("photos")
                        "videos" -> navController.navigate("videos")
                        "notes" -> navController.navigate("notes")
                        "recordings" -> navController.navigate("recordings")
                        else -> navController.navigate("folder/${category.path}")
                    }
                },
                onSettingsClick = {
                    navController.navigate("s3_config")
                }
            )
        }

        // Photos Screen (clearer naming instead of folder/photos)
        composable(
            route = "photos?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val category = "photos"
            val folderCategory = FolderCategory.fromPath(category)

            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = { currentFolderId ->
                    val folderParam = currentFolderId?.let { "?folderId=$it" } ?: ""
                    navController.navigate("photo_camera$folderParam")
                },
                onMediaClick = { index ->
                    navController.navigate("photo_viewer/$index?category=$category")
                }
            )
        }

        // Videos Screen (clearer naming instead of folder/videos)
        composable(
            route = "videos?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val category = "videos"
            val folderCategory = FolderCategory.fromPath(category)

            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = { currentFolderId ->
                    val folderParam = currentFolderId?.let { "?folderId=$it" } ?: ""
                    navController.navigate("video_recorder$folderParam")
                },
                onMediaClick = { index ->
                    navController.navigate("video_viewer/$index?category=$category")
                }
            )
        }

        // Notes Screen (clearer naming instead of folder/notes)
        composable(
            route = "notes?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val category = "notes"
            val folderCategory = FolderCategory.fromPath(category)

            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = { currentFolderId ->
                    val folderParam = currentFolderId?.let { "?folderId=$it" } ?: ""
                    navController.navigate("note_editor$folderParam")
                },
                onMediaClick = { index ->
                    navController.navigate("note_viewer/$index?category=$category")
                }
            )
        }

        // Recordings Screen (clearer naming instead of folder/recordings)
        composable(
            route = "recordings?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val category = "recordings"
            val folderCategory = FolderCategory.fromPath(category)

            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = { currentFolderId ->
                    val folderParam = currentFolderId?.let { "?folderId=$it" } ?: ""
                    navController.navigate("audio_recorder$folderParam")
                },
                onMediaClick = { index ->
                    navController.navigate("audio_viewer/$index?category=$category")
                }
            )
        }

        // Legacy fallback route for backward compatibility
        composable(
            route = "folder/{category}?folderId={folderId}",
            arguments = listOf(
                navArgument("category") { type = NavType.StringType },
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category")
            val folderCategory = category?.let { FolderCategory.fromPath(it) }

            FolderScreen(
                onBackClick = { navController.navigateUp() },
                onAddClick = { currentFolderId ->
                    // Navigate to appropriate capture/creation screen based on category
                    val folderParam = currentFolderId?.let { "?folderId=$it" } ?: ""
                    when (folderCategory?.mediaType) {
                        com.kcpd.myfolder.data.model.MediaType.PHOTO ->
                            navController.navigate("photo_camera$folderParam")
                        com.kcpd.myfolder.data.model.MediaType.VIDEO ->
                            navController.navigate("video_recorder$folderParam")
                        com.kcpd.myfolder.data.model.MediaType.AUDIO ->
                            navController.navigate("audio_recorder$folderParam")
                        com.kcpd.myfolder.data.model.MediaType.NOTE ->
                            navController.navigate("note_editor$folderParam")
                        null -> navController.navigate("camera$folderParam")
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

        composable(
            route = "camera?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            CameraScreen(navController = navController, folderId = folderId)
        }

        composable(
            route = "photo_camera?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            PhotoCameraScreen(navController = navController, folderId = folderId)
        }

        composable(
            route = "video_recorder?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            VideoRecorderScreen(navController = navController, folderId = folderId)
        }

        composable(
            route = "audio_recorder?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            AudioRecorderScreen(navController = navController, folderId = folderId)
        }

        composable(
            route = "note_editor?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            NoteEditorScreen(navController = navController, folderId = folderId)
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

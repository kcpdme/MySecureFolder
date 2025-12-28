package com.kcpd.myfolder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kcpd.myfolder.data.model.FolderCategory
import com.kcpd.myfolder.security.PasswordManager
import com.kcpd.myfolder.security.SecurityManager
import androidx.compose.runtime.collectAsState
import com.kcpd.myfolder.security.VaultManager
import com.kcpd.myfolder.ui.auth.PasswordChangeScreen
import com.kcpd.myfolder.ui.auth.PasswordSetupScreen
import com.kcpd.myfolder.ui.auth.VaultUnlockScreen
import com.kcpd.myfolder.ui.camera.AudioRecorderScreen
import com.kcpd.myfolder.ui.camera.PhotoCameraScreen
import com.kcpd.myfolder.ui.camera.VideoRecorderScreen
import com.kcpd.myfolder.ui.folder.FolderScreen
import com.kcpd.myfolder.ui.gallery.MediaViewerScreen
import com.kcpd.myfolder.ui.home.HomeScreen
import com.kcpd.myfolder.ui.note.NoteEditorScreen
import com.kcpd.myfolder.ui.scanner.DocumentScannerScreen
import com.kcpd.myfolder.ui.settings.S3ConfigScreen
import com.kcpd.myfolder.ui.settings.SettingsScreen
import com.kcpd.myfolder.ui.viewer.AudioViewerScreen
import com.kcpd.myfolder.ui.viewer.NoteViewerScreen
import com.kcpd.myfolder.ui.viewer.PhotoViewerScreen
import com.kcpd.myfolder.ui.viewer.VideoViewerScreen

@Composable
fun MyFolderNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    vaultManager: VaultManager? = null
) {
    val context = LocalContext.current
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Check password setup and vault status to determine start destination
    LaunchedEffect(Unit) {
        val securityManager = SecurityManager(context)
        val passwordManager = PasswordManager(context, securityManager)

        startDestination = when {
            !passwordManager.isPasswordSet() -> "password_setup"
            vaultManager?.isLocked() == true -> "vault_unlock"
            else -> "home"
        }
    }

    // Wait for start destination to be determined
    if (startDestination == null) return

    NavHost(
        navController = navController,
        startDestination = startDestination!!,
        modifier = modifier
    ) {
        composable("password_setup") {
            PasswordSetupScreen(
                onPasswordSet = {
                    navController.navigate("home") {
                        popUpTo("password_setup") { inclusive = true }
                    }
                }
            )
        }

        composable("vault_unlock") {
            VaultUnlockScreen(
                onUnlocked = {
                    navController.navigate("home") {
                        popUpTo("vault_unlock") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            // Observe vault state - navigate to unlock if locked
            vaultManager?.let { manager ->
                val vaultState by manager.vaultState.collectAsState()

                LaunchedEffect(vaultState) {
                    if (vaultState is VaultManager.VaultState.Locked) {
                        // Navigate to unlock screen when vault is locked
                        navController.navigate("vault_unlock") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                }
            }

            HomeScreen(
                onFolderClick = { category ->
                    // Navigate using the folder route with category parameter
                    navController.navigate("folder/${category.path}")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onCameraClick = {
                    navController.navigate("photo_camera")
                },
                onRecorderClick = {
                    navController.navigate("audio_recorder")
                }
            )
        }

        // Main folder route - handles all categories (photos, videos, notes, recordings)
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
                        com.kcpd.myfolder.data.model.MediaType.PDF ->
                            navController.navigate("document_scanner$folderParam")
                        com.kcpd.myfolder.data.model.MediaType.OTHER -> {
                            // Other is import-only (handled via the top bar import action)
                        }
                        null -> navController.navigate("camera$folderParam")
                    }
                },
                onMediaClick = { index, mediaFile ->
                    // Navigate to the appropriate viewer based on the actual media file type
                    // Pass fileId to handle cases where the list is filtered (e.g., search results in ALL_FILES)
                    android.util.Log.d("Navigation", "onMediaClick called: index=$index, fileId=${mediaFile.id}, file=${mediaFile.fileName}, actualType=${mediaFile.mediaType}, category=$category")

                    // Use actual media file type instead of category type
                    // This fixes the issue where ALL_FILES category would route incorrectly
                    when (mediaFile.mediaType) {
                        com.kcpd.myfolder.data.model.MediaType.PHOTO -> {
                            val route = "photo_viewer/$index?category=$category&fileId=${mediaFile.id}"
                            android.util.Log.d("Navigation", "Navigating to photo viewer: $route")
                            navController.navigate(route)
                        }
                        com.kcpd.myfolder.data.model.MediaType.VIDEO -> {
                            val route = "video_viewer/$index?category=$category&fileId=${mediaFile.id}"
                            android.util.Log.d("Navigation", "Navigating to video viewer: $route")
                            navController.navigate(route)
                        }
                        com.kcpd.myfolder.data.model.MediaType.AUDIO -> {
                            val route = "audio_viewer/$index?category=$category&fileId=${mediaFile.id}"
                            android.util.Log.d("Navigation", "Navigating to audio viewer: $route")
                            navController.navigate(route)
                        }
                        com.kcpd.myfolder.data.model.MediaType.NOTE -> {
                            val route = "note_viewer/$index?category=$category&fileId=${mediaFile.id}"
                            android.util.Log.d("Navigation", "Navigating to note viewer: $route")
                            navController.navigate(route)
                        }
                        com.kcpd.myfolder.data.model.MediaType.PDF -> {
                            val route = "media_viewer/$index?category=$category&fileId=${mediaFile.id}"
                            android.util.Log.d("Navigation", "Navigating to PDF viewer: $route")
                            navController.navigate(route)
                        }
                        com.kcpd.myfolder.data.model.MediaType.OTHER -> {
                            val route = "media_viewer/$index?category=$category&fileId=${mediaFile.id}"
                            android.util.Log.d("Navigation", "Navigating to generic viewer: $route")
                            navController.navigate(route)
                        }
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
            // Fallback to PhotoCameraScreen if CameraScreen is removed
            PhotoCameraScreen(navController = navController, folderId = folderId)
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

        composable(
            route = "document_scanner?folderId={folderId}",
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            DocumentScannerScreen(navController = navController, folderId = folderId)
        }

        composable("settings") {
            SettingsScreen(navController = navController)
        }

        composable("password_change") {
            PasswordChangeScreen(navController = navController)
        }

        composable("s3_config") {
            S3ConfigScreen(navController = navController)
        }

        composable(
            route = "media_viewer/{index}?category={category}&fileId={fileId}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("fileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            val fileId = backStackEntry.arguments?.getString("fileId")
            MediaViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category,
                fileId = fileId
            )
        }

        composable(
            route = "photo_viewer/{index}?category={category}&fileId={fileId}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("fileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            val fileId = backStackEntry.arguments?.getString("fileId")
            PhotoViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category,
                fileId = fileId
            )
        }

        composable(
            route = "video_viewer/{index}?category={category}&fileId={fileId}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("fileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            val fileId = backStackEntry.arguments?.getString("fileId")
            VideoViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category,
                fileId = fileId
            )
        }

        composable(
            route = "audio_viewer/{index}?category={category}&fileId={fileId}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("fileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            val fileId = backStackEntry.arguments?.getString("fileId")
            AudioViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category,
                fileId = fileId
            )
        }

        composable(
            route = "note_viewer/{index}?category={category}&fileId={fileId}",
            arguments = listOf(
                navArgument("index") { type = NavType.IntType },
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("fileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val category = backStackEntry.arguments?.getString("category")
            val fileId = backStackEntry.arguments?.getString("fileId")
            NoteViewerScreen(
                navController = navController,
                initialIndex = index,
                category = category,
                fileId = fileId
            )
        }
    }
}

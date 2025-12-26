package com.kcpd.myfolder.ui.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Composable effect that prevents screenshots and screen recording.
 *
 * This adds FLAG_SECURE to the window, which:
 * - Blocks screenshots
 * - Blocks screen recording
 * - Hides content in Recent Apps / Task Switcher
 *
 * Use this in viewer screens to prevent data leakage of decrypted content.
 *
 * Example:
 * ```
 * @Composable
 * fun PhotoViewerScreen() {
 *     ScreenSecureEffect()
 *
 *     // Rest of your UI...
 * }
 * ```
 */
@Composable
fun ScreenSecureEffect() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window

        // Enable FLAG_SECURE
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        onDispose {
            // Clear FLAG_SECURE when leaving the screen
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

package com.kcpd.myfolder

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.kcpd.myfolder.security.SecureFileManager
import com.kcpd.myfolder.security.SecurityManager
import dagger.hilt.android.HiltAndroidApp
import net.sqlcipher.database.SQLiteDatabase
import javax.inject.Inject

@HiltAndroidApp
class MyFolderApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var secureFileManager: SecureFileManager

    override fun onCreate() {
        super.onCreate()

        // Initialize SQLCipher
        SQLiteDatabase.loadLibs(this)
    }

    /**
     * Clear image caches when system reports low memory.
     * This matches Tella's security-conscious approach and prevents OOM errors.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        android.util.Log.w("MyFolderApp", "Low memory detected - clearing image caches")

        // Clear memory cache (decrypted images in RAM)
        imageLoader.memoryCache?.clear()

        // Optionally trigger GC (Android will do this anyway, but being explicit)
        System.gc()
    }

    /**
     * Trim memory caches based on urgency level.
     * Called before onLowMemory() as a preventive measure.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when {
            // App is in background and system is low on memory
            level >= TRIM_MEMORY_BACKGROUND -> {
                android.util.Log.w("MyFolderApp", "Trim memory (background) - clearing all caches")
                imageLoader.memoryCache?.clear()
            }
            // App is running and system is moderately low on memory
            level >= TRIM_MEMORY_RUNNING_MODERATE -> {
                android.util.Log.w("MyFolderApp", "Trim memory (moderate) - trimming 50% of cache")
                imageLoader.memoryCache?.let { cache ->
                    // Trim to 50% of current size
                    val currentSize = cache.size
                    // Coil doesn't have a direct trim method, so we clear and let it rebuild
                    if (currentSize > cache.maxSize / 2) {
                        cache.clear()
                    }
                }
            }
            // App is running and system is critically low on memory
            level >= TRIM_MEMORY_RUNNING_CRITICAL -> {
                android.util.Log.e("MyFolderApp", "Trim memory (critical) - clearing all caches immediately")
                imageLoader.memoryCache?.clear()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}

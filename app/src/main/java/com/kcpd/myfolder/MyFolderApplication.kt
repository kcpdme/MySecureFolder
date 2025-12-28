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

        // Configure StAX for MinIO (Android doesn't have a default StAX provider)
        // This fixes javax.xml.stream.FactoryConfigurationError: Provider com.bea.xml.stream.MXParserFactory not found
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
        System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
        System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")

        // Verify Woodstox is available (critical for S3 multipart uploads)
        try {
            // Force load the class to ensure ProGuard hasn't stripped it
            val factoryClass = Class.forName("com.ctc.wstx.stax.WstxInputFactory")
            val factory = factoryClass.getDeclaredConstructor().newInstance()
            android.util.Log.d("MyFolderApp", "Woodstox XML loaded successfully: ${factory.javaClass.name}")
        } catch (e: Throwable) {
            android.util.Log.e("MyFolderApp", "CRITICAL: Failed to load Woodstox XML provider. S3 uploads may fail.", e)
        }

        // Initialize SQLCipher
        SQLiteDatabase.loadLibs(this)

        // Database Integrity Check & Recovery
        try {
            if (securityManager.isSetup()) {
                if (!securityManager.validateDatabaseKey(this)) {
                    android.util.Log.e("MyFolderApp", "Database corrupted or unreadable. Attempting WAL recovery...")
                    securityManager.deleteDatabaseWal(this)
                    
                    if (securityManager.validateDatabaseKey(this)) {
                        android.util.Log.i("MyFolderApp", "Database recovered successfully!")
                    } else {
                        android.util.Log.e("MyFolderApp", "Database recovery failed. User may need to restore backup.")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MyFolderApp", "Failed to check database integrity", e)
        }

        // Setup crash handler for secure cleanup
        setupCrashHandler()

        // Clean up any orphaned temp files from cache directory
        cleanupTempFiles()
    }

    /**
     * Sets up uncaught exception handler to clean up temp files on crash.
     * Ensures decrypted files don't persist on disk after unexpected app termination.
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MyFolderApp", "Uncaught exception - cleaning up temp files before crash", throwable)
            try {
                cleanupTempFiles()
            } catch (e: Exception) {
                android.util.Log.e("MyFolderApp", "Failed to cleanup during crash", e)
            }
            // Let default handler continue
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Removes all temporary decrypted files from cache directory.
     * These files may be left over from crashes or incomplete cleanup.
     */
    private fun cleanupTempFiles() {
        try {
            val cacheDir = cacheDir
            val tempFiles = cacheDir.listFiles { file ->
                file.name.startsWith("temp_") && file.isFile
            }

            tempFiles?.forEach { file ->
                try {
                    if (file.delete()) {
                        android.util.Log.d("MyFolderApp", "Cleaned up temp file: ${file.name}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MyFolderApp", "Failed to delete temp file: ${file.name}", e)
                }
            }

            if (tempFiles != null && tempFiles.isNotEmpty()) {
                android.util.Log.i("MyFolderApp", "Cleaned up ${tempFiles.size} orphaned temp files")
            }
        } catch (e: Exception) {
            android.util.Log.e("MyFolderApp", "Failed to cleanup temp files", e)
        }
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

    /**
     * Called when app process is terminating.
     * Clean up temporary decrypted files to prevent leaving sensitive data on disk.
     *
     * NOTE: This is NOT guaranteed to be called (e.g., on force-stop or low memory kill).
     * That's why we also clean up on startup in cleanupTempFiles().
     */
    override fun onTerminate() {
        super.onTerminate()
        android.util.Log.i("MyFolderApp", "App terminating - cleaning up temp files")
        cleanupTempFiles()
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}

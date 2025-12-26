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

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}

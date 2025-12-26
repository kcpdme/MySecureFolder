package com.kcpd.myfolder.di

import android.content.Context
import com.kcpd.myfolder.data.database.AppDatabase
import com.kcpd.myfolder.data.database.dao.FolderDao
import com.kcpd.myfolder.data.database.dao.MediaFileDao
import com.kcpd.myfolder.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing encrypted database instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        securityManager: SecurityManager
    ): AppDatabase {
        val passphrase = securityManager.getDatabaseKey()
        return AppDatabase.getInstance(context, passphrase)
    }

    @Provides
    @Singleton
    fun provideMediaFileDao(database: AppDatabase): MediaFileDao {
        return database.mediaFileDao()
    }

    @Provides
    @Singleton
    fun provideFolderDao(database: AppDatabase): FolderDao {
        return database.folderDao()
    }
}

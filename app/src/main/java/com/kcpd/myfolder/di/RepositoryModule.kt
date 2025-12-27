package com.kcpd.myfolder.di

import com.kcpd.myfolder.data.repository.RemoteRepositoryManager
import com.kcpd.myfolder.data.repository.RemoteStorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRemoteStorageRepository(
        remoteRepositoryManager: RemoteRepositoryManager
    ): RemoteStorageRepository
}

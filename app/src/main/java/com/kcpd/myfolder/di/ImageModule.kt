package com.kcpd.myfolder.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.kcpd.myfolder.security.SecureFileManager
import com.kcpd.myfolder.ui.image.EncryptedFileFetcher
import com.kcpd.myfolder.ui.image.ThumbnailFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        secureFileManager: SecureFileManager
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Thumbnail fetcher for ByteArray (highest priority - fastest)
                add(ThumbnailFetcher.Factory())
                // Encrypted file fetcher for MediaFile (for full images)
                add(EncryptedFileFetcher.Factory(secureFileManager))
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)  // 25% of app memory for decoded images
                    .strongReferencesEnabled(true)  // Keep strong refs for better caching
                    .build()
            }
            // Disk cache DISABLED for maximum security
            // Decrypted images should never be written to disk cache
            // This prevents forensic recovery of decrypted data
            .diskCache(null)
            .respectCacheHeaders(false)
            .crossfade(false)  // Disable crossfade globally to reduce recomposition
            .build()
    }
}

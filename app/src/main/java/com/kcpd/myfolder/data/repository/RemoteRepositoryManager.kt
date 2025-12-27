package com.kcpd.myfolder.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kcpd.myfolder.data.model.MediaFile
import com.kcpd.myfolder.data.model.RemoteType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.remoteStorageDataStore by preferencesDataStore(name = "remote_storage_prefs")

@Singleton
class RemoteRepositoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val s3Repository: S3Repository,
    private val googleDriveRepository: GoogleDriveRepository
) : RemoteStorageRepository {

    private val remoteTypeKey = stringPreferencesKey("remote_type")

    val activeRemoteType: Flow<RemoteType> = context.remoteStorageDataStore.data.map { preferences ->
        try {
            val typeString = preferences[remoteTypeKey]
            if (typeString != null) {
                RemoteType.valueOf(typeString)
            } else {
                RemoteType.S3_MINIO // Default
            }
        } catch (e: Exception) {
            RemoteType.S3_MINIO
        }
    }

    suspend fun setRemoteType(type: RemoteType) {
        context.remoteStorageDataStore.edit { preferences ->
            preferences[remoteTypeKey] = type.name
        }
    }

    private suspend fun getActiveRepository(): RemoteStorageRepository {
        val type = activeRemoteType.first()
        android.util.Log.d("RemoteRepoManager", "Active repository type: $type")
        return when (type) {
            RemoteType.S3_MINIO -> s3Repository
            RemoteType.GOOGLE_DRIVE -> googleDriveRepository
        }
    }

    override suspend fun uploadFile(mediaFile: MediaFile): Result<String> {
        return getActiveRepository().uploadFile(mediaFile)
    }

    override suspend fun verifyFileExists(mediaFile: MediaFile): Result<String?> {
        // Verify against the currently active repository to support migrating/syncing to new provider.
        return getActiveRepository().verifyFileExists(mediaFile)
    }

    override suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, String?> {
        // Verify against the currently active repository to support migrating/syncing to new provider.
        return getActiveRepository().verifyMultipleFiles(mediaFiles)
    }
}

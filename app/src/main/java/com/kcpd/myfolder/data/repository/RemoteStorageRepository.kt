package com.kcpd.myfolder.data.repository

import com.kcpd.myfolder.data.model.MediaFile

interface RemoteStorageRepository {
    suspend fun uploadFile(mediaFile: MediaFile): Result<String>
    suspend fun verifyFileExists(mediaFile: MediaFile): Result<Boolean>
    suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, Boolean>
}

package com.kcpd.myfolder.data.repository

import com.kcpd.myfolder.data.model.MediaFile

interface RemoteStorageRepository {
    suspend fun uploadFile(mediaFile: MediaFile): Result<String>
    // Returns the remote URL if found, or null/failure if not found
    suspend fun verifyFileExists(mediaFile: MediaFile): Result<String?>
    // Returns map of ID -> Remote URL (if found)
    suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, String?>
}

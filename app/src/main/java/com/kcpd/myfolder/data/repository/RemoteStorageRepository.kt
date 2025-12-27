package com.kcpd.myfolder.data.repository

import com.kcpd.myfolder.data.model.MediaFile

interface RemoteStorageRepository {
    suspend fun uploadFile(mediaFile: MediaFile): Result<String>
}

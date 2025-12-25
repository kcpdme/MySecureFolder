package com.kcpd.myfolder.ui.settings

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kcpd.myfolder.data.model.S3Config
import com.kcpd.myfolder.data.repository.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class S3ConfigViewModel @Inject constructor(
    application: Application,
    private val s3Repository: S3Repository
) : AndroidViewModel(application) {

    val s3Config: Flow<S3Config?> = s3Repository.s3Config

    fun saveConfig(
        endpoint: String,
        accessKey: String,
        secretKey: String,
        bucketName: String,
        region: String
    ) {
        viewModelScope.launch {
            val config = S3Config(
                endpoint = endpoint.trim(),
                accessKey = accessKey.trim(),
                secretKey = secretKey.trim(),
                bucketName = bucketName.trim(),
                region = region.trim()
            )
            s3Repository.saveS3Config(config)
            Toast.makeText(
                getApplication(),
                "S3 configuration saved",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

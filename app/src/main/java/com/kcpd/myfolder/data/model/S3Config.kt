package com.kcpd.myfolder.data.model

data class S3Config(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucketName: String,
    val region: String = "us-east-1"
)

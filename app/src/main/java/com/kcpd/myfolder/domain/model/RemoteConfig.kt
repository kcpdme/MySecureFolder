package com.kcpd.myfolder.domain.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Sealed class hierarchy representing different types of remote storage configurations.
 * Each remote has a unique ID, user-friendly name, color identifier, and active state.
 */
@Serializable
sealed class RemoteConfig {
    abstract val id: String
    abstract val name: String
    @Serializable(with = ColorSerializer::class)
    abstract val color: Color
    abstract val isActive: Boolean

    /**
     * S3-compatible remote storage configuration (AWS S3, MinIO, DigitalOcean Spaces, etc.)
     */
    @Serializable
    data class S3Remote(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        @Serializable(with = ColorSerializer::class)
        override val color: Color,
        override val isActive: Boolean = true,
        val endpoint: String,
        val accessKey: String,
        val secretKey: String,
        val bucketName: String,
        val region: String = "us-east-1"
    ) : RemoteConfig()

    /**
     * Google Drive remote storage configuration
     */
    @Serializable
    data class GoogleDriveRemote(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        @Serializable(with = ColorSerializer::class)
        override val color: Color,
        override val isActive: Boolean = true,
        val accountEmail: String,
        val refreshToken: String? = null
    ) : RemoteConfig()

    /**
     * WebDAV remote storage configuration (Koofr, Icedrive, Nextcloud, etc.)
     */
    @Serializable
    data class WebDavRemote(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        @Serializable(with = ColorSerializer::class)
        override val color: Color,
        override val isActive: Boolean = true,
        val serverUrl: String,       // e.g., "https://app.koofr.net/dav/Koofr" or "https://webdav.icedrive.io"
        val username: String,
        val password: String,
        val basePath: String = ""    // Optional base path within the WebDAV server
    ) : RemoteConfig()

    companion object {
        /**
         * Available colors for remote visual identification
         */
        val AVAILABLE_COLORS = listOf(
            Color(0xFF2196F3), // Blue
            Color(0xFF4CAF50), // Green
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFFF44336), // Red
            Color(0xFF00BCD4), // Cyan
            Color(0xFFFFEB3B), // Yellow
            Color(0xFFE91E63), // Pink
            Color(0xFF3F51B5), // Indigo
            Color(0xFF009688), // Teal
            Color(0xFF795548), // Brown
            Color(0xFF607D8B)  // Blue Grey
        )
    }
}

/**
 * Custom serializer for Compose Color to store as ARGB integer
 */
object ColorSerializer : kotlinx.serialization.KSerializer<Color> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Color",
        kotlinx.serialization.descriptors.PrimitiveKind.LONG
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Color) {
        encoder.encodeLong(value.value.toLong())
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Color {
        return Color(decoder.decodeLong().toULong())
    }
}

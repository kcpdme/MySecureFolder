package com.kcpd.myfolder.ui.image

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream

/**
 * Custom Coil Fetcher that loads thumbnail byte arrays directly from memory.
 * This is extremely fast - no file I/O, no decryption needed.
 * Thumbnails are stored as plain JPEG byte arrays in the database.
 */
class ThumbnailFetcher(
    private val data: ByteArray,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        android.util.Log.d("ThumbnailFetcher", "Loading thumbnail from byte array (${data.size} bytes)")

        // Convert byte array to BufferedSource for Coil
        val inputStream = ByteArrayInputStream(data)
        val bufferedSource = inputStream.source().buffer()

        // Return the thumbnail as an ImageSource
        return SourceResult(
            source = ImageSource(
                source = bufferedSource,
                context = options.context
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<ByteArray> {
        override fun create(
            data: ByteArray,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return ThumbnailFetcher(data, options)
        }
    }
}

package com.kcpd.myfolder.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kcpd.myfolder.data.database.dao.UploadQueueDao
import com.kcpd.myfolder.data.database.entity.UploadQueueEntity

/**
 * Separate database for upload queue.
 * 
 * This is intentionally NOT encrypted because:
 * 1. It only stores file paths and remote IDs (no sensitive content)
 * 2. The actual media files are already encrypted on disk
 * 3. WorkManager needs to access this without vault unlock
 * 4. Upload queue must survive app death and resume automatically
 * 
 * Security analysis:
 * - fileId: UUID, meaningless without vault access
 * - fileName: Could be sensitive, but user chose to upload it anyway
 * - filePath: Points to encrypted .enc files, useless without master key
 * - remoteId/remoteName: Not sensitive
 * 
 * Alternative: Could encrypt with a device-bound key (AndroidKeystore),
 * but that adds complexity without significant security benefit.
 */
@Database(
    entities = [UploadQueueEntity::class],
    version = 2,  // Bumped for mediaType and folderId columns
    exportSchema = true
)
abstract class UploadQueueDatabase : RoomDatabase() {

    abstract fun uploadQueueDao(): UploadQueueDao

    companion object {
        private const val DATABASE_NAME = "upload_queue.db"

        @Volatile
        private var INSTANCE: UploadQueueDatabase? = null

        /**
         * Gets the database instance.
         * Does NOT require vault unlock - can be accessed by WorkManager.
         */
        fun getInstance(context: Context): UploadQueueDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): UploadQueueDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UploadQueueDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()  // OK for queue - can be rebuilt from remotes
                .build()
        }

        /**
         * Closes the database instance.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

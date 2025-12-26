package com.kcpd.myfolder.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kcpd.myfolder.data.database.dao.FolderDao
import com.kcpd.myfolder.data.database.dao.MediaFileDao
import com.kcpd.myfolder.data.database.entity.FolderEntity
import com.kcpd.myfolder.data.database.entity.MediaFileEntity
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Room database with SQLCipher encryption.
 * All metadata is encrypted at rest.
 */
@Database(
    entities = [
        MediaFileEntity::class,
        FolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaFileDao(): MediaFileDao
    abstract fun folderDao(): FolderDao

    companion object {
        private const val DATABASE_NAME = "myfolder_encrypted.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Gets the encrypted database instance.
         * @param context Application context
         * @param passphrase Encryption key as ByteArray (from SecurityManager)
         */
        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): AppDatabase {
            // Create SQLCipher factory with encryption key
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // For development - remove in production
                .build()
        }

        /**
         * Closes the database instance.
         * Should be called when user logs out or app is reset.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

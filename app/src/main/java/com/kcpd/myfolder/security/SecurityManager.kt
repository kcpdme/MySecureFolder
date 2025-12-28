package com.kcpd.myfolder.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption keys and security operations for the app.
 * Implements the "Hybrid" security model using Argon2id for key derivation
 * and Envelope Encryption for file security.
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ENCRYPTED_PREFS_NAME = "secure_prefs"
        private const val KEY_STORED_MASTER_KEY = "stored_master_key" // Encrypted by Keystore
        private const val KEY_STORED_SEED_WORDS = "stored_seed_words" // Encrypted by Keystore
        private const val KEY_PANIC_PIN_HASH = "panic_pin_hash"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val MASTER_KEY_LENGTH = 32 // 256 bits

        // HKDF context string for database key derivation
        private const val HKDF_CONTEXT_DATABASE = "myfolder-database-encryption-v1"
    }

    private val argon2 = Argon2Kt()

    private val masterKeyAlias: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // EncryptedSharedPreferences uses Android Keystore to encrypt values
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Active Master Key (held in memory while unlocked)
    private var activeMasterKey: SecretKey? = null

    /**
     * Sets the active master key (after successful authentication/recovery).
     */
    fun setActiveMasterKey(key: SecretKey) {
        activeMasterKey = key
    }

    /**
     * Gets the active master key.
     * @throws IllegalStateException if the vault is locked.
     */
    fun getActiveMasterKey(): SecretKey {
        return activeMasterKey ?: throw IllegalStateException("Vault is locked. Master key not available.")
    }

    /**
     * Checks if the vault is unlocked (Master Key is in memory).
     */
    fun isUnlocked(): Boolean {
        return activeMasterKey != null
    }

    /**
     * Derives the Master Key from Password and Seed Words using Argon2id.
     * 
     * @param password User's password
     * @param seedWords List of 12 BIP39 seed words
     * @return 32-byte Master Key
     */
    fun deriveMasterKey(password: String, seedWords: List<String>): SecretKey {
        // Salt = SHA-256(SeedWords)
        // The Seed Words act as the high-entropy global salt.
        val seedString = seedWords.joinToString(" ")
        val salt = MessageDigest.getInstance("SHA-256").digest(seedString.toByteArray(Charsets.UTF_8))

        // Argon2id Derivation
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.toByteArray(Charsets.UTF_8),
            salt = salt,
            tCostInIterations = 3,
            mCostInKibibyte = 64 * 1024 // 64 MB
        )

        return SecretKeySpec(result.rawHashAsByteArray(), "AES")
    }

    /**
     * Wraps (Encrypts) a File Encryption Key (FEK) using the Master Key.
     *
     * @param fek The random File Encryption Key
     * @param masterKey The Master Key
     * @return Pair of (IV, EncryptedFEK)
     */
    fun wrapFEK(fek: SecretKey, masterKey: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        // Generate random IV for this wrapping
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec)
        
        val encryptedBytes = cipher.doFinal(fek.encoded)
        
        return Pair(iv, encryptedBytes)
    }

    /**
     * Unwraps (Decrypts) a File Encryption Key (FEK).
     *
     * @param encryptedFek The encrypted FEK bytes
     * @param iv The IV used for encryption
     * @param masterKey The Master Key
     * @return The original FEK
     */
    fun unwrapFEK(encryptedFek: ByteArray, iv: ByteArray, masterKey: SecretKey): SecretKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        
        val decryptedBytes = cipher.doFinal(encryptedFek)
        
        return SecretKeySpec(decryptedBytes, "AES")
    }

    /**
     * Stores the Master Key and Seed Words in EncryptedSharedPreferences (KeyStore backed).
     * This allows convenience unlock via Biometrics/PIN without re-entering password.
     */
    fun storeCredentials(masterKey: SecretKey, seedWords: List<String>) {
        val masterKeyBase64 = android.util.Base64.encodeToString(masterKey.encoded, android.util.Base64.NO_WRAP)
        val seedWordsString = seedWords.joinToString(" ")
        
        encryptedPrefs.edit()
            .putString(KEY_STORED_MASTER_KEY, masterKeyBase64)
            .putString(KEY_STORED_SEED_WORDS, seedWordsString)
            .apply()
    }

    /**
     * Tries to load the Master Key from secure storage (e.g., after Biometric auth).
     * @return The Master Key if available, null otherwise.
     */
    fun loadStoredMasterKey(): SecretKey? {
        val base64 = encryptedPrefs.getString(KEY_STORED_MASTER_KEY, null) ?: return null
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            SecretKeySpec(bytes, "AES")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads the stored seed words.
     */
    fun loadStoredSeedWords(): List<String>? {
        val seedString = encryptedPrefs.getString(KEY_STORED_SEED_WORDS, null) ?: return null
        return seedString.split(" ")
    }

    /**
     * Checks if credentials are stored (i.e., app is set up).
     */
    fun isSetup(): Boolean {
        return encryptedPrefs.contains(KEY_STORED_MASTER_KEY)
    }

    /**
     * Wipes all stored keys from memory and storage.
     */
    fun wipeKeys() {
        activeMasterKey = null
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Stores the Panic PIN hash.
     */
    fun storePanicPinHash(hash: String) {
        encryptedPrefs.edit().putString(KEY_PANIC_PIN_HASH, hash).apply()
    }

    /**
     * Gets the stored Panic PIN hash.
     */
    fun getPanicPinHash(): String? {
        return encryptedPrefs.getString(KEY_PANIC_PIN_HASH, null)
    }

    /**
     * Deletes the database Write-Ahead Log (WAL) files.
     * This can fix "file is not a database" errors caused by corrupted transaction logs.
     */
    fun deleteDatabaseWal(context: Context) {
        val dbPath = context.getDatabasePath("myfolder_encrypted.db").absolutePath
        val walFile = java.io.File("$dbPath-wal")
        val shmFile = java.io.File("$dbPath-shm")
        
        if (walFile.exists()) {
            walFile.delete()
            android.util.Log.w("SecurityManager", "Deleted WAL file: ${walFile.name}")
        }
        if (shmFile.exists()) {
            shmFile.delete()
            android.util.Log.w("SecurityManager", "Deleted SHM file: ${shmFile.name}")
        }
    }

    /**
     * Deletes the entire encrypted database.
     * Used for total reset when database is corrupted beyond repair.
     */
    fun deleteDatabase(context: Context) {
        // Close connections first
        com.kcpd.myfolder.data.database.AppDatabase.closeDatabase()
        
        val dbPath = context.getDatabasePath("myfolder_encrypted.db")
        if (dbPath.exists()) {
            context.deleteDatabase("myfolder_encrypted.db")
            android.util.Log.w("SecurityManager", "Deleted corrupted database: ${dbPath.name}")
        }
        // Also clean up legacy DB just in case
        context.deleteDatabase("myfolder_database")
    }

    /**
     * Recursively wipes ALL application data (Keys, Files, Database).
     * This is a destructive operation for Panic functionality.
     */
    fun wipeAllData() {
        // 1. Clear keys from memory
        activeMasterKey = null
        
        // 2. Clear Encrypted SharedPreferences (Keys, Seed Words, Panic PIN)
        encryptedPrefs.edit().clear().apply()

        // 3. Delete Secure Storage Directory
        val secureDir = java.io.File(context.filesDir, "secure_media")
        if (secureDir.exists()) {
            secureDir.deleteRecursively()
        }

        // 4. Delete Database(s)
        context.deleteDatabase("myfolder_encrypted.db")
        context.deleteDatabase("myfolder_database") // Legacy

        // 5. Delete all shared preferences files
        val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
        if (prefsDir.exists()) {
            prefsDir.deleteRecursively()
        }
        
        // 6. Delete DataStore files
        val datastoreDir = java.io.File(context.filesDir, "datastore")
        if (datastoreDir.exists()) {
            datastoreDir.deleteRecursively()
        }
    }

    /**
     * Derives a database encryption key from the Master Key.
     * We use HKDF to derive a sub-key so the Master Key itself isn't used for DB.
     */
    fun deriveDatabaseKey(masterKey: SecretKey): ByteArray {
        // Use HKDF to derive DB key
        return hkdf(masterKey.encoded, HKDF_CONTEXT_DATABASE.toByteArray(), 32)
    }

    /**
     * Gets the database encryption key.
     * Tries to load master key if not active.
     */
    fun getDatabaseKey(): ByteArray {
        val masterKey = activeMasterKey ?: loadStoredMasterKey()
            ?: throw IllegalStateException("Cannot access database: Vault locked or not set up")
        return deriveDatabaseKey(masterKey)
    }

    /**
     * Re-encrypts the database with a new Master Key.
     * This is called during password changes to keep the database in sync with the new key.
     *
     * @param context Application context
     * @param oldMasterKey The previous Master Key
     * @param newMasterKey The new Master Key
     * @throws Exception if re-keying fails
     */
    fun rekeyDatabase(context: Context, oldMasterKey: SecretKey, newMasterKey: SecretKey) {
        val oldDbKey = deriveDatabaseKey(oldMasterKey)
        val newDbKey = deriveDatabaseKey(newMasterKey)

        // Close existing database instance to release locks
        com.kcpd.myfolder.data.database.AppDatabase.closeDatabase()

        val dbFile = context.getDatabasePath("myfolder_encrypted.db")

        // If database doesn't exist yet, nothing to re-key
        if (!dbFile.exists()) {
            android.util.Log.i("SecurityManager", "Database doesn't exist yet, skipping rekey")
            return
        }

        try {
            // Open database using SupportFactory to ensure correct key handling (byte[])
            // We use version 3 to match AppDatabase and avoid downgrade errors
            val factory = net.sqlcipher.database.SupportFactory(oldDbKey)
            val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, old: Int, new: Int) {}
                    override fun onDowngrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
            
            // Use getWritableDatabase to open (and decrypt) the database
            val db = factory.create(config).writableDatabase

            try {
                // Convert new key to hex string for PRAGMA rekey
                val newKeyHex = newDbKey.joinToString("") { "%02x".format(it) }
                db.execSQL("PRAGMA rekey = \"x'$newKeyHex'\"")
                android.util.Log.i("SecurityManager", "Database re-keyed successfully")
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Failed to rekey database", e)
            throw IllegalStateException("Database re-keying failed: ${e.message}", e)
        }
    }

    /**
     * Validates if the database can be opened with the current Master Key.
     * Returns true if database is accessible, false if key mismatch or doesn't exist.
     */
    fun validateDatabaseKey(context: Context): Boolean {
        val dbPath = context.getDatabasePath("myfolder_encrypted.db")

        // If database doesn't exist, consider it valid (will be created on first use)
        if (!dbPath.exists()) return true

        return try {
            val dbKey = getDatabaseKey()
            val factory = net.sqlcipher.database.SupportFactory(dbKey)
            val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbPath.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, old: Int, new: Int) {}
                    override fun onDowngrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val db = factory.create(config).readableDatabase
            db.close()
            true
        } catch (e: Exception) {
            android.util.Log.w("SecurityManager", "Database validation failed: ${e.message}")
            false
        }
    }

    /**
     * HKDF implementation for internal key derivation.
     */
    private fun hkdf(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val salt = ByteArray(32) // Fixed zero salt as IKM is already high entropy
        val prk = hmacSha256(salt, ikm)

        val okm = ByteArray(length)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        var t = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()

            val copyLength = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, copyLength)
            offset += copyLength
            counter++
        }

        return okm
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

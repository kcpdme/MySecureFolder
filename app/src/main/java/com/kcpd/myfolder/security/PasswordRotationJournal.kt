package com.kcpd.myfolder.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val JOURNAL_PREFERENCES_FILE = "password_rotation_journal"
private const val JOURNAL_KEY = "journal_state"

@Singleton
class PasswordRotationJournalManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        JOURNAL_PREFERENCES_FILE,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun writeJournal(journal: PasswordRotationJournal) {
        val json = gson.toJson(journal)
        sharedPreferences.edit().putString(JOURNAL_KEY, json).apply()
    }

    fun readJournal(): PasswordRotationJournal {
        val json = sharedPreferences.getString(JOURNAL_KEY, null)
        return if (json != null) {
            gson.fromJson(json, PasswordRotationJournal::class.java)
        } else {
            PasswordRotationJournal.initial()
        }
    }

    fun clearJournal() {
        sharedPreferences.edit().remove(JOURNAL_KEY).apply()
    }

    fun isRotationInProgress(): Boolean {
        val journal = readJournal()
        return journal.rotationState == RotationState.IN_PROGRESS
    }
}

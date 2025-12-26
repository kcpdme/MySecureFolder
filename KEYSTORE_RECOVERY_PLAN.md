# Keystore Recovery Implementation Plan

**Date**: December 26, 2025
**Priority**: CRITICAL SECURITY FIX
**Issue**: Files become unrecoverable if Android Keystore breaks
**Status**: Planning phase

---

## Problem Analysis

### Current Architecture (BROKEN)

```
┌─────────────────────────────────────────────────────────┐
│ User Password → PBKDF2 → Database Key ✅ (Recoverable)  │
│                                                          │
│ Android Keystore → Random AES Key → Files ❌ (Lost!)    │
└─────────────────────────────────────────────────────────┘
```

**Critical Issue:**
- File encryption uses **random key** from Android Keystore
- Key is **NOT password-derived**
- If keystore breaks → Files are **permanently unrecoverable**
- Database is safe (password-derived) but photos/videos are LOST

### When Keystore Can Break

1. **Factory reset** → Keystore wiped
2. **Lock screen removed** → Keystore invalidated
3. **OS upgrade** → Keystore corruption possible
4. **Backup/restore** → Keystore not transferred
5. **Custom ROM** → Keystore implementation varies
6. **Device change** → Keystore is device-specific
7. **Android bugs** → Manufacturer-specific issues

**Impact:** User loses ALL encrypted media even with correct password!

---

## Proposed Solution

### New Architecture (SECURE & RECOVERABLE)

```
┌──────────────────────────────────────────────────────────────┐
│                     User Password                             │
│                          ↓                                    │
│              PBKDF2-SHA256 (100k iterations)                  │
│                          ↓                                    │
│                   Master Key (256-bit)                        │
│                          ↓                                    │
│         ┌────────────────┴────────────────┐                  │
│         ↓                                  ↓                  │
│  File Encryption Key              Database Encryption Key     │
│  (AES-256-GCM)                    (SQLCipher)                 │
│  Stored encrypted                 Stored encrypted            │
│  ✅ RECOVERABLE                    ✅ RECOVERABLE              │
└──────────────────────────────────────────────────────────────┘
```

### Key Derivation Strategy

**Step 1: Password Setup**
```kotlin
fun setupPassword(password: String) {
    // 1. Generate random salt (32 bytes)
    val salt = SecureRandom().nextBytes(32)

    // 2. Derive master key from password
    val masterKey = PBKDF2(password, salt, 100000 iterations, 256-bit)

    // 3. Derive file encryption key from master key
    val fileKey = HKDF(masterKey, "file-encryption", 256-bit)

    // 4. Derive database key from master key
    val dbKey = HKDF(masterKey, "database-encryption", 256-bit)

    // 5. Store encrypted with Android Keystore (fallback protection)
    encryptedPrefs.put("file_encryption_key", encrypt(fileKey, keystoreKey))
    encryptedPrefs.put("database_encryption_key", encrypt(dbKey, keystoreKey))
    encryptedPrefs.put("salt", salt)
}
```

**Step 2: Normal Unlock**
```kotlin
fun unlockVault(password: String): Boolean {
    // Verify password
    if (!verifyPasswordHash(password)) return false

    // Try to load from encrypted storage
    try {
        fileKey = decrypt(encryptedPrefs["file_encryption_key"], keystoreKey)
        dbKey = decrypt(encryptedPrefs["database_encryption_key"], keystoreKey)
        return true
    } catch (keystoreBroken: Exception) {
        // Fallback: Re-derive from password
        return recoverFromPassword(password)
    }
}
```

**Step 3: Keystore Failure Recovery**
```kotlin
fun recoverFromPassword(password: String): Boolean {
    val salt = encryptedPrefs["salt"] ?: return false

    // Re-derive master key from password
    val masterKey = PBKDF2(password, salt, 100000, 256-bit)

    // Re-derive file and database keys
    val fileKey = HKDF(masterKey, "file-encryption", 256-bit)
    val dbKey = HKDF(masterKey, "database-encryption", 256-bit)

    // Cache in memory for session
    this.fileKey = fileKey
    this.dbKey = dbKey

    // Try to re-store in keystore (if available now)
    tryRestoreToKeystore(fileKey, dbKey)

    return true
}
```

---

## Implementation Details

### Files to Modify

1. **SecurityManager.kt** - Add password-derived key generation
2. **PasswordManager.kt** - Integrate file key derivation
3. **SecureFileManager.kt** - Use password-derived key instead of keystore
4. **VaultManager.kt** - Add recovery flow
5. **VaultUnlockScreen.kt** - Show recovery option if keystore broken

### New Methods Needed

```kotlin
// SecurityManager.kt
fun deriveFileEncryptionKeyFromPassword(password: String, salt: ByteArray): SecretKey
fun recoverKeysFromPassword(password: String): RecoveryResult

// PasswordManager.kt
fun setupPasswordWithFileKeyDerivation(password: String): Boolean
fun exportBackupData(): BackupData  // Salt + encrypted metadata

// VaultManager.kt
fun unlockWithRecovery(password: String): UnlockResult
enum class UnlockResult {
    SUCCESS,
    WRONG_PASSWORD,
    KEYSTORE_BROKEN_RECOVERED,
    KEYSTORE_BROKEN_CANNOT_RECOVER
}
```

---

## Security Considerations

### Is This Safe?

**Yes!** ✅ This is MORE secure than current approach:

1. **Double Protection:**
   - Keys stored encrypted in EncryptedSharedPreferences (Android Keystore)
   - Can be re-derived from password if keystore fails
   - Best of both worlds

2. **No Security Downgrade:**
   - Still uses AES-256-GCM for files
   - Still uses PBKDF2-SHA256 for password derivation
   - Still uses hardware keystore when available
   - Adds password recovery as fallback

3. **Standard Practice:**
   - Used by: 1Password, Bitwarden, KeePass, VeraCrypt
   - NIST recommends password-derived keys for user data

### Key Derivation Function (KDF)

**PBKDF2-SHA256:**
- Iterations: 100,000 (NIST minimum)
- Output: 256-bit master key
- Salt: 32 bytes random (stored with hash)

**HKDF (for key expansion):**
```kotlin
fun deriveSubKey(masterKey: ByteArray, context: String): SecretKey {
    val hkdf = HKDFBytesGenerator(SHA256Digest())
    hkdf.init(HKDFParameters(masterKey, null, context.toByteArray()))
    val subKey = ByteArray(32)
    hkdf.generateBytes(subKey, 0, 32)
    return SecretKeySpec(subKey, "AES")
}
```

---

## User Experience

### Normal Flow (Keystore Working)
1. User enters password
2. Password verified instantly
3. Keys loaded from encrypted storage
4. Vault unlocks immediately
5. **No change from current UX**

### Recovery Flow (Keystore Broken)
1. User enters password
2. Password verified
3. Keystore access fails → Show recovery dialog
4. "Recovering encryption keys from password..." (2-3 seconds)
5. Keys re-derived via PBKDF2 (slow by design)
6. Vault unlocks successfully
7. Keys re-stored in keystore (if available now)

### Recovery Dialog Example

```
┌─────────────────────────────────────────────┐
│  🔧 Keystore Recovery                        │
│                                              │
│  Your device's secure storage was reset.    │
│  Recovering your encryption keys from        │
│  your password...                            │
│                                              │
│  [████████████░░░░░░] 75%                   │
│                                              │
│  This may take a few seconds.               │
└─────────────────────────────────────────────┘
```

---

## Migration Strategy

### For Existing Users (Has Encrypted Files)

**Problem:** Files already encrypted with old keystore-only key

**Solutions:**

**Option 1: Re-encrypt All Files (Recommended)**
```kotlin
fun migrateToPasswordDerivedKey(password: String) {
    // 1. Derive new password-based key
    val newFileKey = deriveFileKeyFromPassword(password)

    // 2. Get all encrypted files
    val files = mediaFileDao.getAllFiles()

    // 3. Re-encrypt each file
    files.forEach { file ->
        // Decrypt with old keystore key
        val decrypted = decryptFile(file, oldKeystoreKey)

        // Encrypt with new password-derived key
        val reEncrypted = encryptFile(decrypted, newFileKey)

        // Update file path
        file.encryptedFilePath = reEncrypted.path
        mediaFileDao.update(file)
    }

    // 4. Store new key
    storePasswordDerivedKey(newFileKey)
}
```

**Option 2: Dual-Key System (Backward Compatible)**
```kotlin
fun decryptFile(file: MediaFileEntity): InputStream {
    // Try new password-derived key first
    try {
        return decryptWithPasswordKey(file)
    } catch (e: Exception) {
        // Fallback to old keystore key (legacy files)
        return decryptWithKeystoreKey(file)
    }
}
```

**Option 3: Lazy Migration**
```kotlin
fun decryptFile(file: MediaFileEntity): InputStream {
    if (file.isLegacyKeystoreEncrypted) {
        // Decrypt with old key
        val decrypted = decryptWithKeystoreKey(file)

        // Re-encrypt with new password key (background)
        scope.launch {
            reEncryptFileWithPasswordKey(file)
        }

        return decrypted
    } else {
        return decryptWithPasswordKey(file)
    }
}
```

### For New Users (Fresh Install)

No migration needed:
- Password setup derives file key immediately
- All new files encrypted with password-derived key
- Keystore used only for additional protection layer

---

## Backup & Recovery Flow

### Export Backup
```kotlin
data class VaultBackup(
    val salt: String,           // Base64-encoded
    val version: Int = 1,
    val createdAt: Long
)

fun exportBackup(): String {
    val backup = VaultBackup(
        salt = Base64.encode(this.salt),
        version = 1,
        createdAt = System.currentTimeMillis()
    )
    return Json.encode(backup)
}
```

### Import Backup on New Device
```kotlin
fun importBackup(backupJson: String, password: String): Boolean {
    val backup = Json.decode<VaultBackup>(backupJson)
    val salt = Base64.decode(backup.salt)

    // Derive keys from password + imported salt
    val masterKey = PBKDF2(password, salt, 100000, 256)
    val fileKey = HKDF(masterKey, "file-encryption", 256)
    val dbKey = HKDF(masterKey, "database-encryption", 256)

    // Store in new device's keystore
    storeKeys(fileKey, dbKey)

    return true
}
```

### User Instructions
```
To restore on a new device:
1. Install MyFolder
2. Tap "Restore from backup"
3. Enter your password
4. Paste your backup code
5. Your vault will be restored!

⚠️ Save your backup code:
eyJzYWx0IjoiYmFzZTY0ZW5jb2RlZCIsInZlcnNpb24iOjF9
```

---

## Testing Plan

### Unit Tests
```kotlin
@Test
fun `derives same key from password and salt`() {
    val password = "test1234"
    val salt = ByteArray(32) { 1 }

    val key1 = deriveFileKey(password, salt)
    val key2 = deriveFileKey(password, salt)

    assertEquals(key1, key2)
}

@Test
fun `recovers from keystore failure`() {
    // Setup with password
    setupPassword("password123")

    // Simulate keystore failure
    simulateKeystoreCrash()

    // Should still unlock with password
    assertTrue(unlockWithRecovery("password123"))
}

@Test
fun `different passwords produce different keys`() {
    val salt = ByteArray(32) { 1 }
    val key1 = deriveFileKey("password1", salt)
    val key2 = deriveFileKey("password2", salt)

    assertNotEquals(key1, key2)
}
```

### Integration Tests
1. Encrypt file with password-derived key → Decrypt successfully
2. Simulate keystore failure → Recover with password
3. Export backup → Import on "new device" → Access files
4. Change password → Re-derive keys → Files still accessible

---

## Performance Impact

### Key Derivation Time

**PBKDF2-SHA256 with 100,000 iterations:**
- Modern phone: 100-300ms
- Older phone: 300-800ms

**When does this happen?**
- Only during unlock (once per session)
- NOT on every file access (key cached in memory)
- Acceptable UX cost for security

### Comparison

| Operation | Current | With Password Derivation |
|-----------|---------|--------------------------|
| Vault unlock (keystore working) | 50ms | 50ms (same - load from storage) |
| Vault unlock (keystore broken) | FAIL ❌ | 300ms (PBKDF2 re-derivation) ✅ |
| File decryption | Instant | Instant (same cached key) |
| File encryption | Instant | Instant (same cached key) |

**Net result:** No performance change in normal use, recovery possible in disaster.

---

## Rollout Plan

### Phase 1: Add Password Derivation (2-3 days)
1. Implement HKDF key derivation
2. Add password-derived file key generation
3. Update SecurityManager with dual-key support
4. Add unit tests

### Phase 2: Migration System (2-3 days)
1. Detect legacy keystore-encrypted files
2. Implement lazy re-encryption
3. Add migration progress UI
4. Test with existing database

### Phase 3: Recovery UI (1-2 days)
1. Add keystore failure detection
2. Create recovery dialog
3. Show progress during PBKDF2
4. Handle edge cases

### Phase 4: Backup/Restore (1-2 days)
1. Implement backup export
2. Create restore flow
3. Add QR code for easy transfer
4. Write user documentation

### Phase 5: Testing & Release (2-3 days)
1. Comprehensive integration testing
2. Test on multiple devices
3. Simulate keystore failures
4. Beta testing with users

**Total Estimated Time:** 10-14 days

---

## Comparison: Keystore-Only vs Password-Derived

| Aspect | Current (Keystore-Only) | Proposed (Password-Derived) |
|--------|-------------------------|------------------------------|
| **Security** | Strong (hardware-backed) | Strong (password + hardware) |
| **Recovery** | ❌ Impossible if keystore fails | ✅ Always recoverable with password |
| **Device Transfer** | ❌ Cannot restore on new device | ✅ Backup code + password |
| **Performance** | Fast (cached) | Fast (cached, same speed) |
| **Complexity** | Low | Medium (key derivation + fallback) |
| **User Trust** | ⚠️ Risk of total data loss | ✅ "My password = my data" |

---

## Recommendation

**IMPLEMENT THIS IMMEDIATELY** 🚨

**Why:**
1. **Critical data loss risk** in current implementation
2. **Industry standard** for encrypted vaults (1Password, Bitwarden use this)
3. **No performance cost** in normal use
4. **Better user trust** ("I can always recover with my password")
5. **Required for production** security audit

**Similar Apps:**
- **Tella:** Uses password-derived keys with keystore as additional layer
- **Signal:** Uses password-derived keys for backup encryption
- **1Password:** Master password derives all encryption keys
- **Bitwarden:** Password + KDF for vault encryption

---

## Implementation Priority

**Priority 1 (Critical):**
- [ ] Implement password-derived file key generation
- [ ] Add keystore failure detection
- [ ] Implement recovery from password

**Priority 2 (Important):**
- [ ] Create migration system for existing users
- [ ] Add recovery UI with progress indicator
- [ ] Comprehensive testing

**Priority 3 (Nice to Have):**
- [ ] Backup/restore with QR codes
- [ ] Migration analytics
- [ ] User education in settings

---

**End of Plan**

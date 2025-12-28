# Import Encrypted Files Fix

## 🔧 ISSUE - FIXED

**Problem**: When importing already-encrypted files (e.g., from backup or another device), the database was storing the wrong filename and the file wasn't getting a new random UUID.

**Status**: ✅ FIXED

---

## Problem Statement

### The Issue

When importing an already-encrypted file (`.enc` file), the app correctly:
1. ✅ Detected the file was encrypted
2. ✅ Skipped re-encryption (good - saves time and CPU)
3. ✅ Extracted metadata from the encrypted file header

But incorrectly:
4. ❌ Stored the **encrypted UUID filename** in database's `originalFileName` field
5. ❌ Kept the **same UUID filename** instead of generating a new one

### Example from Build Log

```
Line 9:  Filename from ContentResolver: bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc
Line 60: Metadata: FileMetadata(filename=MEDIA_20251228_172512.jpg, ...)
Line 82: Database stored: fileName=bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc  ❌ WRONG!
```

**Expected**:
```
Database: originalFileName = "MEDIA_20251228_172512.jpg"  ← From metadata
          encryptedFileName = "a1b2c3d4-NEW-UUID-9876-543210fedcba.enc"  ← New random UUID
```

**Actual Before Fix**:
```
Database: originalFileName = "bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc"  ❌ Encrypted name
          encryptedFileName = "bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc"  ❌ Same UUID reused
```

---

## Impact

### User Experience Issues

1. **Wrong Filename in UI**:
   - User sees: `bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc` in file list
   - Should see: `MEDIA_20251228_172512.jpg`

2. **File Appears as "Orphaned"**:
   - Database has encrypted filename as original name
   - No mapping to actual user-friendly name
   - User can't identify what the file is

3. **Breaks File Sharing/Export**:
   - If user exports the file, it would be named `bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc`
   - Should be named `MEDIA_20251228_172512.jpg`

### Security Issues

1. **UUID Reuse**:
   - When importing from backup, same UUID is kept
   - If importing the same file multiple times, potential collision
   - Loses the randomness benefit of UUID filenames

---

## The Fix

### Code Changes

**File**: [ImportMediaUseCase.kt:108-189](app/src/main/java/com/kcpd/myfolder/domain/usecase/ImportMediaUseCase.kt#L108-L189)

### Fix 1: Generate New Random UUID for Imported Files

**Before**:
```kotlin
if (existingMetadata != null) {
    android.util.Log.d("ImportMediaUseCase", "  ✓ File is already encrypted! Skipping encryption.")
    android.util.Log.d("ImportMediaUseCase", "  Metadata: $existingMetadata")

    // Ensure target filename has .enc
    var targetName = fileName  // ❌ Uses imported filename
    if (!targetName.endsWith(".enc")) {
        targetName += ".enc"
    }

    // Ensure unique filename
    var targetFile = File(secureDir, targetName)  // ❌ Keeps same UUID
    var counter = 1
    while (targetFile.exists()) {
        val nameWithoutExt = targetName.substringBeforeLast(".enc")
        targetFile = File(secureDir, "$nameWithoutExt($counter).enc")
        counter++
    }

    // Move temp file to secure storage
    if (tempFile.renameTo(targetFile)) {
        encryptedFile = targetFile
    } else {
        tempFile.copyTo(targetFile, overwrite = true)
        encryptedFile = targetFile
        tempFile.delete()
    }
}
```

**After**:
```kotlin
if (existingMetadata != null) {
    android.util.Log.d("ImportMediaUseCase", "  ✓ File is already encrypted! Skipping encryption.")
    android.util.Log.d("ImportMediaUseCase", "  Metadata: $existingMetadata")

    // SECURITY: Generate NEW random UUID filename for imported encrypted files
    // This ensures consistent security model (all files use random UUIDs)
    // Even if importing from another device/backup, generate new UUID
    val randomFileName = java.util.UUID.randomUUID().toString()  // ✅ New UUID
    val targetFile = File(secureDir, "$randomFileName.enc")

    android.util.Log.d("ImportMediaUseCase", "  New random filename: ${targetFile.name}")

    // Move temp file to secure storage with new UUID filename
    if (tempFile.renameTo(targetFile)) {
        encryptedFile = targetFile
    } else {
        // Fallback copy if rename fails (e.g. cross-filesystem)
        tempFile.copyTo(targetFile, overwrite = true)
        encryptedFile = targetFile
        tempFile.delete()
    }
}
```

### Fix 2: Use Original Filename from Metadata for Database

**Before**:
```kotlin
val entity = MediaFileEntity(
    id = id,
    originalFileName = fileName,  // ❌ "bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc"
    encryptedFileName = encryptedFile.name,
    encryptedFilePath = encryptedFile.absolutePath,
    // ... rest of fields
)
```

**After**:
```kotlin
// CRITICAL FIX: For already-encrypted files, use the ORIGINAL filename from metadata
// not the encrypted filename (UUID) from the import source
val originalFileName = if (existingMetadata != null) {
    // File was already encrypted - use original filename from its metadata
    existingMetadata.filename  // ✅ "MEDIA_20251228_172512.jpg"
} else {
    // File was just encrypted - use the filename we passed to encryptFile
    fileName
}
android.util.Log.d("ImportMediaUseCase", "  Original filename for DB: $originalFileName")

val entity = MediaFileEntity(
    id = id,
    originalFileName = originalFileName,  // ✅ Uses extracted original name
    encryptedFileName = encryptedFile.name,
    encryptedFilePath = encryptedFile.absolutePath,
    // ... rest of fields
)
```

---

## How It Works Now

### Import Flow for Already-Encrypted Files

**Scenario**: User downloads `bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc` from cloud backup and imports it.

**Before Fix**:
```
1. Import file: bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc
2. Detect: Already encrypted ✅
3. Extract metadata: filename="MEDIA_20251228_172512.jpg" ✅
4. Save as: bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc  ❌ Same UUID
5. Database stores: originalFileName="bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc"  ❌ Wrong

Result: User sees "bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc" in file list
```

**After Fix**:
```
1. Import file: bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc
2. Detect: Already encrypted ✅
3. Extract metadata: filename="MEDIA_20251228_172512.jpg" ✅
4. Generate NEW UUID: a1b2c3d4-e5f6-7890-abcd-ef1234567890 ✅
5. Save as: a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc  ✅ New random UUID
6. Database stores: originalFileName="MEDIA_20251228_172512.jpg"  ✅ Correct!
                     encryptedFileName="a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"

Result: User sees "MEDIA_20251228_172512.jpg" in file list ✅
```

---

## Benefits

### 1. Correct User Experience

**Before**:
- File list shows: `bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc` ❌
- User has no idea what this file is

**After**:
- File list shows: `MEDIA_20251228_172512.jpg` ✅
- User can identify their files

### 2. Security Consistency

**Before**:
- New files: Random UUID ✅
- Imported files: Same UUID as source ❌
- Inconsistent security model

**After**:
- New files: Random UUID ✅
- Imported files: New random UUID ✅
- Consistent security model everywhere

### 3. Prevents UUID Collisions

**Before**:
- Importing same file twice → UUID collision → Adds `(1)`, `(2)` suffix
- Breaks clean UUID model

**After**:
- Importing same file twice → Different UUIDs each time
- No collisions, clean UUID model maintained

### 4. Device Independence

**Before**:
- UUIDs tied to source device
- Can track which device created which file

**After**:
- New UUID generated on import
- Device-independent storage
- Better for privacy when sharing backups

---

## Use Cases Fixed

### Use Case 1: Restore from Cloud Backup

**Scenario**: User lost phone, downloads encrypted files from Google Drive/S3 to new device.

**Before Fix**:
- Downloads `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc` from cloud
- Imports to app
- File shows as `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc` in list ❌
- User can't identify files

**After Fix**:
- Downloads `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc` from cloud
- Imports to app
- Extracts original filename from encrypted metadata: `vacation_photo.jpg`
- File shows as `vacation_photo.jpg` in list ✅
- Saves with NEW UUID: `f9e8d7c6-b5a4-3210-9876-543210fedcba.enc`

### Use Case 2: Import from Another App

**Scenario**: User has encrypted files from another MySecureFolder installation or similar app.

**Before Fix**:
- Import encrypted file
- Database stores encrypted filename as original name
- File appears with UUID name in UI

**After Fix**:
- Import encrypted file
- Reads metadata to get original filename
- Database stores correct original name
- File appears with user-friendly name in UI
- Gets new UUID for this device

### Use Case 3: Share Encrypted Files Between Devices

**Scenario**: User wants to transfer encrypted files from Device A to Device B.

**Before Fix**:
- Export from Device A: `uuid-from-device-a.enc`
- Import to Device B: Keeps same UUID
- Both devices have same UUID (correlation possible)

**After Fix**:
- Export from Device A: `uuid-from-device-a.enc`
- Import to Device B: Gets new UUID: `uuid-from-device-b.enc`
- Different UUIDs on each device (better privacy)

---

## Testing

### Manual Test Steps

1. **Export an encrypted file from the app**:
   - Select a file in the app
   - Export/share the encrypted `.enc` file
   - Note the UUID filename (e.g., `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`)

2. **Import it back**:
   - Use import function to import the `.enc` file
   - Check logs for metadata extraction
   - Check that a NEW UUID is generated

3. **Verify in UI**:
   - File should show with ORIGINAL filename (e.g., `vacation_photo.jpg`)
   - Not the encrypted UUID name

4. **Check database**:
   ```sql
   SELECT originalFileName, encryptedFileName FROM media_files ORDER BY createdAt DESC LIMIT 1;
   ```
   - `originalFileName` should be the user-friendly name
   - `encryptedFileName` should be a NEW UUID (different from imported file)

5. **Check filesystem**:
   ```bash
   adb shell ls /data/data/com.kcpd.myfolder/files/secure_media/photos/
   ```
   - Should see NEW UUID filename, not the imported one

### Expected Log Output (After Fix)

```
ImportMediaUseCase: Step 5b: Checking if already encrypted...
ImportMediaUseCase:   ✓ File is already encrypted! Skipping encryption.
ImportMediaUseCase:   Metadata: FileMetadata(filename=MEDIA_20251228_172512.jpg, ...)
ImportMediaUseCase:   New random filename: f9e8d7c6-b5a4-3210-9876-543210fedcba.enc  ← NEW UUID
ImportMediaUseCase: Step 8: Creating database entry...
ImportMediaUseCase:   Original filename for DB: MEDIA_20251228_172512.jpg  ← Correct name
ImportMediaUseCase:   ✓ Database entry created
```

### Verification

**Check 1: UI displays correct name**
```
File list shows:
  MEDIA_20251228_172512.jpg  ✅
NOT:
  bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc  ❌
```

**Check 2: Database has correct mapping**
```sql
originalFileName: MEDIA_20251228_172512.jpg  ✅
encryptedFileName: f9e8d7c6-b5a4-3210-9876-543210fedcba.enc  ✅ (NEW UUID)
```

**Check 3: Filesystem has new UUID**
```bash
/secure_media/photos/f9e8d7c6-b5a4-3210-9876-543210fedcba.enc  ✅ (NEW UUID)
```

---

## Edge Cases Handled

### 1. Corrupted Encrypted File

**Scenario**: Import a file with `.enc` extension but invalid header.

**Behavior**:
- `validateAndGetMetadata()` returns `null`
- Treated as unencrypted file
- Gets encrypted normally with random UUID
- ✅ Gracefully handled

### 2. Encrypted File Without .enc Extension

**Scenario**: Import `myfile.jpg` that's actually encrypted.

**Behavior**:
- `validateAndGetMetadata()` detects encryption by magic header
- Returns metadata
- Skips re-encryption
- Generates new UUID
- ✅ Correctly handled

### 3. Importing Same File Multiple Times

**Scenario**: User imports the same backup file 3 times.

**Before Fix**:
- First import: `uuid.enc`
- Second import: `uuid(1).enc` ❌
- Third import: `uuid(2).enc` ❌

**After Fix**:
- First import: `uuid-1.enc`
- Second import: `uuid-2.enc` (different UUID)
- Third import: `uuid-3.enc` (different UUID)
- ✅ Clean UUIDs, no suffixes

---

## Performance Impact

**Before Fix**:
- No re-encryption ✅
- Fast import ✅
- Wrong database entry ❌

**After Fix**:
- No re-encryption ✅
- Fast import ✅
- Correct database entry ✅
- Minimal overhead (UUID generation is instant)

**Overhead**: ~1-2ms for UUID generation (negligible)

---

## Related Files

- [ImportMediaUseCase.kt](app/src/main/java/com/kcpd/myfolder/domain/usecase/ImportMediaUseCase.kt) - Main fix location
- [SecureFileManager.kt](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt) - `validateAndGetMetadata()` function
- [MediaFileEntity.kt](app/src/main/java/com/kcpd/myfolder/data/database/entity/MediaFileEntity.kt) - Database schema

---

## Timeline

- **Discovered**: 2025-12-28 (User reported imported files showing UUID names)
- **Root Cause**: Database stored imported filename instead of metadata filename
- **Fixed**: 2025-12-28 (Extract original name from metadata, generate new UUID)
- **Status**: ✅ **RESOLVED**

---

## Conclusion

This fix ensures that when importing already-encrypted files:

1. ✅ Original filename is correctly extracted from encrypted metadata
2. ✅ Database stores user-friendly filename for UI display
3. ✅ New random UUID is generated for filesystem storage
4. ✅ Consistent security model across all files
5. ✅ No UUID collisions from repeated imports
6. ✅ Device-independent storage (new UUIDs per device)

**User Experience**:
- Before: Sees `bb27f76f-1972-4ca1-ab4c-21e2e69c249b.enc` ❌
- After: Sees `MEDIA_20251228_172512.jpg` ✅

**Security Model**:
- Before: Inconsistent (imported files keep source UUIDs)
- After: Consistent (all files get fresh random UUIDs) ✅

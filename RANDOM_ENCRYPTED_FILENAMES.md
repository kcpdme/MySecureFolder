# Random Encrypted Filenames Implementation

## Overview
Implemented UUID-based random filenames for encrypted files to improve security and eliminate the "rotated_" prefix issue.

## Problem Statement

**Before:**
```
Original file: MEDIA_20251228_164850.jpg
  → Rotation: rotated_MEDIA_20251228_164850.jpg (temp)
  → Encryption: rotated_MEDIA_20251228_164850.jpg.enc
  → Database: originalFileName = "MEDIA_20251228_164850.jpg"
  → Disk: rotated_MEDIA_20251228_164850.jpg.enc
```

**Issues:**
1. ❌ Encrypted files had "rotated_" prefix (confusing, metadata leakage)
2. ❌ Filename on disk revealed info about content
3. ❌ Inconsistent: DB shows correct name, disk shows "rotated_"
4. ❌ Filesystem pollution with descriptive names

## Solution Implemented

**After:**
```
Original file: MEDIA_20251228_164850.jpg
  → Rotation: rotated_MEDIA_20251228_164850.jpg (temp, deleted after)
  → Encryption: a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc (random UUID)
  → Database: originalFileName = "MEDIA_20251228_164850.jpg"
  → Disk: a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc
```

**Benefits:**
1. ✅ No metadata leakage through filesystem
2. ✅ No "rotated_" prefix pollution
3. ✅ Better security (attacker can't guess content from filename)
4. ✅ Collision-free (UUID guarantees uniqueness)
5. ✅ Clean separation: DB = user data, Disk = encrypted blobs
6. ✅ UI still shows correct filenames (reads from database)

---

## Changes Made

### 1. **SecureFileManager.kt** - Random Filename Generation

**Location:** [SecureFileManager.kt:68-79](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt#L68-L79)

**Before:**
```kotlin
suspend fun encryptFile(sourceFile: File, destinationDir: File): File {
    val encryptedFile = File(destinationDir, sourceFile.name + ENCRYPTED_FILE_EXTENSION)
    // ...
}
```

**After:**
```kotlin
suspend fun encryptFile(
    sourceFile: File,
    destinationDir: File,
    originalFileName: String? = null  // ← NEW parameter
): File {
    // Use random UUID-based filename for encrypted file
    val randomFileName = java.util.UUID.randomUUID().toString()
    val encryptedFile = File(destinationDir, randomFileName + ENCRYPTED_FILE_EXTENSION)

    // Store ORIGINAL filename in metadata, not temp/rotated name
    val metadata = FileMetadata(
        filename = originalFileName ?: sourceFile.name,  // ← Uses original
        timestamp = System.currentTimeMillis()
    )
    // ...
}
```

**Key Changes:**
- Added `originalFileName` parameter (optional, defaults to `sourceFile.name`)
- Generate random UUID for encrypted filename
- Store original filename in encrypted metadata
- Encrypted file: `{uuid}.enc` instead of `{original-name}.enc`

---

### 2. **MediaRepository.kt** - Pass Original Filename

**Updated 3 locations:**

#### **Location 1: Main File Import** [MediaRepository.kt:234-239](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt#L234-L239)

```kotlin
// Before
encryptedFile = secureFileManager.encryptFile(fileToEncrypt, secureDir)

// After
encryptedFile = secureFileManager.encryptFile(
    sourceFile = fileToEncrypt,
    destinationDir = secureDir,
    originalFileName = fileName  // ← Preserves original, not "rotated_"
)
```

#### **Location 2: Migration Encryption** [MediaRepository.kt:82-86](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt#L82-L86)

```kotlin
val encryptedFile = secureFileManager.encryptFile(
    sourceFile = file,
    destinationDir = categorySecureDir,
    originalFileName = file.name
)
```

#### **Location 3: Note Creation** [MediaRepository.kt:323-328](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt#L323-L328)

```kotlin
encryptedFile = secureFileManager.encryptFile(
    sourceFile = tempFile,
    destinationDir = secureDir,
    originalFileName = fileName
)
```

---

### 3. **ImportMediaUseCase.kt** - Import Flow Update

**Location:** [ImportMediaUseCase.kt:141-145](app/src/main/java/com/kcpd/myfolder/domain/usecase/ImportMediaUseCase.kt#L141-L145)

```kotlin
encryptedFile = secureFileManager.encryptFile(
    sourceFile = tempFile,
    destinationDir = secureDir,
    originalFileName = fileName
)
```

---

## How It Works

### **Encryption Flow**

1. **User imports:** `MEDIA_20251228_164850.jpg`
2. **Photo rotation (if needed):** Creates temp file `rotated_MEDIA_20251228_164850.jpg`
3. **Encryption:**
   - Generate random UUID: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
   - Create encrypted file: `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`
   - Store metadata inside encrypted file:
     ```json
     {
       "filename": "MEDIA_20251228_164850.jpg",  // Original name
       "timestamp": 1735401234567
     }
     ```
4. **Database entry:**
   ```kotlin
   MediaFileEntity(
       originalFileName = "MEDIA_20251228_164850.jpg",  // User sees this
       encryptedFileName = "a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc",
       encryptedFilePath = "/data/.../photos/a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"
   )
   ```
5. **Cleanup:** Delete temp `rotated_` file

### **Decryption Flow**

1. **User opens file** in UI (sees "MEDIA_20251228_164850.jpg" from database)
2. **App reads** `encryptedFilePath` from database
3. **Decrypts** `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`
4. **Displays** content

---

## Database Mapping

The database maintains the mapping between user-visible names and encrypted files:

| Field | Example Value | Purpose |
|-------|---------------|---------|
| `id` | `d2b16f4c-31a0-4085-a6d7-8fd8c4d0636a` | Unique record ID |
| `originalFileName` | `MEDIA_20251228_164850.jpg` | **What user sees in UI** |
| `encryptedFileName` | `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc` | Actual filename on disk |
| `encryptedFilePath` | `/data/.../a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc` | Full path to encrypted file |

**UI reads:** `originalFileName` → Shows `MEDIA_20251228_164850.jpg` ✅
**Decryption reads:** `encryptedFilePath` → Opens the actual file ✅

---

## Security Benefits

### **Before (Descriptive Filenames)**
```bash
/data/.../photos/
  ├── vacation_beach_2024.jpg.enc          # ❌ Metadata leakage
  ├── rotated_family_photo.jpg.enc         # ❌ Reveals content
  ├── secret_document.pdf.enc              # ❌ Reveals file type
  └── birthday_party_video.mp4.enc         # ❌ Reveals everything
```

**Attacker with filesystem access can:**
- ❌ Identify sensitive files by name
- ❌ Know file types from extensions
- ❌ Deduce content from names
- ❌ Build metadata profile

### **After (Random Filenames)**
```bash
/data/.../photos/
  ├── a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc  # ✅ No metadata
  ├── f9e8d7c6-b5a4-3210-9876-543210fedcba.enc  # ✅ No context
  ├── 12345678-9abc-def0-1234-567890abcdef.enc  # ✅ Anonymous
  └── 87654321-fedc-ba09-8765-43210fedcba9.enc  # ✅ Secure
```

**Attacker with filesystem access:**
- ✅ Cannot identify files by name
- ✅ Cannot determine file types
- ✅ Cannot deduce content
- ✅ Only sees random UUIDs

---

## Backward Compatibility

### **Existing Files**

Old files with descriptive names **still work**:
- Database has correct mapping
- Decryption reads from `encryptedFilePath`
- UI shows `originalFileName`

### **Migration Path (Optional)**

To rename existing files to UUID format:

```kotlin
suspend fun migrateToRandomFilenames() {
    val allFiles = mediaFileDao.getAllFiles()

    allFiles.forEach { entity ->
        val oldFile = File(entity.encryptedFilePath)
        if (oldFile.exists() && !isUuidFilename(entity.encryptedFileName)) {
            // Generate new random filename
            val newFileName = "${UUID.randomUUID()}.enc"
            val newFile = File(oldFile.parentFile, newFileName)

            // Rename file
            oldFile.renameTo(newFile)

            // Update database
            mediaFileDao.updateFile(
                entity.copy(
                    encryptedFileName = newFileName,
                    encryptedFilePath = newFile.absolutePath
                )
            )
        }
    }
}
```

---

## Testing

### **Manual Testing**

1. **Import a new photo:**
   ```
   Expected encrypted filename: {uuid}.enc (e.g., a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc)
   Expected UI display: Original filename (e.g., IMG_20241228_123456.jpg)
   ```

2. **Import a rotated photo:**
   ```
   Expected: NO "rotated_" prefix in encrypted filename
   Expected: Random UUID filename
   Expected: Original name in UI
   ```

3. **View encrypted file:**
   ```
   Expected: Opens correctly
   Expected: Displays with original filename
   ```

4. **Check logs:**
   ```
   Expected log: "Encrypted to: {uuid}.enc (original: IMG_20241228_123456.jpg)"
   ```

### **Verification**

```bash
# Check encrypted files directory
adb shell ls /data/data/com.kcpd.myfolder/files/secure_media/photos/

# Should see:
# a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc
# f9e8d7c6-b5a4-3210-9876-543210fedcba.enc
# (NOT: rotated_IMG_123.jpg.enc)
```

---

## Files Modified

1. ✅ [SecureFileManager.kt](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt)
   - Added `originalFileName` parameter to `encryptFile()`
   - Generate random UUID for encrypted filename
   - Store original filename in metadata

2. ✅ [MediaRepository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt)
   - Pass `originalFileName` in 3 places (import, migration, note creation)
   - Updated logging to show UUID and original name

3. ✅ [ImportMediaUseCase.kt](app/src/main/java/com/kcpd/myfolder/domain/usecase/ImportMediaUseCase.kt)
   - Pass `originalFileName` during import

---

## Conclusion

**Problem Solved:**
- ❌ No more "rotated_" prefix in encrypted filenames
- ❌ No metadata leakage through filesystem
- ✅ Better security with random UUID filenames
- ✅ Clean separation: DB stores user data, disk stores encrypted blobs
- ✅ UI unchanged (still shows correct filenames from database)

**Security Improvement:**
- **Before:** Encrypted filenames revealed content type and context
- **After:** Encrypted filenames are opaque UUIDs, zero metadata leakage

**User Experience:**
- **Unchanged:** Users see their original filenames in the UI
- **Improved:** No confusing "rotated_" prefixes
- **Robust:** UUID-based naming prevents any filename collisions

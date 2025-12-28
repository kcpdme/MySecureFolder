# Cloud Storage Security Fix: Filename Metadata Leakage

## 🔴 SECURITY ISSUE - FIXED

**Severity**: HIGH
**Impact**: Original filenames exposed on cloud storage (Google Drive, S3)
**Attack Vector**: Cloud provider admins, subpoenas, account compromise
**Status**: ✅ FIXED

---

## Problem Statement

### The Issue

When uploading encrypted files to Google Drive or S3, the app was using the **original user-visible filename** instead of the **encrypted filename (UUID)**.

**What Was Happening**:
```
Local Storage (Secure):
  /secure_media/photos/a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc  ✅ Random UUID

Google Drive (Metadata Leaked):
  MyFolderPrivate/Photos/MEDIA_20251228_164850.jpg  ❌ Original filename exposed!
  (Content: encrypted binary data from UUID file)

S3 Storage (Metadata Leaked):
  MyFolderPrivate/Photos/vacation_beach_2024.jpg  ❌ Original filename exposed!
  (Content: encrypted binary data from UUID file)
```

### Security Impact

**File Content**: ✅ SECURE
- The **actual file content** being uploaded was the encrypted `.enc` file
- Content is AES-CTR encrypted with random FEK (File Encryption Key)
- Attacker cannot decrypt content without Master Key

**Filename Metadata**: ❌ EXPOSED
- Original filenames visible on cloud storage
- Reveals content type, dates, context
- Pattern analysis possible (vacation photos, bank statements, etc.)
- Violates zero-knowledge principle

---

## Threat Model

### Attack Scenarios

1. **Cloud Provider Employee Access**
   - Google/AWS employee with access to file metadata
   - Can see: `family_vacation_2024.jpg`, `tax_document_2023.pdf`
   - Cannot decrypt: File content is encrypted

2. **Account Compromise**
   - Attacker gains access to Google Drive/S3 account
   - Can see: All filenames, folder structure, file sizes
   - Cannot decrypt: Files are encrypted blobs

3. **Government Subpoena**
   - Law enforcement requests cloud storage data
   - Provider hands over: File metadata including names
   - Provider cannot hand over: Decrypted content (they don't have keys)

4. **Cloud Provider Breach**
   - Cloud storage metadata database leaked
   - Exposed: Filenames, folder structures, timestamps
   - Safe: File content (encrypted)

### Privacy Violation

Even with encrypted content, **filename metadata reveals sensitive information**:

```
Examples of metadata leakage:
- "passport_scan_2024.jpg"        → Reveals document type
- "family_vacation_hawaii.mp4"    → Reveals personal life details
- "bank_statement_december.pdf"   → Reveals financial records
- "medical_report_hospital.jpg"   → Reveals health information
```

---

## The Fix

### Code Changes

#### 1. Google Drive Upload Fix

**File**: [GoogleDriveRepository.kt:96-108](app/src/main/java/com/kcpd/myfolder/data/repository/GoogleDriveRepository.kt#L96-L108)

**Before**:
```kotlin
val fileMetadata = com.google.api.services.drive.model.File()
fileMetadata.name = mediaFile.fileName  // ❌ Original filename: "vacation_photo.jpg"
fileMetadata.parents = listOf(parentFolderId)

val mediaContent = FileContent("application/octet-stream", fileToUpload)
```

**After**:
```kotlin
// SECURITY: Use encrypted filename (UUID) to prevent metadata leakage on Google Drive
// The original filename is already encrypted INSIDE the file's metadata
// Using the encrypted filename (UUID.enc) instead of original name prevents:
// - Filename-based content identification by Drive admins/attackers
// - Metadata leakage to Google's servers
// - Pattern analysis of file types and naming conventions
val encryptedFileName = fileToUpload.name  // ✅ UUID: "a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"

val fileMetadata = com.google.api.services.drive.model.File()
fileMetadata.name = encryptedFileName  // ✅ Use encrypted filename, not original
fileMetadata.parents = listOf(parentFolderId)

val mediaContent = FileContent("application/octet-stream", fileToUpload)
```

#### 2. S3 Upload Fix

**File**: [S3Repository.kt:123-140](app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt#L123-L140)

**Before**:
```kotlin
// Step 2: Upload the decrypted file to S3 with uniform path structure
val category = FolderCategory.fromMediaType(mediaFile.mediaType)

// Build folder path if file is in a folder
val folderPath = buildFolderPath(mediaFile.folderId)
val objectName = if (folderPath.isNotEmpty()) {
    "MyFolderPrivate/${category.displayName}/$folderPath/${mediaFile.fileName}"  // ❌ Original name
} else {
    "MyFolderPrivate/${category.displayName}/${mediaFile.fileName}"  // ❌ Original name
}
```

**After**:
```kotlin
// Step 2: Upload the encrypted file to S3 with uniform path structure
val category = FolderCategory.fromMediaType(mediaFile.mediaType)

// SECURITY: Use encrypted filename (UUID) to prevent metadata leakage on S3
// The original filename is already encrypted INSIDE the file's metadata
// Using the encrypted filename (UUID.enc) instead of original name prevents:
// - Filename-based content identification by S3 admins/attackers
// - Metadata leakage to cloud storage providers
// - Pattern analysis of file types and naming conventions
val encryptedFileName = fileToUpload.name  // ✅ UUID: "a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"

// Build folder path if file is in a folder
val folderPath = buildFolderPath(mediaFile.folderId)
val objectName = if (folderPath.isNotEmpty()) {
    "MyFolderPrivate/${category.displayName}/$folderPath/$encryptedFileName"  // ✅ UUID
} else {
    "MyFolderPrivate/${category.displayName}/$encryptedFileName"  // ✅ UUID
}
```

---

## Security Comparison

### Before Fix

**Google Drive**:
```
MyFolderPrivate/
  Photos/
    ├── family_vacation_2024.jpg        ❌ Metadata leaked
    ├── passport_scan.jpg               ❌ Metadata leaked
    └── birthday_party_video.mp4        ❌ Metadata leaked
  Documents/
    ├── tax_return_2023.pdf             ❌ Metadata leaked
    └── bank_statement_december.pdf     ❌ Metadata leaked
```

**S3 Storage**:
```
MyFolderPrivate/Photos/Work/client_meeting_notes.jpg  ❌ Reveals business context
MyFolderPrivate/Photos/Personal/medical_xray.jpg      ❌ Reveals health info
```

**Attacker Knowledge**:
- ❌ Can identify file types from extensions
- ❌ Can deduce content from descriptive names
- ❌ Can build metadata profile of user
- ❌ Can identify sensitive documents
- ✅ Cannot decrypt file content

### After Fix

**Google Drive**:
```
MyFolderPrivate/
  Photos/
    ├── a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc  ✅ No metadata
    ├── f9e8d7c6-b5a4-3210-9876-543210fedcba.enc  ✅ No metadata
    └── 12345678-9abc-def0-1234-567890abcdef.enc  ✅ No metadata
  Documents/
    ├── 87654321-fedc-ba09-8765-43210fedcba9.enc  ✅ No metadata
    └── abcdef12-3456-7890-abcd-ef1234567890.enc  ✅ No metadata
```

**S3 Storage**:
```
MyFolderPrivate/Photos/Work/a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc     ✅ Anonymous
MyFolderPrivate/Photos/Personal/f9e8d7c6-b5a4-3210-9876-543210fedcba.enc ✅ Anonymous
```

**Attacker Knowledge**:
- ✅ Cannot identify file types (all `.enc` extension)
- ✅ Cannot deduce content (random UUIDs)
- ✅ Cannot build meaningful metadata profile
- ✅ Cannot identify sensitive documents
- ✅ Cannot decrypt file content

---

## How It Works

### File Upload Flow

1. **User uploads photo**: `vacation_photo.jpg`
2. **Local encryption**: Creates `/secure_media/photos/a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`
3. **Database stores**:
   - `originalFileName` = `"vacation_photo.jpg"` (for UI display)
   - `encryptedFileName` = `"a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc"`
   - `encryptedFilePath` = `/secure_media/photos/a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`
4. **Cloud upload**: Uploads encrypted file with name `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`

### Where Original Filename is Stored

The original filename is stored in **3 secure locations**:

1. **Database** (encrypted by SQLCipher):
   ```sql
   originalFileName = "vacation_photo.jpg"
   ```

2. **File Metadata** (encrypted inside the `.enc` file header):
   ```
   File Header:
     MAGIC + VERSION + IV + ENCRYPTED_FEK + METADATA_LENGTH + ENCRYPTED_METADATA
                                                               ↑
                                                               Contains: {"filename": "vacation_photo.jpg", "timestamp": 1735401234567}
   ```

3. **UI Display** (read from database):
   - User sees: "vacation_photo.jpg"
   - App displays original name from database

**Cloud Storage**: Only sees random UUID, never the original name ✅

---

## Remaining Metadata Leakage

### What's Still Visible (Acceptable)

1. **Folder Structure**:
   ```
   MyFolderPrivate/Photos/Vacation/Hawaii/
   ```
   - Folder names are still visible
   - This is a known trade-off for usability
   - Users can create generic folder names if needed

2. **File Sizes**:
   - Encrypted file sizes are visible
   - Can reveal approximate original sizes
   - Mitigation: Padding could be added (future enhancement)

3. **Upload Timestamps**:
   - Cloud providers track upload times
   - Can reveal when files were backed up
   - Not easily mitigated

4. **File Count**:
   - Number of files in each category visible
   - Pattern analysis possible
   - Not easily mitigated

### What's Now Hidden (Critical)

1. ✅ **Original Filenames**: Replaced with UUIDs
2. ✅ **File Types**: All files use `.enc` extension
3. ✅ **Content Hints**: No descriptive names to analyze
4. ✅ **File Content**: AES-CTR encrypted

---

## Migration for Existing Files

### Issue

Existing files uploaded **before** this fix will have original filenames on cloud storage.

### Solution Options

**Option 1: Leave Existing Files** (Recommended)
- Existing files remain with original names
- New uploads use UUID names
- User can manually re-upload if concerned

**Option 2: Automatic Re-upload**
- Implement migration script to re-upload with UUID names
- Delete old files with original names
- More complex, but cleaner

**Option 3: Manual Re-upload via UI**
- Add "Re-upload with Secure Names" button in settings
- Let user trigger re-upload for existing files
- Best of both worlds

---

## Testing

### Manual Testing Steps

1. **Setup**: Build and install app with fix
2. **Upload to Google Drive**:
   - Take photo or import file
   - Upload to Google Drive
   - Check Google Drive web interface
   - **Expected**: Filename shows UUID (e.g., `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc`)
   - **NOT Expected**: Original filename (e.g., `MEDIA_20251228_164850.jpg`)

3. **Upload to S3**:
   - Configure S3 settings
   - Upload file
   - Check S3 bucket via web console
   - **Expected**: Object key uses UUID
   - **NOT Expected**: Object key uses original filename

4. **Check App UI**:
   - View uploaded file in app
   - **Expected**: App still shows original filename from database
   - **Expected**: File opens and displays correctly

### Verification Commands

**Google Drive** (via web interface):
```
Before Fix: MyFolderPrivate/Photos/vacation_photo.jpg
After Fix:  MyFolderPrivate/Photos/a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc
```

**S3** (via AWS CLI):
```bash
aws s3 ls s3://my-bucket/MyFolderPrivate/Photos/

# Before Fix:
2024-12-28 10:30:00   12345678 vacation_photo.jpg

# After Fix:
2024-12-28 10:30:00   12345678 a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc
```

---

## Security Recommendations

### Current State ✅

1. ✅ **File Content**: Encrypted with AES-CTR + random FEK
2. ✅ **Filenames**: Random UUIDs on cloud storage
3. ✅ **Master Key**: Never leaves device
4. ✅ **Zero-Knowledge**: Cloud provider cannot decrypt files

### Future Enhancements (Optional)

1. **Folder Name Encryption**
   - Encrypt folder names in cloud storage
   - Store mapping in local database
   - Tradeoff: Harder to browse via web interface

2. **File Padding**
   - Add random padding to files before encryption
   - Obscures actual file sizes
   - Tradeoff: Storage overhead (5-10%)

3. **Dummy Files**
   - Upload fake encrypted files to obscure patterns
   - Makes traffic analysis harder
   - Tradeoff: Storage and bandwidth cost

4. **Upload Timing Obfuscation**
   - Delay uploads randomly
   - Upload in batches instead of immediately
   - Tradeoff: Longer backup times

---

## Comparison with Other Apps

### Cryptomator
- ✅ Uses random filenames on cloud
- ✅ Encrypts folder names too
- ❌ Complex virtual filesystem

### Boxcryptor
- ✅ Uses random filenames
- ✅ Encrypts metadata
- ❌ Closed source

### Tella
- ❌ Does NOT support cloud storage
- ✅ Local storage only (very secure)
- ❌ No backup/sync

### MySecureFolder (After Fix)
- ✅ Uses random filenames on cloud
- ✅ Encrypted file content
- ⚠️ Folder names visible (usability tradeoff)
- ✅ Open source implementation

---

## Related Files

- [GoogleDriveRepository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/GoogleDriveRepository.kt) - Google Drive upload fix
- [S3Repository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt) - S3 upload fix
- [MediaFileEntity.kt](app/src/main/java/com/kcpd/myfolder/data/database/entity/MediaFileEntity.kt) - Database schema
- [SecureFileManager.kt](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt) - Encryption implementation

---

## Timeline

- **Discovered**: 2025-12-28 (User reported seeing original filenames on Google Drive)
- **Root Cause**: Upload code used `mediaFile.fileName` (original) instead of `fileToUpload.name` (encrypted UUID)
- **Fixed**: 2025-12-28 (Updated both Google Drive and S3 repositories)
- **Status**: ✅ **RESOLVED**

---

## Conclusion

This fix ensures that cloud storage providers (Google Drive, S3) **only see random UUIDs** instead of original filenames.

**Before**:
- ❌ Cloud storage: `vacation_photo.jpg`, `bank_statement.pdf` (metadata leaked)
- ✅ File content: Encrypted (secure)

**After**:
- ✅ Cloud storage: `a1b2c3d4-e5f6-7890-abcd-ef1234567890.enc` (no metadata)
- ✅ File content: Encrypted (secure)

**User Experience**:
- ✅ No change - users still see original filenames in the app
- ✅ Files still decrypt and open normally
- ✅ Better privacy - cloud providers see only random UUIDs

**Security Posture**:
- **Zero-Knowledge Backup**: Cloud providers cannot deduce file content from metadata
- **Defense in Depth**: Even if encryption is broken in the future, metadata doesn't reveal sensitive info
- **Privacy First**: Filenames are sensitive data and should be protected

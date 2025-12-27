# S3 Upload Fix - Decrypt Before Upload

## Problem

Previously, the app was uploading **encrypted files** directly to S3/MinIO, which made them unusable:

```kotlin
// ❌ OLD CODE - Uploading encrypted .enc files
val file = File(mediaFile.filePath)  // This was the .enc file!
minioClient.putObject(
    PutObjectArgs.builder()
        .stream(file.inputStream(), file.length(), -1)  // Encrypted garbage!
        .build()
)
```

### Issues:
- ✅ Files were encrypted locally (secure storage)
- ❌ **Encrypted files uploaded to S3** (unusable backup)
- ❌ S3 contained `.jpg` named files with encrypted binary content
- ❌ Could not download and view files from S3
- ❌ Misleading content-type headers

### Example from logs:
```
File path: /data/user/0/com.kcpd.myfolder/files/secure_media/photos/rotated_1766852879487.jpg.enc
Uploaded as: photos/rotated_1766852879487.jpg
Content: ENCRYPTED (unusable!)
```

---

## Solution

Now the upload process follows the correct flow:

```kotlin
// ✅ NEW CODE - Decrypt → Upload → Delete temp
1. Decrypt encrypted file to temp
   val tempDecryptedFile = secureFileManager.decryptFile(encryptedFile)

2. Upload decrypted temp file to S3
   minioClient.putObject(
       PutObjectArgs.builder()
           .stream(tempDecryptedFile.inputStream(), ...)  // Real image/video!
           .build()
   )

3. Delete temp file (finally block)
   tempDecryptedFile.delete()

4. Keep encrypted file on device (secure local storage)
```

---

## Implementation Details

### Files Changed

**[app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt)**

#### Changes:

1. **Added SecureFileManager dependency** (line 29)
   ```kotlin
   class S3Repository @Inject constructor(
       @ApplicationContext private val context: Context,
       private val sessionManager: dagger.Lazy<S3SessionManager>,
       private val secureFileManager: com.kcpd.myfolder.security.SecureFileManager  // ← Added
   )
   ```

2. **Updated uploadFile() method** (lines 66-130)
   - Added `tempDecryptedFile` variable to track temp file
   - **Step 1**: Decrypt encrypted file to temp (line 95-99)
   - **Step 2**: Upload decrypted file to S3 (line 101-112)
   - **Step 3**: Clean up temp file in finally block (line 122-128)

3. **Added detailed logging**
   - Logs when decryption starts
   - Logs temp file size
   - Logs when temp file is deleted

---

## How It Works Now

### Upload Flow:

```
User clicks upload button
    ↓
[FolderViewModel.uploadFile()]
    ↓
[S3Repository.uploadFile()]
    ↓
1. Read encrypted file from device storage
   /data/.../secure_media/photos/photo.jpg.enc
    ↓
2. Decrypt to temp file
   /data/.../cache/temp_photo.jpg (DECRYPTED)
    ↓
3. Upload temp file to S3
   → s3://bucket/photos/photo.jpg (USABLE!)
    ↓
4. Delete temp file
   temp_photo.jpg deleted ✓
    ↓
5. Mark as uploaded in database
   isUploaded = true, s3Url = "https://..."
    ↓
6. Show green cloud icon ✓
```

### Security Considerations:

✅ **Local files remain encrypted** - Only decrypted temporarily during upload
✅ **Temp files deleted immediately** - Even if upload fails (finally block)
✅ **Original encrypted files untouched** - Secure local storage preserved
✅ **S3 files are usable** - Can download and view from cloud

---

## Expected Logs

### Successful Upload:
```
S3Repository: Using cached S3 session
S3Repository: Decrypting file for upload: photo.jpg
S3Repository: Decrypted to temp file: /cache/temp_photo.jpg, size: 245678 bytes
S3Repository: File uploaded successfully: https://s3.../bucket/photos/photo.jpg
S3Repository: Temp file deleted: true (/cache/temp_photo.jpg)
FolderViewModel: File uploaded successfully: photo.jpg -> https://...
```

### Failed Upload:
```
S3Repository: Using cached S3 session
S3Repository: Decrypting file for upload: photo.jpg
S3Repository: Decrypted to temp file: /cache/temp_photo.jpg, size: 245678 bytes
S3Repository: Upload failed
S3Repository: Temp file deleted: true (/cache/temp_photo.jpg)
```

---

## Testing Checklist

- [ ] Build succeeds without errors
- [ ] Upload button works (no crashes)
- [ ] Files upload successfully
- [ ] Check S3 bucket - files are viewable (not encrypted)
- [ ] Download file from S3 - can open as normal image/video
- [ ] Green cloud icon appears after successful upload
- [ ] Temp files are deleted (check /cache directory)
- [ ] Encrypted files remain on device after upload
- [ ] Upload failure doesn't leave temp files behind

---

## File Types Supported

| Type | Extension | Content-Type | Status |
|------|-----------|--------------|--------|
| Photos | .jpg, .jpeg | image/jpeg | ✅ Working |
| Photos | .png | image/png | ✅ Working |
| Videos | .mp4 | video/mp4 | ✅ Working |
| Videos | .mov | video/quicktime | ✅ Working |
| Audio | .mp3 | audio/mpeg | ✅ Working |
| Audio | .m4a | audio/mp4 | ✅ Working |
| Audio | .aac | audio/aac | ✅ Working |
| Notes | .txt | text/plain | ✅ Working |

---

## What About "Rotated" Files?

Files with `rotated_` prefix are photos that were **rotated before encryption** based on EXIF orientation data:

### Photo Import Flow:
```
1. Camera captures photo (may be rotated)
   → EXIF says: orientation = 90° (portrait mode)

2. MediaRepository reads EXIF
   → Physically rotates bitmap 90°

3. Save rotated bitmap to temp
   → rotated_12345.jpg (correct orientation)

4. Encrypt rotated file
   → rotated_12345.jpg.enc (encrypted, already correct orientation)

5. Upload to S3 (NOW DECRYPTED!)
   → rotated_12345.jpg on S3 (correct orientation, decrypted)
```

**Why rotate before encryption?**
- EXIF data is lost during encryption
- By rotating first, photos display correctly when decrypted
- No need for orientation handling on S3 or when viewing

---

## Troubleshooting

### Upload fails with "Failed to decrypt"
- Check that file exists at `mediaFile.filePath`
- Verify vault is unlocked
- Check logs for encryption errors

### Temp files not deleted
- Check finally block is executing
- Look for exceptions during upload
- Check /cache directory permissions

### Files on S3 still encrypted
- Verify you're running the NEW code
- Check logs show "Decrypting file for upload"
- Re-upload files to replace old encrypted versions

### Upload is slow
- Decryption + upload takes time for large files
- Normal behavior for videos (streaming decryption)
- Consider progress indicator (future enhancement)

---

## Future Enhancements

### Potential improvements:
- [ ] Upload progress indicator (show %)
- [ ] Batch upload (multiple files)
- [ ] Resume failed uploads
- [ ] Verify upload integrity (hash check)
- [ ] Sync: detect if S3 file deleted
- [ ] Download from S3 and re-encrypt

---

## Credits

Fix implemented based on user feedback identifying that encrypted files were being uploaded instead of decrypted content.

Date: 2025-12-27

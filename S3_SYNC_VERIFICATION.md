# S3 Upload Verification & Sync

## Feature: Detect Deleted Files on S3

This feature allows the app to verify if uploaded files still exist on S3/MinIO and re-mark them for upload if they've been deleted from the server.

---

## Problem

Previously:
- ❌ Files marked as uploaded (`isUploaded = true`) stayed that way forever
- ❌ If you deleted a file from S3, the green cloud icon still showed
- ❌ No way to detect or re-upload deleted files
- ❌ Database and S3 could get out of sync

---

## Solution

Added three new capabilities:

### 1. **Verify Single File** - Check if one file exists on S3
### 2. **Verify Multiple Files** - Batch check uploaded files
### 3. **Sync Upload Status** - Auto-detect deleted files and mark for re-upload

---

## Implementation

### Files Changed

1. **[S3Repository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt)**
   - Added `verifyFileExists()` - Check if single file exists on S3
   - Added `verifyMultipleFiles()` - Batch check multiple files

2. **[MediaFileDao.kt](app/src/main/java/com/kcpd/myfolder/data/database/dao/MediaFileDao.kt)**
   - Added `markAsNotUploaded()` - Reset upload status in database

3. **[MediaRepository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt)**
   - Added `markAsNotUploaded()` - Wrapper for DAO method

4. **[FolderViewModel.kt](app/src/main/java/com/kcpd/myfolder/ui/folder/FolderViewModel.kt)**
   - Added `verifyUploadStatus()` - Verify single file
   - Added `syncUploadStatus()` - Sync all uploaded files

---

## How It Works

### Method 1: Verify Single File

```kotlin
// In FolderViewModel or wherever you have a MediaFile
viewModel.verifyUploadStatus(mediaFile)
```

**What happens:**
1. Calls S3 `statObject()` API to check if file exists
2. If file exists: Nothing happens (all good ✅)
3. If file deleted: Marks as not uploaded in database
   - `isUploaded = false`
   - `s3Url = NULL`
   - Green cloud icon disappears
   - Upload button appears again

**Logs:**
```
FolderViewModel: File deleted from S3, marked for re-upload: photo.jpg
```

---

### Method 2: Sync All Uploaded Files

```kotlin
// Sync all uploaded files in current folder/category
viewModel.syncUploadStatus()
```

**What happens:**
1. Gets all files with `isUploaded = true`
2. Checks each file on S3 in batch
3. Marks deleted files as not uploaded
4. Logs summary

**Logs:**
```
FolderViewModel: Syncing upload status for 15 files...
S3Repository: File exists on S3: photo1.jpg
S3Repository: File exists on S3: photo2.jpg
S3Repository: File not found on S3: photo3.jpg  ← Deleted!
S3Repository: File exists on S3: photo4.jpg
...
FolderViewModel: Sync complete: 3 files marked for re-upload
```

---

## Usage Examples

### Example 1: Manual Verification on Long-Press

Add to file options menu:

```kotlin
// In FolderScreen or MediaDialogs
TextButton(onClick = {
    viewModel.verifyUploadStatus(mediaFile)
    showDialog = false
}) {
    Icon(Icons.Default.Sync, "Verify Upload")
    Spacer(Modifier.width(8.dp))
    Text("Verify Upload Status")
}
```

### Example 2: Auto-Sync on Screen Launch

Add to FolderScreen or HomeScreen:

```kotlin
LaunchedEffect(Unit) {
    // Sync upload status when screen opens
    viewModel.syncUploadStatus()
}
```

### Example 3: Sync Button in Settings

```kotlin
// In SettingsScreen under S3 section
SettingItem(
    icon = Icons.Default.Sync,
    title = "Sync Upload Status",
    description = "Check which files are still on S3",
    onClick = {
        // Trigger sync for all categories
        homeViewModel.syncAllCategories()
    }
)
```

### Example 4: Periodic Background Sync

```kotlin
// In MainActivity or Application
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Schedule periodic sync every 24 hours
        // (Using WorkManager - not implemented here)
        scheduleUploadStatusSync()
    }
}
```

---

## How Database Updates Work

### Database Schema

```sql
-- Before: File uploaded
UPDATE media_files
SET isUploaded = 1, s3Url = 'https://s3.../photo.jpg'
WHERE id = 'abc123'

-- After: File deleted from S3 (detected)
UPDATE media_files
SET isUploaded = 0, s3Url = NULL
WHERE id = 'abc123'
```

### UI Updates Automatically

The app uses `Flow<List<MediaFile>>` so UI updates reactively:

1. `verifyUploadStatus()` calls `markAsNotUploaded()`
2. Database updates: `isUploaded = false`
3. Flow emits new list
4. UI recomposes automatically
5. Green cloud icon → Upload button ✨

---

## Technical Details

### S3 Verification API

Uses MinIO's `statObject()` method:

```kotlin
try {
    minioClient.statObject(
        StatObjectArgs.builder()
            .bucket(bucketName)
            .`object`("photos/photo.jpg")
            .build()
    )
    // File exists ✅
    return true
} catch (e: Exception) {
    // File not found (deleted) ❌
    return false
}
```

### Error Handling

- **Network error**: Fails gracefully, logs error
- **S3 config missing**: Returns failure result
- **Auth error**: Logs error, doesn't mark files
- **On any error**: Assumes file doesn't exist (safe approach)

---

## Performance Considerations

### Single File Check
- **Fast**: 1 API call (~100-300ms)
- **Use for**: User-triggered verification (long-press, button click)

### Batch Check (Multiple Files)
- **Slower**: N API calls (N = number of uploaded files)
- **Use for**: Background sync, settings page
- **Example**: 20 files × 200ms = 4 seconds

### Optimization Ideas (Future)
- [ ] Use S3 `listObjects()` to get all files at once
- [ ] Compare local database vs S3 listing
- [ ] Only check files uploaded > 7 days ago
- [ ] Cache verification results for 24 hours

---

## UI Integration Options

### Option A: Manual Button (Recommended for MVP)

Add "Verify Uploads" button in settings:

```
Settings > Cloud Storage > Verify Upload Status
```

**Pros:**
- User controls when to check
- No background network usage
- Simple to implement

**Cons:**
- User must remember to sync

---

### Option B: Auto-Sync on App Launch

Check upload status when app opens:

**Pros:**
- Always up-to-date
- No user action needed

**Cons:**
- Network usage on every launch
- Delays app startup if many files

---

### Option C: Smart Sync

Only sync files where:
- `isUploaded = true`
- `uploadedAt > 7 days ago` (add new field)
- Last verified > 24 hours ago (add new field)

**Pros:**
- Efficient
- Catches deleted files
- Minimal network usage

**Cons:**
- More complex
- Need to add timestamp fields

---

## Testing Checklist

### Manual Testing

1. **Upload a file**
   - [ ] File uploads successfully
   - [ ] Green cloud icon appears
   - [ ] `isUploaded = true` in database

2. **Delete file from S3** (using Backblaze/MinIO console)
   - [ ] File deleted from bucket
   - [ ] App still shows green icon (not yet synced)

3. **Verify single file**
   - [ ] Call `verifyUploadStatus(mediaFile)`
   - [ ] Log shows "File not found on S3"
   - [ ] Green cloud icon disappears
   - [ ] Upload button appears
   - [ ] Database: `isUploaded = false`, `s3Url = NULL`

4. **Sync all files**
   - [ ] Upload 5 files
   - [ ] Delete 2 from S3
   - [ ] Call `syncUploadStatus()`
   - [ ] Log shows "2 files marked for re-upload"
   - [ ] Only 3 files show green icon
   - [ ] 2 files show upload button again

5. **Re-upload deleted file**
   - [ ] Click upload button on previously deleted file
   - [ ] File uploads successfully
   - [ ] Green icon appears again

---

## Logs Reference

### Successful Verification (File Exists)
```
S3Repository: File exists on S3: photo.jpg
```

### File Deleted from S3
```
S3Repository: File not found on S3: photo.jpg
FolderViewModel: File deleted from S3, marked for re-upload: photo.jpg
```

### Batch Sync
```
FolderViewModel: Syncing upload status for 20 files...
S3Repository: File exists on S3: photo1.jpg
S3Repository: File exists on S3: photo2.jpg
S3Repository: File not found on S3: photo3.jpg
S3Repository: File exists on S3: photo4.jpg
...
FolderViewModel: Sync complete: 3 files marked for re-upload
```

### Errors
```
S3Repository: Error verifying file existence
FolderViewModel: Failed to verify upload status for photo.jpg
```

---

## Future Enhancements

### 1. Add Last Verified Timestamp
```kotlin
// In MediaFileEntity
val lastVerifiedAt: Long? = null
```

### 2. Bulk Operations
```kotlin
// Get S3 file listing once, compare to database
fun syncAllFiles() {
    val s3Files = listAllS3Objects()
    val localFiles = getAllUploadedFiles()
    val deleted = localFiles - s3Files
    markMultipleAsNotUploaded(deleted)
}
```

### 3. Verification Status in UI
```kotlin
// Show when last verified
if (mediaFile.isUploaded) {
    Text("Verified: ${formatDate(mediaFile.lastVerifiedAt)}")
}
```

### 4. Background Worker
```kotlin
// WorkManager periodic sync
class SyncUploadWorker : Worker() {
    override fun doWork(): Result {
        syncAllUploadedFiles()
        return Result.success()
    }
}
```

---

## API Reference

### S3Repository

```kotlin
// Check if single file exists
suspend fun verifyFileExists(mediaFile: MediaFile): Result<Boolean>

// Check multiple files
suspend fun verifyMultipleFiles(mediaFiles: List<MediaFile>): Map<String, Boolean>
```

### MediaRepository

```kotlin
// Mark file as not uploaded (remove green icon)
suspend fun markAsNotUploaded(mediaFileId: String)
```

### FolderViewModel

```kotlin
// Verify single file, update if deleted
fun verifyUploadStatus(mediaFile: MediaFile)

// Sync all uploaded files in current view
fun syncUploadStatus()
```

---

## Summary

✅ **Verify if files exist on S3**
✅ **Detect deleted files**
✅ **Mark for re-upload automatically**
✅ **Batch verification for multiple files**
✅ **Database stays in sync with S3**
✅ **User can re-upload deleted files**

The green cloud icon now reflects actual S3 status! 🎉

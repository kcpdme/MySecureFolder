# S3 Upload All Files Feature

## Overview

Added bulk upload functionality to the S3 Sync Status screen. Each category now has an **Upload All** button that uploads all non-uploaded files to S3 with visual progress feedback.

---

## Features

### Upload All Button
- **Icon**: CloudUpload (☁️⬆️) - Located next to the Sync icon
- **Color**: Secondary color (different from Sync icon)
- **Function**: Uploads ALL files in a category that aren't already uploaded
- **Progress**: Shows horizontal progress bar and upload count

### Visual Feedback
1. **Progress Counter**: "Uploading 5/20..."
2. **Linear Progress Bar**: Horizontal bar showing 0-100% progress
3. **Loading Spinner**: Shows while uploading (replaces upload button)

### Database Updates
- Each file is marked as uploaded (`isUploaded = true`, `s3Url = URL`)
- Database updates happen immediately after each successful upload
- Counts refresh automatically when upload completes

---

## Implementation

### Files Modified

**[S3SyncScreen.kt](app/src/main/java/com/kcpd/myfolder/ui/settings/S3SyncScreen.kt)**

#### 1. Updated SyncState (lines 39-51)

**Added fields:**
```kotlin
data class SyncState(
    val isUploading: Boolean = false,          // Upload in progress
    val uploadProgress: Float = 0f,            // 0.0 to 1.0
    val currentUploadIndex: Int = 0,           // Current file being uploaded
    val totalFilesToUpload: Int = 0,           // Total files to upload
    // ... existing fields
)
```

#### 2. Added uploadAllFiles() Method (lines 155-238)

**What it does:**
1. Gets all files in the category
2. Filters to only non-uploaded files
3. Uploads each file sequentially
4. Shows progress for each file
5. Marks files as uploaded in database
6. Updates UI state after completion

**Code flow:**
```kotlin
fun uploadAllFiles(category: FolderCategory) {
    // 1. Get files that aren't uploaded
    val filesToUpload = allFiles.filter { !it.isUploaded }

    // 2. Upload each file with progress
    filesToUpload.forEachIndexed { index, mediaFile ->
        val progress = (index + 1).toFloat() / filesToUpload.size

        // Update progress
        updateSyncState(category) {
            it.copy(
                currentUploadIndex = index + 1,
                uploadProgress = progress
            )
        }

        // Upload file
        val result = s3Repository.uploadFile(mediaFile)
        result.onSuccess { url ->
            mediaRepository.markAsUploaded(mediaFile.id, url)
        }
    }

    // 3. Refresh counts
    val uploadedCount = updatedFiles.count { it.isUploaded }
}
```

#### 3. Updated CategorySyncCard UI (lines 322-466)

**Changes:**
- Added `onUploadClick` parameter
- Two buttons side-by-side: Upload (☁️⬆️) and Sync (🔄)
- Progress bar below card when uploading
- Upload status in text: "Uploading 5/20..."

---

## User Experience

### UI Layout

```
┌──────────────────────────────────────┐
│ 📷 Photos                  ☁️⬆️  🔄 │  ← Upload & Sync buttons
│ 10 uploaded • 20 total               │
└──────────────────────────────────────┘
```

### During Upload

```
┌──────────────────────────────────────┐
│ 📷 Photos                   ⏳  🔄  │  ← Spinner replaces upload icon
│ Uploading 5/10...                    │  ← Shows current progress
│ ━━━━━━━━━━━━━━━━━━━━━               │  ← Progress bar (50%)
└──────────────────────────────────────┘
```

### After Upload

```
┌──────────────────────────────────────┐
│ 📷 Photos                  ☁️⬆️  🔄 │
│ 20 uploaded • 20 total               │  ← All uploaded!
└──────────────────────────────────────┘
```

---

## How It Works

### Step-by-Step Flow

1. **User clicks Upload All button** (☁️⬆️ icon)

2. **Filter files to upload**
   ```
   Total files: 20
   Already uploaded: 10
   Need to upload: 10
   ```

3. **Upload each file sequentially**
   ```
   File 1/10: photo1.jpg → Uploading... → Success ✓
   File 2/10: photo2.jpg → Uploading... → Success ✓
   File 3/10: photo3.jpg → Uploading... → Success ✓
   ...
   ```

4. **Update database for each success**
   ```sql
   UPDATE media_files
   SET isUploaded = 1, s3Url = 'https://s3.../photo1.jpg'
   WHERE id = 'abc123'
   ```

5. **UI updates in real-time**
   - Progress bar moves: 0% → 10% → 20% → ... → 100%
   - Counter updates: "Uploading 1/10..." → "Uploading 2/10..." → ...
   - Upload count increases: "10 uploaded" → "11 uploaded" → ...

6. **Completion**
   - Progress bar disappears
   - Upload button returns
   - Count shows: "20 uploaded • 20 total"

---

## Logs Reference

### Starting Upload
```
S3SyncViewModel: Uploading Photos: 10 files
```

### Each File Upload
```
S3SyncViewModel: Uploading photo1.jpg (1/10)
S3Repository: Decrypting file for upload: photo1.jpg
S3Repository: Decrypted to temp file: /cache/temp_photo1.jpg, size: 245678 bytes
S3Repository: File uploaded successfully: https://s3.../photos/photo1.jpg
S3Repository: Temp file deleted: true (/cache/temp_photo1.jpg)
S3SyncViewModel: Uploaded: photo1.jpg
```

### Upload Complete
```
S3SyncViewModel: Photos: Upload complete - 10/10 succeeded
```

### If No Files to Upload
```
S3SyncViewModel: Photos: All files already uploaded
```

---

## Button States

### Upload Button (☁️⬆️)

| State | Icon | Color | Enabled | Action |
|-------|------|-------|---------|--------|
| **Idle** | CloudUpload | Secondary | ✅ Yes | Click to upload all |
| **Uploading** | Spinner | Primary | ❌ Disabled | Shows progress |
| **Sync in progress** | CloudUpload | Secondary | ❌ Disabled | Wait for sync |
| **All uploaded** | CloudUpload | Secondary | ✅ Yes | Nothing to upload |

### Sync Button (🔄)

| State | Icon | Color | Enabled | Action |
|-------|------|-------|---------|--------|
| **Idle** | Sync | Primary | ✅ Yes | Click to verify |
| **Syncing** | Spinner | Primary | ❌ Disabled | Shows progress |
| **Upload in progress** | Sync | Primary | ❌ Disabled | Wait for upload |

---

## Progress Tracking

### Progress Bar

- **Type**: LinearProgressIndicator
- **Range**: 0.0 to 1.0 (0% to 100%)
- **Calculation**: `(currentIndex + 1) / totalFiles`
- **Update**: After each file upload starts
- **Color**: Primary color
- **Location**: Bottom of card, full width

### Progress Text

```
Format: "Uploading X/Y..."
Example: "Uploading 5/20..."

Where:
- X = currentUploadIndex (1-based)
- Y = totalFilesToUpload
```

---

## Error Handling

### Individual File Failures

- Failed files are logged but don't stop the upload
- Success count tracks only successful uploads
- Error logged: `"Failed to upload photo.jpg"`
- Upload continues to next file

### Complete Failure

```kotlin
catch (e: Exception) {
    updateSyncState(category) {
        it.copy(
            isUploading = false,
            error = e.message ?: "Upload failed"
        )
    }
}
```

Shows: `"Error: Network timeout"` (or other error message)

---

## Testing Checklist

### Manual Testing

1. **Basic Upload**
   - [ ] Have 5 photos not uploaded
   - [ ] Click upload button (☁️⬆️)
   - [ ] Progress bar appears and moves
   - [ ] Text shows "Uploading 1/5..." → "Uploading 5/5..."
   - [ ] All 5 files get green cloud icon
   - [ ] Count updates to "5 uploaded • 5 total"

2. **All Files Already Uploaded**
   - [ ] All files have green cloud
   - [ ] Click upload button
   - [ ] Upload completes instantly
   - [ ] Log shows "All files already uploaded"

3. **Upload During Sync**
   - [ ] Click sync button
   - [ ] Upload button is disabled
   - [ ] Wait for sync to complete
   - [ ] Upload button becomes enabled

4. **Sync During Upload**
   - [ ] Click upload button
   - [ ] Sync button is disabled
   - [ ] Wait for upload to complete
   - [ ] Sync button becomes enabled

5. **Large Batch Upload**
   - [ ] 20+ files to upload
   - [ ] Progress bar shows smooth progression
   - [ ] Counter updates for each file
   - [ ] All files marked as uploaded
   - [ ] Database has all `isUploaded = true`

6. **Network Error**
   - [ ] Disconnect network mid-upload
   - [ ] Error message appears
   - [ ] Some files uploaded, some not
   - [ ] Green icons only on successful uploads

---

## Performance Notes

### Sequential Upload

Files are uploaded **one at a time** (sequentially), not in parallel.

**Why sequential?**
- ✅ Easier progress tracking (1/10, 2/10, etc.)
- ✅ Prevents overwhelming network/server
- ✅ Better error handling per file
- ✅ More reliable uploads

**Speed:**
- ~2-5 seconds per file (depends on size)
- Example: 20 files × 3 seconds = ~1 minute total

### Future Optimization

Could add parallel upload (3-5 at a time):
```kotlin
// Upload 5 files at once
filesToUpload.chunked(5).forEach { batch ->
    batch.map { file ->
        async { uploadFile(file) }
    }.awaitAll()
}
```

---

## Database Impact

### Before Upload
```sql
SELECT * FROM media_files WHERE folderId = 'photos';
-- 20 rows: 10 with isUploaded=1, 10 with isUploaded=0
```

### After Upload All
```sql
SELECT * FROM media_files WHERE folderId = 'photos';
-- 20 rows: ALL with isUploaded=1
```

### Each Upload Updates
```sql
UPDATE media_files
SET isUploaded = 1,
    s3Url = 'https://s3.../bucket/photos/filename.jpg'
WHERE id = 'file-id-123'
```

---

## Summary

✅ **Upload All button** - CloudUpload icon on each category
✅ **Visual progress** - Horizontal progress bar (0-100%)
✅ **Upload counter** - "Uploading 5/20..." text
✅ **Database updates** - All successful uploads marked in DB
✅ **Real-time UI** - Counts update as files upload
✅ **Error handling** - Individual failures don't stop batch
✅ **Button states** - Upload and Sync buttons disabled appropriately

Users can now bulk upload all files in a category with one click! 🎉

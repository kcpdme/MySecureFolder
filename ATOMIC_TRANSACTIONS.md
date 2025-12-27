# Atomic Transaction Implementation

## Overview

All file operations (import, deletion, note creation) are now **atomic** using database transactions. This ensures the database and filesystem stay perfectly in sync with no ghost files or orphaned entries.

## What is Atomic?

An atomic operation is "all or nothing" - either the entire operation succeeds, or it's completely rolled back. This prevents partial failures that cause inconsistencies.

## Implementation Details

### 1. File Import (`addMediaFile`)

**Transaction Flow:**
```
1. Encrypt file to disk
2. Generate thumbnail (if applicable)
3. BEGIN TRANSACTION
4.   Insert database entry
5. COMMIT TRANSACTION
```

**Rollback on Failure:**
- If database insert fails → Delete encrypted file from disk
- Ensures no orphaned encrypted files

**Code Location:** [MediaRepository.kt:179-250](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt#L179-L250)

### 2. File Deletion (`deleteMediaFile`)

**Transaction Flow:**
```
1. Delete encrypted file from disk
2. Delete thumbnail (if exists)
3. BEGIN TRANSACTION
4.   Delete database entry
5. COMMIT TRANSACTION
```

**Error Handling:**
- Always removes database entry (even if file deletion fails)
- Prevents ghost entries where DB has record but file doesn't exist
- Comprehensive logging at each step

**Code Location:** [MediaRepository.kt:545-612](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt#L545-L612)

### 3. Note Creation (`saveNote`)

**Transaction Flow:**
```
1. Create temp file with content
2. Encrypt note to disk
3. BEGIN TRANSACTION
4.   Insert database entry
5. COMMIT TRANSACTION
6. Delete temp file (in finally block)
```

**Rollback on Failure:**
- If database insert fails → Delete encrypted note from disk
- Temp file always cleaned up (finally block)

**Code Location:** [MediaRepository.kt:252-320](app/src/main/java/com/kcpd/myfolder/data/repository/MediaRepository.kt#L252-L320)

## Benefits

### Before (Without Transactions)
❌ Import fails → Orphaned encrypted file on disk
❌ Deletion fails → Ghost entry in database
❌ App crashes → Inconsistent state
❌ Database cleared → Files remain as ghosts

### After (With Transactions)
✅ Import fails → Encrypted file automatically deleted
✅ Deletion always removes DB entry → No ghost entries
✅ Any failure → Automatic rollback to consistent state
✅ Database cleared → Cleanup function detects orphans

## Ghost File Detection

Even with transactions, ghost files can occur if:
1. App data is cleared (database wiped, files remain)
2. User manually deletes database file
3. App is force-killed mid-operation (rare)

**Solution:** `cleanupOrphanedFiles()` function detects and removes:
- Unencrypted originals in `/files/media/`
- Ghost encrypted files in `/files/secure_media/` with no DB entry

## Database Transaction Details

Using Room's `database.runInTransaction { }`:
- All operations inside the block are atomic
- SQLite automatically handles BEGIN/COMMIT/ROLLBACK
- Thread-safe (multiple operations don't interfere)
- Crash-safe (incomplete transactions are rolled back on restart)

## Logging

Each operation logs:
- File being processed
- Each step of the transaction
- Success/failure of each operation
- Rollback actions when failures occur

Example deletion log:
```
D/MediaRepository: Deleting media file: video.mp4
D/MediaRepository:   File path: /data/.../MEDIA_20251227_123759.mp4.enc
D/MediaRepository:   File exists: true
D/SecureFileManager: Securely deleting file: MEDIA_20251227_123759.mp4.enc (84123456 bytes)
D/SecureFileManager: Successfully deleted: MEDIA_20251227_123759.mp4.enc
D/MediaRepository:   File deletion result: true
D/MediaRepository:   Database entry removed (transaction committed)
```

## Testing Atomicity

To verify atomic behavior:

1. **Test Import Failure:**
   - Corrupt database during import
   - Verify encrypted file is deleted (rollback)

2. **Test Deletion:**
   - Delete a file
   - Check logs show both file and DB entry removed
   - Verify no ghost files remain

3. **Test Ghost Cleanup:**
   - Clear app data (Settings → Apps → MyFolder → Clear Data)
   - Restart app
   - Run "Clean Orphaned Files"
   - Verify all ghost files are detected and removed

## Future Enhancements

Consider adding:
- Periodic background cleanup (WorkManager)
- File integrity verification on startup
- Automatic recovery from interrupted operations
- Transaction retry logic for temporary failures

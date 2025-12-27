# S3 MinIO Upload Implementation

## Overview
This implementation adds a cloud upload feature to your MyFolder app with session caching for optimal performance and security.

## Architecture

### 1. S3SessionManager
**File:** `app/src/main/java/com/kcpd/myfolder/data/repository/S3SessionManager.kt`

**Purpose:** Manages cached MinIO client connections with lifecycle awareness

**Key Features:**
- Establishes MinIO client connection when vault unlocks
- Caches authenticated client in memory during app session
- Auto-clears session when app backgrounds or vault locks
- Re-establishes connection when app returns to foreground
- Connection health check (verifies bucket accessibility)
- StateFlow-based session state management

**Session States:**
```kotlin
sealed class SessionState {
    object Disconnected
    object Connecting
    data class Connected(endpoint: String, bucketName: String)
    data class Error(message: String)
}
```

**Lifecycle Flow:**
```
Vault Unlocks → establishSession() → Cache MinIO Client
App Backgrounds (onStop) → clearSession()
App Foregrounds (onStart) + Vault Unlocked → establishSession()
Vault Locks → clearSession()
```

### 2. S3Repository Enhancement
**File:** `app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt`

**Changes:**
- Injected `S3SessionManager` (using Dagger Lazy to avoid circular dependency)
- Modified `uploadFile()` to use cached session when available
- Fallback to new client creation if session unavailable

**Upload Logic:**
```kotlin
suspend fun uploadFile(mediaFile: MediaFile): Result<String> {
    val cachedClient = sessionManager.get().getClient()
    val cachedConfig = sessionManager.get().getConfig()

    if (cachedClient != null && cachedConfig != null) {
        // Use cached session (fast!)
        minioClient = cachedClient
    } else {
        // Fallback: create new client
        minioClient = MinioClient.builder()...
    }

    // Upload file
    minioClient.putObject(...)
}
```

### 3. UI Components

#### MediaThumbnail (Grid View)
**File:** `app/src/main/java/com/kcpd/myfolder/ui/folder/MediaThumbnail.kt`

**Added:**
- `onUploadClick` callback parameter
- Cloud icon badge overlay in top-right corner

**Upload States:**
- **Not Uploaded**: Blue circular badge with CloudUpload icon (clickable)
- **Uploading**: Black circular badge with progress spinner
- **Uploaded**: Green circular badge with CloudDone checkmark (non-clickable)
- **Hidden** in multi-select mode

**Visual Design:**
```
┌─────────────┐
│   [Photo]   │
│             │
│        🔵⬆│  ← Clickable badge (32dp)
└─────────────┘
```

#### FolderMediaListItem (List View)
**File:** `app/src/main/java/com/kcpd/myfolder/ui/folder/MediaListItem.kt`

**Added:**
- `onUploadClick` callback parameter
- Upload button on right side of list item

**Upload States:**
- **Not Uploaded**: IconButton with CloudUpload icon (clickable)
- **Uploading**: Circular progress indicator (24dp)
- **Uploaded**: CloudDone icon with green tint (non-clickable)
- **Replaced** by selection indicator in multi-select mode

**Visual Design:**
```
┌────────────────────────────────────┐
│ [📷] Photo.jpg              🔵⬆│  ← IconButton (40dp)
│      2.5 MB • Dec 27            │
└────────────────────────────────────┘
```

### 4. ViewModel Integration
**File:** `app/src/main/java/com/kcpd/myfolder/ui/folder/FolderViewModel.kt`

**Changes:**
- Injected `S3SessionManager`
- Enhanced `uploadFile()` with result handling and logging

**Upload Flow:**
```kotlin
fun uploadFile(mediaFile: MediaFile) {
    viewModelScope.launch {
        _uploadingFiles.value += mediaFile.id  // Start uploading

        val result = s3Repository.uploadFile(mediaFile)
        result.onSuccess { url ->
            mediaFile.copy(isUploaded = true, s3Url = url)
            mediaRepository.updateMediaFile(updatedFile)
        }

        _uploadingFiles.value -= mediaFile.id  // End uploading
    }
}
```

### 5. Screen Integration
**File:** `app/src/main/java/com/kcpd/myfolder/ui/folder/FolderScreen.kt`

**Changes:**
- Passed `onUploadClick` handler to MediaThumbnail (grid view)
- Passed `onUploadClick` handler to FolderMediaListItem (list view)

**Click Handler:**
```kotlin
onUploadClick = { viewModel.uploadFile(mediaFile) }
```

## User Flow

### Initial Setup
1. Navigate to **Settings** → **S3 Configuration**
2. Enter MinIO/S3 details:
   - Endpoint URL (e.g., `http://192.168.1.100:9000`)
   - Access Key
   - Secret Key
   - Bucket Name
   - Region (defaults to `us-east-1`)
3. Save configuration

### Upload Flow
1. **Unlock Vault** → S3SessionManager automatically establishes connection
2. **Navigate to Photos/Videos/Audio/Notes**
3. **View files** in grid or list mode
4. **See cloud icon** on each file:
   - Blue upload icon = Not uploaded
   - Spinner = Uploading
   - Green checkmark = Uploaded
5. **Tap cloud icon** → Upload starts immediately
6. **Watch progress** → Icon shows spinner
7. **See confirmation** → Green checkmark appears

### Session Management
- **App backgrounds** → Session cleared (security)
- **App foregrounds** → Session re-established (if vault unlocked)
- **Vault locks** → Session cleared immediately
- **Vault unlocks** → Session established automatically

## Security Features

✅ **Session tied to vault state** - Session only exists while vault is unlocked
✅ **Auto-clear on background** - No lingering credentials when app goes to background
✅ **Connection verification** - Bucket accessibility checked before caching
✅ **Graceful fallback** - Falls back to new client if session unavailable
✅ **Encrypted credentials** - S3 config stored in encrypted DataStore

## Performance Benefits

🚀 **Faster uploads** - Reuses cached MinIO client instead of creating new connection
💾 **Memory efficient** - Session only cached when vault is unlocked
📡 **Reduced network overhead** - Eliminates repeated authentication handshakes
⚡ **Instant start** - Session pre-established, ready when user clicks upload

## Code Changes Summary

### New Files
- `S3SessionManager.kt` - Session caching manager (~200 lines)

### Modified Files
1. `S3Repository.kt` - Uses cached session for uploads
2. `MediaThumbnail.kt` - Added cloud icon overlay with click handler
3. `MediaListItem.kt` - Added upload button with states
4. `FolderViewModel.kt` - Injected S3SessionManager
5. `FolderScreen.kt` - Wired upload handlers
6. `FolderScreenContent.kt` - Passed upload callbacks to UI components
7. `MediaViewerScreen.kt` - Fixed unrelated compilation error

### Dependency Injection
All components use Hilt for dependency injection:
- `S3SessionManager` is a `@Singleton`
- Injected into `S3Repository` using `dagger.Lazy` (avoids circular dependency)
- Injected into `FolderViewModel` directly

## Testing Checklist

- [ ] Configure S3/MinIO settings
- [ ] Unlock vault - verify session establishes (check logs)
- [ ] Navigate to Photos - see blue upload icons
- [ ] Tap upload icon - verify spinner appears
- [ ] Wait for upload - verify green checkmark appears
- [ ] Background app - verify session clears (check logs)
- [ ] Foreground app - verify session re-establishes
- [ ] Lock vault - verify session clears
- [ ] Try uploading without S3 config - verify error handling
- [ ] Try uploading with wrong credentials - verify error handling
- [ ] Upload large file - verify progress indicator works

## Logs to Monitor

Enable Android Logcat with these tags:
- `S3SessionManager` - Session lifecycle events
- `S3Repository` - Upload operations and caching
- `FolderViewModel` - Upload state management

Example log output:
```
S3SessionManager: S3 session established: http://192.168.1.100:9000/photos
S3Repository: Using cached S3 session
FolderViewModel: File uploaded successfully: IMG_1234.jpg -> http://...
S3SessionManager: App backgrounded, clearing S3 session
```

## Future Enhancements

- **Batch upload** - Upload multiple files with single progress indicator
- **Upload retry** - Automatic retry on network failure
- **Upload queue** - Queue uploads when offline, process when online
- **Upload progress** - Show percentage progress for large files
- **Toast notifications** - User-friendly success/error messages
- **Settings toggle** - Auto-upload on capture option

## Troubleshooting

### Upload fails immediately
- Check S3 configuration in Settings
- Verify MinIO/S3 endpoint is reachable
- Check credentials are correct
- Verify bucket exists and is accessible

### Session not establishing
- Check vault is unlocked
- Verify S3 is configured
- Check logs for connection errors

### Upload icon not appearing
- Check S3 is configured
- Verify vault is unlocked
- Check not in multi-select mode (upload icons hidden)

### Uploads slow
- Check network connection
- Verify MinIO/S3 server performance
- Consider file size (large files take longer)

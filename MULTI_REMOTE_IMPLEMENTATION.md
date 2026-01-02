# Multi-Remote Upload Feature - Implementation Guide

## Overview
This document describes the multi-remote upload feature implementation that allows users to configure and upload files to multiple cloud storage providers (S3, Google Drive) simultaneously.

## Architecture

### Core Components

#### 1. Data Models (`domain/model/`)
- **RemoteConfig.kt** - Sealed class for remote configurations (S3Remote, GoogleDriveRemote)
- **UploadStatus.kt** - Enum for upload states (QUEUED, IN_PROGRESS, SUCCESS, FAILED)
- **RemoteUploadResult.kt** - Upload result for a single remote
- **FileUploadState.kt** - Complete upload state for a file across all remotes

#### 2. Repository Layer (`data/repository/`)
- **RemoteConfigRepository.kt** - CRUD operations for remote configurations using DataStore
- **RemoteRepositoryFactory.kt** - Factory pattern for creating repository instances
  - Creates S3RepositoryInstance and GoogleDriveRepositoryInstance
  - Each remote gets its own repository instance
  - Cached for performance

#### 3. Use Cases (`domain/usecase/`)
- **MultiRemoteUploadCoordinator.kt** - Orchestrates parallel uploads to multiple remotes
  - Manages upload queue and concurrency (5 parallel threads via Semaphore)
  - Tracks per-file, per-remote status
  - Thread-safe state management with Mutex
  - Provides retry functionality

#### 4. UI Layer (`ui/settings/`, `ui/folder/`)
- **RemoteManagementScreen.kt** - List and manage configured remotes
- **AddEditRemoteScreen.kt** - Add/edit remote configurations with color picker
- **MultiRemoteUploadSheet.kt** - Bottom sheet showing upload progress with color-coded remotes

#### 5. Migration (`data/migration/`)
- **RemoteConfigMigration.kt** - Migrates from single remote to multi-remote configuration

## Key Features

### 1. Multiple Remote Configuration
- Users can add unlimited S3 and Google Drive remotes
- Each remote has:
  - Unique name
  - Color identifier (12 preset colors)
  - Active/inactive toggle
  - Type-specific configuration (endpoint, keys, etc.)

### 2. Parallel Upload
- Files upload to ALL active remotes simultaneously
- Concurrency controlled by Semaphore (5 parallel operations)
- Each file tracks individual progress per remote
- Example: 5 files × 3 remotes = up to 15 operations (limited to 5 concurrent)

### 3. Visual Progress Tracking
- Color-coded status indicators
- Per-remote progress display
- Retry failed uploads individually
- Summary statistics

### 4. Thread Safety
- Mutex-protected state updates
- Concurrent operations without race conditions
- Proper cleanup on completion

## Integration Steps

### Step 1: Add Navigation Routes

Update your Navigation setup to include new screens:

```kotlin
// In your NavHost
composable("remote_management") {
    RemoteManagementScreen(navController)
}

composable("add_remote") {
    AddEditRemoteScreen(navController)
}

composable("edit_remote/{remoteId}") { backStackEntry ->
    AddEditRemoteScreen(navController)
}
```

### Step 2: Update Settings Screen

Add a menu item in SettingsScreen to navigate to remote management:

```kotlin
SettingItem(
    icon = Icons.Default.Cloud,
    title = "Cloud Remotes",
    subtitle = "Configure multiple upload destinations",
    onClick = { navController.navigate("remote_management") }
)
```

### Step 3: Run Migration on App Start

In your main activity or application class:

```kotlin
@Inject
lateinit var remoteConfigMigration: RemoteConfigMigration

override fun onCreate() {
    super.onCreate()
    lifecycleScope.launch {
        remoteConfigMigration.migrate()
    }
}
```

### Step 4: Update FolderScreen Upload UI

Replace the old upload queue sheet with the new multi-remote sheet:

```kotlin
// In FolderScreen.kt
val uploadStates by viewModel.uploadStates.collectAsState()

if (uploadStates.isNotEmpty()) {
    MultiRemoteUploadSheet(
        uploadStates = uploadStates,
        onDismiss = { /* handle dismiss */ },
        onRetry = { fileId, remoteId ->
            viewModel.retryUpload(fileId, remoteId)
        },
        onClearCompleted = {
            viewModel.clearCompletedUploads()
        }
    )
}
```

### Step 5: Handle No Active Remotes

Show error when user tries to upload without active remotes:

```kotlin
// Before upload
if (!remoteConfigRepository.hasActiveRemotes()) {
    // Show dialog: "Please configure at least one remote in Settings"
    return
}
```

## Color System

12 predefined colors for visual identification:
- Blue (#2196F3)
- Green (#4CAF50)
- Orange (#FF9800)
- Purple (#9C27B0)
- Red (#F44336)
- Cyan (#00BCD4)
- Yellow (#FFEB3B)
- Pink (#E91E63)
- Indigo (#3F51B5)
- Teal (#009688)
- Brown (#795548)
- Blue Grey (#607D8B)

## State Management

### Upload States Flow

```
QUEUED → IN_PROGRESS → SUCCESS/FAILED
```

### Data Flow

```
User selects files
    ↓
FolderViewModel.uploadFiles()
    ↓
MultiRemoteUploadCoordinator.uploadFiles()
    ↓
For each file:
    For each active remote:
        Launch coroutine → Upload → Update state
    ↓
UI observes uploadStates StateFlow
    ↓
MultiRemoteUploadSheet displays progress
```

## Concurrency Model

```
Max 5 concurrent uploads (Semaphore)
├─ File1 → Remote1 [IN_PROGRESS]
├─ File1 → Remote2 [IN_PROGRESS]
├─ File2 → Remote1 [IN_PROGRESS]
├─ File2 → Remote2 [IN_PROGRESS]
├─ File3 → Remote1 [IN_PROGRESS]
└─ File3 → Remote2 [QUEUED] ← Waiting for slot
```

## Backward Compatibility

### Legacy Fields Deprecated
- `FolderViewModel.uploadingFiles` → Use `uploadStates`
- `FolderViewModel.uploadQueue` → Use `uploadStates`
- `FolderViewModel.uploadResults` → Use `uploadStates`
- `UploadResult` sealed class → Use `FileUploadState`

### Migration Path
1. Old S3 config automatically migrated to first S3 remote
2. Old Google Drive account migrated to first Drive remote
3. Legacy fields kept with `@Deprecated` annotations
4. Can be removed in future version after transition period

## Error Handling

### Upload Failures
- Each remote upload fails independently
- Partial success possible (e.g., 2/3 remotes succeed)
- Retry button available per failed remote
- Error messages displayed under each failed remote

### Edge Cases
- No active remotes → Show configuration prompt
- Network failure → Automatic retry with exponential backoff (in S3Repository)
- Auth expiry → Re-authentication flow
- Insufficient permissions → Clear error message

## Performance Considerations

### Memory
- Each upload uses chunked streaming (MinIO: 5MB chunks)
- Max 5 concurrent = ~25MB peak memory
- Acceptable for modern Android devices

### Battery
- Uploads run in viewModelScope (lifecycle-aware)
- Consider WorkManager for background uploads when app killed
- Add battery optimization detection

### Network
- Respect metered connection preferences
- Add WiFi-only option in settings
- Monitor data usage

## Testing Checklist

- [ ] Add single S3 remote
- [ ] Add single Google Drive remote
- [ ] Add multiple remotes of same type
- [ ] Add multiple remotes of different types
- [ ] Toggle remote active/inactive
- [ ] Upload single file to multiple remotes
- [ ] Upload multiple files to multiple remotes
- [ ] Retry failed upload to specific remote
- [ ] Delete remote
- [ ] Edit remote configuration
- [ ] Color picker selection
- [ ] Migration from old config
- [ ] Network failure handling
- [ ] Auth failure handling
- [ ] Progress display accuracy
- [ ] Clear completed uploads

## Future Enhancements

### Priority 1
- [ ] Upload priority (primary vs backup remotes)
- [ ] Bandwidth throttling per remote
- [ ] Remote health monitoring
- [ ] Export/import remote configurations

### Priority 2
- [ ] Download from fastest available remote
- [ ] Sync verification (check if file exists before upload)
- [ ] Automatic failover
- [ ] Cloud-to-cloud migration

### Priority 3
- [ ] Upload scheduling (off-peak hours)
- [ ] Selective sync (choose which remotes per folder)
- [ ] Remote storage quota display
- [ ] Upload history/logs

## Files Created

```
app/src/main/java/com/kcpd/myfolder/
├── domain/
│   ├── model/
│   │   ├── RemoteConfig.kt
│   │   ├── UploadStatus.kt
│   │   ├── RemoteUploadResult.kt
│   │   └── FileUploadState.kt
│   └── usecase/
│       └── MultiRemoteUploadCoordinator.kt
├── data/
│   ├── repository/
│   │   ├── RemoteConfigRepository.kt
│   │   └── RemoteRepositoryFactory.kt
│   └── migration/
│       └── RemoteConfigMigration.kt
└── ui/
    ├── settings/
    │   ├── RemoteManagementScreen.kt
    │   └── AddEditRemoteScreen.kt
    └── folder/
        └── MultiRemoteUploadSheet.kt
```

## Dependencies

All dependencies already present in build.gradle.kts:
- ✅ kotlinx-serialization-json (for RemoteConfig serialization)
- ✅ DataStore (for persistence)
- ✅ Hilt (for dependency injection)
- ✅ Coroutines (for async operations)
- ✅ Compose Material3 (for UI)
- ✅ MinIO SDK (for S3)
- ✅ Google Drive API (for Drive)

No new dependencies required!

## Summary

This implementation provides a robust, scalable multi-remote upload system with:
- ✅ Clean architecture (domain, data, UI layers)
- ✅ Thread-safe concurrent uploads
- ✅ Intuitive UI with visual indicators
- ✅ Backward compatibility
- ✅ Comprehensive error handling
- ✅ Zero new dependencies

The modular design makes it easy to add new remote types (Dropbox, OneDrive, etc.) in the future by implementing new RemoteConfig subclasses and repository instances.

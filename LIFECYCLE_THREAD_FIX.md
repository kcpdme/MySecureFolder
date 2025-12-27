# Lifecycle Thread Fix - S3 Repository

## Problem

The app crashed when syncing uploads with this error:

```
java.lang.IllegalStateException: Method addObserver must be called on the main thread
    at androidx.lifecycle.LifecycleRegistry.enforceMainThreadIfNeeded
    at androidx.lifecycle.LifecycleRegistry.addObserver
    at com.kcpd.myfolder.data.repository.S3SessionManager.<init>
```

### Root Cause

The issue occurred because:
1. `verifyFileExists()` and `uploadFile()` were running on IO dispatcher (`withContext(Dispatchers.IO)`)
2. They called `sessionManager.get()` on the IO thread
3. This triggered lazy initialization of `S3SessionManager`
4. `S3SessionManager` constructor registers a lifecycle observer
5. **Lifecycle observers MUST be registered on the main thread**

## Solution

Changed both methods to:
1. **First**: Get session manager reference on Main thread
2. **Then**: Do all S3/IO operations on IO thread

### Pattern

```kotlin
// ❌ BEFORE - Crashes
suspend fun verifyFileExists(mediaFile: MediaFile): Result<Boolean> = withContext(Dispatchers.IO) {
    val client = sessionManager.get().getClient()  // ← Crash! Called on IO thread
    // ... S3 operations
}

// ✅ AFTER - Works
suspend fun verifyFileExists(mediaFile: MediaFile): Result<Boolean> {
    // Get session manager on main thread first
    val cachedClient = withContext(Dispatchers.Main) {
        sessionManager.get().getClient()  // ← Safe! Called on main thread
    }

    // Now do S3 operations on IO thread
    return withContext(Dispatchers.IO) {
        // ... S3 operations
    }
}
```

## Files Fixed

**[S3Repository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt)**

### 1. Fixed `uploadFile()` (lines 67-138)

**Before:**
```kotlin
suspend fun uploadFile(mediaFile: MediaFile): Result<String> = withContext(Dispatchers.IO) {
    val cachedClient = sessionManager.get().getClient()  // ← On IO thread
    val cachedConfig = sessionManager.get().getConfig()  // ← On IO thread
    // ...
}
```

**After:**
```kotlin
suspend fun uploadFile(mediaFile: MediaFile): Result<String> {
    // Get session manager on main thread first
    val cachedClient = withContext(Dispatchers.Main) {
        sessionManager.get().getClient()
    }
    val cachedConfig = withContext(Dispatchers.Main) {
        sessionManager.get().getConfig()
    }

    // Now do all IO operations
    return withContext(Dispatchers.IO) {
        // Decrypt, upload, etc.
    }
}
```

### 2. Fixed `verifyFileExists()` (lines 144-198)

Same pattern as above.

### 3. `verifyMultipleFiles()` Works Correctly

This method calls `verifyFileExists()` which now handles threading correctly, so no changes needed.

## Why This Happens

### S3SessionManager Initialization

```kotlin
@Singleton
class S3SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    init {
        // This tries to register lifecycle observer
        val processLifecycle = ProcessLifecycleOwner.get()
        processLifecycle.lifecycle.addObserver(this)  // ← Must be on main thread!
    }
}
```

When `sessionManager.get()` is called from a background thread, it triggers Dagger to create the singleton instance, which runs the init block on the **current thread** (IO thread), causing the crash.

## Testing

### Before Fix
```
User clicks "Photos" sync → Crash immediately
Error: Method addObserver must be called on the main thread
```

### After Fix
```
User clicks "Photos" sync → Works!
S3SyncViewModel: Syncing Photos: 2 uploaded files
S3Repository: File exists on S3: photo1.jpg
S3Repository: File exists on S3: photo2.jpg
S3SyncViewModel: Photos: Verified 2, Deleted 0
```

## Thread Safety Notes

### Main Thread Operations
- ✅ Getting session manager reference
- ✅ Lifecycle observer registration
- ✅ UI updates (handled by ViewModel/StateFlow)

### IO Thread Operations
- ✅ S3 API calls (`statObject`, `putObject`)
- ✅ File encryption/decryption
- ✅ Network operations
- ✅ Database queries (Room handles threading)

### The Pattern

```kotlin
suspend fun s3Operation() {
    // 1. Main thread: Get lifecycle-dependent references
    val client = withContext(Dispatchers.Main) {
        sessionManager.get().getClient()
    }

    // 2. IO thread: Do heavy work
    return withContext(Dispatchers.IO) {
        // S3 operations, file operations, etc.
    }
}
```

## Impact

✅ **Upload Feature**: Now works without crashes
✅ **Sync Feature**: Now works without crashes
✅ **No Performance Impact**: Main thread calls are instant (just getting references)
✅ **Thread Safety**: Proper separation of concerns

## Summary

The fix ensures that:
1. Lifecycle observers are always registered on the main thread
2. Heavy IO operations run on background threads
3. No crashes when accessing S3SessionManager from coroutines

Done! 🎉

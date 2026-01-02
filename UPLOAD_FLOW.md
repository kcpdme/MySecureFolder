# Upload Flow - Sequential File Processing

## Overview

The multi-remote upload system processes files **sequentially** with **parallel remote uploads per file**.

## Upload Strategy

### ✅ Current Implementation

**Sequential File Processing:**
- Process ONE file at a time
- For each file, upload to ALL active remotes in parallel
- Wait for ALL remotes to complete (success or failure) before moving to next file

### Example Flow

**Scenario:** 5 files, 3 active remotes

```
Step 1: Process File 1 (vacation_1.jpg)
├─ Thread 1: Upload to 🟦 My S3         [IN_PROGRESS]
├─ Thread 2: Upload to 🟩 Google Drive  [IN_PROGRESS]
└─ Thread 3: Upload to 🟧 Backup S3     [IN_PROGRESS]
     │
     └─ WAIT for ALL 3 to complete
          │
          ├─ 🟦 My S3        ✓ Success (2.3s)
          ├─ 🟩 Google Drive ✓ Success (3.1s)
          └─ 🟧 Backup S3    ❌ Failed (1.2s - Auth error)

Step 2: Process File 2 (vacation_2.jpg)
├─ Thread 1: Upload to 🟦 My S3         [IN_PROGRESS]
├─ Thread 2: Upload to 🟩 Google Drive  [IN_PROGRESS]
└─ Thread 3: Upload to 🟧 Backup S3     [IN_PROGRESS]
     │
     └─ WAIT for ALL 3 to complete
          │
          ├─ 🟦 My S3        ✓ Success (2.1s)
          ├─ 🟩 Google Drive ✓ Success (2.8s)
          └─ 🟧 Backup S3    ✓ Success (2.5s)

Step 3: Process File 3 (vacation_3.jpg)
... and so on
```

## Thread Model

### Concurrency

**Per File:**
- 3 remotes = 3 parallel threads
- 5 remotes = 5 parallel threads
- 10 remotes = 10 parallel threads

**Across Files:**
- Files are processed sequentially (one at a time)
- No limit on number of parallel remote uploads per file
- Each file waits for all its remote uploads to complete

### Thread Safety

**Mutex Protection:**
```kotlin
stateMutex.withLock {
    _uploadStates.update { ... }
}
```

**Async/Await Pattern:**
```kotlin
// Launch parallel uploads for one file
val uploadJobs = activeRemotes.map { remote ->
    scope.async(Dispatchers.IO) {
        uploadToRemote(file, remote)
    }
}

// Wait for ALL to complete
uploadJobs.awaitAll()

// Then move to next file
```

## Code Implementation

### Upload Coordinator

```kotlin
suspend fun uploadFiles(files: List<MediaFile>, scope: CoroutineScope) {
    val activeRemotes = remoteConfigRepository.getActiveRemotes()

    // Process files ONE AT A TIME
    files.forEach { file ->
        initializeFileState(file, activeRemotes)

        // Upload this file to ALL remotes in parallel
        val uploadJobs = activeRemotes.map { remote ->
            scope.async(Dispatchers.IO) {
                uploadToRemote(file, remote)
            }
        }

        // WAIT for ALL remotes to complete
        uploadJobs.awaitAll()

        // Only then move to next file
    }
}
```

## Benefits of This Approach

### ✅ Advantages

1. **Predictable Resource Usage**
   - Max concurrent uploads = number of active remotes (not unlimited)
   - Example: 3 remotes = max 3 threads at any time

2. **Thread Safety**
   - One file at a time prevents race conditions
   - Simpler state management

3. **Network Efficiency**
   - All remotes upload the same file simultaneously
   - File is read from disk once, sent to all remotes
   - No need to re-read file for each remote

4. **Progress Clarity**
   - User sees clear progress: "File 2 of 5 uploading..."
   - Easy to understand what's happening

5. **Memory Efficient**
   - Only one file's data in memory at a time
   - Even with 10 remotes, same file data is shared

### ⚠️ Trade-offs

1. **Slower for Large Batches**
   - 100 files to 3 remotes: Each file waits for all 3 remotes
   - If one remote is slow, it blocks the next file

2. **Underutilized on Fast Networks**
   - If uploads are fast, there's downtime between files
   - Could theoretically upload more files simultaneously

## UI Experience

### Progress Display

**Sequential Processing Shows:**
```
Uploading Files
2 of 5 files completed

📄 vacation_1.jpg (2.4 MB)
  🟦 My S3        ✓ Uploaded
  🟩 Google Work  ✓ Uploaded
  🟧 Backup S3    ❌ Failed: Auth error

📄 vacation_2.jpg (3.1 MB)  ← Currently uploading
  🟦 My S3        ✓ Uploaded
  🟩 Google Work  ⏳ Uploading... 67%
  🟧 Backup S3    ⏳ Uploading... 34%

📄 vacation_3.jpg (1.8 MB)  ← Waiting
  🟦 My S3        📋 Queued
  🟩 Google Work  📋 Queued
  🟧 Backup S3    📋 Queued
```

### User Perspective

1. User selects 5 files
2. Clicks Upload
3. Bottom sheet appears
4. File 1 uploads to all remotes in parallel
5. When File 1 is done (all remotes complete), File 2 starts
6. And so on...

## Performance Characteristics

### Time Calculation

**Sequential Processing:**
```
Total Time = Sum of (slowest remote per file)

Example:
File 1: max(2.3s, 3.1s, 1.2s) = 3.1s
File 2: max(2.1s, 2.8s, 2.5s) = 2.8s
File 3: max(1.9s, 2.2s, 2.0s) = 2.2s

Total: 3.1 + 2.8 + 2.2 = 8.1 seconds
```

### Resource Usage

**Memory:**
- One file's encrypted data in memory
- Streamed to all remotes simultaneously
- Peak: ~File Size (not File Size × Remotes)

**Network:**
- Full bandwidth utilization per remote
- All remotes upload concurrently
- No artificial throttling

**Threads:**
- Active threads = Number of active remotes
- Max: Number of configured active remotes
- Min: 1 (if only 1 remote active)

## Error Handling

### Partial Failures

**Scenario:** File uploads successfully to 2/3 remotes

```
File 1:
├─ 🟦 My S3        ✓ Success
├─ 🟩 Google Work  ✓ Success
└─ 🟧 Backup S3    ❌ Failed

Result: File 1 considered PARTIALLY successful
Action: Move to File 2
User Can: Retry failed remote individually later
```

### All Failures

**Scenario:** File fails on all remotes

```
File 1:
├─ 🟦 My S3        ❌ Network error
├─ 🟩 Google Work  ❌ Auth failed
└─ 🟧 Backup S3    ❌ Bucket not found

Result: File 1 fully failed
Action: Still move to File 2 (don't block entire queue)
User Can: Fix configs and retry File 1 later
```

## Comparison with Alternative Approaches

### ❌ NOT Used: Full Parallel (All Files × All Remotes)

```
Rejected approach:
- Upload ALL files to ALL remotes simultaneously
- Example: 5 files × 3 remotes = 15 parallel operations
- Problem: Too many concurrent operations
- Risk: Memory pressure, network congestion
```

### ❌ NOT Used: Fully Sequential (One Remote at a Time)

```
Rejected approach:
- File 1 → Remote 1, then Remote 2, then Remote 3
- Then File 2 → Remote 1, then Remote 2, then Remote 3
- Problem: Very slow
- Time: 5 files × 3 remotes × 2s = 30 seconds total
```

### ✅ CHOSEN: Sequential Files, Parallel Remotes

```
Best of both worlds:
- File 1 → All remotes in parallel (wait)
- File 2 → All remotes in parallel (wait)
- Predictable resource usage
- Good performance
- Time: 5 files × max(remote times) ≈ 10-15 seconds
```

## Code Path

### 1. User Initiates Upload

```kotlin
// FolderViewModel.kt
fun uploadFiles(mediaFiles: List<MediaFile>) {
    viewModelScope.launch {
        multiRemoteUploadCoordinator.uploadFiles(mediaFiles, viewModelScope)
    }
}
```

### 2. Coordinator Processes Sequentially

```kotlin
// MultiRemoteUploadCoordinator.kt
suspend fun uploadFiles(files: List<MediaFile>, scope: CoroutineScope) {
    files.forEach { file ->  // SEQUENTIAL
        val uploadJobs = activeRemotes.map { remote ->
            scope.async { uploadToRemote(file, remote) }  // PARALLEL
        }
        uploadJobs.awaitAll()  // WAIT before next file
    }
}
```

### 3. UI Updates Reactively

```kotlin
// MultiRemoteUploadSheet.kt
val uploadStates by viewModel.uploadStates.collectAsState()

uploadStates.forEach { (fileId, state) ->
    FileUploadCard(state)  // Shows real-time progress
}
```

## Summary

**Upload Strategy:**
- ✅ Sequential file processing (one at a time)
- ✅ Parallel remote uploads (all remotes per file)
- ✅ Wait for all remotes before next file
- ✅ Thread-safe with Mutex
- ✅ Predictable resource usage
- ✅ Clean error handling

**Result:**
- Simple, predictable behavior
- Good performance for typical use cases
- Easy to understand and debug
- Safe resource management

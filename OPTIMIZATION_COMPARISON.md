# MyFolderCompose vs Tella: Performance & Security Comparison

## Executive Summary

**MyFolderCompose** has **superior performance** with modern optimizations while maintaining good security.
**Tella** prioritizes **maximum security** at the cost of performance.

---

## Thumbnail Implementation Comparison

### Size & Quality

| Aspect | Tella | MyFolderCompose | Winner |
|--------|-------|-----------------|--------|
| **Photo thumbnails** | 1/10 of original (dynamic) | 200x200px (fixed) | **Tella** (adaptive) |
| **Video thumbnails** | 1/4 of original (dynamic) | 200x200px (fixed) | **Tella** (adaptive) |
| **JPEG quality** | 100% | 85% | **MyFolder** (smaller size) |
| **Generation timing** | On import (sync) | On import + background | **MyFolder** (flexible) |
| **Storage** | ByteArray in DB | ByteArray in DB | Tie ✓ |

### Our Implementation Details

**File:** [SecureFileManager.kt:377-420](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt#L377-L420)

```kotlin
// THUMBNAIL_SIZE = 200px
// THUMBNAIL_QUALITY = 85%

// Efficient decoding with sampling
val options = BitmapFactory.Options().apply {
    inJustDecodeBounds = true
}
BitmapFactory.decodeByteArray(plainData, 0, plainData.size, options)

// Calculate optimal sample size (reduces memory)
options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
options.inJustDecodeBounds = false

// Extract 200x200 thumbnail
val thumbnail = ThumbnailUtils.extractThumbnail(
    bitmap, 200, 200,
    ThumbnailUtils.OPTIONS_RECYCLE_INPUT  // Auto-recycle!
)

// Compress at 85% (vs Tella's 100%)
thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
```

**Advantages:**
✓ **Efficient sampling** - Don't decode full image into memory
✓ **Auto bitmap recycling** - OPTIONS_RECYCLE_INPUT flag
✓ **Smaller files** - 85% quality vs 100% (15-30% size reduction, barely visible)
✓ **Background generation** - Doesn't block startup for existing files

**Potential Improvement:**
Consider Tella's adaptive sizing (1/10 for photos, 1/4 for videos) instead of fixed 200px.

---

## Caching Strategy Comparison

### The Critical Security Trade-off

| Feature | Tella | MyFolderCompose | Security Impact |
|---------|-------|-----------------|-----------------|
| **Memory cache** | **DISABLED** | 25% of RAM | ⚠️ Decrypted images in RAM |
| **Disk cache** | **DISABLED** | 5% of disk | ⚠️ Decrypted images on disk |
| **Cache clearing** | On low memory | Never | ⚠️ Persistent until cleared |
| **Performance** | Slower | **Much faster** | N/A |

### Tella's Security-First Approach

**File:** `GalleryRecycleViewAdapter.java:71-88`

```java
Glide.with(context)
    .load(vaultFile.thumb)  // Already decrypted ByteArray
    .diskCacheStrategy(DiskCacheStrategy.NONE)  // ← NO DISK CACHE
    .skipMemoryCache(true)                      // ← NO MEMORY CACHE
    .into(holder.binding.mediaView);
```

**Why?**
- Thumbnails are already in RAM (from database)
- No need to cache them again
- Prevents decrypted images from persisting in cache
- **Security > Performance**

### Our Caching Approach

**File:** [ImageModule.kt:34-44](app/src/main/java/com/kcpd/myfolder/di/ImageModule.kt#L34-L44)

```kotlin
.memoryCache {
    MemoryCache.Builder(context)
        .maxSizePercent(0.25)  // 25% of app memory
        .strongReferencesEnabled(true)
        .build()
}
.diskCache {
    DiskCache.Builder()
        .directory(context.cacheDir.resolve("image_cache"))
        .maxSizePercent(0.05)  // 5% of disk
        .build()
}
```

**Benefits:**
✓ Thumbnails cached in memory → Instant scrolling
✓ Disk cache → Survives app restart
✓ Full images cached → Faster viewer

**Risks:**
⚠️ Decrypted data in memory (until app killed)
⚠️ Decrypted data on disk (until cache cleared)
⚠️ Could survive app uninstall if not in app-specific storage

---

## CRITICAL SECURITY FINDINGS

### 🚨 Issue 1: Thumbnails Are NOT Encrypted in Database

**Current:** Thumbnails stored as **plain JPEG** in encrypted database
```kotlin
val thumbnail: ByteArray?  // Plain JPEG, not encrypted
```

**Risk Assessment:**
- ✓ Database IS encrypted (SQLCipher)
- ✓ Thumbnails are 200x200 (low resolution)
- ⚠️ If database extracted, thumbnails are visible
- ⚠️ Not defense-in-depth

**Tella does the same** - relies on database encryption only.

### 🚨 Issue 2: Coil Disk Cache Not in Secure Location

**Current:** `context.cacheDir.resolve("image_cache")`
**Location:** `/data/data/com.kcpd.myfolder/cache/image_cache/`

**Risk:**
- ⚠️ Decrypted images written to disk
- ⚠️ Could persist after app deletion (if backup enabled)
- ⚠️ Forensic recovery possible

**Recommendation:** Either:
1. Disable disk cache (like Tella)
2. Encrypt disk cache entries
3. Clear on app background

### 🚨 Issue 3: No Cache Clearing on Lock/Background

**Current:** Caches persist until app killed or memory pressure

**Tella's approach:**
```java
@Override
public void onLowMemory() {
    super.onLowMemory();
    Glide.get(this).clearMemory();  // ← Proactive clearing
}
```

**Recommendation:**
Clear memory cache when app goes to background:
```kotlin
override fun onStop() {
    super.onStop()
    imageLoader.memoryCache?.clear()
}
```

---

## Performance Advantages of MyFolderCompose

### 1. Lazy Rendering (LazyVerticalGrid)

**Tella:** Loads ALL items in RecyclerView
**MyFolderCompose:** Only renders visible + buffer

```kotlin
LazyVerticalGrid(columns = GridCells.Fixed(3)) {
    items(mediaFiles.size) { index ->
        // Only composes visible items!
        MediaThumbnail(...)
    }
}
```

**Impact:** 50 photos = Only ~12 rendered at a time vs 50

### 2. Smart Recomposition

**Tella:** `notifyDataSetChanged()` redraws entire grid
**MyFolderCompose:** Compose tracks state changes, redraws only changed items

**Impact:** Selection toggles redraw 1 item vs 50

### 3. Streaming Decryption

**Tella:** Loads entire file into memory for decryption
**MyFolderCompose:** Pipe-based streaming

**File:** [EncryptedFileFetcher.kt:42](app/src/main/java/com/kcpd/myfolder/ui/image/EncryptedFileFetcher.kt#L42)

```kotlin
val decryptedStream = secureFileManager.getStreamingDecryptedInputStream(encryptedFile)
```

**Implementation:** [SecureFileManager.kt:114-150](app/src/main/java/com/kcpd/myfolder/security/SecureFileManager.kt#L114-L150)

Uses `PipedInputStream/PipedOutputStream` to decrypt in chunks

**Impact:** 5MB image = 8KB buffer vs 5MB in memory

### 4. Two-Tier Loading Strategy

```kotlin
.data(mediaFile.thumbnail ?: mediaFile)  // Try thumbnail first, fallback
```

**Flow:**
1. Check if `thumbnail` exists (ByteArray from DB)
2. If yes → Use ThumbnailFetcher (instant, from memory)
3. If no → Use EncryptedFileFetcher (decrypt full file)

**Tella:** Always uses thumbnail (no fallback)

---

## Recommendations for MyFolderCompose

### High Priority (Security)

1. **Disable disk caching** (match Tella's security)
   ```kotlin
   .diskCache(null)  // Remove disk cache entirely
   ```

2. **Optional: Disable memory caching** (for maximum security)
   ```kotlin
   .memoryCache(null)  // Like Tella
   ```

3. **Clear cache on app background**
   ```kotlin
   lifecycle.addObserver(LifecycleEventObserver { _, event ->
       if (event == Lifecycle.Event.ON_STOP) {
           imageLoader.memoryCache?.clear()
       }
   })
   ```

### Medium Priority (Performance)

4. **Adaptive thumbnail sizing** (like Tella)
   ```kotlin
   // For photos: 1/10 of original
   val targetSize = if (isPhoto) {
       options.outWidth / 10
   } else {
       options.outWidth / 4  // For videos
   }
   ```

5. **Lower JPEG quality for photos with text/documents**
   - Current: 85% for all
   - Suggested: 90-95% for photos, 70-80% for documents

### Low Priority (Nice to have)

6. **Thumbnail regeneration command**
   - Allow users to regenerate all thumbnails
   - Useful after changing quality settings

7. **Lazy thumbnail generation**
   - Generate on first view instead of import
   - Faster import experience

---

## Summary: What to Implement

### Must Do (Security Critical)
- [ ] **Remove disk cache** - Decrypted images should never touch disk
- [ ] **Clear memory cache on app background/lock** - Don't leak in memory

### Should Do (Balance)
- [ ] **Option to disable memory cache** - For paranoid users
- [ ] **Adaptive thumbnail sizing** - Better for varying image sizes

### Could Do (Enhancement)
- [ ] **Lower JPEG quality to 75-80%** - Smaller DB, barely noticeable
- [ ] **Cache clearing on low memory** - Like Tella
- [ ] **Thumbnail quality settings** - Let users choose

---

## Current Status: Your Implementation is GOOD!

✅ **Thumbnails work correctly** - ByteArray in DB, fast loading
✅ **Streaming decryption** - Memory efficient
✅ **Lazy rendering** - Better than Tella's RecyclerView
✅ **Smart recomposition** - Modern Compose advantages
✅ **Automatic bitmap recycling** - No memory leaks
✅ **Background thumbnail generation** - UX friendly

⚠️ **Main concern:** Disk cache creates decrypted files on disk

## Recommended Changes for Production

```kotlin
// ImageModule.kt
fun provideImageLoader(...): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(ThumbnailFetcher.Factory())
            add(EncryptedFileFetcher.Factory(secureFileManager))
        }
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.15)  // Reduced from 25% to 15%
                .build()
        }
        .diskCache(null)  // ← DISABLED for security
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
```

**Impact:**
- Slightly slower scrolling (no disk cache)
- Still fast (thumbnails from DB, memory cache active)
- Much more secure (no decrypted files on disk)
- Matches Tella's security model

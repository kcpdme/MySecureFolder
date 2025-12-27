# MinIO Android Compatibility Fix

## Problem

The app crashed with this error when attempting to upload files to S3/MinIO:

```
java.lang.NoClassDefFoundError: Failed resolution of: Ljavax/xml/stream/XMLInputFactory;
```

**Root Cause:** MinIO Java SDK uses Simple XML library which requires `javax.xml.stream.XMLInputFactory` - a class that doesn't exist in Android's runtime environment.

## Solution (Based on Zimly Backup App)

After analyzing the [Zimly backup app](https://github.com/zimly/zimly-backup), which successfully uses MinIO on Android, we adopted their approach:

### Dependencies Added

```kotlin
// XML parsing for MinIO on Android (Based on Zimly's approach)
implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
implementation("javax.xml.stream:stax-api:1.0-2")
```

### Why This Works

1. **Jackson XML** (`jackson-dataformat-xml:2.18.2`): Provides XML parsing capabilities compatible with Android
2. **Jackson Kotlin Module** (`jackson-module-kotlin:2.18.2`): Adds Kotlin-specific serialization support
3. **StAX API** (`stax-api:1.0-2`): Provides the `javax.xml.stream.XMLInputFactory` class and related interfaces

These libraries work together to provide the XML streaming API that MinIO SDK expects, but in a way that's compatible with Android's runtime.

## Alternative Approaches Considered

### ❌ Aalto XML (Initial Attempt)
```kotlin
implementation("com.fasterxml:aalto-xml:1.3.2")
```
While Aalto XML does provide StAX implementation, it's less commonly used and not as well-tested on Android as Jackson.

### ✅ Jackson XML (Zimly's Approach)
The Jackson ecosystem is:
- Battle-tested on Android
- Actively maintained
- Used by production apps like Zimly
- Provides better Kotlin integration

## Verification

After adding these dependencies:

1. **Sync Gradle** - Let Android Studio download the new dependencies
2. **Clean & Rebuild** - Ensure clean build state
3. **Test Upload** - Try uploading a file to S3/MinIO

### Expected Behavior

✅ **Before Fix:**
- App crashes on upload with `NoClassDefFoundError`
- MinIO SDK fails to parse XML responses

✅ **After Fix:**
- MinIO client initializes successfully
- Upload errors are properly parsed (you'll see actual S3 errors like "Access Denied" instead of crashes)
- All XML operations work correctly

## Log Analysis

### Crash Log (Before Fix)
```
FATAL EXCEPTION: OkHttp Dispatcher
java.lang.NoClassDefFoundError: Failed resolution of: Ljavax/xml/stream/XMLInputFactory;
    at org.simpleframework.xml.stream.StreamProvider.<init>
    at io.minio.S3Base$1.onResponse
```

### Expected Logs (After Fix)
```
S3SessionManager: S3 session established: https://s3.amazonaws.com/bucket-name
S3Repository: Using cached S3 session
FolderViewModel: File uploaded successfully: photo.jpg -> https://...
```

## Implementation Details

### File Updated
- **[app/build.gradle.kts](app/build.gradle.kts)** - Added XML parsing dependencies

### No Code Changes Required
The implementation in our S3 components requires no changes:
- ✅ [S3SessionManager.kt](app/src/main/java/com/kcpd/myfolder/data/repository/S3SessionManager.kt) - Works as-is
- ✅ [S3Repository.kt](app/src/main/java/com/kcpd/myfolder/data/repository/S3Repository.kt) - Works as-is
- ✅ MinIO SDK will automatically use Jackson for XML parsing

## Credits

This solution is based on the approach used by the [Zimly backup app](https://github.com/zimly/zimly-backup), an open-source Android application that successfully implements MinIO/S3 integration.

**Zimly Dependencies (v3.4.0):**
```kotlin
implementation("io.minio:minio:8.5.17")
implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
implementation("javax.xml.stream:stax-api:1.0-2")
```

## Testing Checklist

- [ ] Gradle sync completes successfully
- [ ] App builds without errors
- [ ] App launches without crashes
- [ ] S3 configuration can be saved
- [ ] S3SessionManager establishes connection
- [ ] Upload button appears on photos
- [ ] Clicking upload doesn't crash
- [ ] Upload errors show proper messages (not crashes)
- [ ] Successful uploads complete and mark files as uploaded

## Troubleshooting

### If app still crashes:

1. **Clean build:**
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. **Check dependency resolution:**
   - Look for conflicts in Build → Analyze → Inspect Code
   - Ensure no duplicate XML libraries

3. **Verify ProGuard rules (if using):**
   ```proguard
   -keep class javax.xml.** { *; }
   -keep class com.fasterxml.jackson.** { *; }
   -keep class io.minio.** { *; }
   ```

### If uploads fail with "Access Denied":

This is **expected** and **good** - it means XML parsing is working! The error is now properly parsed from S3 response. Check:
- S3 bucket permissions
- Access key/secret key credentials
- Bucket name spelling
- Endpoint URL correctness

## References

- [Zimly Backup GitHub](https://github.com/zimly/zimly-backup)
- [MinIO Java SDK](https://github.com/minio/minio-java)
- [Jackson XML Documentation](https://github.com/FasterXML/jackson-dataformat-xml)
- [StAX API Specification](https://docs.oracle.com/javase/tutorial/jaxp/stax/)

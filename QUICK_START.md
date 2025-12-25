# Quick Start Guide

## Building and Running the App

### Option 1: Android Studio (Recommended)

1. **Open the project**
   ```bash
   # Open Android Studio
   # File → Open → Select MyFolderCompose directory
   ```

2. **Sync Gradle**
   - Android Studio will automatically prompt to sync
   - Or click "Sync Now" in the notification bar
   - Or File → Sync Project with Gradle Files

3. **Run the app**
   - Click the green "Run" button (▶️)
   - Select your device/emulator
   - Wait for build and installation

### Option 2: Command Line

```bash
# Navigate to project directory
cd MyFolderCompose

# Build the project (if you have Gradle wrapper)
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or build and install in one command
./gradlew installDebug
```

If `gradlew` is not available, you can generate it:
```bash
gradle wrapper --gradle-version 8.2
```

## Testing the App Features

### 1. Camera Capture

**Take a Photo:**
1. Launch app
2. Tap the (+) FAB button
3. Ensure "Photo" mode is selected (camera icon highlighted)
4. Tap the white capture button
5. Photo appears in gallery

**Record Video:**
1. Tap (+) FAB
2. Tap the video camera icon
3. Tap red record button to start
4. Tap stop button to finish
5. Video appears in gallery

**Record Audio:**
1. Tap (+) FAB
2. Tap the microphone icon
3. Tap red record button to start
4. Watch the timer
5. Tap stop button to finish
6. Audio file appears in gallery

### 2. Configure S3/Minio

**Local Minio (Easiest for Testing):**

1. **Start Minio locally:**
   ```bash
   docker run -p 9000:9000 -p 9001:9001 \
     --name minio \
     -e "MINIO_ROOT_USER=minioadmin" \
     -e "MINIO_ROOT_PASSWORD=minioadmin" \
     minio/minio server /data --console-address ":9001"
   ```

2. **Create bucket:**
   - Open http://localhost:9001
   - Login: minioadmin / minioadmin
   - Create bucket named "test-bucket"
   - Make it public (optional, for easier testing)

3. **Configure in app:**
   - Tap settings icon (⚙️) in gallery
   - Enter configuration:
     - Endpoint: `http://10.0.2.2:9000` (emulator) or `http://192.168.x.x:9000` (device)
     - Bucket: `test-bucket`
     - Access Key: `minioadmin`
     - Secret Key: `minioadmin`
     - Region: `us-east-1`
   - Tap "Save Configuration"

**AWS S3:**
1. Create S3 bucket in AWS Console
2. Create IAM user with S3 PutObject permission
3. Generate access keys
4. Configure in app with AWS credentials

### 3. Upload Files to S3

1. Tap any file in gallery
2. File detail dialog appears
3. Tap "Upload" button
4. Confirm upload
5. Wait for "Upload successful" toast
6. Cloud icon (☁️) appears on thumbnail

**Verify upload:**
- Check Minio web UI (http://localhost:9001)
- Or check AWS S3 console
- Files are uploaded to: `bucket/photo/filename.jpg` or `bucket/video/filename.mp4`

### 4. Share Files

1. Tap any file in gallery
2. Tap "Share" button
3. Choose app to share with (Gmail, WhatsApp, etc.)
4. File is shared using Android's native sharing

### 5. Delete Files

1. Tap any file in gallery
2. Tap "Delete" button (red)
3. File is permanently deleted from device

## Troubleshooting

### Camera not working
- **Emulator**: Camera support is limited, use physical device
- **Permissions**: Grant camera and microphone permissions when prompted
- **Error**: Check Logcat for CameraX errors

### Upload fails
- **Endpoint**: Make sure endpoint is accessible from device
  - Emulator: Use `10.0.2.2` instead of `localhost`
  - Device: Use local network IP (e.g., `192.168.1.100`)
- **Credentials**: Verify access key and secret key are correct
- **Bucket**: Ensure bucket exists and is accessible
- **Network**: Check INTERNET permission is granted

### Files not appearing
- **Storage**: Files are in app internal storage, not device gallery
- **Refresh**: Navigate away and back to gallery
- **Check**: Look in `/data/data/com.kcpd.myfolder/files/media/` (requires root)

### Build errors
- **Gradle sync**: Sync Gradle files first
- **Dependencies**: Check internet connection for dependency download
- **JDK**: Ensure JDK 17 is installed
- **SDK**: Install Android SDK 34 via SDK Manager

## Development Tips

### View App Files (Rooted Device/Emulator)
```bash
# Connect to device
adb shell

# Navigate to app directory
cd /data/data/com.kcpd.myfolder/files/media

# List files
ls -la
```

### View DataStore (S3 Config)
```bash
adb shell
cd /data/data/com.kcpd.myfolder/files/datastore
cat s3_config.preferences_pb
```

### Clear App Data
```bash
# Clear all app data (resets S3 config and deletes all files)
adb shell pm clear com.kcpd.myfolder
```

### View Logs
```bash
# Filter by app package
adb logcat | grep "com.kcpd.myfolder"

# Filter by tag
adb logcat -s "S3Repository"
```

### Check Permissions
```bash
# List all permissions
adb shell dumpsys package com.kcpd.myfolder | grep permission
```

## File Locations

### Source Code
- Kotlin files: `app/src/main/java/com/kcpd/myfolder/`
- Resources: `app/src/main/res/`
- Manifest: `app/src/main/AndroidManifest.xml`
- Gradle: `app/build.gradle.kts`

### On Device
- Media files: `/data/data/com.kcpd.myfolder/files/media/`
- DataStore: `/data/data/com.kcpd.myfolder/files/datastore/s3_config.preferences_pb`

## Next Steps

1. **Test all features** on a physical device
2. **Configure real S3** for production use
3. **Customize UI** - Change colors in `ui/theme/Color.kt`
4. **Add features** - See IMPLEMENTATION_SUMMARY.md for ideas
5. **Deploy** - Generate signed APK for distribution

## Common Commands

```bash
# Build debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build
./gradlew clean

# List all tasks
./gradlew tasks
```

## Getting Help

- Check [README.md](README.md) for detailed documentation
- Check [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) for architecture details
- Review source code comments
- Check Android Studio Logcat for errors
- Verify all dependencies are downloaded

## Key Shortcuts (Android Studio)

- **Run app**: Ctrl/Cmd + R
- **Build project**: Ctrl/Cmd + F9
- **Sync Gradle**: Ctrl/Cmd + Shift + O
- **Find file**: Ctrl/Cmd + Shift + N
- **Search code**: Ctrl/Cmd + Shift + F
- **Logcat**: Alt/Opt + 6

Enjoy using MyFolder! 📁📸🎥🎵☁️

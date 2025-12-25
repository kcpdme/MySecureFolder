# Build Checklist - After Upgrade

## ☑️ Pre-Build Setup

- [ ] JDK 21 installed on your system
- [ ] Android Studio Ladybug (2024.2.1) or later installed
- [ ] Internet connection active (for dependency downloads)
- [ ] At least 2GB free disk space

## ☑️ First Build Steps

### 1. Gradle Sync
- [ ] Open project in Android Studio
- [ ] Click "Sync Project with Gradle Files" 🐘
- [ ] Wait for sync to complete (3-5 minutes first time)
- [ ] Verify no sync errors

### 2. JDK Configuration
- [ ] File → Settings → Build, Execution, Deployment → Build Tools → Gradle
- [ ] Verify "Gradle JDK" is set to **JDK 21**
- [ ] If not, download and select JDK 21

### 3. Clean Build
- [ ] Build → Clean Project
- [ ] Wait for clean to complete
- [ ] Build → Rebuild Project
- [ ] Wait for build (2-4 minutes first time)
- [ ] Verify: "BUILD SUCCESSFUL"

## ☑️ Testing Checklist

### Camera Features
- [ ] Launch app
- [ ] Tap (+) FAB button
- [ ] **Photo mode** - Take a photo
  - [ ] Photo appears in gallery
  - [ ] Thumbnail displays correctly
- [ ] **Video mode** - Record a video
  - [ ] Video records successfully
  - [ ] Duration counter works
  - [ ] Video appears in gallery
  - [ ] Video thumbnail displays
- [ ] **Audio mode** - Record audio
  - [ ] Audio records successfully
  - [ ] Duration counter works
  - [ ] Audio appears in gallery with music icon

### Gallery Features
- [ ] Gallery shows all captured files
- [ ] Grid layout displays correctly
- [ ] Images show proper thumbnails
- [ ] Videos show thumbnails
- [ ] Audio shows music note icon
- [ ] Tap a file to open detail dialog
- [ ] Detail dialog shows file info correctly

### S3/Minio Integration
- [ ] Tap settings icon (⚙️) in gallery
- [ ] S3 config screen opens
- [ ] Enter test configuration
- [ ] Save configuration successfully
- [ ] Return to gallery
- [ ] Tap a file → Upload button
- [ ] Upload completes successfully
- [ ] Cloud icon (☁️) appears on thumbnail
- [ ] Toast shows "Upload successful"

### Sharing Feature
- [ ] Tap any file in gallery
- [ ] Tap "Share" button
- [ ] Android share sheet appears
- [ ] Can share to any app
- [ ] File transfers correctly

### Navigation & UI
- [ ] All screen transitions smooth
- [ ] Back button works correctly
- [ ] No UI glitches or crashes
- [ ] Material 3 theme applied correctly
- [ ] Icons display properly

### Permissions
- [ ] App requests camera permission
- [ ] App requests microphone permission
- [ ] Permissions can be granted
- [ ] App works after permission grant
- [ ] Graceful handling if denied

## ☑️ Performance Checks

### Build Performance
- [ ] Clean build completes in < 4 minutes
- [ ] Incremental build completes in < 60 seconds
- [ ] No excessive memory warnings during build

### App Performance
- [ ] App launches in < 3 seconds
- [ ] Camera preview is smooth
- [ ] Gallery scrolling is smooth
- [ ] No frame drops in UI
- [ ] File operations are fast

## ☑️ Error Checks

### Android Studio
- [ ] No red errors in code editor
- [ ] No unresolved references
- [ ] No deprecation warnings (or very few)
- [ ] Logcat shows no crashes

### Specific Library Checks
- [ ] Coil 2.7.0 - Images load correctly
- [ ] CameraX 1.4.1 - Camera works smoothly
- [ ] Minio 8.5.14 - Uploads work
- [ ] Hilt 2.52 - DI works (no "cannot find symbol" errors)

## ☑️ Code Quality

### Warnings Check
- [ ] Build → Analyze → Inspect Code
- [ ] Review any new warnings
- [ ] Fix critical warnings if any

### Lint Check
- [ ] Build → Analyze → Run Inspection by Name → "Lint"
- [ ] Review lint warnings
- [ ] No critical lint errors

## ☑️ Release Build

### Generate Signed Build
- [ ] Configure signing config (if not done)
- [ ] Build → Generate Signed Bundle/APK
- [ ] Select "Android App Bundle"
- [ ] Choose release variant
- [ ] Build completes successfully
- [ ] AAB file generated in `app/release/`

### Test Release Build
- [ ] Install release build on device
- [ ] App launches successfully
- [ ] All features work in release mode
- [ ] No ProGuard issues

## ☑️ Documentation Review

- [ ] Read [UPGRADE_SUMMARY.md](UPGRADE_SUMMARY.md)
- [ ] Read [WHATS_NEXT.md](WHATS_NEXT.md)
- [ ] Understand what changed
- [ ] Know where to find help

## ☑️ Version Control

### Git Commit
- [ ] Review all changed files
- [ ] Commit with descriptive message:
  ```
  Upgrade to latest stable versions (Dec 2025)

  - Kotlin 2.1.0
  - Android 15 (API 35)
  - Java 21 LTS
  - Migrate KAPT → KSP
  - Update 25+ dependencies
  - Coil 2.7.0, CameraX 1.4.1, etc.
  ```

### Create Tag
- [ ] Tag the upgrade commit:
  ```bash
  git tag -a v1.0.0-upgraded -m "Fully upgraded to latest stable versions"
  git push origin v1.0.0-upgraded
  ```

## 🎯 Success Criteria

### ✅ All systems go if:
- ✅ Build successful
- ✅ App launches without crashes
- ✅ Camera captures photos/videos/audio
- ✅ Gallery displays all media correctly
- ✅ Images and videos load
- ✅ S3 upload works
- ✅ File sharing works
- ✅ No critical errors in Logcat

### ⚠️ Needs attention if:
- ⚠️ Images not loading → Check file paths and Logcat
- ⚠️ Build errors → Check JDK 21 installation
- ⚠️ Hilt errors → Clean and rebuild
- ⚠️ Slow builds → Check Gradle configuration

### ⛔ Stop and investigate if:
- ⛔ App crashes on launch
- ⛔ Cannot complete Gradle sync
- ⛔ Build fails with errors
- ⛔ Major features broken

## 📞 Troubleshooting Quick Reference

| Problem | Quick Fix |
|---------|-----------|
| Gradle sync fails | Check internet, invalidate caches |
| Build errors about JDK | Install/select JDK 21 |
| Hilt "cannot find symbol" | Clean project, rebuild |
| Images not loading | Check file paths, Logcat |
| Slow builds | Enable Gradle caching |
| App crashes | Check Logcat for stack trace |

## 📊 Expected Metrics

### Build Times (on average machine)
- First clean build: **2-4 minutes** ✅
- Incremental build: **30-60 seconds** ✅
- Gradle sync: **1-2 minutes** ✅

### App Performance
- Launch time: **< 3 seconds** ✅
- Camera startup: **< 1 second** ✅
- Gallery load: **< 500ms** ✅

---

## 🎉 When Complete

### You'll have:
- ✅ Modern Android app with latest tools
- ✅ 5+ years of stability
- ✅ Faster build times (KSP)
- ✅ Play Store ready
- ✅ Production-ready codebase

**Time to celebrate!** 🎊

Then move on to: [WHATS_NEXT.md](WHATS_NEXT.md) for deployment and future planning.

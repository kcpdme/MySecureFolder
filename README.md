# MyFolder - Secure Media Vault 🔐

> **A privacy-first Android application for securely storing photos, videos, audio recordings, notes, and documents with military-grade encryption.**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-blue.svg)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/Min%20SDK-26-orange.svg)](https://developer.android.com/about/versions/oreo)

---

## 📱 Overview

MyFolder is a **secure personal vault** that keeps your sensitive media files protected with enterprise-grade encryption. All files are encrypted at rest using **AES-256-GCM** with envelope encryption, and the encrypted database ensures your metadata remains private.

### Key Features

| Feature | Description |
|---------|-------------|
| 🔒 **Vault Security** | Password-protected vault with auto-lock and biometric unlock |
| 🔐 **AES-256-GCM Encryption** | Military-grade envelope encryption for all stored files |
| 📸 **Media Capture** | Built-in camera for photos/videos and audio recorder |
| 📄 **Document Scanner** | ML Kit-powered document scanning with edge detection |
| ☁️ **Multi-Cloud Sync** | Backup to S3/MinIO, Google Drive, or WebDAV (Nextcloud, etc.) |
| 📁 **Organization** | Folders, categories (Photos, Videos, Recordings, Notes, PDFs) |
| 🌙 **Modern UI** | Material 3 with dynamic theming and Compose animations |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                         │
│   Jetpack Compose UI • Material 3 • ViewModels with Hilt DI    │
├─────────────────────────────────────────────────────────────────┤
│                        DOMAIN LAYER                             │
│              Use Cases • Domain Models • Interfaces             │
├─────────────────────────────────────────────────────────────────┤
│                         DATA LAYER                              │
│   Repositories • Room DAO • DataStore Preferences • Cloud APIs │
├─────────────────────────────────────────────────────────────────┤
│                       SECURITY LAYER                            │
│  VaultManager • SecureFileManager • PasswordManager • Biometric │
├─────────────────────────────────────────────────────────────────┤
│                      STORAGE LAYER                              │
│    SQLCipher (Encrypted DB) • Encrypted Files • KeyStore       │
└─────────────────────────────────────────────────────────────────┘
```

### Security Model

The app implements a **Hybrid Security Model with Envelope Encryption**:

1. **Master Key** - Derived from user password using Argon2id (memory-hard KDF)
2. **File Encryption Keys (FEK)** - Random AES-256 key generated per-file
3. **Envelope Encryption** - FEK is wrapped (encrypted) with Master Key and stored in file header
4. **Streaming Decryption** - Files decrypt on-the-fly without loading into memory

```
┌──────────────────────────────────────────────────┐
│               ENCRYPTED FILE FORMAT              │
├──────────────────────────────────────────────────┤
│  Magic Bytes (4B)                                │
│  Version (1B)                                    │
│  Wrapped FEK (60B) = IV(12) + EncryptedKey(32) + Tag(16) │
│  Encrypted Metadata (variable) = Original filename, MIME │
│  Encrypted Body = IV + ChaCha20/AES-GCM ciphertext       │
└──────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.0 with Coroutines & Flow |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt (with KSP) |
| **Database** | Room + SQLCipher (encrypted) |
| **Preferences** | DataStore + EncryptedSharedPreferences |
| **Camera** | CameraX |
| **Video Playback** | Media3 ExoPlayer |
| **Image Loading** | Coil |
| **Cloud Storage** | MinIO SDK (S3), Google Drive API, Sardine (WebDAV) |
| **ML Kit** | Document Scanner |
| **Password Hashing** | Argon2kt (Argon2id) |
| **Seed Phrase** | BIP39 for recovery |
| **Encryption** | AES-256-GCM + Android Keystore |

---

## 📂 Project Structure

```
app/src/main/java/com/kcpd/myfolder/
├── MainActivity.kt              # Single-activity entry point
├── MyFolderApplication.kt       # Hilt application + lifecycle setup
├── data/
│   ├── database/                # Room entities, DAOs, migrations
│   ├── model/                   # Domain models (MediaFile, FolderCategory)
│   └── repository/              # Data access layer
│       ├── MediaRepository.kt   # File operations with encryption
│       ├── FolderRepository.kt  # Folder management
│       ├── S3Repository.kt      # MinIO/S3 cloud sync
│       ├── GoogleDriveRepository.kt
│       └── RemoteRepositoryFactory.kt  # Multi-provider abstraction
├── di/                          # Hilt modules
├── domain/
│   ├── model/                   # Business models
│   └── usecase/                 # Business logic
├── security/
│   ├── VaultManager.kt          # Vault lock/unlock, session management
│   ├── SecureFileManager.kt     # Envelope encryption, streaming decrypt
│   ├── PasswordManager.kt       # Argon2id hashing, master key derivation
│   ├── BiometricManager.kt      # Fingerprint/Face unlock
│   ├── SecurityManager.kt       # Keystore operations
│   └── EncryptedFileProvider.kt # ContentProvider for streaming access
└── ui/
    ├── home/                    # Home screen with category cards
    ├── folder/                  # File browser with grid/list views
    ├── camera/                  # Photo/video capture
    ├── audio/                   # Audio recording
    ├── scanner/                 # Document scanner
    ├── viewer/                  # Media viewers (photo, video, audio, PDF)
    ├── settings/                # App settings, cloud configuration
    ├── auth/                    # Password setup, unlock screens
    └── theme/                   # Material 3 theming
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2.1+)
- JDK 21
- Android SDK 35

### Build

```bash
# Clone the repository
git clone <repository-url>
cd MyFolderCompose

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

### Google Drive Setup (Optional)

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the **Google Drive API**
3. Create OAuth 2.0 credentials (Android app)
4. Download `google-services.json` and place in `app/`
5. Add your SHA-1 fingerprint to the OAuth client

---

## 🔧 Configuration

### Cloud Storage Providers

The app supports multiple cloud storage backends:

| Provider | Protocol | Compatible Services |
|----------|----------|---------------------|
| **S3** | S3 API | AWS S3, MinIO, Backblaze B2, Wasabi, DigitalOcean Spaces |
| **Google Drive** | OAuth 2.0 | Google Drive |
| **WebDAV** | WebDAV/HTTP | Nextcloud, ownCloud, Koofr, Icedrive |

Configure in: **Settings → Cloud Backup → Add Remote**

---

## ✨ Future Improvements & Suggestions

### 🔴 High Priority

| Feature | Description | Benefit |
|---------|-------------|---------|
| **Plausible Deniability** | Implement hidden vault with decoy password | Protection against coercion |
| **Secure Delete Verification** | Add toggle for multi-pass overwrite (DoD 5220.22-M) | More thorough secure deletion |
| **Zero-Knowledge Cloud Sync** | Encrypt file names before upload (currently encrypted locally only) | True end-to-end privacy |
| **Auto-Backup Scheduling** | Background WorkManager sync with battery optimization | Automated cloud backup |

### 🟡 Medium Priority

| Feature | Description | Benefit |
|---------|-------------|---------|
| **Secure Share** | Time-limited encrypted links for sharing files | Share without exposing vault |
| **Trash/Recycle Bin** | Soft delete with 30-day recovery | Prevent accidental data loss |
| **Multiple Vaults** | Support multiple independent vaults with different passwords | Separation of data types |
| **Export/Import Vault** | Full encrypted backup to file for migration | Device transfer capability |
| **Push Notifications** | Notify on sync completion/failures | Better user awareness |
| **Wear OS Companion** | Quick lock/unlock from smartwatch | Convenience |

### 🟢 Enhancements

| Feature | Description | Benefit |
|---------|-------------|---------|
| **Tags & Labels** | Custom tags for better organization | Flexible categorization |
| **Advanced Search** | Search by date range, size, EXIF metadata | Find files faster |
| **Batch Import** | Import entire gallery folders at once | Faster onboarding |
| **OCR for Notes/PDFs** | Extract text for full-text search | Searchable documents |
| **Widget** | Quick capture widget on home screen | Faster access |
| **Dark Mode Scheduling** | Auto dark mode based on time/sunset | Battery and eye comfort |

### 🛡️ Security Hardening

| Feature | Description | Status |
|---------|-------------|--------|
| **Root/Jailbreak Detection** | Warn or prevent use on rooted devices | Not implemented |
| **Screenshot Prevention** | FLAG_SECURE on sensitive screens | Partial |
| **Clipboard Clear** | Auto-clear password from clipboard | Not implemented |
| **Panic Button** | Quick wipe or lock on shake/pattern | Not implemented |
| **Audit Log** | Track vault access history | Not implemented |
| **Tamper Detection** | Detect app modification/repackaging | Not implemented |

### 🐛 Technical Debt

| Item | Description | Priority |
|------|-------------|----------|
| **Unit Tests** | Add comprehensive unit test coverage | High |
| **UI Tests** | Compose UI testing with semantics | Medium |
| **CI/CD Pipeline** | GitHub Actions for automated builds/tests | Medium |
| **ProGuard Optimization** | Review and optimize ProGuard rules | Low |
| **Memory Profiling** | Optimize large file handling | Medium |
| **Accessibility** | Full TalkBack and screen reader support | Medium |

---

## 🔒 Security Considerations

### What's Protected

- ✅ All files encrypted with AES-256-GCM
- ✅ Database encrypted with SQLCipher
- ✅ Master key derived with Argon2id (memory-hard)
- ✅ File encryption keys wrapped with envelope encryption
- ✅ Secure file deletion with overwrite
- ✅ Auto-lock on background/timeout
- ✅ Biometric unlock option

### Current Limitations

- ⚠️ Cloud filenames are randomized UUIDs (content encrypted) but bucket listing reveals file count
- ⚠️ Thumbnails stored in database (encrypted) but generated from decrypted source
- ⚠️ No protection against keyloggers or screen capture malware
- ⚠️ Recovery phrase backup recommended (single point of failure otherwise)

---

## 📄 License

[Add your license here]

---

## 🙏 Acknowledgments

- [Tella](https://github.com/Horizontal-org/Tella-Android) - Inspiration for secure file handling patterns
- [SQLCipher](https://www.zetetic.net/sqlcipher/) - Database encryption
- [Argon2kt](https://github.com/nicktehrani/argon2kt) - Password hashing
- [MinIO](https://min.io/) - S3-compatible storage

---

<p align="center">
  <b>Built with ❤️ for privacy</b>
</p>

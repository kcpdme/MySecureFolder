# MyFolder - Private Media Storage with S3/Minio Sync

A simplified, privacy-focused media storage app inspired by Tella Android, built with modern Android technologies including Jetpack Compose.

## Features

### Core Functionality
- **Camera Capture** - Take photos using CameraX
- **Video Recording** - Record videos with CameraX
- **Audio Recording** - Record audio using MediaRecorder
- **Private Gallery** - View all captured media in a private gallery (files stored in app's internal storage)
- **S3/Minio Sync** - One-click upload to S3-compatible storage (AWS S3, Minio, DigitalOcean Spaces, etc.)
- **File Sharing** - Share files with other apps using Android's share functionality

## Technology Stack

### Core Technologies
- **Jetpack Compose** - Modern declarative UI
- **Kotlin** - 100% Kotlin codebase
- **Hilt** - Dependency injection
- **Coroutines & Flow** - Asynchronous operations and reactive data streams

### Key Libraries (Latest Stable - Dec 2025)
- **CameraX** (1.4.1) - Camera and video recording
- **Coil** (2.7.0) - Image loading with video thumbnail support
- **Media3 ExoPlayer** (1.5.0) - Video playback
- **Minio SDK** (8.5.14) - S3-compatible storage uploads
- **Navigation Compose** (2.8.5) - Navigation
- **DataStore** (1.1.1) - Persistent storage for S3 configuration
- **Accompanist Permissions** (0.36.0) - Runtime permissions handling
- **Kotlin** (2.1.0) - Latest stable Kotlin version
- **Compose BOM** (2024.12.01) - Latest Compose components

## Project Structure

```
app/src/main/java/com/kcpd/myfolder/
├── data/
│   ├── model/
│   │   ├── MediaFile.kt          # Data model for media files
│   │   └── S3Config.kt           # S3 configuration model
│   └── repository/
│       ├── MediaRepository.kt     # Media file management
│       └── S3Repository.kt        # S3 upload operations
├── ui/
│   ├── camera/
│   │   ├── CameraScreen.kt       # Camera/Video/Audio capture UI
│   │   └── CameraViewModel.kt    # Camera screen business logic
│   ├── gallery/
│   │   ├── GalleryScreen.kt      # Gallery view with grid layout
│   │   └── GalleryViewModel.kt   # Gallery business logic
│   ├── settings/
│   │   ├── S3ConfigScreen.kt     # S3 configuration UI
│   │   └── S3ConfigViewModel.kt  # S3 settings business logic
│   ├── navigation/
│   │   └── MyFolderNavHost.kt    # Navigation setup
│   └── theme/
│       ├── Color.kt              # Color definitions
│       ├── Theme.kt              # Material 3 theme
│       └── Type.kt               # Typography
├── MainActivity.kt               # Main activity
└── MyFolderApp.kt               # Application class with Hilt

## Architecture

The app follows a clean architecture pattern:

- **UI Layer**: Jetpack Compose screens with ViewModels
- **Domain Layer**: Repository pattern for data access
- **Data Layer**: File system operations and S3 uploads

### Key Components

#### MediaRepository
- Manages media files in app's internal storage
- Provides StateFlow of media files for reactive UI updates
- Handles file creation, deletion, and updates
- Automatically scans media directory on initialization

#### S3Repository
- Manages S3/Minio configuration using DataStore
- Uploads files to S3-compatible storage
- Supports multiple S3 providers (AWS, Minio, DigitalOcean Spaces, etc.)
- Returns Result type for success/failure handling

#### CameraScreen
- Unified screen for photo, video, and audio capture
- Three modes: PHOTO, VIDEO, AUDIO
- Real-time mode switching
- Recording duration display for video/audio

#### GalleryScreen
- Grid layout showing all media files
- Different thumbnails for photos, videos, and audio
- Upload status indicator (cloud icon)
- File detail dialog with upload, share, and delete actions

## Setup Instructions

### Prerequisites
- Android Studio Ladybug or later (2024.2.1+)
- JDK 21 (LTS - Long Term Support until 2029)
- Android SDK with minimum API 26 (Android 8.0)
- Gradle 8.12 (included in wrapper)

### Building the Project

1. Clone or open the project in Android Studio
2. Sync Gradle files
3. Build and run on an emulator or physical device

```bash
# Build the project
./gradlew build

# Install on connected device
./gradlew installDebug
```

### S3/Minio Configuration

To use the upload feature, configure S3/Minio settings:

1. Open the app
2. Tap the settings icon in the gallery
3. Enter your S3 configuration:
   - **Endpoint URL**: Your S3 endpoint (e.g., `https://s3.amazonaws.com` or `http://localhost:9000`)
   - **Bucket Name**: Your S3 bucket name
   - **Access Key**: Your S3 access key
   - **Secret Key**: Your S3 secret key
   - **Region**: AWS region (default: `us-east-1`)

#### Example Configurations

**Minio (Local Development)**
- Endpoint: `http://10.0.2.2:9000` (for Android emulator)
- Bucket: `my-bucket`
- Access Key: Your Minio access key
- Secret Key: Your Minio secret key

**AWS S3**
- Endpoint: `https://s3.amazonaws.com`
- Bucket: Your bucket name
- Access Key: Your AWS access key
- Secret Key: Your AWS secret key
- Region: Your AWS region (e.g., `us-east-1`)

**DigitalOcean Spaces**
- Endpoint: `https://nyc3.digitaloceanspaces.com`
- Bucket: Your space name
- Access Key: Your Spaces access key
- Secret Key: Your Spaces secret key

## Permissions

The app requires the following permissions:

- `CAMERA` - For capturing photos and videos
- `RECORD_AUDIO` - For recording audio and video sound
- `INTERNET` - For uploading to S3/Minio

All permissions are requested at runtime when needed.

## Privacy & Security

- **Private Storage**: All media files are stored in the app's internal storage, not accessible from the device gallery
- **No Analytics**: No tracking or analytics libraries
- **Local First**: All data stays on device until explicitly uploaded
- **Secure Configuration**: S3 credentials stored using Android DataStore

## Differences from Tella

This is a simplified version focusing on core features:

**Included Features:**
- Photo, video, and audio capture
- Private file storage
- S3/Minio sync
- File sharing

**Not Included (from Tella):**
- File encryption
- App camouflage/disguise mode
- Quick delete functionality
- Metadata capture/verification
- Server integration for organizational data collection
- Pattern lock/authentication
- PDF support
- Forms/XForms functionality

## Development

### Adding New Features

The modular architecture makes it easy to add features:

1. Add data models in `data/model/`
2. Create repository in `data/repository/`
3. Build UI screens in `ui/[feature]/`
4. Create ViewModels with Hilt injection
5. Add routes to `MyFolderNavHost.kt`

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## License

This project is open source and available for modification and distribution.

## Acknowledgments

- Inspired by [Tella Android](https://github.com/Horizontal-org/Tella-Android-FOSS)
- Built with Android Jetpack Compose
- Uses Minio SDK for S3 compatibility

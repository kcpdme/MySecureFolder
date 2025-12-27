plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.kcpd.myfolder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kcpd.myfolder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Don't exclude MANIFEST.MF - MinIO SDK needs it to read version info
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // Compose BOM for version alignment (Latest stable)
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android (Latest stable)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation (Latest stable)
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // CameraX (Latest stable)
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // Accompanist for permissions (Latest)
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Coil for image loading (Latest stable 2.x)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // ExifInterface for reading/writing image orientation metadata
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ExoPlayer for video playback (Latest Media3)
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")

    // Minio S3 SDK (Latest)
    implementation("io.minio:minio:8.5.14")

    // XML parsing for MinIO on Android (Based on Zimly's approach)
    // These provide the javax.xml.stream classes needed by MinIO SDK
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("javax.xml.stream:stax-api:1.0-2")
    // Explicitly add Woodstox for StAX implementation on Android
    implementation("com.fasterxml.woodstox:woodstox-core:6.6.2")
    implementation("org.codehaus.woodstox:stax2-api:4.2.2")

    // OkHttp (Latest stable)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines (Latest stable)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Kotlinx Serialization (Latest stable)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt for dependency injection (Latest - using KSP instead of kapt)
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // DataStore for preferences (Latest)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room for encrypted database (Latest stable)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // SQLCipher for database encryption (Latest stable)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // AndroidX Security for encrypted preferences (Latest stable)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric authentication (Latest)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Google Play Services base (required for ML Kit)
    implementation("com.google.android.gms:play-services-base:18.5.0")

    // ML Kit Document Scanner (Latest)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // Testing (Latest)
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.11.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

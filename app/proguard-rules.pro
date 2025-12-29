# MyFolder - ProGuard Rules for Security Libraries
# These rules ensure critical security components are not stripped or obfuscated in release builds

# ===================================
# SQLCipher - Database Encryption
# ===================================
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ===================================
# Minio SDK - S3 Compatible Storage
# ===================================
-keep class io.minio.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn io.minio.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ===================================
# XML Parsing (MinIO / Jackson / Woodstox)
# ===================================
-keep class javax.xml.stream.** { *; }
-keep interface javax.xml.stream.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class com.ctc.wstx.** { *; }
# Explicitly keep the factories referenced by reflection/system property
-keep class com.ctc.wstx.stax.WstxInputFactory { *; }
-keep class com.ctc.wstx.stax.WstxOutputFactory { *; }
-keep class com.ctc.wstx.stax.WstxEventFactory { *; }

-dontwarn com.ctc.wstx.**
-dontwarn org.codehaus.stax2.**
-dontwarn javax.xml.stream.**

# Keep SimpleXML (used by MinIO)
-keep class org.simpleframework.xml.** { *; }
-keep class org.simpleframework.xml.core.** { *; }
-keep class org.simpleframework.xml.stream.** { *; }
-dontwarn org.simpleframework.xml.stream.**

# ===================================
# AndroidX Security Crypto
# ===================================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ===================================
# App Security Classes
# ===================================
# Keep all encryption and security related classes
-keep class com.kcpd.myfolder.security.** { *; }

# Prevent obfuscation of encryption keys and algorithms
-keepclassmembers class com.kcpd.myfolder.security.SecurityManager {
    *** encrypt(...);
    *** decrypt(...);
    *** getDatabaseKey();
}

-keepclassmembers class com.kcpd.myfolder.security.SecureFileManager {
    *** encryptFile(...);
    *** decryptFile(...);
    *** getStreamingDecryptedInputStream(...);
    *** getStreamingEncryptionOutputStream(...);
    *** secureDelete(...);
}

# ===================================
# Database Entities (Room)
# ===================================
# Keep entity classes for SQLCipher
-keep @androidx.room.Entity class * {
    *;
}

# Keep DAO interfaces
-keep @androidx.room.Dao class * {
    *;
}

# Keep database classes
-keep @androidx.room.Database class * {
    *;
}

# ===================================
# Hilt / Dagger (Dependency Injection)
# ===================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

# ===================================
# Kotlin Coroutines
# ===================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===================================
# Jetpack Compose
# ===================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===================================
# Coil Image Loading
# ===================================
-keep class coil.** { *; }
-keep class com.kcpd.myfolder.ui.image.** { *; }

# ===================================
# Media & Camera
# ===================================
-keep class androidx.camera.** { *; }
-keep class androidx.media3.** { *; }

# ===================================
# Serialization
# ===================================
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===================================
# Prevent Optimization Issues
# ===================================
# Don't optimize away important security checks
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep generic signature info for reflection
-keepattributes Signature

# ===================================
# Android KeyStore
# ===================================
-keep class android.security.keystore.** { *; }
-dontwarn android.security.keystore.**

# ===================================
# Reflection Used Classes
# ===================================
# Keep classes that use reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ===================================
# Data Classes
# ===================================
# Keep data classes used for database and API
-keep class com.kcpd.myfolder.data.model.** { *; }
-keep class com.kcpd.myfolder.data.database.entity.** { *; }

# ===================================
# Parcelable
# ===================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===================================
# Native Methods
# ===================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===================================
# Enums
# ===================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===================================
# SECURITY: Strip ALL Log statements in Release
# This prevents sensitive data from being leaked via logcat
# ===================================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

# Also strip Timber logs if used
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# Strip kotlin println/print
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void println(...);
    public static void print(...);
}

# Strip System.out/err
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

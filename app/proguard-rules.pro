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

# Explicitly keep the Woodstox factory classes and their constructors
# These are instantiated via reflection by javax.xml.stream.XMLInputFactory.newInstance()
-keep class com.ctc.wstx.stax.WstxInputFactory {
    public <init>();
    <methods>;
}
-keep class com.ctc.wstx.stax.WstxOutputFactory {
    public <init>();
    <methods>;
}
-keep class com.ctc.wstx.stax.WstxEventFactory {
    public <init>();
    <methods>;
}

# Keep service loader configuration files
-keep class META-INF.services.** { *; }
-keepnames class META-INF.services.**

# Keep the factory finder implementation
-keep class javax.xml.stream.FactoryFinder {
    *;
}

# Keep service provider lookup mechanism
-keepclassmembers class * implements javax.xml.stream.XMLInputFactory {
    public <init>();
}
-keepclassmembers class * implements javax.xml.stream.XMLOutputFactory {
    public <init>();
}
-keepclassmembers class * implements javax.xml.stream.XMLEventFactory {
    public <init>();
}

-dontwarn com.ctc.wstx.**
-dontwarn org.codehaus.stax2.**
-dontwarn javax.xml.stream.**

# Keep SimpleXML (used by MinIO for XML parsing)
# CRITICAL: NodeBuilder uses XMLInputFactory in its static initializer
-keep class org.simpleframework.xml.** { *; }
-keep class org.simpleframework.xml.core.** { *; }
-keep class org.simpleframework.xml.stream.** { *; }

# Keep NodeBuilder and its static initialization - this is the class that fails on async threads
-keep class org.simpleframework.xml.stream.NodeBuilder {
    <clinit>;
    *;
}
-keep class org.simpleframework.xml.stream.StreamProvider {
    public <init>();
    *;
}
-keep class org.simpleframework.xml.stream.ProviderFactory {
    *;
}
-keep class org.simpleframework.xml.core.Persister {
    public <init>();
    *;
}
-dontwarn org.simpleframework.xml.**

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
# Google Drive / Google API Client
# (These libraries use reflection; keep to prevent release-only failures)
# ===================================
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.services.drive.**

-keep class com.google.auth.** { *; }
-dontwarn com.google.auth.**

-keep class com.google.http.client.** { *; }
-dontwarn com.google.http.client.**

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

# ===================================
# R8: Missing optional JDK/Android classes
# These are referenced by optional code paths in dependencies (e.g., Jackson/Apache HTTP)
# and are safe to ignore on Android.
# ===================================
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

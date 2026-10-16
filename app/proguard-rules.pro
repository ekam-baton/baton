# ProGuard / R8 rules for BATON (Production Hardened)

# ─── General Attributes & Stack Traces ───────────────────────────────────────
-keepattributes SourceFile,LineNumberTable,Signature,Exceptions,*Annotation*,InnerClasses
-renamesourcefileattribute SourceFile

# ─── kotlinx.serialization ───────────────────────────────────────────────────
# Keep serializer metadata annotations and companion objects
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable-annotated classes and their generated serializers
-keep,includedescriptorclasses class com.ekam.baton.**$$serializer { *; }
-keepclassmembers class com.ekam.baton.** {
    *** Companion;
}
-keepclasseswithmembers class com.ekam.baton.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── Room Database & SQLCipher ───────────────────────────────────────────────
# Keep all Room @Entity, @Dao, @Database classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# SQLCipher native bindings and reflection
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# ─── Koin Dependency Injection ───────────────────────────────────────────────
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-keepclasseswithmembers class * {
    @org.koin.core.annotation.* <methods>;
}
-keep class * extends androidx.lifecycle.ViewModel { *; }
-dontwarn org.koin.**

# ─── Kotlin Coroutines & Flow ────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ─── Network & HTTP (OkHttp, Retrofit, Ktor) ──────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ─── Native JNI & Rust Cryptography ──────────────────────────────────────────
# JNI native methods (Rust crypto bindings in core:network)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Baton Crypto JNI bridge & classes
-keep class com.ekam.baton.core.network.security.ConnectionSecurityManager { *; }
-keep class com.ekam.baton.core.network.security.CryptoHelper { *; }
-keep class com.ekam.baton.core.network.security.** { *; }

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Matrix Olm SDK JNI bindings
-keep class org.matrix.android.olm.** { *; }
-dontwarn org.matrix.android.olm.**

# ─── WebRTC Voice & Signaling ────────────────────────────────────────────────
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.getstream.webrtc.** { *; }
-dontwarn io.getstream.webrtc.**

# ─── Optical QR Scanner (CameraX & MLKit / ZXing) ────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.android.gms.vision.**

-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.barcodescanner.**

# ─── WorkManager & Background Tasks ──────────────────────────────────────────
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ─── Paging 3 ────────────────────────────────────────────────────────────────
-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# ─── Production Security: Strip System Logs ──────────────────────────────────
# Prevents sensitive debug strings or chat metadata from leaking to logcat
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

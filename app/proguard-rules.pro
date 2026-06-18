# ProGuard / R8 rules for BATON

# ─── kotlinx.serialization ────────────────────────────────────────────────────
# Keep the serializer metadata annotations and companion objects
-keepattributes *Annotation*, InnerClasses
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

# ─── Room ─────────────────────────────────────────────────────────────────────
# Keep all Room @Entity, @Dao, @Database classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ─── Hilt ─────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * {
    @dagger.hilt.* <methods>;
}

# ─── OkHttp & Retrofit ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# ─── General ─────────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

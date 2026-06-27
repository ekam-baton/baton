// Root build.gradle.kts — plugin declarations only (no subproject configuration here)
plugins {
    alias(libs.plugins.android.application)    apply false
    alias(libs.plugins.android.library)        apply false
    alias(libs.plugins.kotlin.android)         apply false
    alias(libs.plugins.kotlin.multiplatform)   apply false
    alias(libs.plugins.kotlin.serialization)   apply false
    alias(libs.plugins.kotlin.compose)         apply false
    alias(libs.plugins.compose.multiplatform)  apply false
    alias(libs.plugins.ksp)                    apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

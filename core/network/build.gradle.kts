plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Network"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
        }
        
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging)
            implementation(libs.okhttp.sse)
            
            implementation(libs.androidx.browser)
            implementation(libs.security.crypto)
            implementation(libs.kotlinx.coroutines.android)
            api("io.getstream:stream-webrtc-android:1.3.10")
            
            implementation(libs.koin.android)
        }
        
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.bundles.testing.unit)
                implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
            }
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.ekam.baton.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

tasks.register<Exec>("compileRustJni") {
    group = "build"
    description = "Compiles the baton-crypto-android Rust library for all Android ABIs using cargo-ndk"
    workingDir = file("../../baton-crypto-android")
    executable = "cargo"
    args("ndk",
         "-t", "arm64-v8a",
         "-t", "armeabi-v7a",
         "-t", "x86",
         "-t", "x86_64",
         "-o", "${projectDir}/src/androidMain/jniLibs",
         "build", "--release"
    )
}

tasks.matching { 
    it.name.startsWith("compile") && it.name.contains("Kotlin")
}.configureEach {
    dependsOn("compileRustJni")
}

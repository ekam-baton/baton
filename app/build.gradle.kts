import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("com.google.gms.google-services") version "4.4.2" // FCM Push Gateway — Pillar 1 V2
}

android {
    namespace = "com.ekam.baton"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekam.baton"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-rc1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
        try {
            FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
        } catch (_: Exception) {}
    }

    val keystoreFilePath: String? = (project.findProperty("BATON_KEYSTORE_FILE") as String?)
        ?: System.getenv("BATON_KEYSTORE_FILE")
        ?: keystoreProps.getProperty("storeFile")
    val resolvedKeystoreFile = keystoreFilePath?.let { file(it) }?.takeIf { it.exists() }
    val hasReleaseKeystore = resolvedKeystoreFile != null

    signingConfigs {
        if (hasReleaseKeystore && resolvedKeystoreFile != null) {
            create("release") {
                storeFile = resolvedKeystoreFile
                storePassword = (project.findProperty("BATON_KEYSTORE_PASSWORD") as String?)
                    ?: System.getenv("BATON_KEYSTORE_PASSWORD")
                    ?: (keystoreProps.getProperty("storePassword") ?: "")
                keyAlias = (project.findProperty("BATON_KEY_ALIAS") as String?)
                    ?: System.getenv("BATON_KEY_ALIAS")
                    ?: (keystoreProps.getProperty("keyAlias") ?: "baton")
                keyPassword = (project.findProperty("BATON_KEY_PASSWORD") as String?)
                    ?: System.getenv("BATON_KEY_PASSWORD")
                    ?: (keystoreProps.getProperty("keyPassword") ?: "")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "**/libsqlcipher.so"
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

tasks.named("check") {
    dependsOn("detekt")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    
    // Test dependencies
    implementation(libs.koin.androidx.compose)
    // Feature modules
    implementation(project(":feature:chat"))
    implementation(project(":feature:agents"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:settings"))

    // Core modules
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(libs.koin.compose.viewmodel)

    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.bundles.compose.core)
    implementation(libs.androidx.graphics.path)
    implementation(libs.navigation.compose)
    implementation(libs.bundles.lifecycle)


implementation(libs.work.runtime.ktx)

    implementation("androidx.appcompat:appcompat:1.6.1")

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Biometric
    implementation(libs.androidx.biometric)

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")

    // Voice / WebRTC
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // FCM Push Gateway — Pillar 1, V2
    // enables blank wakeup notifications so Android wakes from Doze mode
    // to pull the E2EE message queue. Zero content is ever sent via FCM.
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-messaging-ktx")
}


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.ekam.baton"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekam.baton"
        minSdk = 26
        targetSdk = 36
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
    }
}

dependencies {
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

    // Hilt

implementation(libs.work.runtime.ktx)

    // AppCompat (Required for Hilt KSP to resolve AppCompatActivity/FragmentActivity)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

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

    // Firebase Telemetry
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
}


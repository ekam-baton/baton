plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ekam.baton"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekam.baton"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Credentials are read from gradle.properties (git-ignored) or
            // environment variables (BATON_KEYSTORE_PASSWORD / BATON_KEY_PASSWORD),
            // never hardcoded. See keystore.properties.sample for local setup.
            storeFile = file(
                (project.findProperty("BATON_KEYSTORE_FILE") as String?)
                    ?: System.getenv("BATON_KEYSTORE_FILE")
                    ?: "release.jks"
            )
            storePassword = (project.findProperty("BATON_KEYSTORE_PASSWORD") as String?)
                ?: System.getenv("BATON_KEYSTORE_PASSWORD")
                ?: throw GradleException(
                    "Missing BATON_KEYSTORE_PASSWORD. Set it in gradle.properties (git-ignored) " +
                    "or as an environment variable. See keystore.properties.sample."
                )
            keyAlias = (project.findProperty("BATON_KEY_ALIAS") as String?)
                ?: System.getenv("BATON_KEY_ALIAS")
                ?: "baton"
            keyPassword = (project.findProperty("BATON_KEY_PASSWORD") as String?)
                ?: System.getenv("BATON_KEY_PASSWORD")
                ?: throw GradleException(
                    "Missing BATON_KEY_PASSWORD. Set it in gradle.properties (git-ignored) " +
                    "or as an environment variable. See keystore.properties.sample."
                )
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
}


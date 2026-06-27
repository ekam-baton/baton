pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // gradle/libs.versions.toml is auto-discovered as the 'libs' catalog — no explicit from() needed.
}

rootProject.name = "baton"

include(":app")

// Core modules
include(":core:ui")
include(":core:data")
include(":core:network")

// Feature modules
include(":feature:chat")
include(":feature:agents")
include(":feature:memory")
include(":feature:settings")

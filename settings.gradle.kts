@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()             // for Android dependencies
        gradlePluginPortal() // for plugins
        mavenCentral()       // for Kotlin stdlib
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // for Android dependencies
        gradlePluginPortal() // for plugins
        mavenCentral()       // for Kotlin stdlib
    }
}

include(":app")
rootProject.name = "PantherLauncher"
import java.io.File
rootProject.name = "AshenRequiem"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases") { name = "NeoForge" }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("dev")

val modsDir = File(settingsDir, "mods")
modsDir.listFiles()
    ?.filter { it.isDirectory }
    ?.filter { File(it, "build.gradle.kts").exists() || File(it, "build.gradle").exists() }
    ?.filter { !File(it, "settings.gradle.kts").exists() && !File(it, "settings.gradle").exists() }
    ?.forEach { dir ->
        include(":mods:${dir.name}")
        project(":mods:${dir.name}").projectDir = dir
    }

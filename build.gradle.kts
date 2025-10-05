plugins {
    id("net.neoforged.moddev") version "2.0.112" apply false
}

subprojects {
    if (path.startsWith(":mods:") || path == ":dev") {
        apply(plugin = "net.neoforged.moddev")
    }

    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://thedarkcolour.github.io/KotlinForForge/") {
            name = "Kotlin for Forge"
            content { includeGroup("thedarkcolour") }
        }
    }
}

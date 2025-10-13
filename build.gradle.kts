plugins {
    id("net.neoforged.moddev") version "2.0.112" apply false
    id("io.github.themrmilchmann.curseforge-publish") version "0.8.0" apply false
}

val isModProject: (Project) -> Boolean = { p ->
    p.path.startsWith(":mods:")
}

subprojects {
    if (path.startsWith(":mods:") || path == ":dev") {
        apply(plugin = "net.neoforged.moddev")
    }

    if (path.startsWith(":mods:")) {
        apply(plugin = "io.github.themrmilchmann.curseforge-publish")
    }

    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
}

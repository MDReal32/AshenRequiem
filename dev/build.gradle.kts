val modProjects = rootProject.subprojects.filter { it.path.startsWith(":mods:") }

dependencies {
    modProjects.forEach { mp ->
        runtimeOnly(project(mp.path))
    }
}

neoForge {
    version = providers.gradleProperty("neo_version").get()

    parchment {
        mappingsVersion = providers.gradleProperty("parchment_mappings_version").get()
        minecraftVersion = providers.gradleProperty("parchment_minecraft_version").get()
    }

    runs {
        create("client") {
            client()
            gameDirectory = file("pack")
        }
    }
}

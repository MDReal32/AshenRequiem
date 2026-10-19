val modProjects = rootProject.subprojects.filter { it.path.startsWith(":mods:") }
modProjects.forEach { evaluationDependsOn(it.path) }

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
            // ./gradlew :dev:runClient -PquickPlay=localhost joins a server straight from launch.
            providers.gradleProperty("quickPlay").orNull?.let {
                programArguments.addAll("--quickPlayMultiplayer", it)
            }
        }
        // Headless test server. Shared mods (Kotlin for Forge, KubeJS, ...) must be copied into server/mods.
        create("server") {
            server()
            gameDirectory = file("server")
            programArgument("--nogui")
        }
    }
}

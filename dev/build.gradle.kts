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
        // Headless test server. Its mods come from the client pack, see syncServerMods.
        create("server") {
            server()
            gameDirectory = file("server")
            programArgument("--nogui")
        }
    }
}

// Mods that only run on the client (file names start with these). Everything else in pack/mods the server needs too.
val clientOnlyMods = listOf(
    "BadOptimizations", "better-advanced-tooltips", "configured", "Controlling", "darkmodeeverywhere", "gpumemleakfix",
    "jmi-", "JustEnoughResources", "MouseTweaks", "ProbeJS", "Searchables", "sodium",
)

// The server always runs the client pack's shared mods: added, updated and removed ones alike.
val syncServerMods by tasks.registering(Sync::class) {
    from("pack/mods") {
        include("*.jar")
        exclude(clientOnlyMods.map { "$it*" })
    }
    into("server/mods")
}

// The server runs the pack's server scripts and KubeJS data too (build them first with `yarn build` in modpack).
val syncServerScripts by tasks.registering(Sync::class) {
    from("pack/kubejs") { include("server_scripts/**", "startup_scripts/**", "data/**") }
    into("server/kubejs")
    preserve { exclude("server_scripts/**", "startup_scripts/**", "data/**") }
}

tasks.matching { it.name == "runServer" }.configureEach { dependsOn(syncServerMods, syncServerScripts) }

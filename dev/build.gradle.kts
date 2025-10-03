plugins {
    id("net.neoforged.moddev") version "2.0.110"
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
}

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

        }
    }

//    mods {
//        modProjects.forEach { mp ->
//            create(mp.name) {
//                sourceSet(mp.extensions.getByType(SourceSetContainer::class.java).named("main").get())
//            }
//        }
//    }
}

package com.mdreal.cinematica.scene

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mdreal.cinematica.timeline.Scene
import com.mojang.logging.LogUtils
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.AddReloadListenerEvent
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where scenes come from:
 * - datapacks: `data/<namespace>/cinematica/scenes/<path>.json`, id `<namespace>:<path>`;
 * - recordings: `<server dir>/cinematica/scenes/<name>.json`, id `local:<name>`, read fresh from disk on every use so
 *   a scene saved from the recorder plays right away.
 */
object Scenes {
    const val LOCAL = "local"
    private val NAME = Regex("[a-z0-9_-]{1,64}")
    private val LOGGER = LogUtils.getLogger()
    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Volatile
    private var packed: Map<ResourceLocation, Scene> = emptyMap()

    fun hook(bus: IEventBus) {
        bus.addListener<AddReloadListenerEvent> { it.addListener(Loader) }
    }

    fun isValidName(name: String) = NAME.matches(name)

    fun localDir(server: MinecraftServer): Path = server.serverDirectory.resolve("cinematica").resolve("scenes")

    /** Looks up a scene. A bare name (parsed as `minecraft:<name>`) falls back to the recording with that name. */
    fun get(server: MinecraftServer, id: ResourceLocation): Scene? = find(server, id)?.second

    /** The scene and its real id: `local:<name>` for a recording asked for by its bare name. */
    fun find(server: MinecraftServer, id: ResourceLocation): Pair<ResourceLocation, Scene>? {
        packed[id]?.let { return id to it }
        if (id.namespace != LOCAL && id.namespace != ResourceLocation.DEFAULT_NAMESPACE) return null
        val scene = loadLocal(server, id.path) ?: return null
        return ResourceLocation.fromNamespaceAndPath(LOCAL, id.path) to scene
    }

    fun ids(server: MinecraftServer): List<ResourceLocation> {
        val local = runCatching {
            Files.list(localDir(server)).use { files ->
                files.toList().mapNotNull { it.fileName.toString().removeSuffix(".json").takeIf(::isValidName) }
            }
        }.getOrDefault(emptyList())
        return packed.keys.sortedBy { it.toString() } + local.sorted().map { ResourceLocation.fromNamespaceAndPath(LOCAL, it) }
    }

    fun loadLocal(server: MinecraftServer, name: String): Scene? {
        if (!isValidName(name)) return null
        val file = localDir(server).resolve("$name.json")
        if (!Files.isRegularFile(file)) return null
        return runCatching { decode(JsonParser.parseString(Files.readString(file))) }
            .onFailure { LOGGER.error("Cinematica Studio: can't read scene {}: {}", file, it.message) }
            .getOrNull()
    }

    fun saveLocal(server: MinecraftServer, name: String, scene: Scene): Path {
        require(isValidName(name)) { "Invalid scene name '$name'" }
        val dir = localDir(server)
        Files.createDirectories(dir)
        val json = SceneCodecs.SCENE.encodeStart(JsonOps.INSTANCE, scene).getOrThrow()
        val file = dir.resolve("$name.json")
        Files.writeString(file, GSON.toJson(json) + "\n")
        return file
    }

    fun decode(json: JsonElement): Scene = SceneCodecs.SCENE.parse(JsonOps.INSTANCE, json).getOrThrow()

    private object Loader : SimpleJsonResourceReloadListener(GSON, "cinematica/scenes") {
        override fun apply(entries: Map<ResourceLocation, JsonElement>, manager: ResourceManager, profiler: ProfilerFiller) {
            packed = entries.mapNotNull { (id, json) ->
                runCatching { id to decode(json) }
                    .onFailure { LOGGER.error("Cinematica Studio: bad scene {}: {}", id, it.message) }
                    .getOrNull()
            }.toMap()
            LOGGER.info("Cinematica Studio: loaded {} scene(s) from datapacks", packed.size)
        }
    }
}

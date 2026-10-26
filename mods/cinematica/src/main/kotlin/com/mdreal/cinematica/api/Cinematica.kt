package com.mdreal.cinematica.api

import com.mdreal.cinematica.network.LinePayload
import com.mdreal.cinematica.scene.Scenes
import com.mdreal.cinematica.server.Sessions
import com.mdreal.cinematica.timeline.Scene
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.network.PacketDistributor
import java.util.Optional

/**
 * The public, server-side API. Other mods decide *when* a scene plays; Cinematica Studio plays it, keeps the player
 * safe meanwhile and puts them back afterwards. Listen for [SceneEndEvent] to continue once it is over.
 */
object Cinematica {
    /** Plays the scene with [id] for [player]. Returns false when no such scene exists. */
    fun play(player: ServerPlayer, id: ResourceLocation): Boolean {
        val (realId, scene) = Scenes.find(player.server, id) ?: return false
        Sessions.play(player, realId, scene)
        return true
    }

    /** Plays a scene built in code. [id] is only reported back in [SceneEndEvent]. */
    fun play(player: ServerPlayer, id: ResourceLocation, scene: Scene) = Sessions.play(player, id, scene)

    /** Stops a scene or recording early. Fires [SceneEndEvent] with `completed = false` for a scene. */
    fun stop(player: ServerPlayer): Boolean = Sessions.stop(player)

    /**
     * Says a spoken line to [player] outside a scene: [key] is a translation key shown as a subtitle, [sound] the voice,
     * played only if the client has it. Lines queue one after another and wait while a scene plays.
     */
    @JvmOverloads
    fun say(player: ServerPlayer, key: String, sound: ResourceLocation? = null) =
        PacketDistributor.sendToPlayer(player, LinePayload(key, Optional.ofNullable(sound)))

    fun isInScene(player: ServerPlayer): Boolean = Sessions.isActive(player)

    fun scene(server: MinecraftServer, id: ResourceLocation): Scene? = Scenes.get(server, id)

    fun sceneIds(server: MinecraftServer): List<ResourceLocation> = Scenes.ids(server)
}

/** Posted on `NeoForge.EVENT_BUS` when a scene starts playing for a player (not for the recorder). */
class SceneStartEvent(val player: ServerPlayer, val sceneId: ResourceLocation) : Event()

/**
 * Posted on `NeoForge.EVENT_BUS` when a scene ends for a player, after they have their game mode and position back.
 * [completed] is false when it was stopped, replaced by another scene or the player left.
 */
class SceneEndEvent(val player: ServerPlayer, val sceneId: ResourceLocation, val completed: Boolean) : Event()

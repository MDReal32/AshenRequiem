package com.mdreal.cinematica.server

import com.mdreal.cinematica.CinematicaStudio
import com.mdreal.cinematica.api.SceneEndEvent
import com.mdreal.cinematica.network.EndPayload
import com.mdreal.cinematica.network.Mode
import com.mdreal.cinematica.network.StartPayload
import com.mdreal.cinematica.scene.Scenes
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Timeline
import com.mdreal.cinematica.timeline.Vec
import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.UUID
import java.util.function.Supplier

/**
 * Server side of scenes and recordings. While one runs the player is a spectator that the server moves along the
 * camera path, so the chunks around the camera load; the client draws the camera and the actor.
 */
object Sessions {
    /** Extra server ticks after the scene's end before the server stops waiting for the client. */
    private const val TIMEOUT = 200

    /** Most keys a recording may hold, across all tracks. */
    const val MAX_KEYS = 4000

    private val LOGGER = LogUtils.getLogger()

    private val ATTACHMENT_TYPES: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CinematicaStudio.MOD_ID)

    private val RESTORE: DeferredHolder<AttachmentType<*>, AttachmentType<RestoreState>> =
        ATTACHMENT_TYPES.register("restore") { _: ResourceLocation ->
            AttachmentType.builder(Supplier { RestoreState.NONE }).serialize(RestoreState.CODEC).build()
        }

    private class Session(
        val mode: Mode,
        val id: ResourceLocation,
        val scene: Scene,
        val origin: Vec,
        val start: ActorState,
    ) {
        val timeline = Timeline(scene, start)
        var time = 0
    }

    private val active = HashMap<UUID, Session>()

    fun hookAttachments(modBus: IEventBus) {
        ATTACHMENT_TYPES.register(modBus)
    }

    fun hook(bus: IEventBus) {
        bus.addListener<ServerTickEvent.Post> { tick(it) }
        bus.addListener<PlayerEvent.PlayerLoggedInEvent> { (it.entity as? ServerPlayer)?.let(::restore) }
        bus.addListener<PlayerEvent.PlayerLoggedOutEvent> { event ->
            (event.entity as? ServerPlayer)?.let { if (!finish(it, false)) restore(it) }
        }
    }

    fun isActive(player: ServerPlayer) = player.uuid in active

    fun play(player: ServerPlayer, id: ResourceLocation, scene: Scene) = begin(player, Mode.PLAY, id, scene)

    /** Opens the recorder. An existing recording with that name is loaded so it can be extended. */
    fun record(player: ServerPlayer, name: String) {
        val scene = Scenes.loadLocal(player.server, name) ?: Scene(emptyList())
        begin(player, Mode.RECORD, ResourceLocation.fromNamespaceAndPath(Scenes.LOCAL, name), scene)
    }

    /** Ends whatever the player is in and puts them back where they were. */
    fun stop(player: ServerPlayer): Boolean = finish(player, false)

    fun onClientFinished(player: ServerPlayer) {
        if (active[player.uuid]?.mode == Mode.PLAY) finish(player, true)
    }

    fun onSave(player: ServerPlayer, scene: Scene) {
        val session = active[player.uuid]?.takeIf { it.mode == Mode.RECORD } ?: return
        val keys = scene.camera.size + scene.actor.size + scene.fade.size
        if (keys > MAX_KEYS) {
            player.sendSystemMessage(Component.translatable("cinematica_studio.save.too_large", keys, MAX_KEYS))
            return
        }
        runCatching { Scenes.saveLocal(player.server, session.id.path, scene) }
            .onSuccess { player.sendSystemMessage(Component.translatable("cinematica_studio.save.done", session.id.toString(), keys)) }
            .onFailure {
                LOGGER.error("Cinematica Studio: can't save {}", session.id, it)
                player.sendSystemMessage(Component.translatable("cinematica_studio.save.failed", it.message ?: it.toString()))
            }
    }

    private fun begin(player: ServerPlayer, mode: Mode, id: ResourceLocation, scene: Scene) {
        // A scene started while another runs replaces it but keeps the first restore state.
        active.remove(player.uuid)?.let { fireEnd(player, it, false) }
        if (!player.hasData(RESTORE)) player.setData(RESTORE, RestoreState.of(player))
        val restore = player.getData(RESTORE)

        val feet = Vec(restore.pos.x, restore.pos.y, restore.pos.z)
        val origin = if (scene.absolute) Vec.ZERO else feet
        val start = ActorState(feet - origin, restore.yaw, Pose(head = Rot(restore.pitch, 0f, 0f)), Rot.ZERO)

        player.setGameMode(GameType.SPECTATOR)
        active[player.uuid] = Session(mode, id, scene, origin, start)
        PacketDistributor.sendToPlayer(
            player,
            StartPayload(mode, id.toString(), scene, origin, start.pos, restore.yaw, restore.pitch)
        )
    }

    private fun tick(event: ServerTickEvent.Post) {
        if (active.isEmpty()) return
        val players = event.server.playerList
        active.entries.toList().forEach { (uuid, session) ->
            val player = players.getPlayer(uuid) ?: return@forEach run { active.remove(uuid) }
            if (session.mode != Mode.PLAY) return@forEach
            session.time++
            val camera = session.timeline.camera(session.time.toDouble())
            if (camera != null) {
                // The player's body follows the camera so its chunks are loaded and sent.
                val x = session.origin.x + camera.pos.x
                val y = session.origin.y + camera.pos.y - ActorState.EYE_HEIGHT
                val z = session.origin.z + camera.pos.z
                if (player.distanceToSqr(x, y, z) > 0.25) player.connection.teleport(x, y, z, player.yRot, player.xRot)
            }
            if (session.time > session.scene.end + TIMEOUT) finish(player, true)
        }
    }

    /** Ends the player's session, if any. A completed scene with an actor leaves the player where the actor ended. */
    private fun finish(player: ServerPlayer, completed: Boolean): Boolean {
        val session = active.remove(player.uuid) ?: return false
        val target = if (completed && session.scene.actor.isNotEmpty()) {
            val actor = session.timeline.actor(session.scene.end.toDouble())
            val eye = actor.eye()
            Placement(Vec3(session.origin.x + actor.pos.x, session.origin.y + actor.pos.y, session.origin.z + actor.pos.z), eye.yaw, eye.pitch)
        } else {
            null
        }
        restore(player, target)
        PacketDistributor.sendToPlayer(player, EndPayload)
        fireEnd(player, session, completed)
        return true
    }

    private fun fireEnd(player: ServerPlayer, session: Session, completed: Boolean) {
        if (session.mode == Mode.PLAY) NeoForge.EVENT_BUS.post(SceneEndEvent(player, session.id, completed))
    }

    private data class Placement(val pos: Vec3, val yaw: Float, val pitch: Float)

    private fun restore(player: ServerPlayer, target: Placement? = null) {
        if (!player.hasData(RESTORE)) return
        val state = player.getData(RESTORE)
        player.removeData(RESTORE)
        val level = player.server.getLevel(state.dimension) ?: player.server.overworld()
        val place = target ?: Placement(state.pos, state.yaw, state.pitch)
        player.teleportTo(level, place.pos.x, place.pos.y, place.pos.z, place.yaw, place.pitch)
        player.setGameMode(state.gameMode)
        player.resetFallDistance()
    }
}

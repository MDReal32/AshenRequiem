package com.mdreal.ashenrequiem.world.progression

import com.mdreal.ashenrequiem.core.api.AshenRequiem
import com.mdreal.ashenrequiem.core.api.DimensionSpawns
import com.mdreal.ashenrequiem.world.WorldConfig
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent
import net.neoforged.neoforge.event.level.BlockEvent

/**
 * Act I rules for the Dead Lands (ROADMAP D1):
 * - every player wakes up in the Dead Lands on their first join,
 * - until the Gate is repaired, dying always brings them back there,
 * - Nether portals cannot be lit there, so the Gate is the only way out.
 */
object DeadlandsArrival {
    val DEADLANDS: ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands"))

    /** Story location key for the spawn point in the Dead Lands. */
    const val CRADLE = "cradle"

    private val LOGGER = LogUtils.getLogger()

    fun hook(bus: IEventBus) {
        DimensionSpawns.register(DEADLANDS, ::cradle)
        bus.addListener(::onLogin)
        bus.addListener(::onRespawnPosition)
        bus.addListener(::onPortalSpawn)
    }

    private fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (AshenRequiem.isAwakened(player)) return
        val level = deadlands(player) ?: return
        AshenRequiem.setAwakened(player, true)
        sendToCradle(player, level)
    }

    /** Rewrites the respawn target before the player respawns, so they never flash into the Overworld. */
    private fun onRespawnPosition(event: PlayerRespawnPositionEvent) {
        if (event.isFromEndFight) return
        val player = event.entity as? ServerPlayer ?: return
        if (AshenRequiem.isGateRepaired(player.level().server)) return
        if (event.teleportTransition.newLevel().dimension() == DEADLANDS) return
        val level = deadlands(player) ?: return
        val pos = cradle(level)
        setRespawn(player, pos)
        event.teleportTransition = TeleportTransition(
            level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.yRot, player.xRot, TeleportTransition.DO_NOTHING
        )
    }

    private fun onPortalSpawn(event: BlockEvent.PortalSpawnEvent) {
        val level = event.level as? Level ?: return
        if (level.dimension() == DEADLANDS) event.isCanceled = true
    }

    private fun sendToCradle(player: ServerPlayer, level: ServerLevel) {
        val pos = cradle(level)
        setRespawn(player, pos)
        DimensionSpawns.teleport(player, level, pos)
    }

    private fun setRespawn(player: ServerPlayer, pos: BlockPos) {
        val data = LevelData.RespawnData(GlobalPos.of(DEADLANDS, pos), 0f, 0f)
        player.setRespawnPosition(ServerPlayer.RespawnConfig(data, true), false)
    }

    /** The Cradle is chosen once per world and remembered. A real Cradle structure replaces this in Stage 3. */
    private fun cradle(level: ServerLevel): BlockPos {
        AshenRequiem.getLocation(level.server, CRADLE)?.let { return it.pos() }
        val pos = DimensionSpawns.findDryLand(level, 0, 0)
        AshenRequiem.setLocation(level.server, CRADLE, GlobalPos.of(DEADLANDS, pos))
        return pos
    }

    private fun deadlands(player: ServerPlayer): ServerLevel? =
        player.level().server.getLevel(DEADLANDS).also {
            if (it == null) LOGGER.error("Dead Lands dimension {} is missing; is the datapack loaded?", DEADLANDS.identifier())
        }
}

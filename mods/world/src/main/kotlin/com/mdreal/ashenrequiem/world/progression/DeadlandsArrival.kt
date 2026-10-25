package com.mdreal.ashenrequiem.world.progression

import com.mdreal.ashenrequiem.core.api.AshenRequiem
import com.mdreal.ashenrequiem.core.api.DimensionSpawns
import com.mdreal.ashenrequiem.world.WorldConfig
import com.mdreal.ashenrequiem.world.init.Blocks
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.portal.DimensionTransition
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
 *
 * The Cradle also gets its broken workbench and a few heaps of rubble once per world (ROADMAP D11).
 */
object DeadlandsArrival {
    val DEADLANDS: ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands"))

    /** Story location key for the spawn point in the Dead Lands. */
    const val CRADLE = "cradle"

    /** Story location key for the Cradle's broken workbench; set once it's placed. */
    const val CRADLE_WORKBENCH = "cradle_workbench"

    /** Where the rubble goes, relative to the Cradle: enough for a scrap knife and the workbench repair. */
    private val CRADLE_RUBBLE = listOf(BlockPos(-2, 0, 1), BlockPos(1, 0, -3), BlockPos(4, 0, 2), BlockPos(-3, 0, -2), BlockPos(2, 0, 4))

    private val LOGGER = LogUtils.getLogger()

    fun hook(bus: IEventBus) {
        DimensionSpawns.register(DEADLANDS, ::cradle)
        bus.addListener(::onLogin)
        bus.addListener(::onRespawnPosition)
        bus.addListener(::onPortalSpawn)
    }

    private fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val level = deadlands(player) ?: return
        furnishCradle(level)
        if (AshenRequiem.isAwakened(player)) return
        AshenRequiem.setAwakened(player, true)
        sendToCradle(player, level)
    }

    /** Rewrites the respawn target before the player respawns, so they never flash into the Overworld. */
    private fun onRespawnPosition(event: PlayerRespawnPositionEvent) {
        if (event.isFromEndFight) return
        val player = event.entity as? ServerPlayer ?: return
        if (AshenRequiem.isGateRepaired(player.server)) return
        if (event.dimensionTransition.newLevel().dimension() == DEADLANDS) return
        val level = deadlands(player) ?: return
        val pos = cradle(level)
        player.setRespawnPosition(DEADLANDS, pos, 0f, true, false)
        event.dimensionTransition = DimensionTransition(
            level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.yRot, player.xRot, DimensionTransition.DO_NOTHING
        )
    }

    private fun onPortalSpawn(event: BlockEvent.PortalSpawnEvent) {
        val level = event.level as? Level ?: return
        if (level.dimension() == DEADLANDS) event.isCanceled = true
    }

    private fun sendToCradle(player: ServerPlayer, level: ServerLevel) {
        val pos = cradle(level)
        player.setRespawnPosition(DEADLANDS, pos, 0f, true, false)
        player.teleportTo(level, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, player.yRot, player.xRot)
    }

    /** The Cradle is chosen once per world and remembered. A real Cradle structure replaces this in Stage 3. */
    private fun cradle(level: ServerLevel): BlockPos {
        AshenRequiem.getLocation(level.server, CRADLE)?.let { return it.pos() }
        val pos = DimensionSpawns.findDryLand(level, 0, 0)
        AshenRequiem.setLocation(level.server, CRADLE, GlobalPos.of(DEADLANDS, pos))
        return pos
    }

    /** Places the broken workbench beside the Cradle, with rubble around it. Runs once per world, older worlds included. */
    private fun furnishCradle(level: ServerLevel) {
        if (AshenRequiem.getLocation(level.server, CRADLE_WORKBENCH) != null) return
        val cradle = cradle(level)
        val workbench = surface(level, cradle.east(2))
        level.setBlockAndUpdate(workbench, Blocks.BROKEN_WORKBENCH.block.get().defaultBlockState())
        val rubble = Blocks.RUBBLE.block.get().defaultBlockState()
        for (offset in CRADLE_RUBBLE) {
            val pos = surface(level, cradle.offset(offset))
            if (level.isEmptyBlock(pos) && rubble.canSurvive(level, pos)) level.setBlockAndUpdate(pos, rubble)
        }
        AshenRequiem.setLocation(level.server, CRADLE_WORKBENCH, GlobalPos.of(DEADLANDS, workbench))
        LOGGER.info("Placed the Cradle's broken workbench at {}", workbench)
    }

    /** The first free block above the ground. Loads the chunk first: an unloaded one reads as the bottom of the world. */
    private fun surface(level: ServerLevel, pos: BlockPos): BlockPos {
        level.getChunk(pos)
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos)
    }

    private fun deadlands(player: ServerPlayer): ServerLevel? =
        player.server.getLevel(DEADLANDS).also {
            if (it == null) LOGGER.error("Dead Lands dimension {} is missing; is the datapack loaded?", DEADLANDS.location())
        }
}

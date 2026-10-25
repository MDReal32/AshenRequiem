package com.mdreal.ashenrequiem.core.api

import com.mdreal.ashenrequiem.core.radiation.Radiation
import com.mdreal.ashenrequiem.core.state.Attachments
import com.mdreal.ashenrequiem.core.state.PlayerProgress
import com.mdreal.ashenrequiem.core.state.ProgressSync
import com.mdreal.ashenrequiem.core.state.WorldProgress
import net.minecraft.core.GlobalPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge

/**
 * The single entry point for reading and changing story state, server side.
 * Other modules, commands and KubeJS (bound as `AR`) all go through here, so every change
 * is saved, synced and announced with an event.
 */
object AshenRequiem {
    const val MAX_HUSH_STAGE = 5
    const val MAX_LEVEL = 6

    // ---- World-global state ----

    fun getHushStage(server: MinecraftServer): Int = WorldProgress.get(server).hushStage

    /** Clamps to `0..MAX_HUSH_STAGE`. Returns true if the stage changed. */
    fun setHushStage(server: MinecraftServer, stage: Int): Boolean {
        val state = WorldProgress.get(server)
        val next = stage.coerceIn(0, MAX_HUSH_STAGE)
        val old = state.hushStage
        if (next == old) return false
        state.hushStage = next
        state.setDirty()
        NeoForge.EVENT_BUS.post(HushStageChangedEvent(server, old, next))
        return true
    }

    fun isGateRepaired(server: MinecraftServer): Boolean = WorldProgress.get(server).gateRepaired

    fun setGateRepaired(server: MinecraftServer, repaired: Boolean): Boolean {
        val state = WorldProgress.get(server)
        if (state.gateRepaired == repaired) return false
        state.gateRepaired = repaired
        state.setDirty()
        NeoForge.EVENT_BUS.post(GateStateChangedEvent(server, repaired))
        return true
    }

    fun isFinaleWon(server: MinecraftServer): Boolean = WorldProgress.get(server).finaleWon

    fun setFinaleWon(server: MinecraftServer, won: Boolean) {
        val state = WorldProgress.get(server)
        if (state.finaleWon == won) return
        state.finaleWon = won
        state.setDirty()
    }

    fun getLocation(server: MinecraftServer, key: String): GlobalPos? = WorldProgress.get(server).locations[key]

    fun setLocation(server: MinecraftServer, key: String, pos: GlobalPos) {
        val state = WorldProgress.get(server)
        state.locations[key] = pos
        state.setDirty()
    }

    fun removeLocation(server: MinecraftServer, key: String): Boolean {
        val state = WorldProgress.get(server)
        if (state.locations.remove(key) == null) return false
        state.setDirty()
        return true
    }

    fun locations(server: MinecraftServer): Map<String, GlobalPos> = WorldProgress.get(server).locations.toMap()

    // ---- Per-player state ----

    fun progress(player: ServerPlayer): PlayerProgress = player.getData(Attachments.PLAYER_PROGRESS)

    fun hasRequiem(player: ServerPlayer): Boolean = progress(player).requiem

    fun setRequiem(player: ServerPlayer, value: Boolean): Boolean {
        val changed = update(player) { it.copy(requiem = value) }
        if (changed) NeoForge.EVENT_BUS.post(RequiemChangedEvent(player, value))
        return changed
    }

    fun isAwakened(player: ServerPlayer): Boolean = progress(player).awakened

    fun setAwakened(player: ServerPlayer, value: Boolean): Boolean = update(player) { it.copy(awakened = value) }

    fun getChapter(player: ServerPlayer): Int = progress(player).chapter

    fun setChapter(player: ServerPlayer, chapter: Int): Boolean =
        update(player) { it.copy(chapter = chapter.coerceAtLeast(0)) }

    fun getLevel(player: ServerPlayer): Int = progress(player).level

    /** Clamps to `1..MAX_LEVEL`. Returns true if the level changed. */
    fun setLevel(player: ServerPlayer, level: Int): Boolean {
        val old = getLevel(player)
        val changed = update(player) { it.copy(level = level.coerceIn(1, MAX_LEVEL)) }
        if (changed) NeoForge.EVENT_BUS.post(LevelChangedEvent(player, old, getLevel(player)))
        return changed
    }

    fun hasSeenCinematic(player: ServerPlayer, id: String): Boolean = id in progress(player).seenCinematics

    fun markCinematicSeen(player: ServerPlayer, id: String): Boolean =
        update(player) { it.copy(seenCinematics = it.seenCinematics + id) }

    /** The player's radiation reading, in units of the HUD bar (10 is a full bar). Updated once a second. */
    fun getRadiation(player: ServerPlayer): Float = Radiation.units(player)

    /** Wipes a player's story progress. Losing the Requiem this way still fires [RequiemChangedEvent]. */
    fun resetPlayer(player: ServerPlayer) {
        val hadRequiem = hasRequiem(player)
        update(player) { PlayerProgress() }
        if (hadRequiem) NeoForge.EVENT_BUS.post(RequiemChangedEvent(player, false))
    }

    private inline fun update(player: ServerPlayer, transform: (PlayerProgress) -> PlayerProgress): Boolean {
        val old = progress(player)
        val next = transform(old)
        if (next == old) return false
        player.setData(Attachments.PLAYER_PROGRESS, next)
        ProgressSync.sync(player)
        return true
    }
}

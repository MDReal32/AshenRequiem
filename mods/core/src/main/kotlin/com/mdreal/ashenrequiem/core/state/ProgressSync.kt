package com.mdreal.ashenrequiem.core.state

import com.mdreal.ashenrequiem.core.api.AshenRequiem
import com.mdreal.ashenrequiem.core.network.PlayerProgressPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor

/** Keeps each client's copy of its own [PlayerProgress] up to date. */
object ProgressSync {
    fun hook(bus: IEventBus) {
        bus.addListener<PlayerEvent.PlayerLoggedInEvent> { sync(it.entity) }
        bus.addListener<PlayerEvent.PlayerRespawnEvent> { sync(it.entity) }
        bus.addListener<PlayerEvent.PlayerChangedDimensionEvent> { sync(it.entity) }
    }

    fun sync(player: Player) {
        if (player !is ServerPlayer) return
        PacketDistributor.sendToPlayer(player, PlayerProgressPayload(AshenRequiem.progress(player)))
    }
}

package com.mdreal.ashenrequiem.core.network

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mdreal.ashenrequiem.core.compat.jer.LootTableSync
import com.mdreal.ashenrequiem.core.radiation.RadiationSync
import com.mdreal.ashenrequiem.core.recipe.RecipeGateSync
import com.mdreal.ashenrequiem.core.state.PlayerProgress
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

data class PlayerProgressPayload(val progress: PlayerProgress) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PlayerProgressPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayerProgressPayload>(CoreConfig.id("player_progress"))
        val STREAM_CODEC: StreamCodec<ByteBuf, PlayerProgressPayload> =
            ByteBufCodecs.fromCodec(PlayerProgress.CODEC).map(::PlayerProgressPayload, PlayerProgressPayload::progress)
    }
}

/** Client-side mirror of the local player's progress. Plain data, safe to reference from common code. */
object ClientProgress {
    @Volatile
    var current: PlayerProgress = PlayerProgress()
        internal set
}

object Network {
    private const val PROTOCOL = "1"

    fun hook(bus: IEventBus) {
        bus.addListener(::onRegister)
    }

    private fun onRegister(event: RegisterPayloadHandlersEvent) {
        event.registrar(PROTOCOL).playToClient(PlayerProgressPayload.TYPE, PlayerProgressPayload.STREAM_CODEC) { payload, _ ->
            ClientProgress.current = payload.progress
        }
        RecipeGateSync.register(event.registrar(PROTOCOL))
        LootTableSync.register(event.registrar(PROTOCOL))
        RadiationSync.register(event.registrar(PROTOCOL))
    }
}

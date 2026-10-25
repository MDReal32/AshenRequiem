package com.mdreal.ashenrequiem.core.recipe

import com.mdreal.ashenrequiem.core.CoreConfig
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.PayloadRegistrar

data class RecipeGatesPayload(val gates: List<RecipeGates.Gate>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RecipeGatesPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<RecipeGatesPayload>(CoreConfig.id("recipe_gates"))
        val STREAM_CODEC: StreamCodec<ByteBuf, RecipeGatesPayload> =
            ByteBufCodecs.fromCodec(RecipeGates.GATE_CODEC.listOf()).map(::RecipeGatesPayload, RecipeGatesPayload::gates)
    }
}

/** Client-side copy of the server's recipe gates, so JEI can show which recipes are gated. Plain data. */
object ClientRecipeGates {
    @Volatile
    var byRecipe: Map<ResourceLocation, List<RecipeGates.Gate>> = emptyMap()
        private set

    internal fun receive(payload: RecipeGatesPayload) {
        byRecipe = RecipeGates.byRecipe(payload.gates)
    }
}

/** Sends the recipe gates with every datapack sync (joining and `/reload`), before the recipes that make JEI rebuild. */
object RecipeGateSync {
    fun register(registrar: PayloadRegistrar) {
        registrar.playToClient(RecipeGatesPayload.TYPE, RecipeGatesPayload.STREAM_CODEC) { payload, _ ->
            ClientRecipeGates.receive(payload)
        }
    }

    fun send(player: ServerPlayer) {
        PacketDistributor.sendToPlayer(player, RecipeGatesPayload(RecipeGates.all))
    }
}

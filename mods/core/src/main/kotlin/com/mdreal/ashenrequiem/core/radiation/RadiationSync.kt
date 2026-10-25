package com.mdreal.ashenrequiem.core.radiation

import com.mdreal.ashenrequiem.core.CoreConfig
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.registration.PayloadRegistrar

/** The local player's radiation reading in tenths of a unit, so 0..100 fills the 10 icons of the HUD bar. */
data class RadiationPayload(val tenths: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RadiationPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<RadiationPayload>(CoreConfig.id("radiation"))
        val STREAM_CODEC: StreamCodec<ByteBuf, RadiationPayload> =
            ByteBufCodecs.VAR_INT.map(::RadiationPayload, RadiationPayload::tenths)
    }
}

/** Client-side copy of the local player's reading, for the HUD. Plain data. */
object ClientRadiation {
    @Volatile
    var tenths: Int = 0
        internal set
}

object RadiationSync {
    fun register(registrar: PayloadRegistrar) {
        registrar.playToClient(RadiationPayload.TYPE, RadiationPayload.STREAM_CODEC) { payload, _ ->
            ClientRadiation.tenths = payload.tenths
        }
    }
}

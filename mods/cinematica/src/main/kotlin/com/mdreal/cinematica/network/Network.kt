package com.mdreal.cinematica.network

import com.mdreal.cinematica.CinematicaStudio
import com.mdreal.cinematica.scene.SceneCodecs
import com.mdreal.cinematica.server.Sessions
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Vec
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

enum class Mode { PLAY, RECORD }

/**
 * Starts playback or the recorder on the client. Keys are relative to [origin] (unless the scene is absolute, then
 * [origin] is zero); [start] is the player's own feet relative to it, and [yaw]/[pitch] where they were looking.
 */
data class StartPayload(
    val mode: Mode,
    val name: String,
    val scene: Scene,
    val origin: Vec,
    val start: Vec,
    val yaw: Float,
    val pitch: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<StartPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<StartPayload>(CinematicaStudio.id("start"))
        private val SCENE = ByteBufCodecs.fromCodecTrusted(SceneCodecs.SCENE)
        val STREAM_CODEC: StreamCodec<ByteBuf, StartPayload> = StreamCodec.of(
            { buf, p ->
                ByteBufCodecs.VAR_INT.encode(buf, p.mode.ordinal)
                ByteBufCodecs.STRING_UTF8.encode(buf, p.name)
                SCENE.encode(buf, p.scene)
                VEC.encode(buf, p.origin)
                VEC.encode(buf, p.start)
                ByteBufCodecs.FLOAT.encode(buf, p.yaw)
                ByteBufCodecs.FLOAT.encode(buf, p.pitch)
            },
            { buf ->
                StartPayload(
                    Mode.entries[ByteBufCodecs.VAR_INT.decode(buf)],
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    SCENE.decode(buf),
                    VEC.decode(buf),
                    VEC.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf)
                )
            }
        )
    }
}

/** Server to client: the scene or recording is over, give the camera back. */
object EndPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<EndPayload>(CinematicaStudio.id("end"))
    val STREAM_CODEC: StreamCodec<ByteBuf, EndPayload> = StreamCodec.unit(EndPayload)
    override fun type() = TYPE
}

/** Client to server: the client reached the end of the scene, including the handoff. */
object FinishedPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<FinishedPayload>(CinematicaStudio.id("finished"))
    val STREAM_CODEC: StreamCodec<ByteBuf, FinishedPayload> = StreamCodec.unit(FinishedPayload)
    override fun type() = TYPE
}

/** Client to server: save the recorder's scene under the name the record command was given. */
data class SavePayload(val scene: Scene) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<SavePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SavePayload>(CinematicaStudio.id("save"))
        val STREAM_CODEC: StreamCodec<ByteBuf, SavePayload> =
            ByteBufCodecs.fromCodec(SceneCodecs.SCENE).map(::SavePayload, SavePayload::scene)
    }
}

private val VEC: StreamCodec<ByteBuf, Vec> = StreamCodec.composite(
    ByteBufCodecs.DOUBLE, Vec::x,
    ByteBufCodecs.DOUBLE, Vec::y,
    ByteBufCodecs.DOUBLE, Vec::z,
    ::Vec
)

/**
 * Client handlers live behind this, so common code never names a client class. The client entry point sets it; on a
 * dedicated server it stays null and the S2C handlers are never called anyway.
 */
object ClientBridge {
    interface Sink {
        fun start(payload: StartPayload)
        fun end()
    }

    @Volatile
    var sink: Sink? = null
}

object Network {
    private const val PROTOCOL = "1"

    fun hook(bus: IEventBus) {
        bus.addListener(::onRegister)
    }

    private fun onRegister(event: RegisterPayloadHandlersEvent) {
        event.registrar(PROTOCOL)
            .playToClient(StartPayload.TYPE, StartPayload.STREAM_CODEC) { payload, _ -> ClientBridge.sink?.start(payload) }
            .playToClient(EndPayload.TYPE, EndPayload.STREAM_CODEC) { _, _ -> ClientBridge.sink?.end() }
            .playToServer(FinishedPayload.TYPE, FinishedPayload.STREAM_CODEC) { _, ctx ->
                (ctx.player() as? ServerPlayer)?.let(Sessions::onClientFinished)
            }
            .playToServer(SavePayload.TYPE, SavePayload.STREAM_CODEC) { payload, ctx ->
                (ctx.player() as? ServerPlayer)?.let { Sessions.onSave(it, payload.scene) }
            }
    }
}

package com.mdreal.cinematica.network

import com.mdreal.cinematica.CinematicaStudio
import com.mdreal.cinematica.scene.SceneCodecs
import com.mdreal.cinematica.server.Sessions
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Vec
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.Optional
import java.util.UUID

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

/**
 * Server to client: another player nearby, [owner], is watching a scene. Show their actor playing it in the world,
 * [time] ticks in. Fields as in [StartPayload].
 */
data class WitnessPayload(
    val owner: UUID,
    val scene: Scene,
    val origin: Vec,
    val start: Vec,
    val yaw: Float,
    val pitch: Float,
    val time: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WitnessPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<WitnessPayload>(CinematicaStudio.id("witness"))
        private val SCENE = ByteBufCodecs.fromCodecTrusted(SceneCodecs.SCENE)
        val STREAM_CODEC: StreamCodec<ByteBuf, WitnessPayload> = StreamCodec.of(
            { buf, p ->
                UUIDUtil.STREAM_CODEC.encode(buf, p.owner)
                SCENE.encode(buf, p.scene)
                VEC.encode(buf, p.origin)
                VEC.encode(buf, p.start)
                ByteBufCodecs.FLOAT.encode(buf, p.yaw)
                ByteBufCodecs.FLOAT.encode(buf, p.pitch)
                ByteBufCodecs.VAR_INT.encode(buf, p.time)
            },
            { buf ->
                WitnessPayload(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    SCENE.decode(buf),
                    VEC.decode(buf),
                    VEC.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
                )
            }
        )
    }
}

/** Server to client: stop showing [owner]'s actor; they are back in the world, or out of sight. */
data class WitnessEndPayload(val owner: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WitnessEndPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<WitnessEndPayload>(CinematicaStudio.id("witness_end"))
        val STREAM_CODEC: StreamCodec<ByteBuf, WitnessEndPayload> =
            UUIDUtil.STREAM_CODEC.map(::WitnessEndPayload, WitnessEndPayload::owner)
    }
}

/** Server to client: the scene or recording is over, give the camera back. */
object EndPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<EndPayload>(CinematicaStudio.id("end"))
    val STREAM_CODEC: StreamCodec<ByteBuf, EndPayload> = StreamCodec.unit(EndPayload)
    override fun type() = TYPE
}

/** Server to client: say a spoken line, the subtitle [key] (a translation key) and [sound] if the client has it. */
data class LinePayload(val key: String, val sound: Optional<ResourceLocation>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<LinePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<LinePayload>(CinematicaStudio.id("line"))
        val STREAM_CODEC: StreamCodec<ByteBuf, LinePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LinePayload::key,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), LinePayload::sound,
            ::LinePayload
        )
    }
}

/** Client to server: the client reached the end of the scene, including the handoff. */
object FinishedPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<FinishedPayload>(CinematicaStudio.id("finished"))
    val STREAM_CODEC: StreamCodec<ByteBuf, FinishedPayload> = StreamCodec.unit(FinishedPayload)
    override fun type() = TYPE
}

/** Client to server: the watcher paused, resumed or stepped; the server's clock follows so it keeps loading the right chunks. */
data class ControlPayload(val time: Int, val paused: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ControlPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ControlPayload>(CinematicaStudio.id("control"))
        val STREAM_CODEC: StreamCodec<ByteBuf, ControlPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ControlPayload::time,
            ByteBufCodecs.BOOL, ControlPayload::paused,
            ::ControlPayload
        )
    }
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
        fun witness(payload: WitnessPayload)
        fun witnessEnd(owner: UUID)
        fun line(payload: LinePayload)
    }

    @Volatile
    var sink: Sink? = null
}

object Network {
    private const val PROTOCOL = "4"

    fun hook(bus: IEventBus) {
        bus.addListener(::onRegister)
    }

    private fun onRegister(event: RegisterPayloadHandlersEvent) {
        event.registrar(PROTOCOL)
            .playToClient(StartPayload.TYPE, StartPayload.STREAM_CODEC) { payload, _ -> ClientBridge.sink?.start(payload) }
            .playToClient(EndPayload.TYPE, EndPayload.STREAM_CODEC) { _, _ -> ClientBridge.sink?.end() }
            .playToClient(WitnessPayload.TYPE, WitnessPayload.STREAM_CODEC) { payload, _ -> ClientBridge.sink?.witness(payload) }
            .playToClient(WitnessEndPayload.TYPE, WitnessEndPayload.STREAM_CODEC) { payload, _ ->
                ClientBridge.sink?.witnessEnd(payload.owner)
            }
            .playToClient(LinePayload.TYPE, LinePayload.STREAM_CODEC) { payload, _ -> ClientBridge.sink?.line(payload) }
            .playToServer(FinishedPayload.TYPE, FinishedPayload.STREAM_CODEC) { _, ctx ->
                (ctx.player() as? ServerPlayer)?.let(Sessions::onClientFinished)
            }
            .playToServer(ControlPayload.TYPE, ControlPayload.STREAM_CODEC) { payload, ctx ->
                (ctx.player() as? ServerPlayer)?.let { Sessions.onControl(it, payload.time, payload.paused) }
            }
            .playToServer(SavePayload.TYPE, SavePayload.STREAM_CODEC) { payload, ctx ->
                (ctx.player() as? ServerPlayer)?.let { Sessions.onSave(it, payload.scene) }
            }
    }
}

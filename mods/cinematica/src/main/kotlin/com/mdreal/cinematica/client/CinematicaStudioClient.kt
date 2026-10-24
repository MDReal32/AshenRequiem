package com.mdreal.cinematica.client

import com.mdreal.cinematica.CinematicaStudio
import com.mdreal.cinematica.entity.ModEntities
import com.mdreal.cinematica.network.ClientBridge
import com.mdreal.cinematica.network.Mode
import com.mdreal.cinematica.network.StartPayload
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Timeline
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.LayeredDraw
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.RenderHandEvent
import net.neoforged.neoforge.client.event.RenderPlayerEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.common.NeoForge

@Mod(value = CinematicaStudio.MOD_ID, dist = [Dist.CLIENT])
class CinematicaStudioClient(modBus: IEventBus) {
    init {
        ClientBridge.sink = Sink
        modBus.addListener<EntityRenderersEvent.RegisterRenderers> { it.registerEntityRenderer(ModEntities.ACTOR.get(), ::ActorRenderer) }
        modBus.addListener<RegisterKeyMappingsEvent> { event -> Keys.ALL.forEach(event::register) }
        modBus.addListener<RegisterGuiLayersEvent> { it.registerAboveAll(Overlay.ID, Overlay) }

        val bus = NeoForge.EVENT_BUS
        bus.addListener<ClientTickEvent.Post> {
            Recorder.tick()
            Director.tick()
        }
        bus.addListener<RenderFrameEvent.Pre> {
            Recorder.frame()
            Director.frame(it.partialTick.getGameTimeDeltaPartialTick(true))
        }
        bus.addListener<ViewportEvent.ComputeCameraAngles> { Director.angles(it) }
        bus.addListener<ViewportEvent.ComputeFov> { Director.fov(it) }
        // While the camera is elsewhere, the real (spectator) player must not show up or get in the way.
        bus.addListener<RenderPlayerEvent.Pre> { if (Director.active && it.entity === Minecraft.getInstance().player) it.isCanceled = true }
        bus.addListener<RenderHandEvent> { if (Director.active) it.isCanceled = true }
        bus.addListener<RenderGuiLayerEvent.Pre> { if (Director.active && it.name != Overlay.ID) it.isCanceled = true }
        bus.addListener<InputEvent.InteractionKeyMappingTriggered> {
            if (Director.active) {
                it.setSwingHand(false)
                it.isCanceled = true
            }
        }
        bus.addListener<InputEvent.MouseScrollingEvent> { if (Director.active) it.isCanceled = true }
        bus.addListener<ClientPlayerNetworkEvent.LoggingOut> {
            Recorder.stop()
            Director.stop()
            Stage.remove()
        }
    }

    private object Sink : ClientBridge.Sink {
        override fun start(payload: StartPayload) {
            Recorder.stop()
            Director.stop()
            when (payload.mode) {
                Mode.RECORD -> Recorder.start(payload)
                Mode.PLAY -> {
                    val start = ActorState(payload.start, payload.yaw, Pose(head = Rot(payload.pitch, 0f, 0f)), Rot.ZERO)
                    Director.play(Timeline(payload.scene, start), payload.origin, showActor = payload.scene.showActor)
                }
            }
        }

        override fun end() {
            Recorder.stop()
            Director.stop()
        }
    }

    private object Overlay : LayeredDraw.Layer {
        val ID = CinematicaStudio.id("overlay")

        override fun render(graphics: GuiGraphics, deltaTracker: DeltaTracker) {
            val partialTick = deltaTracker.getGameTimeDeltaPartialTick(true)
            Director.overlay(graphics, partialTick)
            if (Recorder.active && !Director.active) Recorder.hud(graphics)
        }
    }
}

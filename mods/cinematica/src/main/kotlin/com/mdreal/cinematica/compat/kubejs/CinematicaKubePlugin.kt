package com.mdreal.cinematica.compat.kubejs

import com.mdreal.cinematica.api.Cinematica
import com.mdreal.cinematica.api.SceneEndEvent
import com.mdreal.cinematica.api.SceneStartEvent
import dev.latvian.mods.kubejs.event.EventGroup
import dev.latvian.mods.kubejs.event.EventGroupRegistry
import dev.latvian.mods.kubejs.player.KubePlayerEvent
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin
import dev.latvian.mods.kubejs.script.BindingRegistry
import dev.latvian.mods.kubejs.script.ScriptType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge

/**
 * Loaded by KubeJS only when it is installed (listed in `kubejs.plugins.txt`).
 *
 * Server scripts get `Cinematica` (`play(player, 'ns:id')`, `stop(player)`, `say(player, 'lang.key', 'ns:sound')`,
 * `isInScene(player)`, `sceneIds(server)`) and the events `CinematicaEvents.started` and `CinematicaEvents.ended`.
 */
class CinematicaKubePlugin : KubeJSPlugin {
    override fun init() {
        NeoForge.EVENT_BUS.addListener<SceneStartEvent> {
            if (STARTED.hasListeners()) STARTED.post(ScriptType.SERVER, SceneKubeEvent(it.player, it.sceneId, false))
        }
        NeoForge.EVENT_BUS.addListener<SceneEndEvent> {
            if (ENDED.hasListeners()) ENDED.post(ScriptType.SERVER, SceneKubeEvent(it.player, it.sceneId, it.completed))
        }
    }

    override fun registerEvents(registry: EventGroupRegistry) = registry.register(GROUP)

    override fun registerBindings(bindings: BindingRegistry) = bindings.add("Cinematica", Cinematica)

    private companion object {
        val GROUP: EventGroup = EventGroup.of("CinematicaEvents")
        val STARTED = GROUP.server("started") { SceneKubeEvent::class.java }
        val ENDED = GROUP.server("ended") { SceneKubeEvent::class.java }
    }
}

/** `event.player`, `event.sceneId`, and on `ended` whether the scene ran to its end (`event.completed`). */
class SceneKubeEvent(private val player: ServerPlayer, val sceneId: ResourceLocation, val completed: Boolean) :
    KubePlayerEvent {
    override fun getEntity(): ServerPlayer = player
}

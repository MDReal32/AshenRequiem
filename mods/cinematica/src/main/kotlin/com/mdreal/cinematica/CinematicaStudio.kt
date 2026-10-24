package com.mdreal.cinematica

import com.mdreal.cinematica.command.CinematicaCommand
import com.mdreal.cinematica.entity.ModEntities
import com.mdreal.cinematica.network.Network
import com.mdreal.cinematica.scene.Scenes
import com.mdreal.cinematica.server.Sessions
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge

@Mod(CinematicaStudio.MOD_ID)
class CinematicaStudio(modBus: IEventBus) {
    init {
        ModEntities.hook(modBus)
        Sessions.hookAttachments(modBus)
        Network.hook(modBus)
        Scenes.hook(NeoForge.EVENT_BUS)
        Sessions.hook(NeoForge.EVENT_BUS)
        CinematicaCommand.hook(NeoForge.EVENT_BUS)
    }

    companion object {
        const val MOD_ID = "cinematica_studio"

        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
    }
}

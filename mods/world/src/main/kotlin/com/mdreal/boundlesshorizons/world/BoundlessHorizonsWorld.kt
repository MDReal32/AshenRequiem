package com.mdreal.boundlesshorizons.world

import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import com.mdreal.boundlesshorizons.world.init.Blocks
import com.mdreal.boundlesshorizons.world.init.CreativeTab
import com.mdreal.boundlesshorizons.world.init.Items
import com.mdreal.boundlesshorizons.world.worldGen.ModWorldgen
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

@Mod(WorldConfig.MOD_ID)
class BoundlessHorizonsWorld(container: ModContainer, modBus: IEventBus) {
    init {
        modBus.addListener(::onCommonSetup)
        container.registerConfig(
            ModConfig.Type.COMMON, WorldCommonConfig.getSpec(), "boundless-horizons/client/world.toml"
        )
        ModWorldgen.hook(modBus)
        CreativeTab.hook(modBus)
        Blocks.hook(modBus)
        Items.hook(modBus)
    }

    fun onCommonSetup(event: FMLCommonSetupEvent) {}
}

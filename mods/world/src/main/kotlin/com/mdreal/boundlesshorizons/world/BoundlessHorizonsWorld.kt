package com.mdreal.boundlesshorizons.world

import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import com.mdreal.boundlesshorizons.world.worldGen.ModWorldgen
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig

@Mod(WorldConfig.MOD_ID)
class BoundlessHorizonsWorld(container: ModContainer, modBus: IEventBus) {
    init {
        container.registerConfig(ModConfig.Type.CLIENT, WorldCommonConfig.getSpec(), "boundless-horizons/client/world.toml")
        ModWorldgen.hook(modBus)
    }
}

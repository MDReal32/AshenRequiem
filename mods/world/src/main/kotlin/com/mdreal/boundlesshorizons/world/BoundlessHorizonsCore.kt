package com.mdreal.boundlesshorizons.world

import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

@Mod(WorldConfig.MOD_ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
class BoundlessHorizonsCore(container: ModContainer, modBus: IEventBus) {
    init {
        container.registerConfig(ModConfig.Type.CLIENT, WorldCommonConfig.spec)
    }

    @SubscribeEvent
    fun onCommonSetup(@Suppress("UNUSED_PARAMETER") event: FMLCommonSetupEvent) {
    }
}

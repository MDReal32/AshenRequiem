package com.mdreal.boundlesshorizons.world

import com.mdreal.boundlesshorizons.world.worldGen.injector.DeadlandsPackInjector
import com.mdreal.boundlesshorizons.world.worldGen.typeSelector.DeadlandsWorldTypeSelector
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

@Mod(WorldConfig.MOD_ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object BoundlessHorizonsCore {
    init {
        DeadlandsPackInjector.init()
        DeadlandsWorldTypeSelector.init()
        
        runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)
                "test"
            }
        )
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
    }

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
    }
}

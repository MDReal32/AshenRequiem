package com.mdreal.boundlesshorizons.world.init

import com.mdreal.boundlesshorizons.world.WorldConfig
import net.minecraft.core.MappedRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister



object CreativeTab {
    val CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WorldConfig.MOD_ID)

    val WORLD_CREATIVE_TAB = CREATIVE_TABS.register("world") { _: ResourceLocation ->
        CreativeModeTab.builder()
            .title(Component.translatable("creative_tab.${WorldConfig.MOD_ID}.world"))
            .icon { ItemStack(Blocks.ASHEN_GRASS.item.get()) }
            .displayItems { _, output ->
                Blocks.allBlocks.forEach { block ->
                    output.accept(ItemStack(block.item.get()))
                }
            }
            .build()
    }

    fun hook(bus: IEventBus) {
        CREATIVE_TABS.register(bus)
    }
}

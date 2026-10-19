package com.mdreal.ashenrequiem.world.init

import com.mdreal.ashenrequiem.world.WorldConfig
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object Items {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, WorldConfig.MOD_ID)

    fun hook(bus: IEventBus) {
        ITEMS.register(bus)
    }
}
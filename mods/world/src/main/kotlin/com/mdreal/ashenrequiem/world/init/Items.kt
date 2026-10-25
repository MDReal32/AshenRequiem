package com.mdreal.ashenrequiem.world.init

import com.mdreal.ashenrequiem.world.WorldConfig
import com.mdreal.ashenrequiem.world.item.ScrapKnifeItem
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister

object Items {
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(WorldConfig.MOD_ID)

    /** Bent metal and broken parts from the rubble. The first-hour material (ROADMAP D11). */
    val SCRAP: DeferredItem<Item> = ITEMS.registerSimpleItem("scrap")
    val SCRAP_KNIFE: DeferredItem<ScrapKnifeItem> =
        ITEMS.registerItem("scrap_knife", ::ScrapKnifeItem, Item.Properties().durability(48))

    /** Items that aren't block items, in creative tab order. */
    val allItems = listOf(SCRAP, SCRAP_KNIFE)

    fun hook(bus: IEventBus) {
        ITEMS.register(bus)
    }
}
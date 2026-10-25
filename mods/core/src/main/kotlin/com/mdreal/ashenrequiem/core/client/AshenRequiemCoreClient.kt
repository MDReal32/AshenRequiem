package com.mdreal.ashenrequiem.core.client

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mdreal.ashenrequiem.core.radiation.ClientRadiation
import com.mdreal.ashenrequiem.core.radiation.Radiation
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

@Mod(value = CoreConfig.MOD_ID, dist = [Dist.CLIENT])
class AshenRequiemCoreClient(modBus: IEventBus) {
    init {
        modBus.addListener<RegisterGuiLayersEvent> { it.registerAbove(VanillaGuiLayers.FOOD_LEVEL, RadiationHud.ID, RadiationHud::render) }
        NeoForge.EVENT_BUS.addListener<ItemTooltipEvent> {
            if (Radiation.strength(it.itemStack) > 0f) it.toolTip.add(1.coerceAtMost(it.toolTip.size), RADIATED)
        }
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> { ClientRadiation.tenths = 0 }
    }

    private companion object {
        val RADIATED: Component = Component.translatable("tooltip.${CoreConfig.MOD_ID}.radiated").withStyle(ChatFormatting.GREEN)
    }
}

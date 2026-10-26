package com.mdreal.ashenrequiem.core.compat.jade

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mdreal.ashenrequiem.core.radiation.Radiation
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Block
import snownee.jade.api.BlockAccessor
import snownee.jade.api.EntityAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.IEntityComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig

/**
 * Core's Jade plugin. Jade finds it on its own and only loads it when Jade is installed. It shows the radiation a
 * looked-at block or dropped stack gives off, in units of the HUD bar, from the synced data maps.
 */
@WailaPlugin
class CoreJadePlugin : IWailaPlugin {
    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(RadiatedBlock, Block::class.java)
        registration.registerEntityComponent(RadiatedItem, ItemEntity::class.java)
    }
}

private val UID: ResourceLocation = CoreConfig.id("radiation")

/** What a block gives off right next to it. */
private object RadiatedBlock : IBlockComponentProvider {
    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val strength = Radiation.strength(accessor.blockState)
        if (strength > 0f) tooltip.add(line("radiation", Radiation.unitsText(strength)))
    }

    override fun getUid() = UID
}

/** What a dropped stack adds when carried, and per item. */
private object RadiatedItem : IEntityComponentProvider {
    override fun appendTooltip(tooltip: ITooltip, accessor: EntityAccessor, config: IPluginConfig) {
        val stack = (accessor.entity as? ItemEntity)?.item ?: return
        val strength = Radiation.strength(stack)
        if (strength <= 0f) return
        tooltip.add(
            if (stack.count == 1) line("radiation", Radiation.unitsText(strength))
            else line("radiation_each", Radiation.unitsText(strength * stack.count), Radiation.unitsText(strength))
        )
    }

    override fun getUid() = UID
}

private fun line(key: String, vararg args: Any): Component =
    Component.translatable("jade.${CoreConfig.MOD_ID}.$key", *args).withStyle(ChatFormatting.GREEN)


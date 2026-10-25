package com.mdreal.ashenrequiem.core

import com.mdreal.ashenrequiem.core.command.ArCommand
import com.mdreal.ashenrequiem.core.compat.jer.LootTableSync
import com.mdreal.ashenrequiem.core.config.CoreCommonConfig
import com.mdreal.ashenrequiem.core.network.Network
import com.mdreal.ashenrequiem.core.radiation.Radiation
import com.mdreal.ashenrequiem.core.recipe.RecipeGates
import com.mdreal.ashenrequiem.core.state.Attachments
import com.mdreal.ashenrequiem.core.state.ProgressSync
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge

@Mod(CoreConfig.MOD_ID)
class AshenRequiemCore(container: ModContainer, modBus: IEventBus) {
    init {
        container.registerConfig(ModConfig.Type.COMMON, CoreCommonConfig.getSpec(), "ashen-requiem/core.toml")
        Attachments.hook(modBus)
        Network.hook(modBus)
        ProgressSync.hook(NeoForge.EVENT_BUS)
        ArCommand.hook(NeoForge.EVENT_BUS)
        RecipeGates.hook(NeoForge.EVENT_BUS)
        LootTableSync.hook(NeoForge.EVENT_BUS)
        Radiation.hook(modBus, NeoForge.EVENT_BUS)
    }
}

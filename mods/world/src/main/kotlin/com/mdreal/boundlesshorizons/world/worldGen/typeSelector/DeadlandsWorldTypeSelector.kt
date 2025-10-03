package com.mdreal.boundlesshorizons.world.worldGen.typeSelector

import com.mdreal.boundlesshorizons.world.WorldConfig
import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.presets.WorldPreset
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

object DeadlandsWorldTypeSelector {
    private val DEADLANDS_KEY: ResourceKey<WorldPreset> =
        ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands"))

    fun init() {
        if (FMLEnvironment.dist.isClient) NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onScreenInit(e: ScreenEvent.Init.Post) {
        if (!WorldCommonConfig.forceDeadlandsOnly) return
        val screen = e.screen as? CreateWorldScreen ?: return

        val key = ResourceKey.create(
            Registries.WORLD_PRESET,
            ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands")
        )

        // only when we have a connection (title screen has none)
        val reg = Minecraft.getInstance().connection
            ?.registryAccess()
            ?.registryOrThrow(Registries.WORLD_PRESET)

        if (reg == null || reg.containsKey(key.location())) {
            screen.uiState.setWorldType(key)
        }
    }
}
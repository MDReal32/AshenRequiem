package com.mdreal.boundlesshorizons.world.worldGen.injector

import com.mdreal.boundlesshorizons.world.WorldConfig
import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import net.minecraft.server.packs.PackType
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddPackFindersEvent
import java.util.Optional

object DeadlandsPackInjector {
    fun init() {
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onAddPackFinders(event: AddPackFindersEvent) {
        if (event.packType != PackType.SERVER_DATA) return
        if (!WorldCommonConfig.forceDeadlandsOnly) return

        val root = ModList.get()
            .getModFileById(WorldConfig.MOD_ID)
            .file
            .findResource("force_deadlands_only")

        val info = PackLocationInfo(
            "${WorldConfig.MOD_ID}:force_deadlands_only",
            Component.literal("Deadlands Only (Built-in)"),
            PackSource.BUILT_IN,
            Optional.empty()
        )


        val supplier = object : Pack.ResourcesSupplier {
            override fun openPrimary(location: PackLocationInfo): PackResources {
                return PathPackResources(location, root)
            }

            override fun openFull(location: PackLocationInfo, meta: Pack.Metadata): PackResources {
                return PathPackResources(location, root)
            }
        }
        
        val selection = PackSelectionConfig(
            false,
            Pack.Position.TOP,
            false
        )


        val source = RepositorySource { accept ->
            val pack = Pack.readMetaAndCreate(
                info,
                supplier,
                event.packType,
                selection
            )

            if (pack != null) accept.accept(pack)
        }
        event.addRepositorySource(source)
    }
}
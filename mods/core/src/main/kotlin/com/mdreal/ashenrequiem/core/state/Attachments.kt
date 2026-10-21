package com.mdreal.ashenrequiem.core.state

import com.mdreal.ashenrequiem.core.CoreConfig
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

object Attachments {
    val ATTACHMENT_TYPES: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CoreConfig.MOD_ID)

    val PLAYER_PROGRESS: DeferredHolder<AttachmentType<*>, AttachmentType<PlayerProgress>> =
        ATTACHMENT_TYPES.register("player_progress") { _: ResourceLocation ->
            AttachmentType.builder(Supplier { PlayerProgress() })
                .serialize(PlayerProgress.CODEC)
                .copyOnDeath()
                .build()
        }

    fun hook(bus: IEventBus) {
        ATTACHMENT_TYPES.register(bus)
    }
}

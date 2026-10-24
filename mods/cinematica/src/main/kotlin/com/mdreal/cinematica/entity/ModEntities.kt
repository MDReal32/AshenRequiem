package com.mdreal.cinematica.entity

import com.mdreal.cinematica.CinematicaStudio
import com.mdreal.cinematica.timeline.ActorState
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object ModEntities {
    private val ENTITY_TYPES: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(Registries.ENTITY_TYPE, CinematicaStudio.MOD_ID)

    val ACTOR: DeferredHolder<EntityType<*>, EntityType<ActorEntity>> = ENTITY_TYPES.register("actor") { _ ->
        EntityType.Builder.of(::ActorEntity, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .eyeHeight(ActorState.EYE_HEIGHT.toFloat())
            .noSave()
            .noSummon()
            .clientTrackingRange(8)
            .build("actor")
    }

    fun hook(bus: IEventBus) {
        ENTITY_TYPES.register(bus)
    }
}

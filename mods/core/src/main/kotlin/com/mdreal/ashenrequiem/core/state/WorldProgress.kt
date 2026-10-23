package com.mdreal.ashenrequiem.core.state

import net.minecraft.core.GlobalPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/** World-global story state, saved with the Overworld. Mutate it only through the API. */
class WorldProgress : SavedData() {
    var hushStage: Int = 0
        internal set
    var gateRepaired: Boolean = false
        internal set
    var finaleWon: Boolean = false
        internal set

    /** Named key locations (Cradle, Vigil Station, the Wound, ...). */
    internal val locations: MutableMap<String, GlobalPos> = mutableMapOf()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putInt("hush_stage", hushStage)
        tag.putBoolean("gate_repaired", gateRepaired)
        tag.putBoolean("finale_won", finaleWon)
        val locs = CompoundTag()
        locations.forEach { (key, pos) ->
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().ifPresent { locs.put(key, it) }
        }
        tag.put("locations", locs)
        return tag
    }

    companion object {
        private const val NAME = "ashen_requiem_progress"

        private val FACTORY = SavedData.Factory(::WorldProgress, ::load, null)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): WorldProgress =
            WorldProgress().apply {
                hushStage = tag.getInt("hush_stage")
                gateRepaired = tag.getBoolean("gate_repaired")
                finaleWon = tag.getBoolean("finale_won")
                val locs = tag.getCompound("locations")
                locs.allKeys.forEach { key ->
                    GlobalPos.CODEC.parse(NbtOps.INSTANCE, locs.get(key)).result().ifPresent { locations[key] = it }
                }
            }

        fun get(server: MinecraftServer): WorldProgress =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}

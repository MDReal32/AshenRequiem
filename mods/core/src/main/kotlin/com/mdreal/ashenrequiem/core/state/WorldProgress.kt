package com.mdreal.ashenrequiem.core.state

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.GlobalPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

/** World-global story state, saved with the Overworld. Mutate it only through the API. */
class WorldProgress() : SavedData() {
    var hushStage: Int = 0
        internal set
    var gateRepaired: Boolean = false
        internal set
    var finaleWon: Boolean = false
        internal set

    /** Named key locations (Cradle, Vigil Station, the Wound, ...). */
    internal val locations: MutableMap<String, GlobalPos> = mutableMapOf()

    private constructor(hushStage: Int, gateRepaired: Boolean, finaleWon: Boolean, locations: Map<String, GlobalPos>) : this() {
        this.hushStage = hushStage
        this.gateRepaired = gateRepaired
        this.finaleWon = finaleWon
        this.locations.putAll(locations)
    }

    companion object {
        private val CODEC: Codec<WorldProgress> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.INT.optionalFieldOf("hush_stage", 0).forGetter(WorldProgress::hushStage),
                Codec.BOOL.optionalFieldOf("gate_repaired", false).forGetter(WorldProgress::gateRepaired),
                Codec.BOOL.optionalFieldOf("finale_won", false).forGetter(WorldProgress::finaleWon),
                Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC)
                    .optionalFieldOf("locations", emptyMap()).forGetter { it.locations },
            ).apply(i) { hush, gate, finale, locs -> WorldProgress(hush, gate, finale, locs) }
        }

        private val TYPE = SavedDataType(CoreConfig.id("progress"), ::WorldProgress, CODEC)

        fun get(server: MinecraftServer): WorldProgress =
            server.overworld().dataStorage.computeIfAbsent(TYPE)
    }
}

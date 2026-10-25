package com.mdreal.ashenrequiem.core.recipe

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.mdreal.ashenrequiem.core.api.AshenRequiem
import com.mojang.logging.LogUtils
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.AddReloadListenerEvent
import java.util.Optional

/**
 * Recipe gates (ROADMAP D11): data files under `data/<namespace>/ashen_requiem/recipe_gates/` that block crafting
 * recipes for a player, by the player's level or in the 2×2 inventory grid.
 *
 * The mixins mark the player while a crafting grid looks up its recipe (vanilla's grid and FastWorkbench's), and the
 * lookup skips the recipes that player can't use, so the next matching recipe wins. Machines have no player and are
 * never gated.
 */
object RecipeGates {
    private val LOGGER = LogUtils.getLogger()

    /** Blocks [recipes] while the player's level is below [untilLevel] (always, if empty); only in the 2×2 grid if [workbenchOnly]. */
    private data class Gate(val recipes: List<ResourceLocation>, val untilLevel: Optional<Int>, val workbenchOnly: Boolean)

    private val GATE_CODEC: Codec<Gate> = RecordCodecBuilder.create { i ->
        i.group(
            ResourceLocation.CODEC.listOf().fieldOf("recipes").forGetter(Gate::recipes),
            Codec.INT.optionalFieldOf("until_level").forGetter(Gate::untilLevel),
            Codec.BOOL.optionalFieldOf("workbench_only", false).forGetter(Gate::workbenchOnly),
        ).apply(i, ::Gate)
    }

    @Volatile
    private var gates: Map<ResourceLocation, List<Gate>> = emptyMap()

    private val crafter = ThreadLocal<Player?>()

    fun hook(bus: IEventBus) {
        bus.addListener<AddReloadListenerEvent> { it.addListener(Loader) }
    }

    @JvmStatic
    fun begin(player: Player) = crafter.set(player)

    @JvmStatic
    fun end() = crafter.remove()

    fun isBlocked(recipe: ResourceLocation, player: ServerPlayer, menu: AbstractContainerMenu): Boolean {
        val forRecipe = gates[recipe] ?: return false
        val level = AshenRequiem.getLevel(player)
        return forRecipe.any { gate ->
            gate.untilLevel.map { level < it }.orElse(true) && (!gate.workbenchOnly || menu is InventoryMenu)
        }
    }

    /**
     * Stands in for `RecipeManager.getRecipeFor` while a player's crafting grid is being checked.
     * Returns null when there's no crafting player or nothing is gated, so the vanilla lookup runs.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun find(
        manager: RecipeManager, type: RecipeType<*>, input: RecipeInput, level: Level, last: RecipeHolder<*>?
    ): Optional<RecipeHolder<*>>? {
        val player = crafter.get() as? ServerPlayer ?: return null
        if (gates.isEmpty()) return null
        if (input.isEmpty) return Optional.empty()
        val menu = player.containerMenu
        fun usable(holder: RecipeHolder<*>) =
            !isBlocked(holder.id, player, menu) && (holder.value as Recipe<RecipeInput>).matches(input, level)
        if (last != null && usable(last)) return Optional.of(last)
        val all = manager.getAllRecipesFor(type as RecipeType<Recipe<RecipeInput>>)
        return Optional.ofNullable(all.firstOrNull(::usable))
    }

    private object Loader : SimpleJsonResourceReloadListener(Gson(), "ashen_requiem/recipe_gates") {
        override fun apply(files: Map<ResourceLocation, JsonElement>, manager: ResourceManager, profiler: ProfilerFiller) {
            val loaded = mutableMapOf<ResourceLocation, MutableList<Gate>>()
            files.forEach { (file, json) ->
                GATE_CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial { LOGGER.error("Recipe gate {} is invalid: {}", file, it) }
                    .ifPresent { gate -> gate.recipes.forEach { loaded.getOrPut(it) { mutableListOf() } += gate } }
            }
            gates = loaded
            LOGGER.info("Loaded {} recipe gate file(s) covering {} recipe(s)", files.size, loaded.size)
        }
    }
}

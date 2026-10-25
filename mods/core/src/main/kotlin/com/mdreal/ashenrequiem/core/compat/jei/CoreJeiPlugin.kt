package com.mdreal.ashenrequiem.core.compat.jei

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mdreal.ashenrequiem.core.network.ClientProgress
import com.mdreal.ashenrequiem.core.recipe.ClientRecipeGates
import com.mdreal.ashenrequiem.core.recipe.RecipeGates
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.gui.builder.ITooltipBuilder
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator
import mezz.jei.api.registration.IAdvancedRegistration
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeHolder

/**
 * Core's JEI plugin. JEI finds it (and every other mod's `@JeiPlugin`) on its own and only loads it when JEI is
 * installed. Core's part is the recipe gates; [BlockInteractionCategory] is here for the other mods' plugins.
 */
@JeiPlugin
class CoreJeiPlugin : IModPlugin {
    override fun getPluginUid(): ResourceLocation = CoreConfig.id("jei")

    override fun registerAdvanced(registration: IAdvancedRegistration) {
        registration.addRecipeCategoryDecorator(RecipeTypes.CRAFTING, RecipeGateDecorator)
    }
}

/**
 * Marks gated crafting recipes under their output with what the gate still asks of this player: red when it stops
 * them everywhere, grey when it only rules out the 2×2 inventory grid. Hovering the mark tells the whole gate.
 */
private object RecipeGateDecorator : IRecipeCategoryDecorator<RecipeHolder<CraftingRecipe>> {
    private const val KEY = "jei.${CoreConfig.MOD_ID}.gate"
    private const val LOCKED_COLOR = 0xFFB02020.toInt()
    private const val WORKBENCH_COLOR = 0xFF404040.toInt()

    override fun draw(
        recipe: RecipeHolder<CraftingRecipe>, category: IRecipeCategory<RecipeHolder<CraftingRecipe>>,
        recipeSlotsView: IRecipeSlotsView, guiGraphics: GuiGraphics, mouseX: Double, mouseY: Double,
    ) {
        val (text, color) = mark(recipe) ?: return
        val font = Minecraft.getInstance().font
        guiGraphics.drawString(font, text, category.width - font.width(text), category.height - font.lineHeight, color, false)
    }

    override fun decorateTooltips(
        tooltip: ITooltipBuilder, recipe: RecipeHolder<CraftingRecipe>, category: IRecipeCategory<RecipeHolder<CraftingRecipe>>,
        recipeSlotsView: IRecipeSlotsView, mouseX: Double, mouseY: Double,
    ) {
        val (text, _) = mark(recipe) ?: return
        val font = Minecraft.getInstance().font
        if (mouseX < category.width - font.width(text) || mouseY < category.height - font.lineHeight) return
        gates(recipe).forEach { gate -> tooltip.add(explain(gate)) }
    }

    private fun gates(recipe: RecipeHolder<CraftingRecipe>) = ClientRecipeGates.byRecipe[recipe.id].orEmpty()

    /** The mark for [recipe], or null when no gate stops this player any more. */
    private fun mark(recipe: RecipeHolder<CraftingRecipe>): Pair<Component, Int>? {
        val level = ClientProgress.current.level
        val gates = gates(recipe)
        gates.firstOrNull { it.blocks(level, inInventoryGrid = false) }?.let { gate ->
            val text = gate.untilLevel.map { Component.translatable("$KEY.level", it) }.orElse(Component.translatable("$KEY.locked"))
            return text to LOCKED_COLOR
        }
        if (gates.any { it.blocks(level, inInventoryGrid = true) }) return Component.translatable("$KEY.workbench") to WORKBENCH_COLOR
        return null
    }

    private fun explain(gate: RecipeGates.Gate): Component {
        val until = gate.untilLevel.orElse(null)
        return when {
            until == null && gate.workbenchOnly -> Component.translatable("$KEY.workbench.always")
            until == null -> Component.translatable("$KEY.locked.always")
            gate.workbenchOnly -> Component.translatable("$KEY.workbench.until", until)
            else -> Component.translatable("$KEY.level.until", until)
        }
    }
}

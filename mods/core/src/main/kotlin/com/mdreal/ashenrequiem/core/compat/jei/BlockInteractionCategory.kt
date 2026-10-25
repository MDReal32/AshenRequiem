package com.mdreal.ashenrequiem.core.compat.jei

import com.mdreal.ashenrequiem.core.CoreConfig
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * A JEI tab for [BlockInteraction]s: the held items, a plus, the block, an arrow and what the block becomes. Any
 * Ashen Requiem mod can give its own in-world recipes a tab with one of these.
 */
open class BlockInteractionCategory(
    private val type: RecipeType<BlockInteraction>,
    private val title: Component,
    private val icon: IDrawable,
) : IRecipeCategory<BlockInteraction> {
    override fun getRecipeType() = type
    override fun getTitle() = title
    override fun getIcon() = icon
    override fun getWidth() = WIDTH
    override fun getHeight() = HEIGHT

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: BlockInteraction, focuses: IFocusGroup) {
        val x = left(recipe)
        recipe.held.forEachIndexed { i, held ->
            builder.addInputSlot(x + i * SLOT, ROW_Y).setStandardSlotBackground().addItemStacks(held.items.toList())
                .addRichTooltipCallback { _, tooltip -> tooltip.add(USE_ON_BLOCK) }
        }
        val blockX = x + recipe.held.size * SLOT + PLUS_WIDTH
        builder.addInputSlot(blockX, ROW_Y).setStandardSlotBackground().addItemStack(recipe.block)
            .addRichTooltipCallback { _, tooltip -> tooltip.add(IN_WORLD) }
        builder.addOutputSlot(blockX + SLOT + ARROW_WIDTH, ROW_Y).setOutputSlotBackground().addItemStack(recipe.result)
            .addRichTooltipCallback { _, tooltip -> tooltip.add(IN_WORLD) }
    }

    override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: BlockInteraction, focuses: IFocusGroup) {
        val blockX = left(recipe) + recipe.held.size * SLOT + PLUS_WIDTH
        builder.addRecipePlusSign().setPosition(blockX - PLUS_WIDTH + 1, ROW_Y + 2)
        builder.addRecipeArrow().setPosition(blockX + SLOT + 3, ROW_Y)
    }

    /** Where the row starts, so it sits centred whatever the number of held items. */
    private fun left(recipe: BlockInteraction): Int {
        val row = recipe.held.size * SLOT + PLUS_WIDTH + SLOT + ARROW_WIDTH + OUTPUT_SLOT
        return (WIDTH - row) / 2
    }

    companion object {
        const val WIDTH = 150
        const val HEIGHT = 28
        private const val SLOT = 18
        private const val OUTPUT_SLOT = 26
        private const val PLUS_WIDTH = 16
        private const val ARROW_WIDTH = 34
        private const val ROW_Y = 5

        private val IN_WORLD = Component.translatable("jei.${CoreConfig.MOD_ID}.in_world").withStyle(ChatFormatting.GRAY)
        private val USE_ON_BLOCK = Component.translatable("jei.${CoreConfig.MOD_ID}.use_on_block").withStyle(ChatFormatting.GRAY)
    }
}

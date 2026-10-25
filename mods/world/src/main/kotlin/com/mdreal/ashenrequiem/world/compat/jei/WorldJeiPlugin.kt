package com.mdreal.ashenrequiem.world.compat.jei

import com.mdreal.ashenrequiem.core.compat.jei.BlockInteraction
import com.mdreal.ashenrequiem.core.compat.jei.BlockInteractionCategory
import com.mdreal.ashenrequiem.world.WorldConfig
import com.mdreal.ashenrequiem.world.block.BrokenWorkbenchBlock
import com.mdreal.ashenrequiem.world.block.WorkbenchMenu
import com.mdreal.ashenrequiem.world.init.Blocks
import com.mdreal.ashenrequiem.world.init.Items
import com.mdreal.ashenrequiem.world.item.ScrapKnifeItem
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.registration.IRecipeTransferRegistration
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.neoforged.neoforge.common.crafting.SizedIngredient

/**
 * The world mod's JEI pages: the scrap knife's scraping and the broken workbench's repair, which happen in the world
 * rather than in a grid; the workbench as a crafting table; and how to come by the Dead Lands' first materials.
 * JEI finds this on its own and only loads it when JEI is installed.
 */
@JeiPlugin
class WorldJeiPlugin : IModPlugin {
    override fun getPluginUid(): ResourceLocation = id("jei")

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val gui = registration.jeiHelpers.guiHelper
        registration.addRecipeCategories(
            BlockInteractionCategory(SCRAPING, Component.translatable("$KEY.scraping"), gui.createDrawableItemLike(Items.SCRAP_KNIFE)),
            BlockInteractionCategory(
                REPAIRING, Component.translatable("$KEY.repairing"), gui.createDrawableItemLike(Blocks.BROKEN_WORKBENCH.item.get())
            ),
        )
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        val knife = SizedIngredient(Ingredient.of(Items.SCRAP_KNIFE), 1)
        registration.addRecipes(SCRAPING, ScrapKnifeItem.SCRAPES.map { (log, scraped) ->
            BlockInteraction(listOf(knife), ItemStack(log), ItemStack(scraped), Component.translatable("$KEY.scraping.note"))
        })

        val needed = BrokenWorkbenchBlock.NEEDED
        registration.addRecipes(REPAIRING, listOf(
            BlockInteraction(
                listOf(
                    SizedIngredient(Ingredient.of(BrokenWorkbenchBlock.SCRAPED_LOGS), needed),
                    SizedIngredient(Ingredient.of(Items.SCRAP), needed),
                ),
                ItemStack(Blocks.BROKEN_WORKBENCH.item.get()), ItemStack(Blocks.WORKBENCH.item.get()),
                Component.translatable("$KEY.repairing.note"),
            )
        ))

        registration.addIngredientInfo(Blocks.RUBBLE.item.get(), Component.translatable("$KEY.info.rubble"))
        registration.addIngredientInfo(Items.SCRAP, Component.translatable("$KEY.info.scrap"))
        registration.addIngredientInfo(Items.SCRAP_KNIFE, Component.translatable("$KEY.info.scrap_knife"))
        registration.addItemStackInfo(
            listOf(ItemStack(Blocks.DEAD_LOG.item.get()), ItemStack(Blocks.DEAD_WOOD.item.get())), Component.translatable("$KEY.info.dead_log")
        )
        registration.addIngredientInfo(
            Blocks.BROKEN_WORKBENCH.item.get(), Component.translatable("$KEY.info.broken_workbench", needed, needed)
        )
        registration.addIngredientInfo(Blocks.WORKBENCH.item.get(), Component.translatable("$KEY.info.workbench"))
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        registration.addRecipeCatalyst(Items.SCRAP_KNIFE, SCRAPING)
        registration.addRecipeCatalyst(Blocks.BROKEN_WORKBENCH.item.get(), REPAIRING)
        registration.addRecipeCatalyst(Blocks.WORKBENCH.item.get(), RecipeTypes.CRAFTING)
    }

    override fun registerRecipeTransferHandlers(registration: IRecipeTransferRegistration) {
        // JEI matches menus by their exact class, so the workbench's crafting menu needs its own "+" handler.
        registration.addRecipeTransferHandler(WorkbenchMenu::class.java, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1, 9, 10, 36)
    }

    companion object {
        private const val KEY = "jei.${WorldConfig.MOD_ID}"

        val SCRAPING: RecipeType<BlockInteraction> = RecipeType(id("scraping"), BlockInteraction::class.java)
        val REPAIRING: RecipeType<BlockInteraction> = RecipeType(id("repairing"), BlockInteraction::class.java)

        private fun id(path: String) = ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, path)
    }
}

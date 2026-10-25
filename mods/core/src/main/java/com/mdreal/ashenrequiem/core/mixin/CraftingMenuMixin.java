package com.mdreal.ashenrequiem.core.mixin;

import com.mdreal.ashenrequiem.core.recipe.RecipeGates;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the crafting player while vanilla's crafting grid (2×2 and 3×3) looks up its recipe. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"))
    private static void ashenRequiem$begin(
        AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots, ResultContainer resultSlots,
        @Nullable RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci
    ) {
        RecipeGates.begin(player);
    }

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void ashenRequiem$end(
        AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots, ResultContainer resultSlots,
        @Nullable RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci
    ) {
        RecipeGates.end();
    }
}

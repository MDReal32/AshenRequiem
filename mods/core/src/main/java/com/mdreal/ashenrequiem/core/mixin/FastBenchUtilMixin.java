package com.mdreal.ashenrequiem.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mdreal.ashenrequiem.core.recipe.RecipeGates;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FastWorkbench replaces vanilla's crafting grid update with its own, run on the next server tick, so the player is
 * marked there too. Skipped when FastWorkbench isn't installed.
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.fastbench.util.FastBenchUtil")
public abstract class FastBenchUtilMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), require = 0)
    private static void ashenRequiem$begin(CallbackInfo ci, @Local(argsOnly = true) Player player) {
        RecipeGates.begin(player);
    }

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"), require = 0)
    private static void ashenRequiem$end(CallbackInfo ci) {
        RecipeGates.end();
    }
}

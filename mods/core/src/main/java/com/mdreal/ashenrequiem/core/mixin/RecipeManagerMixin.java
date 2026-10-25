package com.mdreal.ashenrequiem.core.mixin;

import com.mdreal.ashenrequiem.core.recipe.RecipeGates;
import java.util.Optional;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips gated recipes while a player's crafting grid looks up its result (see RecipeGates). */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(
        method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ashenRequiem$gate(
        RecipeType type, RecipeInput input, Level level, @Nullable RecipeHolder last, CallbackInfoReturnable<Optional> cir
    ) {
        Optional<RecipeHolder<?>> gated = RecipeGates.find((RecipeManager) (Object) this, type, input, level, last);
        if (gated != null) cir.setReturnValue(gated);
    }
}

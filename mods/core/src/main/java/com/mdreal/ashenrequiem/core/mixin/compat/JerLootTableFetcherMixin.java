package com.mdreal.ashenrequiem.core.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mdreal.ashenrequiem.core.compat.jer.ClientLootTables;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * On a server JER has no loot tables and gets an empty one back for everything; it gets the server's instead, sent by
 * {@code LootTableSync}. Skipped when JER isn't installed.
 */
@Pseudo
@Mixin(targets = "jeresources.util.LootTableFetcher")
public abstract class JerLootTableFetcherMixin {
    @ModifyReturnValue(method = "getLootTable", at = @At("RETURN"), require = 0)
    private LootTable ashenRequiem$serverTable(LootTable original, ResourceKey<LootTable> key) {
        if (original != LootTable.EMPTY) return original;
        LootTable synced = ClientLootTables.get(key);
        return synced != null ? synced : original;
    }
}

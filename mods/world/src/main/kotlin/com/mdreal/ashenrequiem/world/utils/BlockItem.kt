package com.mdreal.ashenrequiem.world.utils

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredHolder

data class BlockWithItem(
    val block: DeferredHolder<Block, out Block>,
    val item: DeferredHolder<Item, out BlockItem>
)
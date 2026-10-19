package com.mdreal.ashenrequiem.world.utils

import com.mdreal.ashenrequiem.world.init.Blocks
import com.mdreal.ashenrequiem.world.init.Items
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

inline fun registerBlockWithItem(
    name: String,
    noinline blockSupplier: (BlockBehaviour.Properties) -> BlockBehaviour.Properties,
    crossinline itemProps: () -> Item.Properties = { Item.Properties() }
): BlockWithItem {
    val block = Blocks.BLOCKS.register(name) { _: ResourceLocation -> Block(blockSupplier(BlockBehaviour.Properties.of())) }
    val item  = Items.ITEMS.register(name) { _: ResourceLocation -> BlockItem(block.get(), itemProps()) }
    val pair  = BlockWithItem(block, item)
    Blocks.allBlocks.add(pair)
    return pair
}
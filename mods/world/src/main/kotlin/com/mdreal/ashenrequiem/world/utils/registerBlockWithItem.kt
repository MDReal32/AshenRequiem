package com.mdreal.ashenrequiem.world.utils

import com.mdreal.ashenrequiem.world.init.Blocks
import com.mdreal.ashenrequiem.world.init.Items
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

/** Registers a block and its item. The register helpers set the registry ids that 1.21.2+ requires in properties. */
fun registerBlockWithItem(
    name: String,
    blockProps: (BlockBehaviour.Properties) -> BlockBehaviour.Properties,
    itemProps: (Item.Properties) -> Item.Properties = { it },
    factory: (BlockBehaviour.Properties) -> Block = ::Block
): BlockWithItem {
    val block = Blocks.BLOCKS.registerBlock(name, factory, blockProps)
    val item  = Items.ITEMS.registerSimpleBlockItem(name, block, itemProps)
    val pair  = BlockWithItem(block, item)
    Blocks.allBlocks.add(pair)
    return pair
}

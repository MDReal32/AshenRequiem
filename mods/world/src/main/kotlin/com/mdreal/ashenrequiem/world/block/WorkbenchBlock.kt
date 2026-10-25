package com.mdreal.ashenrequiem.world.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * The Dead Lands' crafting table: the Cradle's workbench once it's repaired, and the only 3×3 grid before level 2
 * (ROADMAP D11). Works exactly like a crafting table.
 */
class WorkbenchBlock(props: Properties) : CraftingTableBlock(props) {
    override fun codec(): MapCodec<out CraftingTableBlock> = CODEC

    override fun getMenuProvider(state: BlockState, level: Level, pos: BlockPos): MenuProvider =
        SimpleMenuProvider({ id, inventory, _ -> WorkbenchMenu(id, inventory, ContainerLevelAccess.create(level, pos), this) }, TITLE)

    companion object {
        val CODEC: MapCodec<WorkbenchBlock> = simpleCodec(::WorkbenchBlock)
        private val TITLE = Component.translatable("container.crafting")
    }
}

/** Vanilla's crafting menu only stays open at a vanilla crafting table, so this one checks for its own block. */
class WorkbenchMenu(id: Int, inventory: Inventory, private val access: ContainerLevelAccess, private val block: Block) :
    CraftingMenu(id, inventory, access) {
    override fun stillValid(player: Player) = stillValid(access, player, block)
}

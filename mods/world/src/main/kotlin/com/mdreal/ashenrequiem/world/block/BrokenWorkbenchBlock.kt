package com.mdreal.ashenrequiem.world.block

import com.mdreal.ashenrequiem.world.WorldConfig
import com.mdreal.ashenrequiem.world.init.Blocks
import com.mdreal.ashenrequiem.world.init.Items
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * The Cradle's workbench, broken (ROADMAP D11). Using scraped logs and scrap on it repairs it into a [WorkbenchBlock].
 */
class BrokenWorkbenchBlock(props: Properties) : Block(props) {
    init {
        registerDefaultState(stateDefinition.any().setValue(LOGS, 0).setValue(SCRAP, 0))
    }

    override fun codec(): MapCodec<out Block> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LOGS, SCRAP)
    }

    override fun useItemOn(
        stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult
    ): ItemInteractionResult {
        val part = when {
            stack.`is`(SCRAPED_LOGS) -> LOGS
            stack.`is`(Items.SCRAP.get()) -> SCRAP
            else -> return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        if (state.getValue(part) >= NEEDED) {
            tellMissing(player, state)
            return ItemInteractionResult.CONSUME
        }
        stack.consume(1, player)
        val next = state.setValue(part, state.getValue(part) + 1)
        if (next.getValue(LOGS) == NEEDED && next.getValue(SCRAP) == NEEDED) {
            level.setBlockAndUpdate(pos, Blocks.WORKBENCH.block.get().defaultBlockState())
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state))
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f)
            player.displayClientMessage(Component.translatable("$KEY.repaired"), true)
        } else {
            level.setBlockAndUpdate(pos, next)
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.8f)
            tellMissing(player, next)
        }
        return ItemInteractionResult.SUCCESS
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide) tellMissing(player, state)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    private fun tellMissing(player: Player, state: BlockState) {
        player.displayClientMessage(
            Component.translatable("$KEY.missing", NEEDED - state.getValue(LOGS), NEEDED - state.getValue(SCRAP)), true
        )
    }

    companion object {
        val CODEC: MapCodec<BrokenWorkbenchBlock> = simpleCodec(::BrokenWorkbenchBlock)

        /** How many scraped logs, and how many scrap, the repair takes. */
        const val NEEDED = 2
        val LOGS: IntegerProperty = IntegerProperty.create("logs", 0, NEEDED)
        val SCRAP: IntegerProperty = IntegerProperty.create("scrap", 0, NEEDED)

        val SCRAPED_LOGS: TagKey<Item> =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "scraped_logs"))

        private const val KEY = "block.${WorldConfig.MOD_ID}.broken_workbench"
    }
}

package com.mdreal.ashenrequiem.world.item

import com.mdreal.ashenrequiem.world.init.Blocks
import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.gameevent.GameEvent

/**
 * A sharpened bit of scrap on a stick. Scrapes the dead, radiated bark off a dead log, leaving the only wood the
 * Dead Lands can still work (ROADMAP D11).
 */
class ScrapKnifeItem(props: Properties) : Item(props) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val state = level.getBlockState(pos)
        val scraped = when (state.block) {
            Blocks.DEAD_LOG.block.get() -> Blocks.SCRAPED_LOG.block.get()
            Blocks.DEAD_WOOD.block.get() -> Blocks.SCRAPED_WOOD.block.get()
            else -> return InteractionResult.PASS
        }
        val player = context.player
        level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1f, 1.2f)
        if (!level.isClientSide) {
            val next = scraped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS))
            level.setBlock(pos, next, Block.UPDATE_ALL_IMMEDIATE)
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, next))
            if (player is ServerPlayer) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, pos, context.itemInHand)
            player?.let { context.itemInHand.hurtAndBreak(1, it, LivingEntity.getSlotForHand(context.hand)) }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}

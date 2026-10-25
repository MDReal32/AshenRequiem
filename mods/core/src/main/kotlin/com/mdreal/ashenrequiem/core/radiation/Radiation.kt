package com.mdreal.ashenrequiem.core.radiation

import com.mdreal.ashenrequiem.core.config.CoreCommonConfig
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Radiation (D12): a live reading of what a player carries and stands near, recalculated every
 * [CoreCommonConfig.radiationInterval] ticks. Nothing builds up; drop the items or walk away and it falls at once.
 * Strengths come from the `ashen_requiem:radiation` data maps, so any mod, datapack or KubeJS script can add sources.
 */
object Radiation {
    private val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("ashen_requiem", "radiation")
    private val STRENGTH: Codec<Float> = Codec.floatRange(0f, Float.MAX_VALUE)

    /** Points each carried item adds (per item, not per stack). */
    val ITEMS: DataMapType<Item, Float> = DataMapType.builder(ID, Registries.ITEM, STRENGTH).synced(STRENGTH, false).build()

    /** Points a block adds when the player is right next to it, falling off to nothing at the radius. */
    val BLOCKS: DataMapType<Block, Float> = DataMapType.builder(ID, Registries.BLOCK, STRENGTH).synced(STRENGTH, false).build()

    val DAMAGE: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE, ID)

    private val readings = HashMap<UUID, Float>()
    private val sent = HashMap<UUID, Int>()

    fun hook(modBus: IEventBus, bus: IEventBus) {
        modBus.addListener<RegisterDataMapTypesEvent> {
            it.register(ITEMS)
            it.register(BLOCKS)
        }
        bus.addListener<PlayerTickEvent.Post> { tick(it.entity) }
        bus.addListener<PlayerEvent.PlayerLoggedInEvent> { resend(it.entity) }
        bus.addListener<PlayerEvent.PlayerRespawnEvent> { resend(it.entity) }
        bus.addListener<PlayerEvent.PlayerChangedDimensionEvent> { resend(it.entity) }
        bus.addListener<PlayerEvent.PlayerLoggedOutEvent> {
            readings.remove(it.entity.uuid)
            sent.remove(it.entity.uuid)
        }
    }

    fun strength(stack: ItemStack): Float = if (stack.isEmpty) 0f else stack.itemHolder.getData(ITEMS) ?: 0f

    fun strength(state: BlockState): Float = state.blockHolder.getData(BLOCKS) ?: 0f

    /** The last reading, in units of the HUD bar. */
    fun units(player: ServerPlayer): Float = (readings[player.uuid] ?: 0f) / pointsPerUnit()

    /** A fresh reading in points, with what made it up (largest first), for `/ar radiation`. */
    fun report(player: ServerPlayer): Pair<Float, List<Pair<String, Float>>> {
        val sources = HashMap<String, Float>()
        val points = measure(player, sources)
        return points to sources.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Health taken per reading at [units], per the config's damage points. */
    fun damage(units: Float, maxHealth: Float): Float {
        val death = CoreCommonConfig.radiationDeathUnits.get().toFloat()
        if (units >= death) return Float.MAX_VALUE
        val curve = CoreCommonConfig.radiationDamage.get().mapNotNull(CoreCommonConfig::parsePoint).sortedBy { it.first }
        if (curve.isEmpty() || units < curve.first().first) return 0f
        for ((a, b) in curve.zipWithNext()) {
            if (units < b.first) return a.second + (b.second - a.second) * (units - a.first) / (b.first - a.first)
        }
        val (lastUnits, lastDamage) = curve.last()
        if (death <= lastUnits) return lastDamage
        return lastDamage + (max(maxHealth, lastDamage) - lastDamage) * (units - lastUnits) / (death - lastUnits)
    }

    fun pointsPerUnit(): Float = CoreCommonConfig.radiationPointsPerUnit.get().toFloat()

    private fun tick(player: Player) {
        if (player is ServerPlayer && player.tickCount % CoreCommonConfig.radiationInterval.get() == 0) update(player)
    }

    private fun resend(player: Player) {
        if (player !is ServerPlayer) return
        sent.remove(player.uuid)
        update(player, hurt = false)
    }

    private fun update(player: ServerPlayer, hurt: Boolean = true) {
        val points = measure(player, null)
        readings[player.uuid] = points
        val units = points / pointsPerUnit()
        val tenths = (units * 10).toInt().coerceIn(0, 100)
        if (sent.put(player.uuid, tenths) != tenths) PacketDistributor.sendToPlayer(player, RadiationPayload(tenths))

        if (!hurt || !player.isAlive || player.isCreative || player.isSpectator) return
        val health = damage(units, player.maxHealth)
        if (health > 0f) player.hurt(player.damageSources().source(DAMAGE), health)
    }

    private fun measure(player: ServerPlayer, sources: MutableMap<String, Float>?): Float {
        var total = 0f
        fun add(label: String, points: Float) {
            if (points <= 0f) return
            total += points
            sources?.merge(label, points, Float::plus)
        }

        val inventory = player.inventory
        val held = CoreCommonConfig.radiationHeldMultiplier.get().toFloat()
        fun carry(stack: ItemStack, multiplier: Float) {
            val strength = strength(stack)
            if (strength > 0f) add("carried ${stack.hoverName.string}", strength * stack.count * multiplier)
        }
        inventory.items.forEachIndexed { i, stack -> carry(stack, if (i == inventory.selected) held else 1f) }
        inventory.armor.forEach { carry(it, 1f) }
        inventory.offhand.forEach { carry(it, held) }

        val radius = CoreCommonConfig.radiationRadius.get()
        if (radius <= 0) return total
        val reach = radius.toDouble()
        val center = player.position().add(0.0, player.bbHeight / 2.0, 0.0)
        fun falloff(distance: Double) = if (distance >= reach) 0f else (1.0 - distance / reach).toFloat()

        // Blocks: whole chunk sections whose palette holds nothing radiated are skipped without looking at a block.
        val level = player.level()
        val origin = BlockPos.containing(center)
        val min = origin.offset(-radius, -radius, -radius)
        val max = origin.offset(radius, radius, radius)
        for (cx in (min.x shr 4)..(max.x shr 4)) for (cz in (min.z shr 4)..(max.z shr 4)) {
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
            for (cy in max(min.y shr 4, level.minSection)..min(max.y shr 4, level.maxSection - 1)) {
                val section = chunk.getSection(level.getSectionIndexFromSectionY(cy))
                if (section.hasOnlyAir() || !section.maybeHas { strength(it) > 0f }) continue
                for (x in max(min.x, cx shl 4)..min(max.x, (cx shl 4) + 15))
                    for (y in max(min.y, cy shl 4)..min(max.y, (cy shl 4) + 15))
                        for (z in max(min.z, cz shl 4)..min(max.z, (cz shl 4) + 15)) {
                            val state = section.getBlockState(x and 15, y and 15, z and 15)
                            val strength = strength(state)
                            if (strength <= 0f) continue
                            val distance = sqrt(center.distanceToSqr(x + 0.5, y + 0.5, z + 0.5))
                            add("nearby ${state.block.name.string}", strength * falloff(distance))
                        }
            }
        }

        // Dropped items.
        val box = AABB.ofSize(center, reach * 2, reach * 2, reach * 2)
        for (item in level.getEntitiesOfClass(ItemEntity::class.java, box) { it.isAlive }) {
            val stack = item.item
            val strength = strength(stack)
            if (strength <= 0f) continue
            add("dropped ${stack.hoverName.string}", strength * stack.count * falloff(item.position().distanceTo(center)))
        }
        return total
    }
}

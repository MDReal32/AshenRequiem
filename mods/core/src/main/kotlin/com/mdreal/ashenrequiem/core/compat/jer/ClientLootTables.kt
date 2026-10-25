package com.mdreal.ashenrequiem.core.compat.jer

import com.mojang.logging.LogUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.storage.loot.LootTable
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge

/** The server's loot tables on the client (see [LootTableSync]). Each one is decoded the first time JER asks. */
object ClientLootTables {
    private val LOGGER = LogUtils.getLogger()

    private var parts: Array<ByteArray?> = emptyArray()
    private var tables: CompoundTag? = null
    private val decoded = HashMap<ResourceKey<LootTable>, LootTable>()

    fun hook() {
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> { clear() }
    }

    @Synchronized
    fun receive(payload: LootTablesPayload) {
        if (payload.index == 0) parts = arrayOfNulls(payload.count)
        if (payload.count != parts.size || payload.index !in parts.indices) return
        parts[payload.index] = payload.data
        if (parts.any { it == null }) return

        val bytes = ByteArrayOutputStream().apply { parts.forEach { write(it!!) } }.toByteArray()
        parts = emptyArray()
        tables = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap())
        decoded.clear()
    }

    @Synchronized
    fun clear() {
        parts = emptyArray()
        tables = null
        decoded.clear()
    }

    /** The server's table for [key], or null when there's none (or no server sent any). */
    @JvmStatic
    @Synchronized
    fun get(key: ResourceKey<LootTable>): LootTable? {
        decoded[key]?.let { return it }
        val tag = tables?.get(key.location().toString()) ?: return null
        val registries = Minecraft.getInstance().connection?.registryAccess() ?: return null
        val table = LootTable.DIRECT_CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag)
            .resultOrPartial { LOGGER.debug("Loot table {} from the server didn't decode: {}", key.location(), it) }
            .orElse(null) ?: return null
        decoded[key] = table
        return table
    }
}

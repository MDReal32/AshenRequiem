package com.mdreal.ashenrequiem.core.compat.jer

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mojang.logging.LogUtils
import io.netty.buffer.ByteBuf
import java.io.ByteArrayOutputStream
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ReloadableServerRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.loot.LootTable
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.PayloadRegistrar

/** One piece of the server's loot tables: gzipped NBT, split to stay under the packet size limit. */
class LootTablesPayload(val index: Int, val count: Int, val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<LootTablesPayload> = TYPE

    companion object {
        const val MAX_CHUNK = 512 * 1024
        val TYPE = CustomPacketPayload.Type<LootTablesPayload>(CoreConfig.id("loot_tables"))
        val STREAM_CODEC: StreamCodec<ByteBuf, LootTablesPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LootTablesPayload::index,
            ByteBufCodecs.VAR_INT, LootTablesPayload::count,
            ByteBufCodecs.byteArray(MAX_CHUNK), LootTablesPayload::data,
            ::LootTablesPayload,
        )
    }
}

/**
 * Loot tables live only on the server, and Just Enough Resources reads them only in singleplayer, so on a server
 * its mob drops and chest loot come up empty. The server sends its tables to clients that have JER; a mixin
 * (`JerLootTableFetcherMixin`) hands them to JER. They go out with every datapack sync (joining and `/reload`),
 * which is before the recipes that make JEI rebuild, so JER always reads the current ones.
 */
object LootTableSync {
    private const val JER = "jeresources"
    private val LOGGER = LogUtils.getLogger()

    /** The encoded tables of the last reload, so each player doesn't cost a fresh encode. */
    private var cache: Pair<ReloadableServerRegistries.Holder, List<ByteArray>>? = null

    fun hook(bus: IEventBus) {
        bus.addListener<OnDatapackSyncEvent> { event -> event.relevantPlayers.forEach(::send) }
    }

    /** The server always offers the channel; a client only opens it when JER is there to use the tables. */
    fun register(registrar: PayloadRegistrar) {
        if (FMLEnvironment.dist.isClient && !ModList.get().isLoaded(JER)) return
        registrar.optional().playToClient(LootTablesPayload.TYPE, LootTablesPayload.STREAM_CODEC) { payload, _ ->
            ClientLootTables.receive(payload)
        }
        if (FMLEnvironment.dist.isClient) {
            ClientLootTables.hook()
        }
    }

    private fun send(player: ServerPlayer) {
        // Singleplayer and the LAN host's own client read the tables straight from the integrated server.
        val listener = player.connection
        if (listener.getConnection().isMemoryConnection || !listener.hasChannel(LootTablesPayload.TYPE)) return
        val chunks = encode(player.server)
        chunks.forEachIndexed { i, chunk -> PacketDistributor.sendToPlayer(player, LootTablesPayload(i, chunks.size, chunk)) }
    }

    private fun encode(server: MinecraftServer): List<ByteArray> {
        val holder = server.reloadableRegistries()
        cache?.let { (cached, chunks) -> if (cached === holder) return chunks }

        val ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
        val tables = CompoundTag()
        var skipped = 0
        for (id in holder.getKeys(Registries.LOOT_TABLE)) {
            val table = holder.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id))
            LootTable.DIRECT_CODEC.encodeStart(ops, table).result().ifPresentOrElse({ tables.put(id.toString(), it) }) { skipped++ }
        }
        val bytes = ByteArrayOutputStream().also { NbtIo.writeCompressed(tables, it) }.toByteArray()
        val chunks = (bytes.indices step LootTablesPayload.MAX_CHUNK).map {
            bytes.copyOfRange(it, minOf(it + LootTablesPayload.MAX_CHUNK, bytes.size))
        }
        LOGGER.info("Loot tables for JER: {} tables in {} KB ({} could not be encoded)", tables.size(), bytes.size / 1024, skipped)
        cache = holder to chunks
        return chunks
    }
}

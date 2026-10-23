package com.mdreal.ashenrequiem.core.command

import com.mdreal.ashenrequiem.core.api.AshenRequiem
import com.mdreal.ashenrequiem.core.api.DimensionSpawns
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.RegisterCommandsEvent

private typealias Ctx = CommandContext<CommandSourceStack>

/** `/ar` debug commands. Every story state can be reproduced from here. */
object ArCommand {
    fun hook(bus: IEventBus) {
        bus.addListener<RegisterCommandsEvent> { register(it.dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ar").requires { it.hasPermission(2) }
                .then(Commands.literal("state").withTargets(::state))
                .then(
                    Commands.literal("hush")
                        .then(Commands.literal("get").executes(::hushGet))
                        .then(
                            Commands.literal("set").then(
                                Commands.argument("stage", IntegerArgumentType.integer(0, AshenRequiem.MAX_HUSH_STAGE))
                                    .executes(::hushSet)
                            )
                        )
                )
                .then(
                    Commands.literal("gate").then(
                        Commands.argument("repaired", BoolArgumentType.bool()).executes(::gateSet)
                    )
                )
                .then(
                    Commands.literal("requiem")
                        .then(Commands.literal("grant").withTargets { ctx, players -> requiem(ctx, players, true) })
                        .then(Commands.literal("revoke").withTargets { ctx, players -> requiem(ctx, players, false) })
                )
                .then(Commands.literal("reset").withTargets(::reset))
                .then(
                    Commands.literal("tp").then(
                        Commands.argument("dimension", DimensionArgument.dimension()).withTargets(::tp)
                    )
                )
                .then(
                    Commands.literal("debug").then(
                        Commands.literal("biomes").then(
                            Commands.argument("dimension", DimensionArgument.dimension())
                                .executes { biomes(it, 2048, 64, null) }
                                .then(
                                    Commands.argument("radius", IntegerArgumentType.integer(64, 20000))
                                        .executes { biomes(it, IntegerArgumentType.getInteger(it, "radius"), 64, null) }
                                        .then(
                                            Commands.argument("step", IntegerArgumentType.integer(16, 4096))
                                                .executes { biomes(it, IntegerArgumentType.getInteger(it, "radius"), IntegerArgumentType.getInteger(it, "step"), null) }
                                                .then(
                                                    Commands.argument("seeds", IntegerArgumentType.integer(1, 200))
                                                        .executes {
                                                            biomes(
                                                                it, IntegerArgumentType.getInteger(it, "radius"),
                                                                IntegerArgumentType.getInteger(it, "step"),
                                                                IntegerArgumentType.getInteger(it, "seeds")
                                                            )
                                                        }
                                                )
                                        )
                                )
                        )
                    )
                )
        )
    }

    /** Runs [action] on the command's own player, or on an optional `targets` argument. */
    private fun <T : ArgumentBuilder<CommandSourceStack, T>> T.withTargets(
        action: (Ctx, Collection<ServerPlayer>) -> Int
    ): T = executes { action(it, listOf(it.source.playerOrException)) }
        .then(
            Commands.argument("targets", EntityArgument.players())
                .executes { action(it, EntityArgument.getPlayers(it, "targets")) }
        )

    private fun Ctx.reply(text: String) = source.sendSuccess({ Component.literal(text) }, true)

    private fun state(ctx: Ctx, players: Collection<ServerPlayer>): Int {
        val server = ctx.source.server
        ctx.reply(
            "World: hush=${AshenRequiem.getHushStage(server)}, gate=${AshenRequiem.isGateRepaired(server)}, " +
                "finale=${AshenRequiem.isFinaleWon(server)}"
        )
        AshenRequiem.locations(server).forEach { (key, pos) ->
            ctx.reply("  $key -> ${pos.dimension().location()} ${pos.pos().toShortString()}")
        }
        players.forEach { ctx.reply("${it.scoreboardName}: ${AshenRequiem.progress(it)}") }
        return players.size
    }

    private fun hushGet(ctx: Ctx): Int {
        val stage = AshenRequiem.getHushStage(ctx.source.server)
        ctx.reply("Hush stage: $stage")
        return stage
    }

    private fun hushSet(ctx: Ctx): Int {
        val stage = IntegerArgumentType.getInteger(ctx, "stage")
        val changed = AshenRequiem.setHushStage(ctx.source.server, stage)
        ctx.reply(if (changed) "Hush stage set to $stage" else "Hush stage is already $stage")
        return stage
    }

    private fun gateSet(ctx: Ctx): Int {
        val repaired = BoolArgumentType.getBool(ctx, "repaired")
        AshenRequiem.setGateRepaired(ctx.source.server, repaired)
        ctx.reply("Gate repaired: $repaired")
        return 1
    }

    private fun requiem(ctx: Ctx, players: Collection<ServerPlayer>, value: Boolean): Int {
        players.forEach { AshenRequiem.setRequiem(it, value) }
        ctx.reply("${if (value) "Granted" else "Revoked"} the Requiem for ${players.size} player(s)")
        return players.size
    }

    private fun reset(ctx: Ctx, players: Collection<ServerPlayer>): Int {
        players.forEach(AshenRequiem::resetPlayer)
        ctx.reply("Reset story progress for ${players.size} player(s)")
        return players.size
    }

    /** `/ar debug biomes <dimension> [radius] [step] [seeds]`: biome share and nearest distance, no chunks generated. */
    private fun biomes(ctx: Ctx, radius: Int, step: Int, seeds: Int?): Int {
        val level = DimensionArgument.getDimension(ctx, "dimension")
        val started = System.nanoTime()
        val stats = BiomeSampler.sample(level, radius, step, seeds)
        val ms = (System.nanoTime() - started) / 1_000_000
        ctx.reply("Biomes in ${level.dimension().location()} within $radius (step $step, ${seeds ?: "world"} seed(s), ${ms}ms):")
        stats.forEach {
            ctx.reply(
                "  %-45s %5.1f%% (%.1f-%.1f)  in %d seed(s)  nearest %d-%d".format(
                    it.biome, it.meanShare, it.minShare, it.maxShare, it.seedsFound, it.nearestMin, it.nearestMax
                )
            )
        }
        return stats.size
    }

    private fun tp(ctx: Ctx, players: Collection<ServerPlayer>): Int {
        val level = DimensionArgument.getDimension(ctx, "dimension")
        players.forEach { DimensionSpawns.teleport(it, level) }
        ctx.reply("Sent ${players.size} player(s) to ${level.dimension().location()}")
        return players.size
    }
}

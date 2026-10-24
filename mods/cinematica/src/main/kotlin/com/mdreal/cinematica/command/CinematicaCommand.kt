package com.mdreal.cinematica.command

import com.mdreal.cinematica.api.Cinematica
import com.mdreal.cinematica.scene.Scenes
import com.mdreal.cinematica.server.Sessions
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.RegisterCommandsEvent

private typealias Ctx = CommandContext<CommandSourceStack>

/**
 * `/cinematica play <scene> [targets]`, `stop [targets]`, `record <name>`, `list`. A bare scene name means the
 * recording `local:<name>`.
 */
object CinematicaCommand {
    private val UNKNOWN_SCENE = SimpleCommandExceptionType(Component.translatable("cinematica_studio.command.unknown_scene"))
    private val BAD_NAME = SimpleCommandExceptionType(Component.translatable("cinematica_studio.command.bad_name"))

    fun hook(bus: IEventBus) {
        bus.addListener<RegisterCommandsEvent> { register(it.dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cinematica").requires { it.hasPermission(2) }
                .then(
                    Commands.literal("play").then(
                        Commands.argument("scene", ResourceLocationArgument.id())
                            .suggests { ctx, builder -> SharedSuggestionProvider.suggestResource(Scenes.ids(ctx.source.server), builder) }
                            .executes { play(it, listOf(it.source.playerOrException)) }
                            .then(
                                Commands.argument("targets", EntityArgument.players())
                                    .executes { play(it, EntityArgument.getPlayers(it, "targets")) }
                            )
                    )
                )
                .then(
                    Commands.literal("stop")
                        .executes { stop(it, listOf(it.source.playerOrException)) }
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .executes { stop(it, EntityArgument.getPlayers(it, "targets")) }
                        )
                )
                .then(
                    Commands.literal("record").then(
                        Commands.argument("name", StringArgumentType.word()).executes(::record)
                    )
                )
                .then(Commands.literal("list").executes(::list))
        )
    }

    private fun Ctx.reply(component: Component) = source.sendSuccess({ component }, true)

    private fun play(ctx: Ctx, players: Collection<ServerPlayer>): Int {
        val id = Scenes.find(ctx.source.server, ResourceLocationArgument.getId(ctx, "scene"))?.first
            ?: throw UNKNOWN_SCENE.create()
        players.forEach { Cinematica.play(it, id) }
        ctx.reply(Component.translatable("cinematica_studio.command.playing", id.toString(), players.size))
        return players.size
    }

    private fun stop(ctx: Ctx, players: Collection<ServerPlayer>): Int {
        val stopped = players.count(Cinematica::stop)
        ctx.reply(Component.translatable("cinematica_studio.command.stopped", stopped))
        return stopped
    }

    private fun record(ctx: Ctx): Int {
        val name = StringArgumentType.getString(ctx, "name")
        if (!Scenes.isValidName(name)) throw BAD_NAME.create()
        Sessions.record(ctx.source.playerOrException, name)
        ctx.reply(Component.translatable("cinematica_studio.command.recording", name))
        return 1
    }

    private fun list(ctx: Ctx): Int {
        val ids = Scenes.ids(ctx.source.server)
        ctx.reply(Component.translatable("cinematica_studio.command.list", ids.size, ids.joinToString(", ")))
        return ids.size
    }
}

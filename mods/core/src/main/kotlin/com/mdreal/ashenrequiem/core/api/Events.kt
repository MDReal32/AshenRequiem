package com.mdreal.ashenrequiem.core.api

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.Event

/** Posted on the NeoForge game bus after the global Hush stage changes. */
class HushStageChangedEvent(val server: MinecraftServer, val oldStage: Int, val newStage: Int) : Event()

/** Posted on the NeoForge game bus after the Gate is marked repaired or broken. */
class GateStateChangedEvent(val server: MinecraftServer, val repaired: Boolean) : Event()

/** Posted on the NeoForge game bus after a player gains or loses the Requiem mark. */
class RequiemChangedEvent(val player: ServerPlayer, val hasRequiem: Boolean) : Event()

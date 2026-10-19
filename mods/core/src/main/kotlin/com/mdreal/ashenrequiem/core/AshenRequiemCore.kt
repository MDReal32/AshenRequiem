package com.mdreal.ashenrequiem.core

import com.mdreal.ashenrequiem.core.command.ArCommand
import com.mdreal.ashenrequiem.core.network.Network
import com.mdreal.ashenrequiem.core.state.Attachments
import com.mdreal.ashenrequiem.core.state.ProgressSync
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge

@Mod(CoreConfig.MOD_ID)
class AshenRequiemCore(modBus: IEventBus) {
    init {
        Attachments.hook(modBus)
        Network.hook(modBus)
        ProgressSync.hook(NeoForge.EVENT_BUS)
        ArCommand.hook(NeoForge.EVENT_BUS)
    }
}

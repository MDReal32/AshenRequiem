package com.mdreal.ashenrequiem.core.compat.kubejs

import com.mdreal.ashenrequiem.core.api.AshenRequiem
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin
import dev.latvian.mods.kubejs.script.BindingRegistry

/**
 * Exposes the story API to scripts as `AR`, e.g. `AR.setHushStage(event.server, 3)`.
 * Loaded by KubeJS through `kubejs.plugins.txt`, so this class is never touched when KubeJS is absent.
 */
class AshenRequiemKubePlugin : KubeJSPlugin {
    override fun registerBindings(bindings: BindingRegistry) {
        bindings.add("AR", AshenRequiem)
    }
}

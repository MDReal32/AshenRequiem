package com.mdreal.ashenrequiem.core.compat.jer

import com.mojang.logging.LogUtils
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lets mods add their own entries to Just Enough Resources. JER's plugin annotation is never found on NeoForge (it
 * scans for its interface instead of the annotation), so `JerProxyMixin` calls these right after JER rebuilds its
 * lists: on joining a world and after `/reload`. Listeners add straight to JER's registries, which were just cleared,
 * and can read loot tables through JER, server ones included.
 */
object JerHooks {
    private val LOGGER = LogUtils.getLogger()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    /** Only call this when JER is loaded; the listener is free to use JER's classes. */
    fun onRebuild(listener: Runnable) {
        listeners += listener
    }

    @JvmStatic
    fun rebuilt() {
        for (listener in listeners) {
            runCatching { listener.run() }.onFailure { LOGGER.warn("A JER entry failed to register", it) }
        }
    }
}

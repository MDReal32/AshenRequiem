package com.mdreal.boundlesshorizons.core

import com.sun.jna.platform.win32.COM.util.Factory

class BlockFactory {}

class BlockRegistries private constructor(blockId: String, factory: BlockFactory) {
    companion object {
        fun prepare(blockId: String, factory: BlockFactory): BlockRegistries {
            return BlockRegistries(blockId, factory)
        }
    }

    fun register() {}
}
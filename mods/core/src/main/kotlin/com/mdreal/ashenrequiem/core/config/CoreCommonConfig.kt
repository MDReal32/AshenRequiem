package com.mdreal.ashenrequiem.core.config

import net.neoforged.neoforge.common.ModConfigSpec

object CoreCommonConfig {
    private val spec = ModConfigSpec.Builder()

    init {
        spec.push("radiation")
    }

    val radiationPointsPerUnit: ModConfigSpec.DoubleValue = spec
        .comment("Radiation points in one unit of the HUD bar (it shows 10 units).")
        .defineInRange("pointsPerUnit", 100.0, 1.0, 100000.0)

    val radiationHeldMultiplier: ModConfigSpec.DoubleValue = spec
        .comment("How much more the items in the main hand and offhand count than the rest of the inventory. 1 counts them the same.")
        .defineInRange("heldMultiplier", 1.0, 0.0, 100.0)

    val radiationRadius: ModConfigSpec.IntValue = spec
        .comment("How far, in blocks, radiated blocks and dropped items reach. Their strength falls off to nothing at this distance.")
        .defineInRange("radius", 6, 0, 16)

    val radiationInterval: ModConfigSpec.IntValue = spec
        .comment("Ticks between two readings (and two hits of radiation damage).")
        .defineInRange("interval", 20, 1, 1200)

    val radiationDamage: ModConfigSpec.ConfigValue<List<out String>> = spec
        .comment(
            "Damage per reading, as \"units:health\" points in increasing order. Below the first point there is no damage;",
            "between two points it grows in a straight line.",
        )
        .defineListAllowEmpty("damage", listOf("4:1", "5:2", "7:8", "8:16"), { "0:0" }) { it is String && parsePoint(it) != null }

    val radiationDeathUnits: ModConfigSpec.DoubleValue = spec
        .comment("At this many units radiation kills outright. From the last damage point up to here it grows to the player's full health.")
        .defineInRange("deathUnits", 10.0, 0.0, 1000.0)

    init {
        spec.pop()
    }

    fun getSpec(): ModConfigSpec = spec.build()

    fun parsePoint(text: String): Pair<Float, Float>? {
        val parts = text.split(':')
        if (parts.size != 2) return null
        val units = parts[0].trim().toFloatOrNull() ?: return null
        val damage = parts[1].trim().toFloatOrNull() ?: return null
        return units to damage
    }
}

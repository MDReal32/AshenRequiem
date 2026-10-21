package com.mdreal.ashenrequiem.world.screens

import com.mdreal.ashenrequiem.world.WorldConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.presets.WorldPreset

/**
 * A stripped-down create-world screen: name, game mode, create. Everything else comes from the
 * wrapped vanilla screen, which also does the actual world creation (opened up by our access transformer).
 */
class MinimalDeadlandsCreateWorldScreen private constructor(
    private val mc: Minecraft, private val sourceScreen: CreateWorldScreen
) : Screen(Component.translatable("selectWorld.create")) {
    companion object {
        fun wrap(vanilla: CreateWorldScreen): MinimalDeadlandsCreateWorldScreen =
            MinimalDeadlandsCreateWorldScreen(Minecraft.getInstance(), vanilla)
    }

    private val PRESET_KEY: ResourceKey<WorldPreset> = ResourceKey.create(
        Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "ashen_requiem")
    )

    private val ui get() = sourceScreen.uiState
    private val widgets: MutableMap<String, AbstractWidget> = mutableMapOf()

    override fun init() {
        widgets.clear()
        setDefaultWorldPreset()
        addNameBox()
        addGameModeButton()
        addCreateButton()
        addBackButton()
    }

    fun <T : AbstractWidget> widget(id: String): T? = widgets[id] as? T

    private fun setDefaultWorldPreset() {
        val presets = ui.settings.worldgenRegistries().compositeAccess().registryOrThrow(Registries.WORLD_PRESET)
        val holder = presets.getHolder(PRESET_KEY)
            .orElseThrow { IllegalStateException("Ashen Requiem preset not found: $PRESET_KEY") }

        ui.worldType = WorldCreationUiState.WorldTypeEntry(holder)
        ui.onChanged()
    }

    private fun addNameBox() {
        val w = 240
        val x = width / 2 - w / 2
        val y = height / 2 - 40

        widgets["nameBox"] = addRenderableWidget(
            EditBox(font, x, y, w, 20, Component.translatable("selectWorld.enterName")).apply {
                value = ui.name
                setResponder { ui.name = it }
            }
        )
    }

    private fun addCreateButton() {
        val w = 200
        val x = width / 2 - w / 2
        val y = height / 2 + 20

        widgets["createButton"] = addRenderableWidget(
            Button.builder(Component.translatable("selectWorld.create")) {
                sourceScreen.onCreate()
            }.bounds(x, y, w, 20).build()
        )
    }

    private fun addGameModeButton() {
        val w = 240
        val x = width / 2 - w / 2
        val y = height / 2 - 10

        widgets["gameMode"] = addRenderableWidget(
            CycleButton.builder { mode: WorldCreationUiState.SelectedGameMode -> mode.displayName }
                .withValues(
                    WorldCreationUiState.SelectedGameMode.SURVIVAL,
                    WorldCreationUiState.SelectedGameMode.HARDCORE,
                    WorldCreationUiState.SelectedGameMode.CREATIVE
                ).withInitialValue(ui.gameMode).create(x, y, w, 20, Component.translatable("selectWorld.gameMode")) { _, v ->
                    ui.gameMode = v
                }
        )
    }

    private fun addBackButton() {
        val w = 200
        val x = width / 2 - w / 2
        val y = height / 2 + 44

        widgets["backButton"] = addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) {
                mc.setScreen(null)
            }.bounds(x, y, w, 20).build()
        )
    }
}

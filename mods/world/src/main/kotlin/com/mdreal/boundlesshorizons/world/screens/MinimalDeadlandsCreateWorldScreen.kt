package com.mdreal.boundlesshorizons.world.screens


import com.mojang.serialization.Lifecycle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows
import net.minecraft.core.LayeredRegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.server.RegistryLayer
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.storage.PrimaryLevelData
import net.minecraft.world.level.storage.WorldData

class MinimalDeadlandsCreateWorldScreen private constructor(
    private val mc: Minecraft,
    private val sourceScreen: CreateWorldScreen
) : Screen(Component.translatable("selectWorld.create")) {
    companion object {
        fun wrap(vanilla: CreateWorldScreen): MinimalDeadlandsCreateWorldScreen =
            MinimalDeadlandsCreateWorldScreen(Minecraft.getInstance(), vanilla)
    }

    private val ui get() = sourceScreen.uiState
    private val widgets: MutableMap<String, AbstractWidget> = mutableMapOf()

    override fun init() {
        widgets.clear()
        addNameBox()
        addGameModeButton()
        addCreateButton()
        addBackButton()
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(g, mouseX, mouseY, partialTick)
        super.render(g, mouseX, mouseY, partialTick)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : AbstractWidget> widget(id: String): T? = widgets[id] as? T

    private fun addNameBox() {
        val w = 240
        val x = width / 2 - w / 2
        val y = height / 2 - 40

        widgets["nameBox"] = addRenderableWidget(
            EditBox(font, x, y, w, 20, Component.translatable("selectWorld.enterName")).apply {
                value = ui.name
            }
        )
    }

    private fun addCreateButton() {
        val w = 200
        val x = width / 2 - w / 2
        val y = height / 2 + 20

        widgets["createButton"] = addRenderableWidget(
            Button.builder(Component.translatable("selectWorld.create")) {
                onCreateWorld()
            }.bounds(x, y, w, 20).build()
        )
    }

    private fun addGameModeButton() {
        val w = 240
        val x = width / 2 - w / 2
        val y = height / 2 - 10

        widgets["gameMode"] = addRenderableWidget(
            CycleButton.builder<WorldCreationUiState.SelectedGameMode> { it.displayName }
                .withValues(
                    WorldCreationUiState.SelectedGameMode.SURVIVAL,
                    WorldCreationUiState.SelectedGameMode.HARDCORE,
                    WorldCreationUiState.SelectedGameMode.CREATIVE
                )
                .create(x, y, w, 20, Component.translatable("selectWorld.gameMode")) { _, v ->
                    ui.gameMode = v
                }
        )
    }

    private fun onCreateWorld() {
        // quick feedback screen
        mc.forceSetScreen(GenericMessageScreen(Component.translatable("createWorld.preparing")))

        val ctx = ui.settings
        val baked = ctx.selectedDimensions().bake(ctx.datapackDimensions())
        val layered: LayeredRegistryAccess<RegistryLayer> =
            ctx.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, *arrayOf(baked.dimensionsRegistryAccess()))

        val lifecycleWorldFlags =
            if (FeatureFlags.isExperimental(ctx.dataConfiguration().enabledFeatures()))
                Lifecycle.experimental() else Lifecycle.stable()
        val lifecycleAllRegs = layered.compositeAccess().allRegistriesLifecycle()
        val lifecycleTotal = lifecycleAllRegs.add(lifecycleWorldFlags)

        // re-use vanilla world-creation flow
        WorldOpenFlows.confirmWorldCreation(
            mc,
            sourceScreen,
            lifecycleTotal,
            {
                val debug = false
                val levelSettings = LevelSettings(
                    ui.name.trim(),
                    ui.gameMode.gameType,
                    ui.isHardcore,
                    ui.difficulty,
                    ui.isAllowCommands,
                    ui.gameRules,
                    ui.settings.dataConfiguration()
                )
                val worldData: WorldData = PrimaryLevelData(levelSettings, ctx.options(), PrimaryLevelData.SpecialWorldProperty.NONE, lifecycleTotal)
                mc.createWorldOpenFlows().createLevelFromExistingSettings(
                    mc.levelSource.createAccess(ui.targetFolder),
                    ctx.dataPackResources(),
                    layered,
                    worldData
                )
            },
            true
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
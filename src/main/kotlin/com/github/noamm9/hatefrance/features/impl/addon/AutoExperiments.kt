package com.github.noamm9.hatefrance.features.impl.addon

import com.github.noamm9.config.types.SliderSetting
import com.github.noamm9.config.types.ToggleSetting
import com.github.noamm9.event.impl.ContainerEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.utils.ChatUtils.removeFormatting
import com.github.noamm9.utils.GuiUtils
import com.github.noamm9.utils.PlayerUtils
import com.github.noamm9.utils.items.ItemUtils.hasGlint
import com.github.noamm9.utils.items.ItemUtils.lore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.Item
import java.util.IdentityHashMap

object AutoExperiments : Feature(
    name = "AutoExperiments",
    description = "Selects, starts and solves Chronomatron and Ultrasequencer experiments."
) {
    private val clickDelay by SliderSetting("Click Delay", 200, 100, 1000, 10)
    private val delayVariety by SliderSetting("Delay Variety", 50, 0, 1000, 10)
    private val autoStart by ToggleSetting("Auto Start", true)
        .withDescription("Selects the highest available stakes and reopens the table. Keep looking at the table.")
    private val autoClose by ToggleSetting("Auto Close", true)
    private val serumCount by SliderSetting("Serum Count", 0, 0, 3, 1)
    private val getMaxXp by ToggleSetting("Get Max XP", false)

    private val solver = ExperimentSolver()
    private val cells = List(50) { Cell(it) }
    private val itemIds = IdentityHashMap<Item, String>()
    private val experimentSlots = listOf(21 to 29, 23 to 33)
    private var nextClickTime = 0L
    private var reopenAt = 0L
    private var experimentScreen: AbstractContainerScreen<*>? = null

    override fun init() {
        register<ContainerEvent.Open> {
            reset()
            val title = event.screen.title.string.removeFormatting()
            solver.open(title)
            experimentScreen = event.screen.takeIf {
                solver.active || autoStart.value && (title == "Experimentation Table" || title == "Experiment Over" ||
                        isStakeSelection(title, "Chronomatron") || isStakeSelection(title, "Ultrasequencer"))
            }
            nextClickTime = if (solver.active) 0L else System.currentTimeMillis() + delay()
        }

        register<ContainerEvent.Close> {
            if (event.screen === experimentScreen) reset()
        }
        register<WorldChangeEvent> { reset() }

        register<ContainerEvent.MouseClick> {
            if (solver.active) event.isCanceled = true
        }

        register<TickEvent.Start> {
            if (experimentScreen == null && reopenAt == 0L) return@register
            val currentPlayer = mc.player
            if (currentPlayer == null) {
                reset()
                return@register
            }
            val now = System.currentTimeMillis()
            if (reopenAt != 0L) {
                if (!autoStart.value || mc.gui.screen() != null) reset()
                else if (now >= reopenAt) {
                    reopenAt = 0L
                    PlayerUtils.rightClick()
                }
                return@register
            }

            val screen = experimentScreen ?: return@register
            if (mc.gui.screen() !== screen || currentPlayer.containerMenu !== screen.menu) {
                reset()
                return@register
            }

            if (!solver.active) {
                if (!autoStart.value || now < nextClickTime) return@register
                if (screen.title.string.removeFormatting() == "Experiment Over") reopenTable()
                else nextMenuClick(screen)?.let { slot ->
                    GuiUtils.clickSlot(slot, GuiUtils.ButtonType.MIDDLE)
                    nextClickTime = now + delay()
                }
                return@register
            }

            val slots = screen.menu.slots
            if (slots.size < cells.size) return@register
            for (i in 9..49) {
                val stack = slots[i].item
                val cell = cells[i]
                cell.itemId = if (stack.isEmpty) "" else itemIds.getOrPut(stack.item) {
                    BuiltInRegistries.ITEM.getKey(stack.item).toString()
                }
                cell.count = if (stack.isEmpty) 0 else stack.count
                cell.foil = !stack.isEmpty && stack.hasGlint()
            }
            solver.nextClick(cells, now >= nextClickTime)?.let { slot ->
                GuiUtils.clickSlot(slot, GuiUtils.ButtonType.MIDDLE)
                nextClickTime = now + delay()
            }

            if (autoClose.value && solver.shouldClose(cells, chronomatronTarget(), ultrasequencerTarget())) {
                reopenTable()
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }

    private fun reset() {
        solver.close()
        nextClickTime = 0L
        reopenAt = 0L
        experimentScreen = null
    }

    private fun reopenTable() {
        reset()
        mc.player?.closeContainer()
        if (autoStart.value) reopenAt = System.currentTimeMillis() + delay()
    }

    private fun nextMenuClick(screen: AbstractContainerScreen<*>): Int? {
        val title = screen.title.string.removeFormatting()
        val slots = screen.menu.slots
        fun tooltip(slot: Int) = slots.getOrNull(slot)?.item?.takeUnless { it.isEmpty }?.let { item ->
            listOf(item.hoverName.string) + item.lore
        }.orEmpty()

        if (title == "Experimentation Table") {
            if (tooltip(22).any { it.removeFormatting().contains("on cooldown", ignoreCase = true) }) return null
            for ((statusSlot, startSlot) in experimentSlots) {
                val status = tooltip(statusSlot)
                if (status.isEmpty()) continue
                if (status.none {
                        val line = it.removeFormatting()
                        line.contains("Experiment completed") || line.contains("Add-on locked!")
                    }) {
                    return startSlot.takeIf { slots.getOrNull(it)?.hasItem() == true }
                }
            }
            return null
        }

        val stakes = when {
            isStakeSelection(title, "Chronomatron") -> 24 downTo 20
            isStakeSelection(title, "Ultrasequencer") -> 23 downTo 21
            else -> return null
        }
        return stakes.firstOrNull { slot ->
            val lore = tooltip(slot)
            lore.isNotEmpty() && lore.none {
                val line = it.removeFormatting()
                line.contains("Enchanting level too low!") || line.contains("Not enough experience!")
            }
        }
    }

    private fun isStakeSelection(title: String, game: String) = title.startsWith(game) && title.endsWith("Stakes")

    private fun delay(): Long {
        val variety = if (delayVariety.value == 0) 0 else (0..delayVariety.value).random()
        return clickDelay.value.toLong() + variety
    }

    private fun chronomatronTarget() = if (getMaxXp.value) 15 else 11 - serumCount.value

    private fun ultrasequencerTarget() = if (getMaxXp.value) 20 else 8 - serumCount.value
}
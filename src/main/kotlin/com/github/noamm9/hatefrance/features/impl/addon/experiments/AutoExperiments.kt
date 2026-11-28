package com.github.noamm9.hatefrance.features.impl.addon.experiments

import com.github.noamm9.config.types.RangeSliderSetting
import com.github.noamm9.config.types.SliderSetting
import com.github.noamm9.config.types.ToggleSetting
import com.github.noamm9.event.impl.ContainerEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.warn
import com.github.noamm9.hatefrance.utils.ContainerUtils.itemId
import com.github.noamm9.hatefrance.utils.ContainerUtils.serverItem
import com.github.noamm9.utils.ChatUtils.removeFormatting
import com.github.noamm9.utils.ChatUtils.unformattedText
import com.github.noamm9.utils.GuiUtils
import com.github.noamm9.utils.PlayerUtils
import com.github.noamm9.utils.items.ItemUtils.hasGlint
import com.github.noamm9.utils.items.ItemUtils.lore
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object AutoExperiments : Feature(
    name = "AutoExperiments",
    description = "Automatically plays the Experimentation Table and claims its rewards."
) {
    private val clickDelay by RangeSliderSetting("Click Delay", 200 to 250, 100, 2000, 10, "ms")
        .jsonName("Click Delay Range")
        .withDescription("Random delay between clicks.")
    private val getMaxXp by ToggleSetting("Get Max XP")
        .withDescription("Keeps playing the add-ons after the extra Superpairs clicks are maxed.")
    private val renewCount by SliderSetting("Renew Experiments", 0, 0, 3, 1)
        .withDescription("Extra charges to buy per day with XP levels and bits.")
    private val autoSuperpairs by ToggleSetting("Auto Superpairs")
        .withDescription("Also plays Superpairs and buys missing XP levels with Titanic Experience Bottles.")
    private val avoidXpPairs by ToggleSetting("Avoid XP Pairs")
        .withDescription("Matches Enchanting Exp pairs only when nothing else is left.")
        .showIf { autoSuperpairs.value }

    private val addons = ExperimentSolver()
    private val superpairs = SuperpairsSolver()
    private val flow = TableFlow(playSuperpairs = { autoSuperpairs.value }, renewLimit = { renewCount.value })
    private val addonCells = (9..49).map(::Cell)
    private val superpairsCells = (0..44).map(::SuperpairsCell)

    private var experimentScreen: AbstractContainerScreen<*>? = null
    private var openedAt = 0L
    private var nextClickTime = 0L
    private var stuckReported = false

    private var reopenOnClose = false
    private var reopenAt = 0L
    private var reopenCheckAt = 0L

    override fun init() {
        register<ContainerEvent.Open> {
            // resizing the window opens the same screen again
            if (event.screen === experimentScreen) return@register
            reset()
            val title = event.screen.title.unformattedText
            val now = System.currentTimeMillis()
            flow.menuOpened(title)
            addons.open(title)
            superpairs.open(title, autoSuperpairs.value, avoidXpPairs.value)
            experimentScreen = event.screen.takeIf { addons.active || superpairs.active || flow.handles(title) }
            openedAt = now
            nextClickTime = if (addons.active) 0L else now + delay()
        }

        register<ContainerEvent.Close> {
            if (event.screen !== experimentScreen) return@register
            val reopen = reopenOnClose
            reset()
            if (reopen) scheduleReopen()
        }

        register<WorldChangeEvent> { resetAll() }

        register<ContainerEvent.MouseClick> {
            if (addons.active || superpairs.blocksInput) event.isCanceled = true
        }

        register<TickEvent.Start> {
            val now = System.currentTimeMillis()
            val player = mc.player ?: return@register reset()
            if (reopenAt != 0L) return@register tickReopen(now)
            val screen = experimentScreen
            if (screen == null) {
                flow.reset()
                checkReopened(now)
                return@register
            }
            if (mc.gui.screen() !== screen || player.containerMenu !== screen.menu) return@register reset()

            when {
                superpairs.active -> playSuperpairs(screen, now)
                addons.active -> playAddon(screen, now)
                now >= nextClickTime -> {
                    val title = screen.title.unformattedText
                    perform(flow.next(screen, title, player.experienceLevel, now), now)
                }
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        resetAll()
    }

    private fun playAddon(screen: AbstractContainerScreen<*>, now: Long) {
        val slots = screen.menu.slots
        if (slots.size <= addonCells.last().slot) return
        for (cell in addonCells) {
            val stack = slots[cell.slot].item
            cell.itemId = stack.itemId()
            cell.count = stack.count
            cell.foil = stack.hasGlint()
        }
        addons.nextClick(addonCells, now >= nextClickTime)?.let { click(it, now) }

        val rewardLength = flow.addonRewardLength.takeUnless { getMaxXp.value || it == 0 }
        // hypixel opens "Experiment Over", the table is only reopened if it doesn't
        if (addons.shouldClose(addonCells, rewardLength)) closeMenu(reopen = true)
    }

    private fun playSuperpairs(screen: AbstractContainerScreen<*>, now: Long) {
        val slots = screen.menu.slots
        for (cell in superpairsCells) {
            val stack = slots.getOrNull(cell.slot)?.serverItem()
            cell.itemId = stack?.itemId().orEmpty()
            cell.name = stack?.hoverName?.unformattedText?.trim().orEmpty()
            cell.lore = stack?.lore?.map { it.removeFormatting().trim() }.orEmpty()
        }
        superpairs.update(superpairsCells, now)
        when {
            superpairs.stopped -> reportStuck("Superpairs clicks are not answered anymore. Finish the board yourself.")
            // closing the board at 0 clicks opens "Superpairs Rewards"
            superpairs.isFinished(now) -> closeMenu(reopen = true)
            else -> superpairs.nextClick(now, now >= nextClickTime)?.let { click(it, now) }
        }
    }

    private fun perform(action: MenuAction, now: Long) {
        when (action) {
            is MenuAction.Click -> click(action.slot, now)
            is MenuAction.Claim -> {
                GuiUtils.clickSlot(action.slot, GuiUtils.ButtonType.LEFT)
                nextClickTime = now + delay()
                reopenOnClose = true
            }

            is MenuAction.Close -> closeMenu(action.reopen)
            // give the menu time to load its items first
            is MenuAction.Stuck -> if (now - openedAt >= STUCK_MS) reportStuck(action.message)
            MenuAction.Wait -> Unit
        }
    }

    private fun click(slot: Int, now: Long) {
        GuiUtils.clickSlot(slot, GuiUtils.ButtonType.MIDDLE)
        nextClickTime = now + delay()
    }

    private fun reportStuck(message: String) {
        if (stuckReported) return
        stuckReported = true
        warn(message)
    }

    private fun closeMenu(reopen: Boolean) {
        reset()
        mc.player?.closeContainer()
        if (reopen) scheduleReopen()
    }

    private fun scheduleReopen() {
        reopenAt = System.currentTimeMillis() + REOPEN_DELAY_MS + delay()
    }

    private fun tickReopen(now: Long) {
        // hypixel or the player opened a menu
        if (mc.gui.screen() != null) return reset()
        if (now < reopenAt) return
        reopenAt = 0L
        PlayerUtils.rightClick()
        reopenCheckAt = now + REOPEN_CHECK_MS
    }

    private fun checkReopened(now: Long) {
        if (reopenCheckAt == 0L || now < reopenCheckAt) return
        reopenCheckAt = 0L
        if (mc.gui.screen() == null) warn("The Experimentation Table did not reopen. Look at the table.")
    }

    // the xp purchase and add-on rewards span several menus, so they're kept
    private fun reset() {
        addons.close()
        superpairs.close()
        experimentScreen = null
        nextClickTime = 0L
        stuckReported = false
        reopenOnClose = false
        reopenAt = 0L
        reopenCheckAt = 0L
    }

    private fun resetAll() {
        reset()
        flow.reset()
    }

    private fun delay() = (clickDelay.start..clickDelay.end).random().toLong()

    private const val REOPEN_DELAY_MS = 1000L
    private const val REOPEN_CHECK_MS = 3000L
    private const val STUCK_MS = 3000L
}

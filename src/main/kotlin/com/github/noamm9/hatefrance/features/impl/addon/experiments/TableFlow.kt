package com.github.noamm9.hatefrance.features.impl.addon.experiments

import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.ADDON_SLOTS
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.BOTTLES
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.BOTTLES_SLOT
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.CHRONOMATRON_STAKES
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.EXPERIMENT_OVER
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.EXPERIMENT_OVER_SLOT
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.RENEW_SLOT
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.RENEW_XP_COSTS
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.SUPERPAIRS_REWARDS
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.SUPERPAIRS_REWARDS_SLOT
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.SUPERPAIRS_SLOT
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.SUPERPAIRS_STAKES
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.TABLE
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.ULTRASEQUENCER_STAKES
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.has
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.info
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.warn
import com.github.noamm9.hatefrance.utils.ContainerUtils.findSlotByName
import com.github.noamm9.hatefrance.utils.ContainerUtils.slotLines
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

/**
 * Experimentation table menu flow: add-ons, superpairs at the highest stakes, rewards and renewals
 * Missing xp levels are bought in "Bottles of Enchanting" first
 */
internal class TableFlow(
    private val playSuperpairs: () -> Boolean,
    private val renewLimit: () -> Int
) {
    private val xp = XpPurchase()

    // longest chain or series of the picked add-on stakes, serums included
    var addonRewardLength = 0
        private set

    // no second renewal in the same menu, so a stale tooltip can't buy one too many
    private var renewClickedAt = 0L

    fun handles(title: String) = when (title) {
        TABLE, EXPERIMENT_OVER -> true
        SUPERPAIRS_REWARDS -> playSuperpairs()
        BOTTLES -> xp.active
        else -> when (ExperimentTable.stakesGame(title)) {
            "Chronomatron", "Ultrasequencer" -> true
            "Superpairs" -> playSuperpairs()
            else -> false
        }
    }

    fun menuOpened(title: String) {
        renewClickedAt = 0L
        if (title == TABLE) addonRewardLength = 0
    }

    // no table menu open anymore, so a started xp purchase is dropped
    fun reset() = xp.clear()

    fun next(screen: AbstractContainerScreen<*>, title: String, level: Int, now: Long): MenuAction = when (title) {
        SUPERPAIRS_REWARDS -> claim(screen, SUPERPAIRS_REWARDS_SLOT)
        EXPERIMENT_OVER -> claim(screen, EXPERIMENT_OVER_SLOT)
        TABLE -> table(screen, level, now)
        BOTTLES -> xp.next(screen, level, now) ?: backToTable(screen)
        else -> when (val game = ExperimentTable.stakesGame(title)) {
            "Superpairs" -> superpairsStakes(screen)
            "Chronomatron" -> addonStakes(screen, CHRONOMATRON_STAKES, game)
            "Ultrasequencer" -> addonStakes(screen, ULTRASEQUENCER_STAKES, game)
            else -> MenuAction.Wait
        }
    }

    private fun table(screen: AbstractContainerScreen<*>, level: Int, now: Long): MenuAction {
        if (screen.slotLines(SUPERPAIRS_SLOT).has("on cooldown")) {
            return renew(screen, level, now) ?: MenuAction.Close(reopen = false).also {
                info("All experiments are done for today.")
            }
        }
        for ((statusSlot, startSlot) in ADDON_SLOTS) {
            val status = screen.slotLines(statusSlot)
            if (status.has("Experiment completed") || status.has("Add-on locked!")) continue
            // claimed add-ons show "On cooldown!", locked ones "Unknown experiment!"
            if (screen.slotLines(startSlot).has("Click to play")) return MenuAction.Click(startSlot)
        }
        if (!playSuperpairs()) return MenuAction.Wait
        if (xp.active && level < xp.target) openBottles(screen)?.let { return it }
        if (screen.slotLines(SUPERPAIRS_SLOT).has("Click to play")) return MenuAction.Click(SUPERPAIRS_SLOT)
        return MenuAction.Stuck("Nothing on the Experimentation Table can be started.")
    }

    // null when no more charges should be bought today
    private fun renew(screen: AbstractContainerScreen<*>, level: Int, now: Long): MenuAction? {
        if (renewClickedAt != 0L) {
            // wait for the table to show the new charge
            if (now - renewClickedAt <= RENEW_TIMEOUT_MS) return MenuAction.Wait
            return stop("Renew Experiments did not answer.")
        }
        val lines = screen.slotLines(RENEW_SLOT)
        if (lines.firstOrNull() != "Renew Experiments") return null
        val cost = ExperimentTable.renewCost(lines) ?: return null
        // the xp cost tells which renewal of the day it is
        if (RENEW_XP_COSTS.indexOf(cost) !in 0 until renewLimit()) return null
        if (lines.has("Click to")) {
            xp.clear()
            renewClickedAt = now
            return MenuAction.Click(RENEW_SLOT)
        }
        if (!lines.has("Cannot afford")) return null
        if (level >= cost) return stop("Not enough bits for Renew Experiments.")
        // xp.fail already told the player
        if (xp.failed) return MenuAction.Close(reopen = false)
        xp.start(cost, XpPurchase.Purpose.RENEW)
        return openBottles(screen) ?: MenuAction.Close(reopen = false)
    }

    private fun stop(message: String): MenuAction {
        warn(message)
        return MenuAction.Close(reopen = false)
    }

    private fun superpairsStakes(screen: AbstractContainerScreen<*>): MenuAction {
        for (slot in SUPERPAIRS_STAKES) {
            val lines = screen.slotLines(slot)
            if (lines.isEmpty() || lines.has("Enchanting level too low!")) continue
            if (lines.has("Click to play")) {
                xp.clear()
                return MenuAction.Click(slot)
            }
            if (xp.failed || !lines.has("Not enough experience!")) continue
            val cost = ExperimentTable.startingCost(lines) ?: continue
            xp.start(cost, XpPurchase.Purpose.SUPERPAIRS)
            return backToTable(screen)
        }
        return MenuAction.Stuck("No Superpairs stakes can be played.")
    }

    private fun addonStakes(screen: AbstractContainerScreen<*>, stakes: IntProgression, game: String): MenuAction {
        for (slot in stakes) {
            val lines = screen.slotLines(slot)
            if (lines.isEmpty() || lines.has("Enchanting level too low!")) continue
            if (lines.has("Not enough experience!")) continue
            addonRewardLength = ExperimentTable.rewardLength(lines) ?: 0
            return MenuAction.Click(slot)
        }
        return MenuAction.Stuck("No $game stakes can be played.")
    }

    private fun claim(screen: AbstractContainerScreen<*>, slot: Int): MenuAction {
        val lines = screen.slotLines(slot)
        return when {
            lines.isEmpty() -> MenuAction.Wait
            lines.has("Click to claim") -> MenuAction.Claim(slot)
            else -> MenuAction.Close(reopen = true)
        }
    }

    private fun openBottles(screen: AbstractContainerScreen<*>): MenuAction? {
        val button = screen.slotLines(BOTTLES_SLOT).firstOrNull()
        if (button == "Experience Bottles") return MenuAction.Click(BOTTLES_SLOT)
        xp.fail("The Experience Bottles button is missing.")
        return null
    }

    private fun backToTable(screen: AbstractContainerScreen<*>): MenuAction {
        // "Go Back" moves between menus and game versions
        return screen.findSlotByName("Go Back")?.let { MenuAction.Click(it) } ?: MenuAction.Close(reopen = true)
    }

    private companion object {
        const val RENEW_TIMEOUT_MS = 5000L
    }
}

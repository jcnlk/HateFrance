package com.github.noamm9.hatefrance.features.impl.addon.experiments

import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.TITANIC_SLOT
import com.github.noamm9.hatefrance.features.impl.addon.experiments.ExperimentTable.warn
import com.github.noamm9.hatefrance.utils.ContainerUtils.slotLines
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

/**
 * Buys missing xp levels with titanic experience bottles
 * Owned bottles are used first, otherwise hypixel buys one from the bazaar
 */
internal class XpPurchase {
    enum class Purpose(val fallback: String) {
        SUPERPAIRS("Playing the highest stakes you can pay."),
        RENEW("Skipping Renew Experiments.")
    }

    // level to reach, 0 when nothing is bought
    var target = 0
        private set

    var failed = false
        private set

    private var purpose = Purpose.SUPERPAIRS
    private var bottles = 0
    private var levelBefore = 0
    private var clickedAt = 0L

    val active get() = target != 0

    fun start(levels: Int, purpose: Purpose) {
        target = levels
        this.purpose = purpose
        bottles = 0
        clickedAt = 0L
    }

    fun clear() {
        target = 0
        failed = false
        bottles = 0
        clickedAt = 0L
    }

    fun fail(reason: String) {
        if (!failed) warn("$reason ${purpose.fallback}")
        target = 0
        failed = true
        clickedAt = 0L
    }

    /**
     * Next step in "Bottles of Enchanting", null once the menu should be left
     */
    fun next(screen: AbstractContainerScreen<*>, level: Int, now: Long): MenuAction? {
        if (target == 0 || level >= target) return null
        if (clickedAt != 0L) {
            if (level <= levelBefore) {
                if (now - clickedAt < BOTTLE_TIMEOUT_MS) return MenuAction.Wait
                fail("The Titanic Experience Bottle did not raise your level. Check your coins.")
                return null
            }
            clickedAt = 0L
        }
        if (bottles >= MAX_BOTTLES) {
            fail("$MAX_BOTTLES Titanic Experience Bottles were not enough for $target levels.")
            return null
        }
        if (screen.slotLines(TITANIC_SLOT).firstOrNull() != "Titanic Experience Bottle") {
            fail("The Titanic Experience Bottle is missing.")
            return null
        }
        bottles++
        levelBefore = level
        clickedAt = now
        return MenuAction.Click(TITANIC_SLOT)
    }

    private companion object {
        // 1,100,000 xp orbs per bottle, about level 275 to 444
        const val MAX_BOTTLES = 5
        const val BOTTLE_TIMEOUT_MS = 3000L
    }
}

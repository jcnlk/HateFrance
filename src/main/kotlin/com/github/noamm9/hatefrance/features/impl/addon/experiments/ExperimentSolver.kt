package com.github.noamm9.hatefrance.features.impl.addon.experiments

// registry ids instead of Items fields, those differ between game versions
internal class Cell(val slot: Int) {
    var itemId = ""
    var count = 0
    var foil = false
}

/**
 * Chronomatron and ultrasequencer solver
 * Slot 49 shows glowstone while the pattern is shown and a clock while it has to be repeated
 */
internal class ExperimentSolver {
    private enum class Mode { NONE, CHRONOMATRON, ULTRASEQUENCER }

    private var mode = Mode.NONE
    private val chronomatron = mutableListOf<Int>()
    private var chronoRevealSeen = false
    private var chronoIndex = 0
    private var chronoRoundComplete = false
    private val ultrasequencer = IntArray(36) { -1 }
    private var ultraSize = 0
    private var ultraIndex = 0

    val active get() = mode != Mode.NONE

    fun open(title: String) {
        mode = when {
            title.startsWith("Chronomatron (") -> Mode.CHRONOMATRON
            title.startsWith("Ultrasequencer (") -> Mode.ULTRASEQUENCER
            else -> Mode.NONE
        }
        clear()
    }

    fun close() {
        mode = Mode.NONE
        clear()
    }

    fun nextClick(cells: List<Cell>, canClick: Boolean): Int? {
        return when (mode) {
            Mode.CHRONOMATRON -> nextChronomatron(cells, canClick)
            Mode.ULTRASEQUENCER -> nextUltrasequencer(cells, canClick)
            Mode.NONE -> null
        }
    }

    /**
     * True once the "Chain of N" or "Series of N" from the stakes tooltip is completed, or the xp cap without one
     * Checked once the next round is shown, so the server already counted the last one
     */
    fun shouldClose(cells: List<Cell>, rewardLength: Int?): Boolean {
        return when (mode) {
            Mode.CHRONOMATRON -> chronoRoundComplete && chronomatron.size >= (rewardLength ?: MAX_CHAIN)
            // hypixel bug: the "Series of N" clicks are already given after N - 1 (see skyhanni)
            Mode.ULTRASEQUENCER -> control(cells) == GLOWSTONE &&
                    ultraSize > (rewardLength?.minus(1) ?: MAX_SERIES)

            Mode.NONE -> false
        }
    }

    private fun nextChronomatron(cells: List<Cell>, canClick: Boolean): Int? {
        val control = control(cells) ?: return null
        if (control == GLOWSTONE) {
            if (chronoRevealSeen && chronoIndex >= chronomatron.size) chronoRoundComplete = true
            chronoRevealSeen = false
            chronoIndex = 0
            return null
        }
        if (control != CLOCK) return null

        cells.firstOrNull { it.slot in 10..43 && it.foil }?.let { reveal ->
            if (!chronoRevealSeen) {
                chronomatron += reveal.slot
                chronoRevealSeen = true
                chronoRoundComplete = false
                chronoIndex = 0
            }
        }
        return chronomatron.getOrNull(chronoIndex)?.takeIf { canClick }?.also { chronoIndex++ }
    }

    private fun nextUltrasequencer(cells: List<Cell>, canClick: Boolean): Int? {
        val control = control(cells) ?: return null
        if (control == GLOWSTONE) {
            clearUltrasequencer()
            for (cell in cells) {
                if (cell.slot !in 9..44 || !isSequenceItem(cell.itemId)) continue
                if (cell.count !in 1..36 || ultrasequencer[cell.count - 1] != -1) {
                    clearUltrasequencer()
                    break
                }
                ultrasequencer[cell.count - 1] = cell.slot
                ultraSize++
            }
            if ((0 until ultraSize).any { ultrasequencer[it] == -1 }) clearUltrasequencer()
            return null
        }
        if (control != CLOCK || !canClick || ultraIndex >= ultraSize) return null
        return ultrasequencer[ultraIndex].also { ultraIndex++ }
    }

    private fun clearUltrasequencer() {
        ultrasequencer.fill(-1)
        ultraSize = 0
        ultraIndex = 0
    }

    private fun clear() {
        chronomatron.clear()
        chronoRevealSeen = false
        chronoIndex = 0
        chronoRoundComplete = false
        clearUltrasequencer()
    }

    private fun control(cells: List<Cell>) = cells.firstOrNull { it.slot == 49 }?.itemId

    private fun isSequenceItem(id: String) =
        id.endsWith("_dye") || id == "minecraft:lapis_lazuli" || id == "minecraft:bone_meal"

    private companion object {
        const val GLOWSTONE = "minecraft:glowstone"
        const val CLOCK = "minecraft:clock"

        // no more xp after these
        const val MAX_CHAIN = 15
        const val MAX_SERIES = 20
    }
}

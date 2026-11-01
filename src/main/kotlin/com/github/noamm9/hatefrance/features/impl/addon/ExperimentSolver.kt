package com.github.noamm9.hatefrance.features.impl.addon

internal class Cell(
    val slot: Int,
    var itemId: String = "",
    var count: Int = 0,
    var foil: Boolean = false
)

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

    fun shouldClose(cells: List<Cell>, chronoTarget: Int, ultraTarget: Int): Boolean {
        return when (mode) {
            Mode.CHRONOMATRON -> chronoRoundComplete && chronomatron.size > chronoTarget
            Mode.ULTRASEQUENCER -> cell(cells)?.itemId == "minecraft:clock" &&
                    ultraIndex >= ultraSize && ultraSize >= ultraTarget

            Mode.NONE -> false
        }
    }

    private fun nextChronomatron(cells: List<Cell>, canClick: Boolean): Int? {
        val control = cell(cells)?.itemId ?: return null
        if (control == "minecraft:glowstone") {
            if (chronoRevealSeen && chronoIndex >= chronomatron.size) chronoRoundComplete = true
            chronoRevealSeen = false
            chronoIndex = 0
            return null
        }
        if (control != "minecraft:clock") return null

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
        val control = cell(cells)?.itemId ?: return null
        if (control == "minecraft:glowstone") {
            clearUltrasequencer()
            for (cell in cells) {
                if (cell.slot !in 9..44 || !isSequenceItem(cell)) continue
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
        if (control != "minecraft:clock" || !canClick || ultraIndex >= ultraSize) return null
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

    private fun cell(cells: List<Cell>) = cells.firstOrNull { it.slot == 49 }

    private fun isSequenceItem(cell: Cell): Boolean {
        val item = cell.itemId.substringAfter(':')
        return item.endsWith("_dye") || item == "lapis_lazuli" || item == "bone_meal"
    }
}

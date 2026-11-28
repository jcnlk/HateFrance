package com.github.noamm9.hatefrance.features.impl.addon.experiments

internal class SuperpairsCell(val slot: Int) {
    var itemId = ""
    var name = ""
    var lore = emptyList<String>()
}

/**
 * Superpairs solver working from the board state
 *
 * Face-down cards are cyan stained glass, their name shows the turn state: "Click any button!",
 * "Click a second button!", "?" while a wrong pair is shown and "Next button is instantly rewarded!" after instant find
 * Shown cards are remembered, known pairs are taken first and powerups are single cards
 * Slot 4 shows "Remaining Clicks: N", at 0 all cards are revealed and the board has to be closed to get the rewards
 */
internal class SuperpairsSolver {
    private data class Face(val itemId: String, val name: String, val lore: List<String>)
    private class Card(val key: Face, val powerup: Boolean, val experience: Boolean)

    var active = false
        private set

    private val cardSlots = HashSet<Int>()
    private val hidden = HashSet<Int>()
    private val seen = HashMap<Int, Card>()
    private val clicked = HashSet<Int>()
    private val turns = ArrayList<Pair<Int, Int>>()
    private val instantRewarded = HashSet<Int>()
    private val refused = HashSet<Int>()
    private var instantSlot: Int? = null
    private var turnFirst: Int? = null
    private var lastClicked: Int? = null
    private var prompt: String? = null
    private var clicksLeft = -1
    private var pending: Int? = null
    private var pendingWasHidden = false
    private var pendingClicks = -1
    private var pendingPrompt: String? = null
    private var pendingSince = 0L
    private var settledAt = 0L
    private var failures = 0

    // clicks stopped getting answered, the rest of the board is left to the player
    var stopped = false
        private set

    private var avoidExperience = false

    val blocksInput get() = active && !stopped

    fun open(title: String, enabled: Boolean, avoidExperience: Boolean) {
        clear()
        active = enabled && title.startsWith("Superpairs (")
        this.avoidExperience = avoidExperience
    }

    fun close() {
        clear()
        active = false
    }

    fun update(cells: List<SuperpairsCell>, now: Long) {
        if (!active || stopped) return
        cells.getOrNull(CLICKS_SLOT)?.let { cell ->
            CLICKS_PATTERN.find(cell.name)?.groupValues?.get(1)?.toIntOrNull()?.let { clicksLeft = it }
        }

        hidden.clear()
        var boardPrompt: String? = null
        for (slot in BOARD) {
            val cell = cells.getOrNull(slot) ?: continue
            if (isHidden(cell)) {
                cardSlots += slot
                hidden += slot
                if (boardPrompt == null || PROMPT_PRIORITY.indexOf(cell.name) > PROMPT_PRIORITY.indexOf(boardPrompt)) {
                    boardPrompt = cell.name
                }
            } else if (slot in cardSlots && cell.itemId.isNotEmpty() && clicksLeft != 0) {
                seen[slot] = Card(
                    Face(cell.itemId, cell.name, cell.lore.filterNot(String::isBlank)),
                    cell.lore.any { it.contains("powerup", ignoreCase = true) },
                    cell.name.endsWith("Enchanting Exp")
                )
            }
        }
        prompt = boardPrompt
        resolveInstantFind()

        pending?.let { slot ->
            val answered = if (pendingWasHidden) slot !in hidden || clicksLeft != pendingClicks
            else clicksLeft != pendingClicks || prompt != pendingPrompt
            if (answered) {
                pending = null
                failures = 0
                settledAt = now
            } else if (now - pendingSince > CLICK_TIMEOUT_MS) {
                forgetPending()
                if (++failures >= MAX_FAILURES) stopped = true
            }
        }

        if (pending == null) when (prompt) {
            ANY_PROMPT, INSTANT_PROMPT -> turnFirst = null
            SECOND_PROMPT -> if (turnFirst == null) turnFirst = recoverTurnFirst()
        }
    }

    /**
     * No clicks left and the last one was answered
     */
    fun isFinished(now: Long): Boolean {
        return active && !stopped && clicksLeft == 0 && pending == null && now - settledAt >= FINISH_DELAY_MS
    }

    fun nextClick(now: Long, canClick: Boolean): Int? {
        if (!active || stopped || !canClick || pending != null || clicksLeft <= 0) return null
        if (prompt == ANSWERING_PROMPT || now - settledAt < SETTLE_MS) return null
        return decide()?.also { clicked(it, now) }
    }

    private fun decide(): Int? {
        val taken = taken()
        val first = turnFirst
        val available = cardSlots.filter { slot ->
            slot !in taken && slot != first && (slot in hidden || seen[slot]?.powerup == false)
        }.sorted()
        val known = available.filter { seen[it]?.powerup == false }.groupBy { seen.getValue(it).key }
        val (pairs, singles) = known.values.partition { it.size >= 2 }.let { (pairs, singles) ->
            pairs.sortedBy { isAvoided(it.first()) } to singles.map { it.first() }.sortedBy(::isAvoided)
        }
        val unknown = available.filter { it in hidden && it !in seen }.sortedBy { it in clicked }
        val wantedPair = pairs.firstOrNull()?.takeUnless { isAvoided(it.first()) }

        if (prompt == INSTANT_PROMPT) {
            return singles.firstOrNull()?.takeUnless(::isAvoided) ?: unknown.firstOrNull() ?: wantedPair?.first()
            ?: singles.firstOrNull() ?: pairs.firstOrNull()?.first()
        }
        if (first != null) {
            val firstCard = seen[first] ?: return null
            // the first click is spent either way, so a known twin is always matched
            return known[firstCard.key]?.firstOrNull() ?: unknown.firstOrNull() ?: singles.firstOrNull()
        }
        if (clicksLeft < 2) return unknown.firstOrNull()
        return wantedPair?.first() ?: unknown.firstOrNull() ?: pairs.firstOrNull()?.first()
    }

    private fun isAvoided(slot: Int) = avoidExperience && seen[slot]?.experience == true

    private fun clicked(slot: Int, now: Long) {
        lastClicked = slot
        clicked += slot
        instantSlot = null
        val first = turnFirst
        when {
            prompt == INSTANT_PROMPT -> {
                instantRewarded += slot
                instantSlot = slot
                turnFirst = null
            }

            first == null -> turnFirst = slot
            else -> {
                turns += first to slot
                turnFirst = null
            }
        }
        pending = slot
        pendingWasHidden = slot in hidden
        pendingClicks = clicksLeft
        pendingPrompt = prompt
        pendingSince = now
    }

    private fun forgetPending() {
        val slot = pending ?: return
        pending = null
        clicked -= slot
        // a face-up card that ignores clicks was already collected
        if (slot !in hidden) refused += slot
        when {
            instantSlot == slot -> {
                instantRewarded -= slot
                instantSlot = null
            }

            turns.lastOrNull()?.second == slot -> turnFirst = turns.removeAt(turns.lastIndex).first
            turnFirst == slot -> turnFirst = null
        }
    }

    // the server still waits for a second card: the last one was a powerup or the turn was missed
    private fun recoverTurnFirst(): Int? {
        val last = turns.lastOrNull()
        if (last != null && last.second == lastClicked && seen[last.second]?.powerup == true) {
            turns.removeAt(turns.lastIndex)
            return last.first
        }
        return lastClicked?.takeIf { it !in taken() }
    }

    // instant find also reveals the partner of the rewarded card, both are collected
    private fun resolveInstantFind() {
        val slot = instantSlot ?: return
        val key = seen[slot]?.key ?: return
        val taken = taken()
        val partner = seen.entries.firstOrNull { (other, card) ->
            other != slot && other !in hidden && other !in taken && card.key == key
        }?.key ?: return
        instantRewarded += partner
        instantSlot = null
    }

    private fun taken(): Set<Int> {
        val taken = HashSet(instantRewarded)
        taken += refused
        for ((first, second) in turns) {
            val key = seen[first]?.key ?: continue
            if (first != second && seen[second]?.key == key) {
                taken += first
                taken += second
            }
        }
        return taken
    }

    private fun clear() {
        cardSlots.clear()
        hidden.clear()
        seen.clear()
        clicked.clear()
        turns.clear()
        instantRewarded.clear()
        refused.clear()
        instantSlot = null
        turnFirst = null
        lastClicked = null
        prompt = null
        clicksLeft = -1
        pending = null
        pendingWasHidden = false
        pendingClicks = -1
        pendingPrompt = null
        pendingSince = 0L
        settledAt = 0L
        failures = 0
        stopped = false
    }

    private fun isHidden(cell: SuperpairsCell) = cell.itemId == HIDDEN_ITEM ||
            cell.itemId.endsWith("_stained_glass") && HIDDEN_NAME.matches(cell.name)

    private companion object {
        const val CLICKS_SLOT = 4
        const val HIDDEN_ITEM = "minecraft:cyan_stained_glass"
        const val ANY_PROMPT = "Click any button!"
        const val SECOND_PROMPT = "Click a second button!"
        const val INSTANT_PROMPT = "Next button is instantly rewarded!"
        const val ANSWERING_PROMPT = "?"
        val PROMPT_PRIORITY = listOf(ANY_PROMPT, INSTANT_PROMPT, SECOND_PROMPT, ANSWERING_PROMPT)
        val BOARD = 9..44
        val HIDDEN_NAME = Regex("""\?|Click a(?: seco)?n[dy] button!?|Next button is instantly rewarded!?""")
        val CLICKS_PATTERN = Regex("""Remaining Clicks: (\d+)""")

        const val SETTLE_MS = 150L
        const val CLICK_TIMEOUT_MS = 2000L
        const val FINISH_DELAY_MS = 1000L
        const val MAX_FAILURES = 3
    }
}

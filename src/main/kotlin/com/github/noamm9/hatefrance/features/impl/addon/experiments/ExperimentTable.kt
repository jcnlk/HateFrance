package com.github.noamm9.hatefrance.features.impl.addon.experiments

import com.github.noamm9.utils.ChatUtils

/**
 * Experimentation table menus, slots and tooltip texts, taken from the wiki and gameplay videos
 */
internal object ExperimentTable {
    const val TABLE = "Experimentation Table"
    const val EXPERIMENT_OVER = "Experiment Over"
    const val SUPERPAIRS_REWARDS = "Superpairs Rewards"
    const val BOTTLES = "Bottles of Enchanting"

    // main menu
    const val SUPERPAIRS_SLOT = 22
    const val RENEW_SLOT = 31
    const val BOTTLES_SLOT = 50

    // status pane to start button of chronomatron and ultrasequencer
    val ADDON_SLOTS = listOf(21 to 29, 23 to 33)

    // highest stakes first. superpairs: metaphysical, transcendent, supreme, grand, high, beginner
    val CHRONOMATRON_STAKES = 24 downTo 20
    val ULTRASEQUENCER_STAKES = 23 downTo 21
    val SUPERPAIRS_STAKES = listOf(32, 31, 30, 23, 22, 21)

    const val TITANIC_SLOT = 14
    const val EXPERIMENT_OVER_SLOT = 11
    const val SUPERPAIRS_REWARDS_SLOT = 13

    // xp cost of the 1st, 2nd and 3rd renewal of the day
    val RENEW_XP_COSTS = listOf(50, 100, 200)

    private val REWARD_LENGTH = Regex("""(?:Chain|Series) of (\d+):""")
    private val STARTING_COST = Regex("""Starting cost: (\d+) XP Levels?""", RegexOption.IGNORE_CASE)
    private val RENEW_COST = Regex("""^(\d+) XP Levels?$""", RegexOption.IGNORE_CASE)

    /**
     * Game of a stakes menu like "Superpairs ➜ Stakes", null for other menus
     */
    fun stakesGame(title: String) = title.takeIf { it.endsWith("Stakes") }?.substringBefore(' ')

    /**
     * Longest "Chain of N" or "Series of N" that gives superpairs clicks, serums already included
     */
    fun rewardLength(lines: List<String>) = lines.mapNotNull { REWARD_LENGTH.find(it)?.intGroup() }.maxOrNull()

    fun startingCost(lines: List<String>) = lines.firstNotNullOfOrNull { STARTING_COST.find(it)?.intGroup() }

    fun renewCost(lines: List<String>) = lines.firstNotNullOfOrNull { RENEW_COST.find(it)?.intGroup() }

    fun List<String>.has(text: String) = any { it.contains(text, ignoreCase = true) }

    fun warn(message: String) = ChatUtils.modMessage("&cAuto Experiments: $message")

    fun info(message: String) = ChatUtils.modMessage("&aAuto Experiments: $message")

    private fun MatchResult.intGroup() = groupValues[1].toIntOrNull()
}

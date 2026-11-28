package com.github.noamm9.hatefrance.features.impl.addon.experiments

/**
 * Next step in an experimentation table menu, carried out by [AutoExperiments]
 */
internal sealed interface MenuAction {
    data class Click(val slot: Int) : MenuAction

    // hypixel closes the menu after claiming, then the table is reopened for the next charge
    data class Claim(val slot: Int) : MenuAction

    // reopen: open the table again unless hypixel opens another menu first
    data class Close(val reopen: Boolean) : MenuAction

    // nothing to click, message is shown once if the menu stays like this
    data class Stuck(val message: String) : MenuAction

    // waiting for the server, e.g. after a purchase
    data object Wait : MenuAction
}

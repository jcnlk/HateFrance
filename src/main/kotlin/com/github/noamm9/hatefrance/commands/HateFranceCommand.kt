package com.github.noamm9.hatefrance.commands

import com.github.noamm9.commands.CommandBuilder
import com.github.noamm9.init.types.ICommandProvider
import com.github.noamm9.ui.clickgui.ClickGuiScreen
import com.github.noamm9.utils.GuiUtils

object HateFranceCommand : ICommandProvider {
    internal val names = arrayOf("hatefrance", "hf")

    override fun CommandBuilder.command() {
        setName(*names)
        runs { GuiUtils.setScreen(ClickGuiScreen()) }
    }
}

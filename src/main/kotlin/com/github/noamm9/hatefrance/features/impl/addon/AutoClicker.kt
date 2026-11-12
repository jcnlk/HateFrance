package com.github.noamm9.hatefrance.features.impl.addon

import com.github.noamm9.commands.CommandBuilder
import com.github.noamm9.config.ConfigManager
import com.github.noamm9.config.types.KeybindSetting
import com.github.noamm9.config.types.RangeSliderSetting
import com.github.noamm9.config.types.TextInputSetting
import com.github.noamm9.config.types.ToggleSetting
import com.github.noamm9.event.impl.KeyboardEvent
import com.github.noamm9.event.impl.MouseClickEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.init.types.ICommandProvider
import com.github.noamm9.utils.ChatUtils
import com.github.noamm9.utils.MathUtils
import com.github.noamm9.utils.PlayerUtils
import com.github.noamm9.utils.items.ItemUtils.itemUUID
import com.github.noamm9.utils.items.ItemUtils.skyblockId
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.*
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.world.phys.BlockHitResult
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

// https://github.com/jcnlk/quoi/blob/multiversion/src/main/kotlin/quoi/module/impl/general/AutoClicker.kt
object AutoClicker : Feature(
    name = "Auto Clicker",
    description = "Automatically clicks while the configured mouse or keyboard key is held."
), ICommandProvider {
    private val breakBlocks by ToggleSetting("Allow Breaking Blocks")
        .withDescription("Keeps left click held while looking at a breakable block.")
    private val stopOnSwap by ToggleSetting("Stop On Item Swap")
        .withDescription("Stops both clickers when the selected hotbar slot changes.")
    private val favouriteItems by ToggleSetting("Whitelist Only")
        .withDescription("Only clicks while holding an item listed below.")
    private val favLeft by TextInputSetting("Left Click Whitelist", "")
        .withDescription("Comma-separated item UUIDs, SkyBlock IDs, or display names.")
        .showIf { favouriteItems.value }
    private val favRight by TextInputSetting("Right Click Whitelist", "")
        .withDescription("Comma-separated item UUIDs, SkyBlock IDs, or display names.").showIf { favouriteItems.value }

    private val blockDungeonBreaker by ToggleSetting("Block Dungeon Breaker")
        .withDescription("Prevents clicking while holding Dungeon Breaker.")

    private val leftClick by ToggleSetting("Enable Left Click")
    private val leftCps by RangeSliderSetting("Left CPS", 10 to 12, 1, 20, 1).showIf { leftClick.value }
    private val leftClickKeybind by KeybindSetting(
        "Left Click Keybind",
        InputConstants.MOUSE_BUTTON_LEFT
    ).apply { isMouse = true }

    private val rightClick by ToggleSetting("Enable Right Click")
    private val rightCps by RangeSliderSetting("Right CPS", 10 to 12, 1, 20, 1).showIf { rightClick.value }
    private val rightClickKeybind by KeybindSetting(
        "Right Click Keybind",
        InputConstants.MOUSE_BUTTON_RIGHT
    ).apply { isMouse = true }

    private val left = ClickState()
    private val right = ClickState()
    private var lastHeldSlot = -1
    private var isMining = false
    private val heldMouseButtons = BooleanArray(9)
    private val whitelistCache = mutableMapOf<TextInputSetting, Pair<String, Set<String>>>()
    private val clickScope by lazy { CoroutineScope(SupervisorJob() + mc.asCoroutineDispatcher()) }
    private val random = Random()

    private class ClickState {
        var job: Job? = null
        var blocked = false
        var lastDriftTime = 0L
        var baseCpsDrift = 10.0
    }

    override fun init() {
        register<KeyboardEvent.KeyPressed>(receiveCancelled = true) {
            if (event.action != InputConstants.PRESS) return@register
            updateHeldSlot()
            val key = event.keyEvent.key
            if (!leftClickKeybind.isMouse && leftClickKeybind.value == key) left.blocked = false
            if (!rightClickKeybind.isMouse && rightClickKeybind.value == key) right.blocked = false
        }

        register<MouseClickEvent>(receiveCancelled = true) {
            if (event.button !in heldMouseButtons.indices) return@register
            updateHeldSlot()
            heldMouseButtons[event.button] = event.action == InputConstants.PRESS
            if (event.action == InputConstants.RELEASE) {
                if (leftClickKeybind.isMouse && leftClickKeybind.value == event.button) {
                    left.blocked = false
                    stopClicking(true)
                }
                if (rightClickKeybind.isMouse && rightClickKeybind.value == event.button) {
                    right.blocked = false
                    stopClicking(false)
                }
                updateMiningState()
            }
            if (event.action != InputConstants.PRESS || mc.gui.screen() != null || !mc.isWindowActive) return@register

            for (side in 0..1) {
                val isLeft = side == 0
                val keybind = if (isLeft) leftClickKeybind else rightClickKeybind
                if (!keybind.isMouse || keybind.value != event.button || !shouldAutoClick(isLeft)) continue
                if (isLeft) PlayerUtils.leftClick() else PlayerUtils.rightClick()
                startClicking(isLeft, delayFirstClick = true)
                event.isCanceled = true
            }
        }

        register<TickEvent.End> {
            if (mc.player == null) {
                reset()
                return@register
            }

            updateHeldSlot()
            if (!isHeld(leftClickKeybind)) left.blocked = false
            if (!isHeld(rightClickKeybind)) right.blocked = false

            if (mc.gui.screen() != null || !mc.isWindowActive) {
                reset()
                return@register
            }

            if (shouldAutoClick(true)) startClicking(true) else stopClicking(true)
            if (shouldAutoClick(false)) startClicking(false) else stopClicking(false)
            updateMiningState()
        }

        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    override fun CommandBuilder.command() {
        setName("autoclicker", "ac")
        description("Manage the Auto Clicker whitelist")
        for (action in WhitelistAction.entries) {
            literal(action.name.lowercase()) {
                argument("button", StringArgumentType.word()) {
                    suggests { listOf("left", "right") }
                    runs { context -> updateWhitelist(context, action) }
                }
            }
        }
    }

    private enum class WhitelistAction { ADD, REMOVE, CLEAR }

    private fun updateWhitelist(
        context: CommandContext<FabricClientCommandSource>,
        action: WhitelistAction
    ) {
        val side = StringArgumentType.getString(context, "button").lowercase()
        val setting = when (side) {
            "left" -> favLeft
            "right" -> favRight
            else -> {
                ChatUtils.modMessage("&cUse left or right as the button.")
                return
            }
        }

        val previousValue = setting.value
        val values = whitelistValues(setting).toMutableSet()
        val item = heldItemKey()
        val message = when {
            action == WhitelistAction.CLEAR -> {
                setting.value = ""
                "&aCleared the $side list."
            }
            item == null -> "&cYou are not holding an item."
            action == WhitelistAction.ADD && !values.add(item) -> "&cThis item is already in the $side list."
            action == WhitelistAction.REMOVE && !values.remove(item) -> "&cThis item is not in the $side list."
            else -> {
                setting.value = values.sorted().joinToString(", ")
                if (action == WhitelistAction.ADD) "&aAdded ${itemDisplayName()} &ato the $side list."
                else "&aRemoved ${itemDisplayName()} &afrom the $side list."
            }
        }
        if (setting.value != previousValue) ConfigManager.save()
        ChatUtils.modMessage(message)
    }

    private fun heldItemKey(): String? {
        val stack = mc.player?.mainHandItem?.takeUnless { it.isEmpty } ?: return null
        return stack.itemUUID.takeUnless(String::isBlank)
            ?: stack.skyblockId.takeUnless(String::isBlank)
            ?: stack.hoverName.string.takeUnless(String::isBlank)
    }

    private fun itemDisplayName(): String =
        mc.player?.mainHandItem?.hoverName?.string ?: "item"

    private fun whitelistValues(setting: TextInputSetting): Set<String> {
        val text = setting.value
        val cached = whitelistCache[setting]
        if (cached?.first == text) return cached.second
        return text.split(',').map(String::trim).filter(String::isNotEmpty).toSet().also {
            whitelistCache[setting] = text to it
        }
    }

    private fun shouldClick(isLeft: Boolean): Boolean {
        if (mc.gui.screen() != null || !mc.isWindowActive) return false
        val stack = mc.player?.mainHandItem ?: return false
        val skyblockId = if (blockDungeonBreaker.value) stack.skyblockId else null
        if (skyblockId == "DUNGEONBREAKER") {
            return false
        }

        if (!favouriteItems.value) return true
        if (stack.isEmpty) return false
        val values = whitelistValues(if (isLeft) favLeft else favRight)
        if (values.isEmpty()) return false
        return stack.itemUUID in values || (skyblockId
            ?: stack.skyblockId) in values || stack.hoverName.string in values
    }

    private fun isHeld(keybind: KeybindSetting): Boolean {
        if (keybind.isMouse) return heldMouseButtons.getOrElse(keybind.value) { false }
        if (keybind.value <= 0) return false
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(keybind.value)
        *///?} else {
        return InputConstants.isKeyDown(mc.window, keybind.value)
        //?}
    }

    private fun updateHeldSlot() {
        val currentSlot = mc.player?.inventory?.selectedSlot ?: return
        if (stopOnSwap.value && lastHeldSlot != -1 && currentSlot != lastHeldSlot) {
            stopClicking(true)
            stopClicking(false)
            left.blocked = isHeld(leftClickKeybind)
            right.blocked = isHeld(rightClickKeybind)
        }
        lastHeldSlot = currentSlot
    }

    private fun shouldAutoClick(isLeft: Boolean): Boolean {
        val enabledForButton = if (isLeft) leftClick else rightClick
        val keybind = if (isLeft) leftClickKeybind else rightClickKeybind
        if ((if (isLeft) left else right).blocked) return false
        if (stopOnSwap.value && lastHeldSlot != -1 && mc.player?.inventory?.selectedSlot != lastHeldSlot) return false
        return enabled && enabledForButton.value && isHeld(keybind) && shouldClick(isLeft)
    }

    private val lookingAtBreakable: Boolean
        get() {
            if (!breakBlocks.value) return false
            val hit = mc.hitResult as? BlockHitResult ?: return false
            val level = mc.level ?: return false
            val state = level.getBlockState(hit.blockPos)
            return !state.isAir && state.fluidState.isEmpty
        }

    private fun startClicking(isLeft: Boolean, delayFirstClick: Boolean = false) {
        val state = if (isLeft) left else right
        if (state.job?.isActive == true) return
        state.job = clickScope.launch { click(isLeft, delayFirstClick) }
    }

    private fun stopClicking(isLeft: Boolean) {
        val state = if (isLeft) left else right
        state.job?.cancel()
        state.job = null
    }

    private fun updateMiningState() {
        val shouldMine = left.job?.isActive == true && lookingAtBreakable
        if (shouldMine == isMining) return

        mc.options.keyAttack.isDown = shouldMine
        isMining = shouldMine
    }

    private fun getNextClick(now: Long, isLeft: Boolean): Long {
        val timing = if (isLeft) left else right
        if (now - timing.lastDriftTime > 1000) {
            val cps = if (isLeft) leftCps else rightCps
            val targetCps = ((cps.start + cps.end) / 2.0).coerceAtLeast(1.0)
            timing.baseCpsDrift = (targetCps + MathUtils.gaussianRandom(-10, 10) / 10.0).coerceAtLeast(1.0)
            timing.lastDriftTime = now
        }

        val baseDelay = (1000.0 / timing.baseCpsDrift).toLong()
        val gaussian = random.nextGaussian()
        val offset = if (gaussian < 0) (gaussian * 10).toLong() else (gaussian * 25).toLong()
        var finalDelay = baseDelay + offset

        val roll = random.nextDouble()
        if (roll < 0.01) finalDelay += random.nextInt(100, 250)
        else if (roll < 0.03) finalDelay = random.nextInt(5, 15).toLong()

        return now + finalDelay.coerceAtLeast(1L)
    }

    private suspend fun click(isLeft: Boolean, delayFirstClick: Boolean) {
        if (delayFirstClick) {
            val now = System.currentTimeMillis()
            delay((getNextClick(now, isLeft) - now).coerceAtLeast(1L).milliseconds)
        }
        while (currentCoroutineContext().isActive && shouldAutoClick(isLeft)) {
            if (!isLeft) PlayerUtils.rightClick()
            else if (!lookingAtBreakable) PlayerUtils.leftClick()

            val now = System.currentTimeMillis()
            delay((getNextClick(now, isLeft) - now).coerceAtLeast(1L).milliseconds)
        }
    }

    private fun reset() {
        stopClicking(true)
        stopClicking(false)
        if (isMining) mc.options.keyAttack.isDown = false
        isMining = false
        lastHeldSlot = -1
        heldMouseButtons.fill(false)
    }
}

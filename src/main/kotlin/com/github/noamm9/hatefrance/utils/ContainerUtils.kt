package com.github.noamm9.hatefrance.utils

import com.github.noamm9.NoammAddons.mc
import com.github.noamm9.utils.ChatUtils.removeFormatting
import com.github.noamm9.utils.ChatUtils.unformattedText
import com.github.noamm9.utils.items.ItemUtils.lore
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.*

/**
 * Chest menu helpers, player inventory slots are ignored
 */
object ContainerUtils {
    private val itemIds = IdentityHashMap<Item, String>()

    /**
     * [lines] of a menu slot, empty for empty slots
     */
    fun AbstractContainerScreen<*>.slotLines(slot: Int): List<String> {
        val item = menu.slots.getOrNull(slot)?.takeUnless { it.isPlayerSlot() }?.item
        if (item == null || item.isEmpty) return emptyList()
        return item.lines()
    }

    fun AbstractContainerScreen<*>.findSlotByName(name: String): Int? {
        return menu.slots.firstOrNull { slot ->
            !slot.isPlayerSlot() && slot.hasItem() && slot.item.hoverName.unformattedText == name
        }?.index
    }

    /**
     * Stack the server sent, other mods replace [SimpleContainer.getItem] results (e.g. remembered superpairs cards)
     */
    fun Slot.serverItem(): ItemStack {
        return (container as? SimpleContainer)?.items?.getOrNull(containerSlot) ?: item
    }

    /**
     * Item name followed by its lore, without formatting
     */
    fun ItemStack.lines() = (listOf(hoverName.string) + lore).map { it.removeFormatting().trim() }

    /**
     * Registry id like "minecraft:clock", empty for an empty stack
     */
    fun ItemStack.itemId(): String {
        if (isEmpty) return ""
        return itemIds.getOrPut(item) { BuiltInRegistries.ITEM.getKey(item).toString() }
    }

    private fun Slot.isPlayerSlot() = container === mc.player?.inventory
}

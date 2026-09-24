package net.trilleo.mc.plugins.tritown.listeners.items

import net.trilleo.mc.plugins.tritown.content.ContentItems
import org.bukkit.block.Container
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.CrafterCraftEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent

/**
 * Keeps content items inert.
 *
 * Every one is an echo shard underneath (see [ContentItems]), and the only
 * thing an echo shard does is craft a recovery compass. Refusing that, in a
 * crafting grid or a crafter, leaves a content item nothing to be but itself.
 */
class ContentItemListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        if (event.inventory.matrix.any { ContentItems.idOf(it) != null }) event.inventory.result = null
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onCrafter(event: CrafterCraftEvent) {
        val crafter = event.block.getState(false) as? Container ?: return
        if (crafter.inventory.contents.any { ContentItems.idOf(it) != null }) event.isCancelled = true
    }
}

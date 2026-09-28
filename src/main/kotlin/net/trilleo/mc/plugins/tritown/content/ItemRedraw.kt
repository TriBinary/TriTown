package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.gear.Gear
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Keeps what TriTown draws on items — gear and content items — in step with
 * the content files and the item language.
 *
 * The server cannot show an item's text to each player in their own language,
 * so both are written in the one item language and carry a stamp of what they
 * were drawn from. An edit to a content file, a language file or
 * `item-language` leaves every copy out there stale until something asks
 * here, which redraws it in place and costs one read when nothing changed.
 */
object ItemRedraw {

    /** Redraws [stack] if it is gear or a content item drawn from older content. @return whether it was redrawn */
    fun refresh(stack: ItemStack?): Boolean = Gear.refresh(stack) || ContentItems.refresh(stack)

    /** [refresh]es every stack in [inventory]. */
    fun refreshAll(inventory: Inventory) {
        inventory.contents.forEach(::refresh)
    }
}

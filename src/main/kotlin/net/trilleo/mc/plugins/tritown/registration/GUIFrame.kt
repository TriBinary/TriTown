package net.trilleo.mc.plugins.tritown.registration

import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * The border drawn around a menu's content, and the slots it leaves free.
 *
 * Shared rather than living in [PagedPluginGUI], because a plain [PluginGUI]
 * that lays out a grid of its own wants exactly the same border and the same
 * idea of which slots are inside it.
 */
object GUIFrame {

    private const val ROW_SIZE = 9

    /** A single border slot: black glass with no name and no tooltip. */
    fun pane(): ItemStack = itemStack(Material.BLACK_STAINED_GLASS_PANE) {
        name(" ")
        hideTooltip(true)
    }

    /**
     * The slots inside the border, in reading order.
     *
     * The top and bottom rows and the first and last column are the border, so
     * content is everything in between. A menu that wants the bottom row for
     * navigation or buttons of its own simply draws over it: that row is the
     * bottom edge either way, and is never content.
     */
    fun contentSlots(rows: Int): List<Int> {
        if (rows < 3) return emptyList()

        return buildList {
            for (row in 1..rows - 2) {
                for (column in 1 until ROW_SIZE - 1) add(row * ROW_SIZE + column)
            }
        }
    }

    /**
     * Fills every slot of [inventory] that is not in [contentSlots] with the border.
     *
     * Callers draw their own buttons over it afterwards, so a reserved row can be
     * bordered first and then written into.
     */
    fun draw(inventory: Inventory, contentSlots: Collection<Int>) {
        val inside = contentSlots.toSet()
        val pane = pane()
        for (slot in 0 until inventory.size) {
            if (slot !in inside) inventory.setItem(slot, pane.clone())
        }
    }
}

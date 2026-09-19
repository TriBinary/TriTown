package net.trilleo.mc.plugins.tritown.shops

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tritown.enums.ShopSortMode
import org.bukkit.inventory.ItemStack

/**
 * Puts a shop's entries in order.
 *
 * Sorting is done here rather than in the menu that asks for it, because the
 * order of a shop is the shop's own business and a sorted list has to be
 * written back into the same [ShopDefinition.entries] list the rest of the
 * plugin holds on to.
 */
object ShopSorting {

    private val plain = PlainTextComponentSerializer.plainText()

    /** Rearranges [shop]'s entries into [mode]'s order, in place. */
    fun sort(shop: ShopDefinition, mode: ShopSortMode) {
        val sorted = shop.entries.sortedWith(comparator(mode))
        shop.entries.clear()
        shop.entries.addAll(sorted)
    }

    /**
     * An entry with no price sorts last whichever way the prices run, because
     * a display piece has no price to rank it by and reversing the order is
     * not a reason to put it at the top.
     */
    private fun comparator(mode: ShopSortMode): Comparator<ShopEntry> = when (mode) {
        ShopSortMode.NAME -> compareBy { name(it.item) }
        ShopSortMode.NAME_REVERSED -> compareByDescending { name(it.item) }
        ShopSortMode.PRICE -> compareBy<ShopEntry> { it.buy == null }.thenBy { it.buy?.money ?: 0.0 }
        ShopSortMode.PRICE_REVERSED ->
            compareBy<ShopEntry> { it.buy == null }.thenByDescending { it.buy?.money ?: 0.0 }
    }

    /**
     * What an item is called for the purpose of ordering it.
     *
     * A renamed item sorts under the name it was given; anything else sorts
     * under its material, which is the server's own language rather than each
     * viewer's — one shop has one order, and it cannot depend on who is
     * looking at it.
     */
    private fun name(item: ItemStack): String {
        val meta = item.itemMeta
        val custom = if (meta != null && meta.hasDisplayName()) meta.displayName()?.let(plain::serialize) else null
        return (custom ?: item.type.name.replace('_', ' ')).lowercase()
    }
}

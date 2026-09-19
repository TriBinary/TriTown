package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.MatchMode
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * One line of goods in a shop.
 *
 * The [item] is stored verbatim, custom data and all, and its own stack size is
 * the bundle: an entry holding 16 bread sells sixteen loaves per click. Buying
 * and selling are independent, so an entry can do either, both, or — with both
 * left null — act as a display piece.
 *
 * @param id stable across reordering and renaming, because purchase counters are keyed by it
 */
data class ShopEntry(
    val id: String = UUID.randomUUID().toString(),
    var item: ItemStack,
    var buy: ShopCost? = null,
    var sell: ShopCost? = null,
    var gate: ShopGate = ShopGate.OPEN,
    var limit: ShopLimit? = null,
    var stock: ShopStock? = null,
    var discountable: Boolean = true,
    var matchMode: MatchMode = MatchMode.EXACT,
    var stats: ShopStats = ShopStats(),
) {

    /** How many items one bundle is. */
    val bundleSize: Int get() = item.amount.coerceAtLeast(1)

    /** Whether a player can buy this. */
    val isBuyable: Boolean get() = buy != null

    /** Whether the shop buys this back. */
    val isSellable: Boolean get() = sell != null

    /** One bundle as a single stack, for a menu slot rather than for handing over. */
    fun displayStack(): ItemStack = item.clone().apply { amount = bundleSize.coerceAtMost(item.maxStackSize) }

    /**
     * [bundles] bundles of the goods, split into stacks the game allows.
     *
     * Split here rather than left as one oversized stack, so that what is
     * checked for room is exactly what is handed over.
     */
    fun goodsStacks(bundles: Int = 1): List<ItemStack> {
        val total = bundleSize * bundles
        val perStack = item.maxStackSize.coerceAtLeast(1)

        return buildList {
            var outstanding = total
            while (outstanding > 0) {
                val size = minOf(outstanding, perStack)
                add(item.clone().apply { amount = size })
                outstanding -= size
            }
        }
    }

    /** Whether [stack] is close enough to the goods to count, under this entry's [matchMode]. */
    fun matches(stack: ItemStack): Boolean = when (matchMode) {
        MatchMode.EXACT -> item.isSimilar(stack)
        MatchMode.MATERIAL -> item.type == stack.type
    }
}

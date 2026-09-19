package net.trilleo.mc.plugins.tritown.shops

import org.bukkit.inventory.ItemStack

/**
 * What one bundle costs, or what one bundle pays out.
 *
 * Money and items are both optional and both may be set at once, so an entry can
 * be sold for currency, bartered for items, or priced as a mix of the two.
 *
 * @param money the currency amount, before any discount
 * @param items the stacks required alongside it; each stack's amount is its quantity
 */
data class ShopCost(val money: Double = 0.0, val items: List<ItemStack> = emptyList()) {

    /** Whether this costs nothing at all. */
    val isFree: Boolean get() = money <= 0.0 && items.isEmpty()

    /** Whether any currency changes hands. */
    val hasMoney: Boolean get() = money > 0.0

    /** A deep copy, so a stored cost cannot be mutated through a stack handed out of it. */
    fun copyDeep(): ShopCost = ShopCost(money, items.map { it.clone() })

    companion object {
        /** A cost of nothing, used for a giveaway or an item-only trade. */
        val FREE = ShopCost()
    }
}

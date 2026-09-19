package net.trilleo.mc.plugins.tritown.shops

/**
 * What one entry has traded since the counters were last reset.
 *
 * Counts bundles rather than items, matching what a player clicks, and keeps
 * the two currency directions apart so an owner can see at a glance whether a
 * shop is draining the economy or feeding it.
 */
data class ShopStats(
    var bought: Long = 0L,
    var sold: Long = 0L,
    var moneyIn: Double = 0.0,
    var moneyOut: Double = 0.0,
) {

    /** Records a player buying [bundles] for [money]. */
    fun recordBuy(bundles: Int, money: Double) {
        bought += bundles
        moneyIn += money
    }

    /** Records a player selling [bundles] for [money]. */
    fun recordSell(bundles: Int, money: Double) {
        sold += bundles
        moneyOut += money
    }

    /** Whether anything has been traded at all. */
    val isEmpty: Boolean get() = bought == 0L && sold == 0L

    /** Clears every counter. */
    fun reset() {
        bought = 0L
        sold = 0L
        moneyIn = 0.0
        moneyOut = 0.0
    }
}

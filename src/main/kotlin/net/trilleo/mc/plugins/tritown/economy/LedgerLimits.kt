package net.trilleo.mc.plugins.tritown.economy

/**
 * The bounds the ledger enforces on every balance.
 *
 * @param capByCurrency         the largest balance each currency may hold, in minor units;
 *                              a currency absent from the map is uncapped
 * @param allowNegativeBalances when true, a withdrawal may take an account below zero
 */
data class LedgerLimits(
    val capByCurrency: Map<String, Long> = emptyMap(),
    val allowNegativeBalances: Boolean = false,
) {

    /** The cap for [currencyId], or `null` when it is uncapped. */
    fun capFor(currencyId: String): Long? = capByCurrency[currencyId]

    companion object {
        /** No cap on any currency, and no overdrafts. */
        val DEFAULT = LedgerLimits()
    }
}

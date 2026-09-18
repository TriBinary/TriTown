package net.trilleo.mc.plugins.tritown.economy

/**
 * A currency the economy can hold balances in.
 *
 * Only [CurrencyRegistry.primary] is exposed through Vault — the Vault API has
 * room for exactly one currency — so any additional currency is reachable only
 * through TriTown's own commands and services.
 *
 * @param id               stable key used in storage and commands; never shown to players
 * @param singular         display name for an amount of exactly one
 * @param plural           display name for any other amount
 * @param symbol           short prefix such as `$`
 * @param fractionalDigits digits kept after the decimal point, which fixes the minor-unit scale
 * @param plainFormat      unformatted pattern over `%symbol%`, `%amount%` and `%currency%`, used for
 *                         Vault's `format` where other plugins print the result verbatim
 * @param richFormat       MiniMessage pattern over the same placeholders, used for TriTown's own messages
 */
data class Currency(
    val id: String,
    val singular: String,
    val plural: String,
    val symbol: String,
    val fractionalDigits: Int,
    val plainFormat: String,
    val richFormat: String,
) {
    init {
        require(id.isNotBlank()) { "Currency id must not be blank" }
        require(fractionalDigits in 0..6) { "fractional-digits must be between 0 and 6, got $fractionalDigits" }
    }

    /** Converts [amount] to this currency's minor units. */
    fun of(amount: Double): Money = Money.ofDouble(amount, fractionalDigits)

    /** Converts [money] back to a `Double` at this currency's scale. */
    fun toDouble(money: Money): Double = money.toDouble(fractionalDigits)

    /** The display name matching [money]'s magnitude. */
    fun nameFor(money: Money): String = if (money.abs().minor == pow10(fractionalDigits)) singular else plural

    private fun pow10(exponent: Int): Long {
        var result = 1L
        repeat(exponent) { result *= 10L }
        return result
    }
}

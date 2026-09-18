package net.trilleo.mc.plugins.tritown.economy

/**
 * The outcome of a ledger operation.
 *
 * Failures carry a player-facing reason rather than throwing, because the Vault
 * provider is called from Towny's own threads where an exception would be
 * swallowed or would spam the console.
 */
sealed interface EconomyResult {

    /**
     * @param moved                the amount that actually changed hands
     * @param balance              the balance of the account after the operation
     * @param counterpartyBalance  the other account's balance, set only by transfers
     */
    data class Success(
        val moved: Money,
        val balance: Money,
        val counterpartyBalance: Money? = null,
    ) : EconomyResult

    /** @param reason a player-facing explanation, already plain text */
    data class Failure(val reason: String) : EconomyResult

    val isSuccess: Boolean
        get() = this is Success

    /** The resulting balance on success, or [Money.ZERO] on failure. */
    val balanceOrZero: Money
        get() = (this as? Success)?.balance ?: Money.ZERO

    /** The failure reason, or `null` when the operation succeeded. */
    val failureReason: String?
        get() = (this as? Failure)?.reason
}

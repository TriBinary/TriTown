package net.trilleo.mc.plugins.tritown.economy

/**
 * The outcome of a ledger operation.
 *
 * Failures carry a reason rather than throwing, because the Vault provider is
 * called from Towny's own threads where an exception would be swallowed or
 * would spam the console. The reason is a translation key, not a sentence: the
 * ledger has no idea who is going to read it, so the language is chosen where
 * the failure is shown.
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

    /** @param key the translation key of a player-facing explanation, under `money.error.*` */
    data class Failure(val key: String) : EconomyResult

    val isSuccess: Boolean
        get() = this is Success

    /** The resulting balance on success, or [Money.ZERO] on failure. */
    val balanceOrZero: Money
        get() = (this as? Success)?.balance ?: Money.ZERO

    /** The failure's translation key, or `null` when the operation succeeded. */
    val failureKey: String?
        get() = (this as? Failure)?.key
}

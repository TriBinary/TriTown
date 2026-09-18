package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.TransactionType
import java.util.*

/**
 * One movement of money, filed against one account.
 *
 * A transfer produces two records, one for each side, because each is a
 * separate account with its own history — that is also why `/eco history` for a
 * player never shows the same payment twice.
 *
 * @param id           monotonic within a run, seeded from the tail of the log at startup
 * @param account      the account this record belongs to
 * @param counterparty the other side of a transfer, if there was one
 * @param amount       minor units, always positive; [type] says which way it went
 * @param balanceAfter the account's balance once this had been applied
 * @param source       where the call came from — `command`, `vault`, `towny`
 * @param reason       a short human description
 * @param meta         room for a later feature to attach its own keys, such as a shop or payday id
 */
data class TransactionRecord(
    val id: Long,
    val timestamp: Long,
    val account: UUID,
    val counterparty: UUID?,
    val currency: String,
    val type: TransactionType,
    val amount: Long,
    val balanceAfter: Long,
    val source: String,
    val reason: String,
    val meta: Map<String, String> = emptyMap(),
) {
    /** The amount as [Money]. */
    val money: Money get() = Money(amount)

    /** The resulting balance as [Money]. */
    val balance: Money get() = Money(balanceAfter)
}

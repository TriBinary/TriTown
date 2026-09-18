package net.trilleo.mc.plugins.tritown.economy.storage

/**
 * One account as it is written to storage.
 *
 * Kept separate from `MoneyAccount` on purpose: the live account is mutable and
 * concurrent, while this is a flat, immutable copy taken under the account's
 * lock. Because nothing else depends on its shape, the on-disk format can change
 * without touching the ledger.
 *
 * @param balances                minor units keyed by currency id; zero balances are omitted
 * @param startingBalanceGranted  whether the wallet has already been paid its starting balance
 */
data class StoredAccount(
    val uuid: String,
    val name: String,
    val type: String,
    val balances: Map<String, Long>,
    val createdAt: Long,
    val updatedAt: Long,
    val startingBalanceGranted: Boolean = false,
)

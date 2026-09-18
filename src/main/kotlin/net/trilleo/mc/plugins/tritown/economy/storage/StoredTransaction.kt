package net.trilleo.mc.plugins.tritown.economy.storage

/**
 * One transaction as it is written to the log.
 *
 * @param v the schema version of this line, so a future change can be detected per record.
 *   A line written by a version this build does not understand is skipped with a warning
 *   rather than aborting the load — history is worth keeping, but it is not money.
 */
data class StoredTransaction(
    val v: Int,
    val id: Long,
    val timestamp: Long,
    val account: String,
    val counterparty: String?,
    val currency: String,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val source: String,
    val reason: String,
    val meta: Map<String, String>,
)

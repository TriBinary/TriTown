package net.trilleo.mc.plugins.tritown.economy.storage

import java.util.UUID

/** Raised when economy data cannot be read or written safely. */
class EconomyStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Everything the economy loaded at startup.
 *
 * @param fractionalDigits the currency scale the data was written with, after any rescaling
 */
data class LoadedAccounts(
    val fractionalDigits: Int,
    val accounts: List<StoredAccount>,
)

/**
 * Where economy accounts are kept between restarts.
 *
 * The shipped implementation is [JsonEconomyStorage]. An SQL implementation is
 * the natural next backend and only has to satisfy this interface — nothing
 * above it knows how the data is stored.
 *
 * ### Contract
 *
 * * [initialize] and [loadAccounts] run once, synchronously, before the server
 *   finishes starting. They are the only place blocking I/O is acceptable.
 * * [saveAccounts] and [deleteAccount] are called off the main thread, so they
 *   may block, but they must be safe to call concurrently with reads happening
 *   in the ledger.
 * * A failure to *read* must throw. Starting with an empty ledger and writing
 *   that emptiness back a minute later is the one outcome worse than not
 *   starting at all.
 *
 * ### What this interface does not promise
 *
 * The ledger is authoritative **in memory**. A second server writing the same
 * backing store would be clobbered on the next flush. Sharing an economy across
 * a network needs an invalidation hook and a way to broadcast it, neither of
 * which exists here.
 */
interface EconomyStorage {

    /** The schema version this implementation writes. */
    val schemaVersion: Int

    /**
     * Prepares the backing store: creates directories or tables, and checks
     * that anything already there can be read.
     *
     * @throws EconomyStorageException when the store cannot be opened safely
     */
    fun initialize()

    /**
     * Reads every account.
     *
     * @throws EconomyStorageException when the data is unreadable
     */
    fun loadAccounts(): LoadedAccounts

    /** Writes [accounts], replacing any stored copy of the same UUIDs. */
    fun saveAccounts(accounts: Collection<StoredAccount>)

    /** Removes the account for [uuid], if it is stored. */
    fun deleteAccount(uuid: UUID)

    /** Appends [records] to the transaction log. */
    fun appendTransactions(records: List<StoredTransaction>)

    /**
     * Reads back the most recent [maxPerAccount] transactions for each account,
     * to seed the in-memory history at startup.
     *
     * Unlike the accounts, a transaction log that cannot be read is logged and
     * skipped rather than fatal. History is worth keeping, but it is not money.
     */
    fun loadRecentTransactions(maxPerAccount: Int): Map<String, List<StoredTransaction>>

    /** Discards logged transactions older than [olderThanEpochMs]. */
    fun pruneTransactions(olderThanEpochMs: Long)

    /** Flushes anything buffered and releases resources. */
    fun close()
}

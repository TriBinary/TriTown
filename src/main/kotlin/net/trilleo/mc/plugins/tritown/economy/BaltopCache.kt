package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.AccountType
import java.util.*

/**
 * The balance leaderboard, kept ready so the command never sorts on the main
 * thread.
 *
 * [rebuild] runs on the flush task, which is already walking every account, and
 * swaps in a whole new immutable list. Readers take it without any locking, so
 * the worst that can happen is a leaderboard one flush interval out of date —
 * which is fine for a leaderboard, and why the refresh time is shown with it.
 */
object BaltopCache {

    /** One line of the leaderboard. */
    data class Entry(val uuid: UUID, val name: String, val type: AccountType, val balance: Money)

    /** The leaderboard as of [refreshedAt], highest balance first. */
    data class Snapshot(val entries: List<Entry>, val refreshedAt: Long)

    /**
     * How many entries are kept.
     *
     * Bounded so a server with tens of thousands of accounts does not hold a
     * sorted copy of all of them; at the default ten per page that is a hundred
     * pages, which is far past what anyone scrolls.
     */
    const val MAX_ENTRIES: Int = 1_000

    @Volatile
    var current: Snapshot = Snapshot(emptyList(), 0L)
        private set

    /**
     * Rebuilds the leaderboard from [ledger].
     *
     * Server and NPC accounts are never listed. Towny's closed-economy server
     * account collects everything the economy would otherwise destroy, so it
     * would sit at the top forever. Town and nation banks are listed only when
     * [includeGovernments] is set, since Towny already shows those and caches
     * them for up to ten minutes, so the two would disagree.
     */
    fun rebuild(ledger: EconomyLedger, currency: Currency, includeGovernments: Boolean) {
        val entries = ledger.accounts()
            .asSequence()
            .filter { isListed(it.type, includeGovernments) }
            .map { Entry(it.uuid, it.name, it.type, it.balance(currency.id)) }
            .filter { it.balance.isPositive }
            .sortedByDescending { it.balance.minor }
            .take(MAX_ENTRIES)
            .toList()

        current = Snapshot(entries, System.currentTimeMillis())
    }

    /** Forgets the leaderboard, so a stale one is never shown after a shutdown. */
    fun clear() {
        current = Snapshot(emptyList(), 0L)
    }

    private fun isListed(type: AccountType, includeGovernments: Boolean): Boolean = when (type) {
        AccountType.PLAYER, AccountType.UNKNOWN -> true
        AccountType.TOWN, AccountType.NATION -> includeGovernments
        AccountType.NPC, AccountType.SERVER -> false
    }
}

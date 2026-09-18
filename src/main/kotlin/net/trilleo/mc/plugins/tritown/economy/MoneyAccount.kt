package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.AccountType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A live economy account.
 *
 * The UUID is the identity; [name] exists only so that Vault's deprecated
 * name-based methods and TriTown's own commands can find the account, and it
 * changes whenever the owning player, town or nation is renamed.
 *
 * Balances are mutated only by [EconomyLedger] while holding [lock]. Reading a
 * single balance without the lock is safe and deliberate — the map is
 * concurrent and a `Long` value cannot tear — but any read-modify-write, and
 * any snapshot that must be internally consistent, has to take the lock.
 */
class MoneyAccount(
    val uuid: UUID,
    @Volatile var name: String,
    @Volatile var type: AccountType,
    private val balances: ConcurrentHashMap<String, Long> = ConcurrentHashMap(),
    val createdAt: Long = System.currentTimeMillis(),
    @Volatile var updatedAt: Long = createdAt,
    /**
     * Whether this wallet has already been given the configured starting balance.
     *
     * Tracked explicitly rather than inferred from the account being new: Towny
     * can create an account for a resident who has never joined, and that player
     * should still be paid their starting balance the first time they log in.
     */
    @Volatile var startingBalanceGranted: Boolean = false,
) {

    internal val lock = Any()

    /** The balance held in [currencyId]. Absent currencies read as zero. */
    fun balance(currencyId: String): Money = Money(balances[currencyId] ?: 0L)

    /** Whether this account has ever held a balance in [currencyId]. */
    fun holds(currencyId: String): Boolean = balances.containsKey(currencyId)

    /** An immutable copy of every non-zero balance. Callers holding [lock] get a consistent view. */
    fun snapshotBalances(): Map<String, Long> = balances.filterValues { it != 0L }

    internal fun setBalance(currencyId: String, minor: Long) {
        balances[currencyId] = minor
    }

    internal fun replaceBalances(replacement: Map<String, Long>) {
        balances.clear()
        balances.putAll(replacement)
    }

    override fun toString(): String = "MoneyAccount($uuid, $name, $type)"
}

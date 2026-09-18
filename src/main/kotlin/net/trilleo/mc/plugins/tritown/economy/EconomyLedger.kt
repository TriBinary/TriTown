package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.AccountType
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-memory book of every economy account, and the only place a balance is
 * ever changed.
 *
 * This class deliberately has no Bukkit, Vault or Towny imports. Towny calls
 * the economy from its own threads by default, so everything here is written to
 * be thread-safe, and keeping the arithmetic free of the server API is what
 * makes it testable without a running server. Working out who an account
 * belongs to is the surrounding service's job.
 *
 * ### Locking
 *
 * The maps are concurrent, and account creation goes through `computeIfAbsent`
 * so one UUID can never produce two accounts. Every read-modify-write on a
 * balance holds that account's own monitor, and a two-account transfer takes
 * both monitors **in UUID order**, which is what stops two players paying each
 * other at the same moment from deadlocking.
 */
class EconomyLedger(limits: LedgerLimits = LedgerLimits.DEFAULT) {

    private val accounts = ConcurrentHashMap<UUID, MoneyAccount>()
    private val nameIndex = ConcurrentHashMap<String, UUID>()
    private val dirty: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Volatile
    var limits: LedgerLimits = limits

    /** How many accounts are loaded. */
    val size: Int
        get() = accounts.size

    // ── Lookup ──────────────────────────────────────────────────────────

    /** The account for [uuid], or `null` when it does not exist. */
    fun get(uuid: UUID): MoneyAccount? = accounts[uuid]

    /** Whether an account exists for [uuid]. */
    fun has(uuid: UUID): Boolean = accounts.containsKey(uuid)

    /** The account registered under [name], case-insensitively, or `null` when the name is unknown. */
    fun byName(name: String): MoneyAccount? = nameIndex[key(name)]?.let { accounts[it] }

    /** Every loaded account. The collection is a live view and must not be modified. */
    fun accounts(): Collection<MoneyAccount> = accounts.values

    /**
     * Returns the account for [uuid], creating it with [name] and [type] when
     * it does not exist yet.
     *
     * An existing account keeps its balances; its name and type are refreshed,
     * because Towny may have renamed the owning town, or the owner may have
     * joined since the account was first seen.
     */
    fun getOrCreate(uuid: UUID, name: String, type: AccountType): MoneyAccount {
        val created = booleanArrayOf(false)
        val account = accounts.computeIfAbsent(uuid) { id ->
            created[0] = true
            MoneyAccount(id, name, type)
        }

        if (created[0]) {
            nameIndex[key(name)] = uuid
            markDirty(account)
            return account
        }

        if (!account.name.equals(name, ignoreCase = true)) rename(account, name)
        if (account.type == AccountType.UNKNOWN && type != AccountType.UNKNOWN) {
            account.type = type
            markDirty(account)
        }
        return account
    }

    /**
     * Points [name] at [account], dropping the account's previous name from the
     * index.
     *
     * The old key is removed only while it still maps to this account, so a name
     * already handed to a different account is left alone.
     */
    fun rename(account: MoneyAccount, name: String) {
        val oldKey = key(account.name)
        val newKey = key(name)
        if (oldKey != newKey) nameIndex.remove(oldKey, account.uuid)
        nameIndex[newKey] = account.uuid
        account.name = name
        markDirty(account)
    }

    /** Removes [uuid] from the ledger entirely and returns the account that was removed. */
    fun remove(uuid: UUID): MoneyAccount? {
        val account = accounts.remove(uuid) ?: return null
        nameIndex.remove(key(account.name), uuid)
        dirty.remove(uuid)
        return account
    }

    /**
     * Names starting with [prefix], case-insensitively, for tab completion.
     *
     * Reads the in-memory index, so offline accounts complete without any disk
     * access. [limit] keeps the completion packet small.
     */
    fun suggestNames(prefix: String, limit: Int): List<String> {
        val needle = key(prefix)
        val matches = ArrayList<String>(limit)
        for (account in accounts.values) {
            if (matches.size >= limit) break
            if (key(account.name).startsWith(needle)) matches.add(account.name)
        }
        return matches
    }

    // ── Balances ────────────────────────────────────────────────────────

    /** The balance [uuid] holds in [currency], or zero when the account does not exist. */
    fun balance(uuid: UUID, currency: Currency): Money =
        accounts[uuid]?.balance(currency.id) ?: Money.ZERO

    /** Adds [amount] to [account]. Fails rather than clamping when the balance cap would be exceeded. */
    fun deposit(account: MoneyAccount, currency: Currency, amount: Money): EconomyResult {
        if (amount.isNegative) return EconomyResult.Failure("Amount cannot be negative")

        return synchronized(account.lock) {
            val next = try {
                account.balance(currency.id).plusExact(amount)
            } catch (_: ArithmeticException) {
                return@synchronized EconomyResult.Failure("That amount is too large to hold")
            }

            val cap = limits.capFor(currency.id)
            if (cap != null && next.minor > cap) {
                return@synchronized EconomyResult.Failure("Balance cap reached")
            }

            account.setBalance(currency.id, next.minor)
            touch(account)
            EconomyResult.Success(amount, next)
        }
    }

    /** Takes [amount] from [account], refusing to overdraw unless [LedgerLimits.allowNegativeBalances] is set. */
    fun withdraw(account: MoneyAccount, currency: Currency, amount: Money): EconomyResult {
        if (amount.isNegative) return EconomyResult.Failure("Amount cannot be negative")

        return synchronized(account.lock) {
            val next = try {
                account.balance(currency.id).minusExact(amount)
            } catch (_: ArithmeticException) {
                return@synchronized EconomyResult.Failure("That amount is too large to hold")
            }

            if (next.isNegative && !limits.allowNegativeBalances) {
                return@synchronized EconomyResult.Failure("Insufficient funds")
            }

            account.setBalance(currency.id, next.minor)
            touch(account)
            EconomyResult.Success(amount, next)
        }
    }

    /** Sets [account]'s balance to exactly [amount]. */
    fun setBalance(account: MoneyAccount, currency: Currency, amount: Money): EconomyResult {
        if (amount.isNegative && !limits.allowNegativeBalances) {
            return EconomyResult.Failure("Balance cannot be negative")
        }

        val cap = limits.capFor(currency.id)
        if (cap != null && amount.minor > cap) return EconomyResult.Failure("Balance cap reached")

        return synchronized(account.lock) {
            val previous = account.balance(currency.id)
            account.setBalance(currency.id, amount.minor)
            touch(account)
            EconomyResult.Success(amount.minusExact(previous).abs(), amount)
        }
    }

    /**
     * Moves [amount] from [from] to [to] as one atomic step.
     *
     * Both monitors are taken in UUID order so that two transfers running in
     * opposite directions serialise instead of deadlocking.
     */
    fun transfer(from: MoneyAccount, to: MoneyAccount, currency: Currency, amount: Money): EconomyResult {
        if (from.uuid == to.uuid) return EconomyResult.Failure("Cannot transfer to the same account")
        if (amount.isNegative) return EconomyResult.Failure("Amount cannot be negative")

        val first = if (from.uuid < to.uuid) from else to
        val second = if (first === from) to else from

        return synchronized(first.lock) {
            synchronized(second.lock) {
                val fromNext = try {
                    from.balance(currency.id).minusExact(amount)
                } catch (_: ArithmeticException) {
                    return@synchronized EconomyResult.Failure("That amount is too large to hold")
                }
                if (fromNext.isNegative && !limits.allowNegativeBalances) {
                    return@synchronized EconomyResult.Failure("Insufficient funds")
                }

                val toNext = try {
                    to.balance(currency.id).plusExact(amount)
                } catch (_: ArithmeticException) {
                    return@synchronized EconomyResult.Failure("That amount is too large to hold")
                }
                val cap = limits.capFor(currency.id)
                if (cap != null && toNext.minor > cap) {
                    return@synchronized EconomyResult.Failure("The recipient has reached the balance cap")
                }

                from.setBalance(currency.id, fromNext.minor)
                to.setBalance(currency.id, toNext.minor)
                touch(from)
                touch(to)
                EconomyResult.Success(amount, fromNext, toNext)
            }
        }
    }

    // ── Persistence support ─────────────────────────────────────────────

    /**
     * Seeds the ledger from storage without marking anything dirty.
     *
     * Call once, before the economy opens for business.
     */
    fun load(loaded: Collection<MoneyAccount>) {
        for (account in loaded) {
            accounts[account.uuid] = account
            nameIndex[key(account.name)] = account.uuid
        }
    }

    /** Marks [account] as needing to be written to storage. */
    fun markDirty(account: MoneyAccount) {
        dirty.add(account.uuid)
    }

    /**
     * Takes the set of accounts changed since the last drain, and clears it.
     *
     * Clearing before the caller snapshots is deliberate: a change made after
     * the snapshot re-marks the account and is picked up next round. Draining
     * afterwards would discard that mark and lose the write.
     */
    fun drainDirty(): Set<UUID> {
        val batch = HashSet(dirty)
        dirty.removeAll(batch)
        return batch
    }

    /** Whether anything is waiting to be written. */
    fun hasPendingWrites(): Boolean = dirty.isNotEmpty()

    /**
     * Applies [mapper] to the account for [uuid] while holding its lock, so the
     * result cannot catch a half-finished transfer.
     */
    fun <T> snapshot(uuid: UUID, mapper: (MoneyAccount) -> T): T? {
        val account = accounts[uuid] ?: return null
        return synchronized(account.lock) { mapper(account) }
    }

    private fun touch(account: MoneyAccount) {
        account.updatedAt = System.currentTimeMillis()
        dirty.add(account.uuid)
    }

    private fun key(name: String): String = name.lowercase(Locale.ROOT)
}

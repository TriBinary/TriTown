package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.storage.EconomyStorage
import net.trilleo.mc.plugins.tritown.economy.storage.StoredAccount
import net.trilleo.mc.plugins.tritown.enums.AccountType
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * TriTown's economy, as the rest of the plugin sees it.
 *
 * This is the layer that knows about players, Towny and storage; the arithmetic
 * underneath it in [EconomyLedger] knows about none of those. Every method here
 * is safe to call from any thread, because the Vault provider that sits on top
 * is called from Towny's own threads.
 *
 * ### Dependency direction
 *
 * ```
 * commands / listeners / guis / tasks  ->  EconomyUtil  ->  { EconomyService | an external Vault provider }
 * TriTownVaultEconomy                  ->  EconomyService
 * EconomyService                       ->  EconomyLedger, EconomyStorage      (leaf)
 * ```
 *
 * This service must never reach back up that chain — no `EconomyUtil`, no
 * `ServicesManager`, no `net.milkbowl.vault`. Doing so would loop a call
 * straight back into the Vault provider that is already inside this service.
 */
object EconomyService {

    private lateinit var logger: Logger
    private var storage: EconomyStorage? = null

    /** The book of accounts. Exposed so the leaderboard and admin tools can read it without copying. */
    lateinit var ledger: EconomyLedger
        private set

    /**
     * Whether the economy is loaded and open for business.
     *
     * The Vault provider reports this as `isEnabled`, so a reference another
     * plugin kept across a reload fails loudly instead of quietly mutating a
     * ledger that is on its way out.
     */
    @Volatile
    var isReady: Boolean = false
        private set

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * Opens [store] and loads every account.
     *
     * Called from `onLoad`, which is early enough for Towny to find the Vault
     * provider when it sets up its own economy during `onEnable`. Blocking I/O
     * is acceptable here and nowhere else.
     *
     * @throws net.trilleo.mc.plugins.tritown.economy.storage.EconomyStorageException when the data cannot be read
     */
    fun initialize(pluginLogger: Logger, store: EconomyStorage, settings: EconomySettings) {
        logger = pluginLogger
        ledger = EconomyLedger(settings.ledgerLimits())

        store.initialize()
        val loaded = store.loadAccounts()
        ledger.load(loaded.accounts.mapNotNull(::toAccount))
        storage = store
    }

    /** Marks the economy open and gives anyone already online an account. */
    fun start() {
        if (storage == null) return
        isReady = true
        Bukkit.getOnlinePlayers().forEach { ensurePlayerAccount(it) }
    }

    /** Re-applies the settings that can change without a restart. */
    fun applySettings(settings: EconomySettings) {
        if (storage == null) return
        ledger.limits = settings.ledgerLimits()
    }

    /** Writes everything outstanding and closes storage. */
    fun shutdown() {
        if (storage == null) return
        isReady = false
        flush()
        runCatching { storage?.close() }
        storage = null
    }

    // ── Accounts ────────────────────────────────────────────────────────

    /** The account for [uuid], or `null` when there is none. */
    fun account(uuid: UUID): MoneyAccount? = if (storage == null) null else ledger.get(uuid)

    /** Whether [uuid] has an account. */
    fun hasAccount(uuid: UUID): Boolean = storage != null && ledger.has(uuid)

    /** Whether any account answers to [name]. */
    fun hasAccount(name: String): Boolean = resolveByName(name) != null

    /**
     * Returns [player]'s account, creating it when it does not exist.
     *
     * Towny calls this through Vault's `createPlayerAccount` for towns and
     * nations as well as residents, so the type is worked out from the name
     * rather than assumed, and only a real player wallet is given the starting
     * balance.
     */
    fun ensureAccount(player: OfflinePlayer): MoneyAccount? {
        if (storage == null) return null
        val name = AccountResolver.nameOf(player)
        val account = ledger.getOrCreate(player.uniqueId, name, AccountResolver.classify(player.uniqueId, name))
        grantStartingBalance(account)
        return account
    }

    /** Returns the account [name] belongs to, creating it when the name resolves but the account does not. */
    fun ensureAccount(name: String): MoneyAccount? {
        if (storage == null) return null
        ledger.byName(name)?.let { return it }
        val identity = AccountResolver.resolveName(name) ?: return null
        val account = ledger.getOrCreate(identity.uuid, identity.name, identity.type)
        grantStartingBalance(account)
        return account
    }

    /** Looks [player] up without creating anything. */
    fun resolve(player: OfflinePlayer): MoneyAccount? = account(player.uniqueId)

    /**
     * Looks an account up by name without creating one, falling back to Towny's
     * own name-to-UUID mapping for a town or nation bank the index has not seen
     * yet.
     */
    fun resolveByName(name: String): MoneyAccount? {
        if (storage == null) return null
        ledger.byName(name)?.let { return it }
        val identity = AccountResolver.resolveName(name) ?: return null
        return ledger.get(identity.uuid)
    }

    /**
     * Gives [player] a wallet, promoting an account Towny may already have
     * created for them, and pays the starting balance if it has not been paid.
     */
    fun ensurePlayerAccount(player: OfflinePlayer): MoneyAccount? {
        if (storage == null) return null
        val account = ledger.getOrCreate(player.uniqueId, AccountResolver.nameOf(player), AccountType.PLAYER)

        if (account.type != AccountType.PLAYER) {
            account.type = AccountType.PLAYER
            ledger.markDirty(account)
        }

        grantStartingBalance(account)
        return account
    }

    /**
     * Pays [account] its starting balance, once, if it is a player wallet.
     *
     * Whether it has been paid is recorded on the account rather than inferred
     * from the account being new, because Towny can create a wallet for a
     * resident who has never joined — and that player should still be paid the
     * first time they log in.
     */
    private fun grantStartingBalance(account: MoneyAccount) {
        if (account.startingBalanceGranted || account.type != AccountType.PLAYER) return

        account.startingBalanceGranted = true
        ledger.markDirty(account)

        val amount = EconomySettings.snapshot.startingBalance
        if (amount <= 0.0) return

        val currency = CurrencyRegistry.primary
        ledger.deposit(account, currency, currency.of(amount))
    }

    /** Removes [uuid] from the ledger and from storage. */
    fun deleteAccount(uuid: UUID) {
        if (storage == null) return
        ledger.remove(uuid) ?: return
        runCatching { storage?.deleteAccount(uuid) }
            .onFailure { logger.log(Level.WARNING, "Failed to delete economy account $uuid", it) }
    }

    /** Names starting with [prefix], for tab completion. Online players are offered first. */
    fun suggestAccountNames(prefix: String, limit: Int): List<String> {
        if (storage == null) return emptyList()
        val online = Bukkit.getOnlinePlayers()
            .map { it.name }
            .filter { it.startsWith(prefix, ignoreCase = true) }
        val stored = ledger.suggestNames(prefix, limit)
        return (online + stored).distinct().take(limit)
    }

    // ── Balances ────────────────────────────────────────────────────────

    // The currency defaults to null rather than to CurrencyRegistry.primary
    // because a default argument is evaluated before the body runs, and the
    // registry is empty whenever the economy is switched off.

    /** The balance [uuid] holds, or zero when there is no account. */
    fun balance(uuid: UUID, currency: Currency? = null): Money {
        if (storage == null) return Money.ZERO
        return ledger.balance(uuid, currency ?: CurrencyRegistry.primary)
    }

    /** The balance [name] holds, or zero when the name is unknown. */
    fun balanceByName(name: String, currency: Currency? = null): Money {
        if (storage == null) return Money.ZERO
        val account = resolveByName(name) ?: return Money.ZERO
        return account.balance((currency ?: CurrencyRegistry.primary).id)
    }

    /** Whether [account] holds at least [amount]. */
    fun has(account: MoneyAccount, currency: Currency, amount: Money): Boolean =
        account.balance(currency.id) >= amount

    /** Adds [amount] to [account]. */
    fun deposit(account: MoneyAccount, currency: Currency, amount: Money): EconomyResult =
        guard { ledger.deposit(account, currency, amount) }

    /** Takes [amount] from [account]. */
    fun withdraw(account: MoneyAccount, currency: Currency, amount: Money): EconomyResult =
        guard { ledger.withdraw(account, currency, amount) }

    /** Sets [account]'s balance to exactly [amount]. */
    fun setBalance(account: MoneyAccount, currency: Currency, amount: Money): EconomyResult =
        guard { ledger.setBalance(account, currency, amount) }

    /** Moves [amount] between two accounts as a single atomic step. */
    fun transfer(from: MoneyAccount, to: MoneyAccount, currency: Currency, amount: Money): EconomyResult =
        guard { ledger.transfer(from, to, currency, amount) }

    // ── Persistence ─────────────────────────────────────────────────────

    /**
     * Writes every account changed since the last flush.
     *
     * Runs on the flush task's thread and on shutdown. The dirty set is drained
     * before the snapshots are taken, so a change made mid-flush is written next
     * round rather than lost.
     */
    fun flush() {
        val store = storage ?: return
        val batch = ledger.drainDirty()
        if (batch.isEmpty()) return

        val snapshots = batch.mapNotNull { uuid -> ledger.snapshot(uuid) { it.toStored() } }
        runCatching { store.saveAccounts(snapshots) }
            .onFailure { logger.log(Level.SEVERE, "Failed to write economy accounts", it) }
    }

    /** Writes one account immediately, for changes that should not wait for the next flush. */
    fun flushAccount(uuid: UUID) {
        val store = storage ?: return
        val snapshot = ledger.snapshot(uuid) { it.toStored() } ?: return
        runCatching { store.saveAccounts(listOf(snapshot)) }
            .onFailure { logger.log(Level.SEVERE, "Failed to write economy account $uuid", it) }
    }

    private inline fun guard(operation: () -> EconomyResult): EconomyResult =
        if (storage == null) EconomyResult.Failure("The economy is not available") else operation()

    private fun MoneyAccount.toStored() = StoredAccount(
        uuid = uuid.toString(),
        name = name,
        type = type.name,
        balances = snapshotBalances(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        startingBalanceGranted = startingBalanceGranted,
    )

    private fun toAccount(stored: StoredAccount): MoneyAccount? {
        val uuid = runCatching { UUID.fromString(stored.uuid) }.getOrNull() ?: run {
            logger.warning("Skipping economy account with an unreadable UUID: ${stored.uuid}")
            return null
        }
        val type = runCatching { AccountType.valueOf(stored.type) }.getOrDefault(AccountType.UNKNOWN)
        return MoneyAccount(
            uuid = uuid,
            name = stored.name,
            type = type,
            balances = ConcurrentHashMap(stored.balances),
            createdAt = stored.createdAt,
            updatedAt = stored.updatedAt,
            startingBalanceGranted = stored.startingBalanceGranted,
        )
    }
}

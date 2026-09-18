package net.trilleo.mc.plugins.tritown.economy.vault

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyFormat
import net.trilleo.mc.plugins.tritown.economy.EconomyResult
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.economy.MoneyAccount
import net.trilleo.mc.plugins.tritown.utils.Lang
import org.bukkit.OfflinePlayer

/**
 * TriTown's implementation of Vault's economy, which is what lets Towny run
 * without a separate economy plugin.
 *
 * ### Why this implements [Economy] directly
 *
 * Vault ships an `AbstractEconomy` that reduces every `OfflinePlayer` overload
 * to `player.getName()`. Towny addresses a town's bank with a synthetic offline
 * player whose UUID is the town's and whose name is `town-Riverbend`; routing
 * that through the name would throw away the identity the account is keyed on.
 * So every overload is implemented here, and the UUID always wins.
 *
 * Towny falls back to the deprecated name-based methods when its
 * `economy.advanced.modern` setting is off, so those are implemented too and
 * resolve to the very same account.
 *
 * ### World parameters
 *
 * Accepted and ignored. A balance is global per currency; per-world money would
 * be a second currency, not a second world.
 *
 * ### Threading
 *
 * Towny's `economy.use_async` defaults to true, so everything here can be
 * called off the main thread. Nothing in the read and write paths touches
 * blocking I/O or main-thread-only API.
 */
// Vault's name-based methods are deprecated but not optional: Towny calls them
// whenever its economy.advanced.modern setting is off, so they are implemented
// and delegated to like any other overload.
@Suppress("DEPRECATION")
class TriTownVaultEconomy : Economy {

    override fun isEnabled(): Boolean = EconomyService.isReady

    override fun getName(): String = PROVIDER_NAME

    override fun hasBankSupport(): Boolean = false

    override fun fractionalDigits(): Int = CurrencyRegistry.primary.fractionalDigits

    override fun format(amount: Double): String = EconomyFormat.plain(amount)

    override fun currencyNameSingular(): String = CurrencyRegistry.primary.singular

    override fun currencyNamePlural(): String = CurrencyRegistry.primary.plural

    // ── Account existence ───────────────────────────────────────────────

    override fun hasAccount(player: OfflinePlayer): Boolean = EconomyService.hasAccount(player.uniqueId)

    override fun hasAccount(player: OfflinePlayer, worldName: String?): Boolean = hasAccount(player)

    @Deprecated("Vault's name-based API", ReplaceWith("hasAccount(player)"))
    override fun hasAccount(playerName: String): Boolean = EconomyService.hasAccount(playerName)

    @Deprecated("Vault's name-based API", ReplaceWith("hasAccount(player, worldName)"))
    override fun hasAccount(playerName: String, worldName: String?): Boolean = hasAccount(playerName)

    override fun createPlayerAccount(player: OfflinePlayer): Boolean =
        EconomyService.ensureAccount(player) != null

    override fun createPlayerAccount(player: OfflinePlayer, worldName: String?): Boolean =
        createPlayerAccount(player)

    @Deprecated("Vault's name-based API", ReplaceWith("createPlayerAccount(player)"))
    override fun createPlayerAccount(playerName: String): Boolean =
        EconomyService.ensureAccount(playerName) != null

    @Deprecated("Vault's name-based API", ReplaceWith("createPlayerAccount(player, worldName)"))
    override fun createPlayerAccount(playerName: String, worldName: String?): Boolean =
        createPlayerAccount(playerName)

    // ── Reading ─────────────────────────────────────────────────────────

    override fun getBalance(player: OfflinePlayer): Double = toDouble(EconomyService.balance(player.uniqueId))

    override fun getBalance(player: OfflinePlayer, world: String?): Double = getBalance(player)

    @Deprecated("Vault's name-based API", ReplaceWith("getBalance(player)"))
    override fun getBalance(playerName: String): Double = toDouble(EconomyService.balanceByName(playerName))

    @Deprecated("Vault's name-based API", ReplaceWith("getBalance(player, world)"))
    override fun getBalance(playerName: String, world: String?): Double = getBalance(playerName)

    override fun has(player: OfflinePlayer, amount: Double): Boolean =
        holds(EconomyService.balance(player.uniqueId), amount)

    override fun has(player: OfflinePlayer, worldName: String?, amount: Double): Boolean = has(player, amount)

    @Deprecated("Vault's name-based API", ReplaceWith("has(player, amount)"))
    override fun has(playerName: String, amount: Double): Boolean =
        holds(EconomyService.balanceByName(playerName), amount)

    @Deprecated("Vault's name-based API", ReplaceWith("has(player, worldName, amount)"))
    override fun has(playerName: String, worldName: String?, amount: Double): Boolean = has(playerName, amount)

    // ── Writing ─────────────────────────────────────────────────────────

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse =
        withdraw(EconomyService.resolve(player), amount)

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String?, amount: Double): EconomyResponse =
        withdrawPlayer(player, amount)

    @Deprecated("Vault's name-based API", ReplaceWith("withdrawPlayer(player, amount)"))
    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse =
        withdraw(EconomyService.resolveByName(playerName), amount)

    @Deprecated("Vault's name-based API", ReplaceWith("withdrawPlayer(player, worldName, amount)"))
    override fun withdrawPlayer(playerName: String, worldName: String?, amount: Double): EconomyResponse =
        withdrawPlayer(playerName, amount)

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse =
        deposit(EconomyService.ensureAccount(player), amount)

    override fun depositPlayer(player: OfflinePlayer, worldName: String?, amount: Double): EconomyResponse =
        depositPlayer(player, amount)

    @Deprecated("Vault's name-based API", ReplaceWith("depositPlayer(player, amount)"))
    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse =
        deposit(EconomyService.ensureAccount(playerName), amount)

    @Deprecated("Vault's name-based API", ReplaceWith("depositPlayer(player, worldName, amount)"))
    override fun depositPlayer(playerName: String, worldName: String?, amount: Double): EconomyResponse =
        depositPlayer(playerName, amount)

    // ── Banks ───────────────────────────────────────────────────────────
    //
    // Towny's town and nation banks are ordinary accounts reached through the
    // player methods above, which is exactly how Towny expects to find them.
    // Vault's separate bank API is a different, name-owned concept that nothing
    // on this server uses.

    override fun createBank(name: String, player: OfflinePlayer): EconomyResponse = noBanks()

    @Deprecated("Vault's name-based API", ReplaceWith("createBank(name, player)"))
    override fun createBank(name: String, player: String): EconomyResponse = noBanks()

    override fun deleteBank(name: String): EconomyResponse = noBanks()

    override fun bankBalance(name: String): EconomyResponse = noBanks()

    override fun bankHas(name: String, amount: Double): EconomyResponse = noBanks()

    override fun bankWithdraw(name: String, amount: Double): EconomyResponse = noBanks()

    override fun bankDeposit(name: String, amount: Double): EconomyResponse = noBanks()

    override fun isBankOwner(name: String, player: OfflinePlayer): EconomyResponse = noBanks()

    @Deprecated("Vault's name-based API", ReplaceWith("isBankOwner(name, player)"))
    override fun isBankOwner(name: String, playerName: String): EconomyResponse = noBanks()

    override fun isBankMember(name: String, player: OfflinePlayer): EconomyResponse = noBanks()

    @Deprecated("Vault's name-based API", ReplaceWith("isBankMember(name, player)"))
    override fun isBankMember(name: String, playerName: String): EconomyResponse = noBanks()

    override fun getBanks(): MutableList<String> = mutableListOf()

    // ── Shared plumbing ─────────────────────────────────────────────────

    private fun toDouble(money: Money): Double = CurrencyRegistry.primary.toDouble(money)

    private fun holds(balance: Money, amount: Double): Boolean {
        val required = parse(amount) ?: return false
        return balance >= required
    }

    /** Converts a Vault amount, returning `null` for anything that is not a usable number. */
    private fun parse(amount: Double): Money? =
        runCatching { CurrencyRegistry.primary.of(amount) }.getOrNull()

    private fun withdraw(account: MoneyAccount?, amount: Double): EconomyResponse {
        val currency = CurrencyRegistry.primary
        val money = parse(amount) ?: return failure(amount, 0.0, "money.error.unusable-amount")

        // A zero withdrawal from an account that does not exist yet is a no-op,
        // not a failure: Towny does this while working out whether a cost applies.
        if (account == null) {
            return if (money.isZero) success(0.0, 0.0) else failure(amount, 0.0, "money.error.no-account")
        }

        return respond(amount, EconomyService.withdraw(account, currency, money), currency.toDouble(account.balance(currency.id)))
    }

    private fun deposit(account: MoneyAccount?, amount: Double): EconomyResponse {
        val currency = CurrencyRegistry.primary
        val money = parse(amount) ?: return failure(amount, 0.0, "money.error.unusable-amount")
        if (account == null) return failure(amount, 0.0, "money.error.account-not-created")

        return respond(amount, EconomyService.deposit(account, currency, money), currency.toDouble(account.balance(currency.id)))
    }

    private fun respond(amount: Double, result: EconomyResult, fallbackBalance: Double): EconomyResponse =
        when (result) {
            is EconomyResult.Success -> success(
                CurrencyRegistry.primary.toDouble(result.moved),
                CurrencyRegistry.primary.toDouble(result.balance),
            )

            is EconomyResult.Failure -> failure(amount, fallbackBalance, result.key)
        }

    private fun success(moved: Double, balance: Double) =
        EconomyResponse(moved, balance, EconomyResponse.ResponseType.SUCCESS, null)

    /**
     * Vault hands the message straight to whichever plugin asked, and there is
     * no player behind the call to take a language from, so it is translated
     * into the configured one — English while `language` is `auto`.
     */
    private fun failure(attempted: Double, balance: Double, key: String) =
        EconomyResponse(attempted, balance, EconomyResponse.ResponseType.FAILURE, Lang.tr(null, key))

    private fun noBanks() = EconomyResponse(
        0.0,
        0.0,
        EconomyResponse.ResponseType.NOT_IMPLEMENTED,
        Lang.tr(null, "money.error.no-banks"),
    )

    companion object {
        /**
         * The name Vault and Towny show for this provider.
         *
         * It must not collide with another registered provider: Towny keys its
         * providers by this name and throws on a duplicate, and it treats
         * "EssentialsX Economy" specially by rewriting the UUID version of
         * non-player accounts.
         */
        const val PROVIDER_NAME = "TriTown"
    }
}

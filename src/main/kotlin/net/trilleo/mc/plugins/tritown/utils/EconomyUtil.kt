package net.trilleo.mc.plugins.tritown.utils

import net.kyori.adventure.text.Component
import net.milkbowl.vault.economy.Economy
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.EconomyFormat
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.vault.TriTownVaultEconomy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

/**
 * The single way the rest of the plugin touches money.
 *
 * TriTown normally supplies the server's economy itself, but an owner can hand
 * that job to another plugin, so everything here goes through whichever Vault
 * provider actually won. Feature code should not care which one that is.
 *
 * ### Usage
 *
 * ```kotlin
 * val cost = 500.0
 * if (!EconomyUtil.withdraw(player, cost)) {
 *     player.sendPrefixed(player.tr("shop.too-expensive", "cost" to EconomyUtil.format(cost)))
 *     return true
 * }
 * ```
 *
 * Do not call this during `onEnable` or from registration code: the winning
 * provider is not settled until every plugin has enabled.
 */
object EconomyUtil {

    private var provider: Economy? = null

    /** The registered Vault [Economy] provider. Throws if none is registered. */
    val economy: Economy
        get() = provider ?: Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
            ?.also { provider = it }
        ?: error("No Vault economy provider is registered")

    /** Whether a Vault economy provider is registered. */
    val isAvailable: Boolean
        get() = runCatching { economy }.isSuccess

    /** Whether the economy in use is TriTown's own, rather than another plugin's. */
    val isInternal: Boolean
        get() = runCatching { economy }.getOrNull() is TriTownVaultEconomy

    /** The current balance of [player]. */
    fun balance(player: OfflinePlayer): Double = economy.getBalance(player)

    /** Whether [player] has at least [amount]. */
    fun has(player: OfflinePlayer, amount: Double): Boolean = economy.has(player, amount)

    /** Takes [amount] from [player]. Returns `false` without charging if they cannot afford it or the provider refuses. */
    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        require(amount >= 0) { "amount must not be negative" }
        return has(player, amount) && economy.withdrawPlayer(player, amount).transactionSuccess()
    }

    /** Gives [amount] to [player]. Returns `false` if the provider refuses. */
    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        require(amount >= 0) { "amount must not be negative" }
        return economy.depositPlayer(player, amount).transactionSuccess()
    }

    /**
     * Takes [amount] from [player], recorded as having come from [source] for [reason].
     *
     * The attribution reaches the ledger through the thread the call is made on,
     * so it lands on the record without this having to know which provider won.
     * Another plugin's economy keeps no such record and simply ignores it.
     */
    fun withdraw(player: OfflinePlayer, amount: Double, source: String, reason: String): Boolean =
        EconomyContext.with(source, reason) { withdraw(player, amount) }

    /** Gives [amount] to [player], recorded as having come from [source] for [reason]. */
    fun deposit(player: OfflinePlayer, amount: Double, source: String, reason: String): Boolean =
        EconomyContext.with(source, reason) { deposit(player, amount) }

    /**
     * Moves [amount] from [from] to [to].
     *
     * On TriTown's own economy this is one atomic step, so a payment can never
     * leave money in neither account. On another plugin's economy it falls back
     * to a withdrawal and a deposit, refunding the sender if the deposit fails.
     */
    fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: Double): Boolean {
        require(amount >= 0) { "amount must not be negative" }
        if (from.uniqueId == to.uniqueId) return false

        if (isInternal) {
            val currency = CurrencyRegistry.primary
            val sender = EconomyService.resolve(from) ?: return false
            val recipient = EconomyService.ensureAccount(to) ?: return false
            return EconomyService.transfer(sender, recipient, currency, currency.of(amount)).isSuccess
        }

        if (!withdraw(from, amount)) return false
        if (deposit(to, amount)) return true

        deposit(from, amount)
        return false
    }

    /** Formats [amount] with the economy's currency, e.g. `$1,000.00`. */
    fun format(amount: Double): String = economy.format(amount)

    /**
     * Formats [amount] for TriTown's own messages, using the configured
     * MiniMessage pattern.
     *
     * Falls back to the provider's plain text when another plugin supplies the
     * economy, since only TriTown's currency has a rich pattern.
     */
    fun formatRich(amount: Double): Component =
        if (isInternal && CurrencyRegistry.isLoaded) EconomyFormat.rich(amount) else Component.text(format(amount))

    /** Forgets the cached provider, so the next access looks it up again. */
    fun reset() {
        provider = null
    }
}

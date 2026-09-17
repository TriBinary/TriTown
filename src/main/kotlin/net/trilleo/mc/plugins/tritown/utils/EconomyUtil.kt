package net.trilleo.mc.plugins.tritown.utils

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

/**
 * Access to the server economy through Vault.
 *
 * Vault is a hard dependency, but the economy itself is provided by another plugin (EssentialsX, CMI, …) that may
 * enable after TriTown, so the provider is resolved on first use rather than in `onEnable`.
 * [net.trilleo.mc.plugins.tritown.Main] checks [isAvailable] once every plugin has enabled and disables TriTown when no
 * provider is registered, so every other caller can assume [economy] exists.
 *
 * ### Usage
 *
 * ```kotlin
 * if (EconomyUtil.withdraw(player, 100.0)) {
 *     player.sendPrefixed("Paid ${EconomyUtil.format(100.0)}")
 * }
 * ```
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

    /** Formats [amount] with the economy's currency, e.g. `$1,000.00`. */
    fun format(amount: Double): String = economy.format(amount)

    /** Forgets the cached provider, so the next access looks it up again. */
    fun reset() {
        provider = null
    }
}

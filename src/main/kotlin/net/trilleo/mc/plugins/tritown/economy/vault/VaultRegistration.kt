package net.trilleo.mc.plugins.tritown.economy.vault

import net.milkbowl.vault.economy.Economy
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.enums.ProviderMode
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers TriTown's economy with Vault, or stands aside for another plugin's.
 *
 * ### Why this happens in `onLoad`
 *
 * Towny works out which economy to use inside its own `onEnable`, and because
 * TriTown depends on Towny, Towny enables first. Registering the Vault service
 * from TriTown's `onEnable` would therefore always be too late, and Towny would
 * report no economy at all. `onLoad` runs for every plugin before any plugin
 * enables, which is early enough.
 *
 * If the order is ever disturbed, Towny can be told to look again with
 * `/townyadmin eco convert modern`, or the server can be restarted.
 */
object VaultRegistration {

    private var provider: TriTownVaultEconomy? = null

    /** Whether TriTown registered the provider currently serving the server. */
    val isRegistered: Boolean
        get() = provider != null

    /**
     * Registers the economy service according to `economy.provider.mode`.
     *
     * @return true when TriTown registered a provider
     */
    fun register(plugin: JavaPlugin): Boolean {
        val settings = EconomySettings.snapshot
        if (!settings.enabled) return false

        when (settings.providerMode) {
            ProviderMode.EXTERNAL -> {
                plugin.logger.info(
                    "economy.provider.mode is 'external'; TriTown will use whichever economy another plugin supplies"
                )
                return false
            }

            ProviderMode.AUTO -> {
                val installed = settings.deferTo.firstOrNull { Bukkit.getPluginManager().getPlugin(it) != null }
                if (installed != null) {
                    plugin.logger.info(
                        "Deferring the economy to '$installed'. Set economy.provider.mode to 'internal' to supply it anyway."
                    )
                    return false
                }
            }

            ProviderMode.INTERNAL -> Unit
        }

        val priority = if (settings.providerMode == ProviderMode.INTERNAL) {
            ServicePriority.Highest
        } else {
            ServicePriority.Low
        }

        val economy = TriTownVaultEconomy()
        Bukkit.getServicesManager().register(Economy::class.java, economy, plugin, priority)
        provider = economy
        plugin.logger.info(
            "Registered TriTown as a Vault economy provider (mode=${settings.providerMode}, priority=$priority)"
        )
        return true
    }

    /** Withdraws the provider, if TriTown registered one. */
    fun unregister() {
        provider?.let { Bukkit.getServicesManager().unregister(Economy::class.java, it) }
        provider = null
    }
}

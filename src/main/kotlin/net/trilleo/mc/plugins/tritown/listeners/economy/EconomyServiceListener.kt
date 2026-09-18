package net.trilleo.mc.plugins.tritown.listeners.economy

import net.milkbowl.vault.economy.Economy
import net.trilleo.mc.plugins.tritown.economy.vault.TriTownVaultEconomy
import net.trilleo.mc.plugins.tritown.economy.vault.VaultRegistration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Warns when a second economy plugin registers with Vault after TriTown has.
 *
 * This matters more than it looks: Towny decides which economy to use exactly
 * once, while it is enabling, and holds on to that choice. A provider that
 * shows up afterwards will be listed by Vault but ignored by Towny, so town
 * banks and player wallets would quietly disagree about where the money is.
 */
class EconomyServiceListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (!VaultRegistration.isRegistered) return

        val registration = event.provider
        if (registration.service != Economy::class.java) return

        val provider = registration.provider
        if (provider is TriTownVaultEconomy) return

        plugin.logger.warning(
            "'${registration.plugin.name}' registered a second Vault economy (${(provider as Economy).name}) after " +
                "TriTown did. Towny has already chosen which economy to use and will not notice this one, so the " +
                "two can disagree about balances. Pick one: set economy.provider.mode to 'external' in TriTown's " +
                "config, or remove the other plugin. Then restart, or run /townyadmin eco convert modern to make " +
                "Towny look again."
        )
    }
}

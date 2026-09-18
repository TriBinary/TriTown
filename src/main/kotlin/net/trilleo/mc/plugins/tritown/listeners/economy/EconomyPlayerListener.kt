package net.trilleo.mc.plugins.tritown.listeners.economy

import net.trilleo.mc.plugins.tritown.economy.EconomyService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Keeps player wallets in step with who is actually on the server.
 *
 * Joining is where an account is created, paid its starting balance, and
 * promoted from the placeholder type Towny leaves behind when it creates an
 * account for a resident who has never logged in. It is also where a rename is
 * picked up, so the name index stays usable for tab completion and for Vault's
 * older name-based methods.
 */
class EconomyPlayerListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        EconomyService.ensurePlayerAccount(event.player)
    }

    /** Writes the player's balance out now, rather than leaving it to the next flush. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        EconomyService.flushAccount(event.player.uniqueId)
    }
}

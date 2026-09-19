package net.trilleo.mc.plugins.tritown.listeners.admin

import net.trilleo.mc.plugins.tritown.guis.admin.PanelState
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Drops what an administrator was looking at when they leave.
 *
 * The panel's menus hand each other the chosen window, so it cannot be cleared
 * when one of them closes — opening the next one closes the last. Quitting is
 * the one moment where nothing is going to be opened next.
 */
class PanelStateListener : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        PanelState.forget(event.player)
    }
}

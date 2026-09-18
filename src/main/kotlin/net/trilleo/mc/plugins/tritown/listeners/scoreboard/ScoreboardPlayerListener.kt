package net.trilleo.mc.plugins.tritown.listeners.scoreboard

import net.trilleo.mc.plugins.tritown.scoreboard.ScoreboardService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Puts a joining player's sidebar back up, refreshes it when they change world,
 * and lets go of what it remembered about them when they leave.
 *
 * Taking the board itself down on quit is Towny's job: its `HUDManager` does
 * that for every registered HUD, and TriTown's is one of them.
 */
class ScoreboardPlayerListener(private val plugin: JavaPlugin) : Listener {

    /**
     * The sidebar goes up a tick after the join rather than during it, so that
     * plugins which hand a joining player their own scoreboard have already
     * done so and Towny's takeover check sees the real state.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) ScoreboardService.show(player)
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = ScoreboardService.forget(event.player)
}

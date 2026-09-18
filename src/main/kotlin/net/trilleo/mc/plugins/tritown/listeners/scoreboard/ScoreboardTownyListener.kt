package net.trilleo.mc.plugins.tritown.listeners.scoreboard

import com.palmergames.bukkit.towny.event.DeleteNationEvent
import com.palmergames.bukkit.towny.event.DeleteTownEvent
import com.palmergames.bukkit.towny.event.NationAddTownEvent
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent
import com.palmergames.bukkit.towny.event.NewDayEvent
import com.palmergames.bukkit.towny.event.PlayerChangePlotEvent
import com.palmergames.bukkit.towny.event.TownAddResidentEvent
import com.palmergames.bukkit.towny.event.TownBlockSettingsChangedEvent
import com.palmergames.bukkit.towny.event.TownClaimEvent
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent
import com.palmergames.bukkit.towny.event.economy.BankTransactionEvent
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent
import net.trilleo.mc.plugins.tritown.scoreboard.ScoreboardService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Redraws the sidebar when Towny changes something it shows, so a claim, a
 * bank deposit or a new day appears without waiting for the refresh interval.
 *
 * Towny refreshes its own plot and map HUDs on a plot change by name, so a HUD
 * registered by another plugin gets nothing from it — crossing a chunk border
 * has to be handled here.
 *
 * Every handler defers to [ScoreboardService.refreshSoon], which collapses a
 * burst into one redraw and moves the work onto the main thread. That also
 * makes [PlayerChangePlotEvent] safe to handle, since Towny fires it
 * asynchronously when the move itself was asynchronous.
 */
class ScoreboardTownyListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlotChange(event: PlayerChangePlotEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onResidentJoinTown(event: TownAddResidentEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onResidentLeaveTown(event: TownRemoveResidentEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onTownJoinNation(event: NationAddTownEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onTownLeaveNation(event: NationRemoveTownEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClaim(event: TownClaimEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onUnclaim(event: TownUnclaimEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlotSettingsChanged(event: TownBlockSettingsChangedEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBankTransaction(event: BankTransactionEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onNewDay(event: NewDayEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onTownDeleted(event: DeleteTownEvent) = ScoreboardService.refreshSoon()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onNationDeleted(event: DeleteNationEvent) = ScoreboardService.refreshSoon()
}

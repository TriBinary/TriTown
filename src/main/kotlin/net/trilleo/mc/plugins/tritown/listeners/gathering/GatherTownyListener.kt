package net.trilleo.mc.plugins.tritown.listeners.gathering

import com.palmergames.bukkit.towny.event.DeleteTownEvent
import com.palmergames.bukkit.towny.event.MobRemovalEvent
import com.palmergames.bukkit.towny.event.actions.TownyActionEvent
import com.palmergames.bukkit.towny.event.actions.TownyDestroyEvent
import com.palmergames.bukkit.towny.event.actions.TownyItemuseEvent
import com.palmergames.bukkit.towny.event.actions.TownySwitchEvent
import com.palmergames.bukkit.towny.event.mobs.MobSpawnRemovalEvent
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.gathering.GatherPermits
import net.trilleo.mc.plugins.tritown.gathering.Spawners
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Where resource regions meet Towny's own rules.
 *
 * A town can deny its residents the right to destroy and use anything, and
 * a starter town usually does. Gathering is the one exception: an action
 * TriTown has already cleared, for that player at that block this tick (see
 * [GatherPermits]), has Towny's refusal lifted. Nothing else is touched, so a
 * resident still cannot build, open a door or break a block that is not a
 * ripe resource. Building is never lifted at all.
 *
 * Towny's mob rules would also remove a spawner's monsters from a town that
 * has mobs switched off, so those are let be.
 */
class GatherTownyListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDestroy(event: TownyDestroyEvent) = lift(event)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onItemuse(event: TownyItemuseEvent) = lift(event)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSwitch(event: TownySwitchEvent) = lift(event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpawnRemoval(event: MobSpawnRemovalEvent) {
        if (Spawners.isSpawning || event.entity?.let(Spawners::isSpawned) == true) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRemoval(event: MobRemovalEvent) {
        if (Spawners.isSpawned(event.entity)) event.isCancelled = true
    }

    /** A deleted town's regions go with it, each grown back first so nothing is left as bedrock. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onTownDeleted(event: DeleteTownEvent) {
        val regions = GatherManager.ofTown(event.townUUID)
        for (region in regions) {
            Spawners.clear(region)
            GatherManager.delete(region.id)
            Main.instance.logger.info("Removed the resource region ${region.id}: its town ${event.townName} was deleted")
        }
    }

    private fun lift(event: TownyActionEvent) {
        if (event.isCancelled && GatherPermits.isGranted(event.player, event.location)) {
            event.isCancelled = false
            event.suppressMessage()
        }
    }
}

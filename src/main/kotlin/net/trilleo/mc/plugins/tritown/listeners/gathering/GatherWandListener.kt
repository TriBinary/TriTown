package net.trilleo.mc.plugins.tritown.listeners.gathering

import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Color
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * The region wand: a left click picks the first corner, a right click the
 * second, and each pick traces the box chosen so far.
 *
 * Also lets go of everything gathering remembers about a player who leaves.
 */
class GatherWandListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || !GatherEditors.isWand(event.item)) return
        val block = event.clickedBlock ?: return
        val player = event.player
        if (!player.hasPermission(GatherEditors.ADMIN_PERMISSION)) return

        val first = when (event.action) {
            Action.LEFT_CLICK_BLOCK -> true
            Action.RIGHT_CLICK_BLOCK -> false
            else -> return
        }
        event.isCancelled = true

        val selection = GatherEditors.selection(player)
        if (first) selection.first = block.location else selection.second = block.location
        player.sendPrefixed(
            player.tr(
                if (first) "gathering.wand.first" else "gathering.wand.second",
                "x" to block.x, "y" to block.y, "z" to block.z,
            )
        )

        val area = selection.area() ?: return
        player.sendPrefixed(player.tr("gathering.wand.size", "volume" to area.volume))
        GatherEditors.outline(player, area, OUTLINE_SECONDS, Color.AQUA)
    }

    /** A creative-mode click breaks a block outright, which a wand must never do. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (GatherEditors.isWand(event.player.inventory.itemInMainHand)) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        GatherEditors.forget(player)
        GatherPermits.forget(player)
        GatherNotices.forget(player)
        Harvest.forget(player)
    }

    private companion object {
        const val OUTLINE_SECONDS = 10
    }
}

package net.trilleo.mc.plugins.tritown.listeners.items

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import net.trilleo.mc.plugins.tritown.content.ItemRedraw
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Redraws gear and content items drawn from older content or another item
 * language wherever a player is about to look at them: as they join, open an
 * inventory, pick something up, or take a piece in hand or put one on.
 * [ItemRedraw.refresh] is one read when nothing has changed, so this costs next
 * to nothing after the first time.
 */
class ItemRefreshListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = ItemRedraw.refreshAll(event.player.inventory)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onOpen(event: InventoryOpenEvent) {
        ItemRedraw.refreshAll(event.inventory)
        ItemRedraw.refreshAll(event.player.inventory)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onHeld(event: PlayerItemHeldEvent) {
        ItemRedraw.refresh(event.player.inventory.getItem(event.newSlot))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onArmor(event: PlayerArmorChangeEvent) {
        ItemRedraw.refresh(event.player.inventory.getItem(event.slot))
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onPickup(event: EntityPickupItemEvent) {
        if (event.entity !is Player) return
        val stack = event.item.itemStack
        if (ItemRedraw.refresh(stack)) event.item.itemStack = stack
    }
}

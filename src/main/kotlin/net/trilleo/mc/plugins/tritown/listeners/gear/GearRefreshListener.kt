package net.trilleo.mc.plugins.tritown.listeners.gear

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import net.trilleo.mc.plugins.tritown.gear.Gear
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory

/**
 * Redraws gear drawn from older content wherever a player is about to look at
 * it: as they join, open an inventory, pick something up, or take a piece in
 * hand or put one on. [Gear.refresh] is one read when nothing has changed, so
 * this costs next to nothing after the first time.
 */
class GearRefreshListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = refresh(event.player.inventory)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onOpen(event: InventoryOpenEvent) {
        refresh(event.inventory)
        refresh(event.player.inventory)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onHeld(event: PlayerItemHeldEvent) {
        Gear.refresh(event.player.inventory.getItem(event.newSlot))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onArmor(event: PlayerArmorChangeEvent) {
        Gear.refresh(event.player.inventory.getItem(event.slot))
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onPickup(event: EntityPickupItemEvent) {
        if (event.entity !is Player) return
        val stack = event.item.itemStack
        if (Gear.refresh(stack)) event.item.itemStack = stack
    }

    private fun refresh(inventory: Inventory) {
        inventory.contents.forEach(Gear::refresh)
    }
}

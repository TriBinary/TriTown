package net.trilleo.mc.plugins.tritown.listeners.combat

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import net.trilleo.mc.plugins.tritown.combat.*
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.gear.Gear
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * Keeps [PlayerStats] honest: a player's sheet is dropped whenever what they
 * wear, hold or are affected by may have changed, and rebuilt the next time it
 * is read. It also writes a shooter's stats onto what they fire.
 */
class StatListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onArmor(event: PlayerArmorChangeEvent) = PlayerStats.invalidate(event.player)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onHeld(event: PlayerItemHeldEvent) = PlayerStats.invalidate(event.player)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) = PlayerStats.invalidate(event.player)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onDrop(event: PlayerDropItemEvent) = PlayerStats.invalidate(event.player)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPickup(event: EntityPickupItemEvent) {
        (event.entity as? Player)?.let(PlayerStats::invalidate)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        (event.player as? Player)?.let(PlayerStats::invalidate)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEffect(event: EntityPotionEffectEvent) {
        (event.entity as? Player)?.let(PlayerStats::invalidate)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = PlayerStats.invalidate(event.player)

    /**
     * An arrow is worth what its bow makes it: the bow's Damage if it is gear,
     * or what `vanilla.bow-attack` says a vanilla bow counts as.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onShoot(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val projectile = event.projectile as? Projectile ?: return
        if (!Combat.isActive(shooter.world)) return

        val balance = ContentRegistry.balance
        val damage = Gear.stats(event.bow)?.get(Stat.DAMAGE) ?: (balance.vanilla.bowAttack * balance.lens)
        val multiplier = DamageMath.shotMultiplier(damage, balance.vanilla.arrowReference, balance)
        ShotStats.write(projectile, PlayerStats.sheet(shooter), multiplier)
    }

    /**
     * Tridents are thrown, not shot, so they come through here instead. A
     * thrown trident is measured against its own melee hit, so it lands for
     * what it would have struck for.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onThrow(event: ProjectileLaunchEvent) {
        val trident = event.entity as? Trident ?: return
        val thrower = trident.shooter as? Player ?: return
        if (!Combat.isActive(thrower.world)) return

        val balance = ContentRegistry.balance
        val item = trident.itemStack
        val vanilla = StatSources.attackDamage(item)
        val damage = Gear.stats(item)?.get(Stat.DAMAGE) ?: (vanilla * balance.lens)
        val multiplier = DamageMath.shotMultiplier(damage, 1.0 + vanilla, balance)
        ShotStats.write(trident, PlayerStats.sheet(thrower), multiplier)
    }
}

package net.trilleo.mc.plugins.tritown.listeners.gathering

import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.protection.Protection
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.EntitiesLoadEvent

/**
 * The mobs a region's spawners keep about.
 *
 * Only the town's residents may hurt, shear or milk them, and a resident does
 * so even where the town denies residents the right to destroy or use: the hit
 * or the click is cleared here first and Towny's refusal of it lifted (see
 * [GatherPermits]). Nobody may lead them off, rename them or breed more, and
 * the sun does not burn them, so a spawner always has what it was set up for.
 */
class GatherEntityListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onHit(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        val (region, _) = Spawners.ownerOf(victim) ?: return
        val player = Protection.responsible(event.damager) ?: return
        if (GatherEditors.isBuilding(player)) return
        // Anyone may defend themselves against a monster that has come for them.
        if ((victim as? Mob)?.target == player) return

        val refusal = GatherAccess.refusal(player, region, victim.location)
        if (refusal != null) {
            event.isCancelled = true
            GatherAccess.hint(player, region, refusal)
            return
        }
        GatherPermits.grant(player, victim.location)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        val (region, _) = Spawners.ownerOf(entity) ?: return
        val player = event.player
        if (GatherEditors.isBuilding(player)) return

        val held = player.inventory.getItem(event.hand).type
        if (held == Material.NAME_TAG || held == Material.LEAD) {
            event.isCancelled = true
            return
        }

        val refusal = GatherAccess.refusal(player, region, entity.location)
        if (refusal != null) {
            event.isCancelled = true
            GatherAccess.hint(player, region, refusal)
            return
        }
        GatherPermits.grant(player, entity.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val (_, spawner) = Spawners.ownerOf(entity) ?: return
        Spawners.forget(entity)
        entity.killer?.let { GatherStats.add(it, spawner.category) }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBreed(event: EntityBreedEvent) {
        if (spawned(event.mother) || spawned(event.father)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onLeash(event: org.bukkit.event.entity.PlayerLeashEntityEvent) {
        if (spawned(event.entity)) event.isCancelled = true
    }

    /** Burning in daylight; fire a player or a block sets is still fire. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCombust(event: EntityCombustEvent) {
        if (event !is EntityCombustByEntityEvent && event !is EntityCombustByBlockEvent && spawned(event.entity)) {
            event.isCancelled = true
        }
    }

    /** A drowned zombie or a struck pig would stop being the mob its spawner counts. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onTransform(event: EntityTransformEvent) {
        if (spawned(event.entity)) event.isCancelled = true
    }

    @EventHandler
    fun onLoad(event: EntitiesLoadEvent) {
        event.entities.forEach(Spawners::adopt)
    }

    private fun spawned(entity: Entity): Boolean = entity !is Player && Spawners.isSpawned(entity)
}

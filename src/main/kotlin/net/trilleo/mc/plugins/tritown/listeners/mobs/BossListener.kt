package net.trilleo.mc.plugins.tritown.listeners.mobs

import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.mobs.MobSetup
import net.trilleo.mc.plugins.tritown.mobs.boss.BossFights
import net.trilleo.mc.plugins.tritown.mobs.boss.BossSummons
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Sigils and the bosses they summon.
 *
 * A sigil is a content item, so it does nothing on its own; right-clicked in
 * the main hand, it summons its boss through [BossSummons]. A boss keeps to its
 * arena: it never teleports on its own, as an enderman would, or goes through
 * a portal. Only TriTown moves it (see [MobSetup.teleport]).
 */
class BossListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val id = ContentItems.idOf(event.item) ?: return
        val boss = ContentRegistry.bestiary.bossForSigil(id) ?: return

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        BossSummons.summon(event.player, boss)
    }

    @EventHandler(ignoreCancelled = true)
    fun onTeleport(event: EntityTeleportEvent) {
        val mob = event.entity as? LivingEntity ?: return
        if (BossFights.isBoss(mob) && !MobSetup.isMovingOnPurpose(mob.uniqueId)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onPortal(event: EntityPortalEvent) {
        val mob = event.entity as? LivingEntity ?: return
        if (BossFights.isBoss(mob)) event.isCancelled = true
    }
}

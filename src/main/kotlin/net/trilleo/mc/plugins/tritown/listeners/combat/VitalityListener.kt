package net.trilleo.mc.plugins.tritown.listeners.combat

import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.combat.PlayerStats
import net.trilleo.mc.plugins.tritown.combat.Stat
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason

/**
 * Vitality: a player's natural regeneration and healing potions give back
 * more, by its value in percent. Health is a share of the pool, so this is a
 * share too, and grows with the pool rather than falling behind it.
 */
class VitalityListener : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onRegain(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        if (event.regainReason !in HEALING || !Combat.isActive(player.world)) return
        val vitality = PlayerStats.sheet(player)[Stat.VITALITY]
        if (vitality > 0.0) event.amount *= 1.0 + vitality / 100.0
    }

    private companion object {
        val HEALING = setOf(RegainReason.SATIATED, RegainReason.REGEN, RegainReason.MAGIC, RegainReason.MAGIC_REGEN)
    }
}

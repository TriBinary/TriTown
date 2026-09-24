package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.mobs.MobProfiles
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * The RPG health pool drawn over an entity's vanilla health.
 *
 * **Vanilla health is the truth.** Nothing here is stored: the pool is always
 * as full as the entity's vanilla health is, so regeneration, potions, totems,
 * `/kill` and death need nothing from TriTown, and the two can never disagree.
 * Neither a player's nor a mob's vanilla maximum is ever changed.
 */
object CombatHealth {

    fun vanillaMax(entity: LivingEntity): Double =
        entity.getAttribute(Attribute.MAX_HEALTH)?.value?.takeIf { it > 0.0 } ?: DamageMath.VANILLA_PLAYER_HEALTH

    fun max(entity: LivingEntity): Double = when (entity) {
        is Player -> DamageMath.playerMaxHealth(PlayerStats.sheet(entity)[Stat.HEALTH], vanillaMax(entity))
        else -> DamageMath.mobMaxHealth(vanillaMax(entity), ContentRegistry.balance, MobProfiles.level(entity))
    }

    fun current(entity: LivingEntity): Double =
        DamageMath.toRpg(entity.health, max(entity), vanillaMax(entity)).coerceAtLeast(0.0)
}

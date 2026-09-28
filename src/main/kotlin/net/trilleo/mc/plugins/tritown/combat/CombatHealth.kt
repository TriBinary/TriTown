package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.mobs.MobPower
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

    fun max(entity: LivingEntity): Double {
        if (entity is Player) return DamageMath.playerMaxHealth(
            PlayerStats.sheet(entity)[Stat.HEALTH],
            vanillaMax(entity)
        )
        val balance = ContentRegistry.balance
        val profile = MobProfiles.of(entity)
        return DamageMath.mobMaxHealth(vanillaMax(entity), balance, profile.level, MobPower.health(profile, balance))
    }

    /** How full the pool is, from 0 to 1 — the same as the entity's hearts. */
    fun fraction(entity: LivingEntity): Double = (entity.health / vanillaMax(entity)).coerceIn(0.0, 1.0)

    fun current(entity: LivingEntity): Double =
        DamageMath.toRpg(entity.health, max(entity), vanillaMax(entity)).coerceAtLeast(0.0)
}

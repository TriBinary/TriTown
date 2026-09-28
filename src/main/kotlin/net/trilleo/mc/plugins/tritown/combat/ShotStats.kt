package net.trilleo.mc.plugins.tritown.combat

import org.bukkit.NamespacedKey
import org.bukkit.entity.Projectile
import org.bukkit.persistence.PersistentDataType

/**
 * What a shot was fired with, written onto an arrow or trident as it leaves
 * the bow or the hand: the shooter's stats, and what the weapon makes each
 * point of the shot's damage worth ([DamageMath.shotMultiplier]).
 *
 * A shot lands with what it was fired with, so swapping to a different item
 * while it flies changes nothing. Kept in the projectile's own data, it
 * outlives the shooter logging off and survives a chunk unload.
 */
object ShotStats {

    class Shot(val stats: StatSheet, val multiplier: Double)

    private val KEY = NamespacedKey("tritown", "shot")
    private val CARRIED = listOf(Stat.STRENGTH, Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE)

    fun write(projectile: Projectile, shooter: StatSheet, multiplier: Double) {
        val values = CARRIED.map { shooter[it] } + multiplier
        projectile.persistentDataContainer.set(KEY, PersistentDataType.LIST.doubles(), values)
    }

    fun read(projectile: Projectile): Shot? {
        val values = projectile.persistentDataContainer.get(KEY, PersistentDataType.LIST.doubles()) ?: return null
        if (values.size != CARRIED.size + 1) return null
        return Shot(StatSheet.of(*CARRIED.zip(values).toTypedArray()), values.last())
    }
}

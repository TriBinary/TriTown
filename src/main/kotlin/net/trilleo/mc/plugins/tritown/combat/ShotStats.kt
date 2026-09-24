package net.trilleo.mc.plugins.tritown.combat

import org.bukkit.NamespacedKey
import org.bukkit.entity.Projectile
import org.bukkit.persistence.PersistentDataType

/**
 * The shooter's stats, written onto an arrow or trident as it leaves the bow.
 *
 * A shot lands with the stats it was fired with, so swapping to a different
 * item while it flies changes nothing. Kept in the projectile's own data, it
 * outlives the shooter logging off and survives a chunk unload.
 */
object ShotStats {

    private val KEY = NamespacedKey("tritown", "shot")
    private val CARRIED = listOf(Stat.STRENGTH, Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE)

    fun write(projectile: Projectile, shooter: StatSheet) {
        projectile.persistentDataContainer.set(KEY, PersistentDataType.LIST.doubles(), CARRIED.map { shooter[it] })
    }

    fun read(projectile: Projectile): StatSheet? {
        val values = projectile.persistentDataContainer.get(KEY, PersistentDataType.LIST.doubles()) ?: return null
        if (values.size != CARRIED.size) return null
        return StatSheet.of(*CARRIED.zip(values).toTypedArray())
    }
}

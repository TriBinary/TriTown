package net.trilleo.mc.plugins.tritown.mobs

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.content.SpawnPlace
import org.bukkit.Location
import org.bukkit.World
import kotlin.math.hypot

/**
 * The level a mob spawning somewhere would be.
 *
 * Distance from the world's spawn picks the ring (see [LevelZone]); night and
 * depth add to it in the Overworld. **Town claims are always level 1**, so a
 * town is safe, and a mob farm built inside one works exactly as it does in
 * vanilla. Towny is asked afresh every time.
 */
object MobZones {

    /** Everything that went into a level, so `/tritown mob level` can say why. */
    class Reading(val level: Int, val ring: Int, val night: Boolean, val deep: Boolean, val town: Boolean)

    fun levelAt(location: Location): Int = read(location).level

    fun read(location: Location): Reading {
        if (!TownyAPI.getInstance().isWilderness(location)) return Reading(1, 0, false, false, true)

        val world = location.world
        val settings = MobSettings.snapshot
        val zone = settings.zone(world)
        val spawn = world.spawnLocation
        val distance = hypot(location.x - spawn.x, location.z - spawn.z)

        val overworld = world.environment == World.Environment.NORMAL
        val night = overworld && !world.isDayTime
        val deep = overworld && location.y < 0
        val bonus = (if (night) settings.nightBonus else 0) + (if (deep) settings.depthBonus else 0)

        return Reading(zone.level(distance, bonus), zone.ring(distance), night, deep, false)
    }

    /** What a custom mob's spawn rule is checked against at [location]. */
    fun place(location: Location): SpawnPlace {
        val world = location.world
        return SpawnPlace(
            environment = world.environment.name.lowercase(),
            world = world.name.lowercase(),
            biome = location.block.biome.key.asString(),
            y = location.blockY,
            night = world.environment == World.Environment.NORMAL && !world.isDayTime,
        )
    }
}

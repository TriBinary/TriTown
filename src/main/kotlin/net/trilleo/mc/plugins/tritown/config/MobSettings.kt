package net.trilleo.mc.plugins.tritown.config

import net.trilleo.mc.plugins.tritown.mobs.LevelZone
import org.bukkit.World
import org.bukkit.entity.EntityType

/**
 * An immutable snapshot of the `mobs` block of `config.yml`.
 *
 * @param nightBonus levels an Overworld mob gains for spawning at night
 * @param depthBonus levels an Overworld mob gains for spawning below Y 0
 * @param xpPerLevel how much more experience a mob drops per level above 1, as a fraction
 * @param nameplates whether levelled mobs carry their level and health as a name
 * @param rankExempt kinds of mob that never spawn ranked
 * @param customMobs whether the custom mobs of `bestiary.yml` take the place of wild spawns
 */
data class MobSettings(
    val nightBonus: Int,
    val depthBonus: Int,
    val overworld: LevelZone,
    val nether: LevelZone,
    val end: LevelZone,
    val worlds: Map<String, LevelZone>,
    val xpPerLevel: Double,
    val nameplates: Boolean,
    val rankExempt: Set<EntityType>,
    val customMobs: Boolean,
) {

    /** The rings mobs in [world] follow: its own, if it has any, or those of its kind of world. */
    fun zone(world: World): LevelZone = worlds[world.name.lowercase()] ?: when (world.environment) {
        World.Environment.NETHER -> nether
        World.Environment.THE_END -> end
        else -> overworld
    }

    companion object {

        private const val LEVELS = "mobs.levels"

        @Volatile
        private var current: MobSettings? = null

        val snapshot: MobSettings
            get() = current ?: error("Mob settings have not been loaded yet")

        val isLoaded: Boolean
            get() = current != null

        fun load(config: PluginConfig): MobSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): MobSettings {
            val overworld = zone(config, "$LEVELS.overworld", LevelZone(1, 300.0, 500.0, 2, 30))
            val worlds = config.getKeys("$LEVELS.worlds").associate { name ->
                name.lowercase() to zone(config, "$LEVELS.worlds.$name", overworld)
            }
            return MobSettings(
                nightBonus = config.getInt("mobs.levels.night-bonus", 2).coerceAtLeast(0),
                depthBonus = config.getInt("mobs.levels.depth-bonus", 2).coerceAtLeast(0),
                overworld = overworld,
                nether = zone(config, "$LEVELS.nether", LevelZone(4, 64.0, 64.0, 2, 45)),
                end = zone(config, "$LEVELS.end", LevelZone(8, 1000.0, 500.0, 3, 60)),
                worlds = worlds,
                xpPerLevel = config.getDouble("mobs.xp-per-level", 0.02).coerceAtLeast(0.0),
                nameplates = config.getBoolean("mobs.nameplates", true),
                rankExempt = config.getStringList("mobs.ranks.exempt")
                    .mapNotNull { name -> EntityType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                    .toSet(),
                customMobs = config.getBoolean("mobs.custom.enabled", true),
            )
        }

        /** A zone at [path], with anything it leaves out taken from [fallback]. */
        private fun zone(config: PluginConfig, path: String, fallback: LevelZone): LevelZone = LevelZone(
            base = config.getInt("$path.base", fallback.base).coerceAtLeast(1),
            spawnRadius = config.getDouble("$path.spawn-radius", fallback.spawnRadius).coerceAtLeast(0.0),
            ringWidth = config.getDouble("$path.ring-width", fallback.ringWidth).coerceAtLeast(1.0),
            perRing = config.getInt("$path.per-ring", fallback.perRing).coerceAtLeast(0),
            cap = config.getInt("$path.cap", fallback.cap).coerceAtLeast(1),
        )
    }
}

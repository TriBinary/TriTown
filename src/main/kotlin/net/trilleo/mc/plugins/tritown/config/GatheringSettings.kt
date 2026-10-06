package net.trilleo.mc.plugins.tritown.config

import net.trilleo.mc.plugins.tritown.gathering.GatherCategory
import org.bukkit.Material

/**
 * An immutable snapshot of the `gathering` block of `config.yml`.
 *
 * @param enabled       whether resource regions exist at all
 * @param maxVolume     the most blocks one region may cover
 * @param defaultRegrow seconds a new resource takes to grow back
 * @param depleted      what stands in for a harvested block, per block category
 * @param spawnerRange  how close a player has to be for a spawner to call up mobs
 * @param effects       whether a node growing back shows particles and plays a sound
 * @param entryTitles   whether walking into a region shows its name
 * @param saveSeconds   seconds between writing harvested blocks to disk
 */
data class GatheringSettings(
    val enabled: Boolean,
    val maxVolume: Long,
    val defaultRegrow: Int,
    val depleted: Map<GatherCategory, Material>,
    val spawnerRange: Double,
    val effects: Boolean,
    val entryTitles: Boolean,
    val saveSeconds: Long,
) {

    /** The block a harvested node of [category] leaves behind when its rule names none. */
    fun depletedFor(category: GatherCategory): Material = depleted[category] ?: Material.BEDROCK

    companion object {

        @Volatile
        private var current: GatheringSettings? = null

        val snapshot: GatheringSettings
            get() = current ?: error("Gathering settings have not been loaded yet")

        val isLoaded: Boolean
            get() = current != null

        fun load(config: PluginConfig): GatheringSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): GatheringSettings = GatheringSettings(
            enabled = config.getBoolean("gathering.enabled", true),
            maxVolume = config.getLong("gathering.max-volume", 250_000L).coerceAtLeast(1L),
            defaultRegrow = config.getInt("gathering.default-regrow-seconds", 60).coerceIn(1, 86_400),
            depleted = GatherCategory.entries.filter { it.forBlocks }.associateWith { category ->
                val name = config.getString("gathering.depleted-blocks.${category.name.lowercase()}", "BEDROCK")
                Material.matchMaterial(name)?.takeIf { it.isBlock } ?: Material.BEDROCK
            },
            spawnerRange = config.getDouble("gathering.spawner-range", 48.0).coerceIn(8.0, 256.0),
            effects = config.getBoolean("gathering.effects", true),
            entryTitles = config.getBoolean("gathering.entry-titles", true),
            saveSeconds = config.getLong("gathering.save-interval", 60L).coerceIn(5L, 3600L),
        )
    }
}

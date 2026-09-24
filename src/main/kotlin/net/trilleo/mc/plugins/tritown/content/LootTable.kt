package net.trilleo.mc.plugins.tritown.content

/**
 * What wild mobs drop on top of vanilla's loot, read from `mobs.yml`.
 *
 * Mob kinds are held by their `EntityType` names, so this stays plain Kotlin
 * that a test can build.
 *
 * @param families every mob kind that drops a material, by `EntityType` name
 * @param essence the essence each level drops, lowest first: a mob drops the first grade its level is within
 */
data class LootTable(
    val families: Map<String, Family>,
    val rules: Rules,
    val normal: Drop,
    val elite: Drop,
    val champion: Drop,
    val essence: List<EssenceGrade>,
) {

    /** Mob kinds that drop the same [material]. */
    data class Family(val id: String, val material: String)

    /**
     * @param minLevel the lowest level a mob drops anything extra at
     * @param playerShare percent of a mob's health players must have dealt for it to drop anything extra
     * @param magicFindCap the most Magic Find that counts
     */
    data class Rules(val minLevel: Int, val playerShare: Double, val magicFindCap: Double)

    /**
     * What a mob of one rank drops: its family's material [chance] percent of
     * the time, and essence. Amounts are inclusive ranges; an essence range of
     * zero drops none.
     */
    data class Drop(
        val chance: Double,
        val minAmount: Int,
        val maxAmount: Int,
        val minEssence: Int,
        val maxEssence: Int,
    )

    data class EssenceGrade(val item: String, val maxLevel: Int)

    /** The essence a mob of [level] drops: the first grade its level is within, or the last. */
    fun essenceFor(level: Int): String? = (essence.firstOrNull { level <= it.maxLevel } ?: essence.lastOrNull())?.item

    companion object {
        val EMPTY = LootTable(
            families = emptyMap(),
            rules = Rules(minLevel = 1, playerShare = 50.0, magicFindCap = 100.0),
            normal = Drop(0.0, 0, 0, 0, 0),
            elite = Drop(0.0, 0, 0, 0, 0),
            champion = Drop(0.0, 0, 0, 0, 0),
            essence = emptyList(),
        )
    }
}

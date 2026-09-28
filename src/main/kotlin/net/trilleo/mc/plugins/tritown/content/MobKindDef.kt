package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.mobs.Affix

/**
 * A custom mob defined in `bestiary.yml`: a vanilla kind of mob with a name, a
 * look, traits and loot of its own.
 *
 * It never lists its health or its hits. It lists what it multiplies its
 * kind's by, on top of what its level and rank make of them, and a mob only
 * stores which kind it is — so a retune reaches every one already out there.
 * Its name and description are `mob.kind.<id>.name` and `mob.kind.<id>.lore` in
 * the language files.
 *
 * @param base the vanilla kind it is, by its `EntityType` name
 * @param spawn when it takes the place of a wild spawn of its [base] kind
 * @param rankable whether it may still spawn as an elite or a champion
 * @param health what it multiplies its pool by
 * @param damage what it multiplies its hits by
 * @param defense Defense it has, the way a player's armor works against mobs
 * @param speed percent faster it moves; less than 0 is slower
 * @param scale how large it is, where 1 is its kind's own size
 * @param knockback percent of knockback it shrugs off
 * @param affixes affixes it always has, on top of any its rank rolls
 * @param equipment what it wears and holds, for the look alone
 * @param loot what it drops on top of its family's material and its rank's essence
 */
data class MobKindDef(
    val id: String,
    val base: String,
    val spawn: SpawnRule?,
    val rankable: Boolean,
    val health: Double,
    val damage: Double,
    val defense: Double,
    val speed: Double,
    val scale: Double,
    val knockback: Double,
    val affixes: Set<Affix>,
    val equipment: Map<CostumeSlot, Costume>,
    val loot: List<LootEntry>,
) {

    val nameKey: String
        get() = "$KEY_PREFIX$id.name"

    val loreKey: String
        get() = "$KEY_PREFIX$id.lore"

    companion object {
        /** Where custom mobs' names live in the language files, which the language tests know is built at runtime. */
        const val KEY_PREFIX = "mob.kind."
    }
}

/**
 * When a custom mob takes the place of a wild spawn of its kind: somewhere it
 * may live, at a level it may be, and then [chance] percent of the time.
 *
 * @param worlds kinds of world (`normal`, `nether`, `the_end`) or world names it lives in; empty for any
 * @param biomes biome keys such as `minecraft:desert` it lives in; empty for any
 */
data class SpawnRule(
    val worlds: Set<String>,
    val biomes: Set<String>,
    val minLevel: Int,
    val maxLevel: Int,
    val chance: Double,
    val time: SpawnTime,
    val minY: Int?,
    val maxY: Int?,
) {

    /** Whether a wild spawn at [level], at [place], may become this kind before its chance is rolled. */
    fun allows(level: Int, place: SpawnPlace): Boolean {
        if (level !in minLevel..maxLevel) return false
        if (worlds.isNotEmpty() && place.environment !in worlds && place.world !in worlds) return false
        if (biomes.isNotEmpty() && place.biome !in biomes) return false
        if (minY != null && place.y < minY) return false
        if (maxY != null && place.y > maxY) return false
        return when (time) {
            SpawnTime.ANY -> true
            SpawnTime.DAY -> !place.night
            SpawnTime.NIGHT -> place.night
        }
    }
}

enum class SpawnTime {
    ANY, DAY, NIGHT;

    companion object {
        fun of(name: String): SpawnTime? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Where a wild mob is spawning, as plain values a [SpawnRule] is checked
 * against. Names are lower-case, and the biome is a full key.
 *
 * @param night whether it is night in the Overworld; never in the Nether or the End
 */
data class SpawnPlace(val environment: String, val world: String, val biome: String, val y: Int, val night: Boolean)

/** Where a custom mob wears or holds a piece of its costume. */
enum class CostumeSlot {
    HEAD, CHEST, LEGS, FEET, HAND, OFF_HAND;

    companion object {
        fun of(name: String): CostumeSlot? =
            entries.firstOrNull { it.name.equals(name.replace('-', '_'), ignoreCase = true) }
    }
}

/**
 * What a custom mob wears in one slot. Only ever a look: it gives the mob no
 * armor or attack damage, and never drops.
 */
sealed interface Costume {

    /** A piece of gear from `gear.yml`, by id, as it looks. */
    data class Gear(val id: String) : Costume

    /** A vanilla item, by its Minecraft name. */
    data class Vanilla(val material: String) : Costume
}

/** One line of a custom mob's own loot, rolled on its own, [chance] percent of the time. */
sealed interface LootEntry {

    val chance: Double

    /** Between [min] and [max] of the content item [id]. */
    data class Item(val id: String, override val chance: Double, val min: Int, val max: Int) : LootEntry

    /** A finished piece of the gear [id], at a rarity from `drop-odds`. */
    data class Gear(val id: String, override val chance: Double) : LootEntry
}

/** Everything `bestiary.yml` defines, by id. */
data class BestiaryCatalog(val kinds: Map<String, MobKindDef>) {

    private val variants: Map<String, List<MobKindDef>> = kinds.values.filter { it.spawn != null }.groupBy { it.base }

    /** The kinds that may take the place of a wild spawn of [type] (an `EntityType` name), in the file's order. */
    fun variantsFor(type: String): List<MobKindDef> = variants[type].orEmpty()

    companion object {
        val EMPTY = BestiaryCatalog(emptyMap())
    }
}

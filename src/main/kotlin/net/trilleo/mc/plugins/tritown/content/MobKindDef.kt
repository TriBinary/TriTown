package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.mobs.Ability
import net.trilleo.mc.plugins.tritown.mobs.Affix

/**
 * A custom mob defined in `bestiary.yml`: a vanilla kind of mob with a name, a
 * look, traits and loot of its own. A variant takes the place of wild spawns
 * ([spawn]); a boss is summoned with its sigil ([boss]).
 *
 * It never lists its health or its hits. It lists what it multiplies its
 * kind's by, on top of what its level and rank make of them, and a mob only
 * stores which kind it is — so a retune reaches every one already out there.
 * Its name and description are `mob.kind.<id>.name` and `mob.kind.<id>.lore` in
 * the language files.
 *
 * @param base the vanilla kind it is, by its `EntityType` name
 * @param spawn when it takes the place of a wild spawn of its [base] kind; `null` for a boss
 * @param boss what makes it a boss, or `null` for a variant
 * @param rankable whether it may still spawn as an elite or a champion
 * @param health what it multiplies its pool by
 * @param damage what it multiplies its hits by
 * @param defense Defense it has, the way a player's armor works against mobs
 * @param speed percent faster it moves; less than 0 is slower
 * @param scale how large it is, where 1 is its kind's own size
 * @param knockback percent of knockback it shrugs off
 * @param affixes affixes it always has, on top of any its rank rolls
 * @param abilities what it does in a fight, on cooldowns
 * @param minions the id of the kind its Summon calls, or `null` for its own
 * @param equipment what it wears and holds, for the look alone
 * @param loot what it drops on top of its family's material and its rank's essence; a
 *   boss's is all it drops, rolled once for each player who earned a share
 */
data class MobKindDef(
    val id: String,
    val base: String,
    val spawn: SpawnRule?,
    val boss: BossDef?,
    val rankable: Boolean,
    val health: Double,
    val damage: Double,
    val defense: Double,
    val speed: Double,
    val scale: Double,
    val knockback: Double,
    val affixes: Set<Affix>,
    val abilities: List<Ability>,
    val minions: String?,
    val equipment: Map<CostumeSlot, Costume>,
    val loot: List<LootEntry>,
) {

    val nameKey: String
        get() = "$KEY_PREFIX$id.name"

    val loreKey: String
        get() = "$KEY_PREFIX$id.lore"

    /** The line announced as a boss enters its phase at [index], counting from 0. */
    fun phaseKey(index: Int): String = "$KEY_PREFIX$id.phase-${index + 1}"

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

/**
 * What makes a custom mob a boss: a fight a player starts on purpose, with its
 * [sigil], at a fixed [level], held to an arena around where it was summoned.
 *
 * @param sigil the id of the item in `items.yml` that summons it
 * @param arena how far from where it was summoned it fights, in blocks
 * @param bar the colour of its boss bar: pink, blue, red, green, yellow, purple or white
 * @param phases what changes as its health falls, highest threshold first
 */
data class BossDef(
    val level: Int,
    val sigil: String,
    val place: SummonPlace,
    val arena: Double,
    val bar: String,
    val phases: List<BossPhase>,
) {
    companion object {
        val BAR_COLORS = setOf("pink", "blue", "red", "green", "yellow", "purple", "white")
    }
}

/**
 * Where a boss may be summoned.
 *
 * @param worlds kinds of world (`normal`, `nether`, `the_end`) or world names; empty for any
 * @param water whether it must be summoned from water
 * @param maxY the highest it may be summoned at, or `null` for any height
 */
data class SummonPlace(val worlds: Set<String>, val water: Boolean, val maxY: Int?)

/**
 * What a boss gains once its health falls below [below] percent: affixes and
 * abilities it keeps from then on, and [summon] minions called at once.
 */
data class BossPhase(val below: Double, val affixes: Set<Affix>, val abilities: List<Ability>, val summon: Int)

/**
 * How every boss fight goes, from the `rules` block of `bestiary.yml`.
 *
 * @param contributorShare percent of a boss's health a player must have dealt to earn a share of its loot
 * @param lootRange how near a contributor must be for their share to fall where the boss did; further off, it goes
 *   straight to them
 * @param idleSeconds how long a boss waits with nobody in its arena before it leaves
 * @param ritualSeconds how long a sigil takes to summon its boss
 * @param arenaMargin how far past its arena a boss may be pulled before it is taken back
 * @param gearChance percent chance each share has of a finished piece of the boss's tier, too
 * @param gearOdds the rarities a boss's gear drops at, weighted
 */
data class BossRules(
    val contributorShare: Double,
    val lootRange: Double,
    val idleSeconds: Int,
    val ritualSeconds: Int,
    val arenaMargin: Double,
    val gearChance: Double,
    val gearOdds: Map<Rarity, Double>,
) {
    companion object {
        val DEFAULT = BossRules(
            contributorShare = 10.0,
            lootRange = 48.0,
            idleSeconds = 120,
            ritualSeconds = 3,
            arenaMargin = 8.0,
            gearChance = 25.0,
            gearOdds = mapOf(Rarity.RARE to 40.0, Rarity.EPIC to 35.0, Rarity.LEGENDARY to 20.0, Rarity.MYTHIC to 5.0),
        )
    }
}

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

/** Everything `bestiary.yml` defines: its custom mobs by id, and how boss fights go. */
data class BestiaryCatalog(val kinds: Map<String, MobKindDef>, val rules: BossRules = BossRules.DEFAULT) {

    private val variants: Map<String, List<MobKindDef>> = kinds.values.filter { it.spawn != null }.groupBy { it.base }
    private val bySigil: Map<String, MobKindDef> =
        kinds.values.mapNotNull { def -> def.boss?.let { it.sigil to def } }.toMap()

    /** The kinds that may take the place of a wild spawn of [type] (an `EntityType` name), in the file's order. */
    fun variantsFor(type: String): List<MobKindDef> = variants[type].orEmpty()

    /** The boss the item [id] summons, if it is a sigil. */
    fun bossForSigil(id: String): MobKindDef? = bySigil[id]

    companion object {
        val EMPTY = BestiaryCatalog(emptyMap())
    }
}

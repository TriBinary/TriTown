package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.content.LootEntry
import net.trilleo.mc.plugins.tritown.content.LootTable
import kotlin.random.Random

/**
 * What a dead mob drops on top of vanilla's loot. Plain Kotlin over a [Random]
 * the caller hands in, so the odds can be tested with a seed.
 *
 * Whether it drops anything at all — a wild mob, killed by a player, who with
 * the others dealt enough of the damage — is the caller's to decide; this only
 * rolls the dice.
 */
object LootRoller {

    data class Drop(val item: String, val amount: Int)

    /** A line of a custom mob's own loot that came up, and how many of it: always one piece of gear. */
    data class Won(val entry: LootEntry, val amount: Int)

    /**
     * The drops of a mob of [type] (its `EntityType` name), [rank] and
     * [level], killed by someone with [magicFind]. Magic Find only multiplies
     * a chance that is short of certain, up to the table's cap.
     */
    fun roll(table: LootTable, type: String, rank: MobRank, level: Int, magicFind: Double, random: Random): List<Drop> {
        if (level < table.rules.minLevel) return emptyList()
        val drop = dropFor(table, rank)
        val drops = mutableListOf<Drop>()

        table.families[type]?.let { family ->
            if (random.nextDouble() * 100.0 < drop.chance * boost(table, magicFind)) {
                val amount = random.nextInt(drop.minAmount, drop.maxAmount + 1)
                if (amount > 0) drops += Drop(family.material, amount)
            }
        }

        table.essenceFor(level)?.let { essence ->
            val amount = random.nextInt(drop.minEssence, drop.maxEssence + 1)
            if (amount > 0) drops += Drop(essence, amount)
        }
        return drops
    }

    /**
     * Whether a mob of [rank] and [level] drops a finished piece of gear, its
     * chance multiplied by Magic Find like any other rare drop.
     */
    fun dropsGear(table: LootTable, rank: MobRank, level: Int, magicFind: Double, random: Random): Boolean {
        if (level < table.rules.minLevel) return false
        val chance = dropFor(table, rank).gearChance
        if (chance <= 0.0) return false
        return random.nextDouble() * 100.0 < chance * boost(table, magicFind)
    }

    /**
     * What a custom mob's own [entries] give, each rolled on its own and each
     * chance multiplied by Magic Find like any other. Nothing below the table's
     * minimum level.
     */
    fun rollEntries(table: LootTable, entries: List<LootEntry>, level: Int, magicFind: Double, random: Random): List<Won> {
        if (level < table.rules.minLevel) return emptyList()
        val boost = boost(table, magicFind)
        return entries.mapNotNull { entry ->
            if (random.nextDouble() * 100.0 >= entry.chance * boost) return@mapNotNull null
            val amount = when (entry) {
                is LootEntry.Item -> random.nextInt(entry.min, entry.max + 1)
                is LootEntry.Gear -> 1
            }
            Won(entry, amount)
        }
    }

    private fun dropFor(table: LootTable, rank: MobRank): LootTable.Drop = when (rank) {
        MobRank.NORMAL -> table.normal
        MobRank.ELITE -> table.elite
        MobRank.CHAMPION -> table.champion
    }

    private fun boost(table: LootTable, magicFind: Double): Double =
        1.0 + magicFind.coerceIn(0.0, table.rules.magicFindCap) / 100.0
}

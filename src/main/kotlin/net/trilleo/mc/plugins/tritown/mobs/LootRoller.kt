package net.trilleo.mc.plugins.tritown.mobs

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

    /**
     * The drops of a mob of [type] (its `EntityType` name), [rank] and
     * [level], killed by someone with [magicFind]. Magic Find only multiplies
     * a chance that is short of certain, up to the table's cap.
     */
    fun roll(table: LootTable, type: String, rank: MobRank, level: Int, magicFind: Double, random: Random): List<Drop> {
        if (level < table.rules.minLevel) return emptyList()
        val drop = when (rank) {
            MobRank.NORMAL -> table.normal
            MobRank.ELITE -> table.elite
            MobRank.CHAMPION -> table.champion
        }
        val drops = mutableListOf<Drop>()

        table.families[type]?.let { family ->
            val boost = 1.0 + magicFind.coerceIn(0.0, table.rules.magicFindCap) / 100.0
            if (random.nextDouble() * 100.0 < drop.chance * boost) {
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
}

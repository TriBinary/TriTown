package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.content.Balance
import kotlin.random.Random

/**
 * The dice a wild mob's rank and affixes are decided by. Plain Kotlin over a
 * [Random] the caller hands in, so the odds can be tested with a seed.
 */
object MobRoll {

    /** The rank of a wild mob spawning at [level]. The champion roll comes first. */
    fun rank(level: Int, balance: Balance, random: Random): MobRank {
        val champion = balance.ranks.champion
        if (level >= champion.minLevel && random.nextDouble() * 100.0 < champion.chance) return MobRank.CHAMPION
        val elite = balance.ranks.elite
        if (level >= elite.minLevel && random.nextDouble() * 100.0 < elite.chance) return MobRank.ELITE
        return MobRank.NORMAL
    }

    /** Distinct affixes for a mob of [rank], as many as its tuning allows. */
    fun affixes(rank: MobRank, balance: Balance, random: Random): List<Affix> {
        val tuning = rank.tuning(balance) ?: return emptyList()
        val count = random.nextInt(tuning.minAffixes, tuning.maxAffixes + 1).coerceAtMost(Affix.entries.size)
        return Affix.entries.shuffled(random).take(count)
    }
}

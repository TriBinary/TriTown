package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.combat.Stat
import kotlin.math.pow

/**
 * What gear is worth, the `gear` block of `balance.yml`.
 *
 * Every piece has the same number of points as every other piece of its slot
 * and tier, and spends them on stats by the weights `gear.yml` gives it. So an
 * item's shape is its author's choice, and its worth is the budget's: no piece
 * can come out stronger than its tier allows.
 *
 * @param slotPoints points a piece has to spend, by slot
 * @param stats what points buy of each stat, and how that grows with the tier
 * @param rarity what each rarity multiplies a piece's stats by
 * @param starBonus percent more base stats each star gives
 * @param rollMin the lowest quality, in percent, a stat can roll at
 * @param reforgeShare a reforge's worth, in percent of the piece's points
 * @param craftOdds the rarities the Forge crafts, weighted
 * @param dropOdds the rarities a champion's gear drops at, weighted
 */
data class GearTuning(
    val slotPoints: Map<GearSlot, Double>,
    val stats: Map<Stat, StatCurve>,
    val rarity: Map<Rarity, Double>,
    val starBonus: Double,
    val maxStars: Int,
    val rollMin: Int,
    val rollMax: Int,
    val reforgeShare: Double,
    val craftOdds: Map<Rarity, Double>,
    val dropOdds: Map<Rarity, Double>,
) {

    /**
     * What 100 points buy of one stat on tier-1 gear, and how much more each
     * tier above buys: a tier-T piece's points buy `growth^(T-1)` times as much.
     */
    data class StatCurve(val per100: Double, val growth: Double) {

        fun value(points: Double, tier: Int): Double = points / 100.0 * per100 * growth.pow(tier.coerceAtLeast(1) - 1)
    }

    fun points(slot: GearSlot): Double = slotPoints[slot] ?: 0.0

    fun curve(stat: Stat): StatCurve = stats[stat] ?: StatCurve(0.0, 1.0)

    fun rarityMultiplier(rarity: Rarity): Double = this.rarity[rarity] ?: 1.0

    fun starMultiplier(stars: Int): Double = 1.0 + starBonus / 100.0 * stars.coerceIn(0, maxStars)

    companion object {
        val DEFAULT = GearTuning(
            slotPoints = mapOf(
                GearSlot.WEAPON to 100.0,
                GearSlot.BOW to 100.0,
                GearSlot.HELMET to 50.0,
                GearSlot.CHESTPLATE to 80.0,
                GearSlot.LEGGINGS to 70.0,
                GearSlot.BOOTS to 40.0,
            ),
            stats = mapOf(
                Stat.HEALTH to StatCurve(120.0, 1.5),
                Stat.DEFENSE to StatCurve(100.0, 1.3),
                Stat.DAMAGE to StatCurve(50.0, 1.7),
                Stat.STRENGTH to StatCurve(85.0, 1.4),
                Stat.CRIT_CHANCE to StatCurve(20.0, 1.0),
                Stat.CRIT_DAMAGE to StatCurve(80.0, 1.25),
                Stat.SPEED to StatCurve(10.0, 1.0),
                Stat.VITALITY to StatCurve(40.0, 1.05),
                Stat.MAGIC_FIND to StatCurve(30.0, 1.05),
            ),
            rarity = mapOf(
                Rarity.COMMON to 1.0,
                Rarity.UNCOMMON to 1.06,
                Rarity.RARE to 1.12,
                Rarity.EPIC to 1.2,
                Rarity.LEGENDARY to 1.3,
                Rarity.MYTHIC to 1.4,
            ),
            starBonus = 4.0,
            maxStars = 5,
            rollMin = 90,
            rollMax = 100,
            reforgeShare = 8.0,
            craftOdds = mapOf(Rarity.COMMON to 60.0, Rarity.UNCOMMON to 25.0, Rarity.RARE to 11.0, Rarity.EPIC to 4.0),
            dropOdds = mapOf(Rarity.UNCOMMON to 45.0, Rarity.RARE to 35.0, Rarity.EPIC to 15.0, Rarity.LEGENDARY to 5.0),
        )
    }
}

/**
 * What the Forge charges, the `forge` block of `balance.yml`. Money is scaled
 * by `moneyGrowth^(tier-1)`, so a tier-10 piece costs what its tier is worth.
 *
 * @param refineCap the highest rarity the Forge refines to; anything above only drops
 */
data class ForgeTuning(
    val moneyGrowth: Double,
    val upgradeEssence: Int,
    val upgradeMoney: Double,
    val refineEssence: Int,
    val refineMoney: Double,
    val refineCap: Rarity,
    val reforgeEssence: Int,
    val reforgeMoney: Double,
    val salvageEssence: Int,
) {

    fun money(base: Double, tier: Int): Double = base * moneyGrowth.pow(tier.coerceAtLeast(1) - 1)

    companion object {
        val DEFAULT = ForgeTuning(
            moneyGrowth = 1.5,
            upgradeEssence = 2,
            upgradeMoney = 100.0,
            refineEssence = 6,
            refineMoney = 400.0,
            refineCap = Rarity.LEGENDARY,
            reforgeEssence = 1,
            reforgeMoney = 150.0,
            salvageEssence = 2,
        )
    }
}

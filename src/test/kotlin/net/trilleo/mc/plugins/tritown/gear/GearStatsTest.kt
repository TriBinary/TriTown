package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.content.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GearStatsTest {

    private val tuning = GearTuning.DEFAULT

    private fun def(slot: GearSlot, tier: Int, vararg weights: Pair<Stat, Double>) =
        GearDef("test", slot, tier, "IRON_SWORD", null, null, null, weights.toMap(), null)

    private fun data(rarity: Rarity = Rarity.COMMON, stars: Int = 0, reforge: String? = null) =
        GearData("test", rarity, stars, emptyMap(), reforge, 0)

    /**
     * How many of its points a piece spent, worked back from its stats — the
     * same for every piece of a slot and tier, however it is shaped.
     */
    private fun spent(def: GearDef): Double {
        val stats = GearStats.of(def, data(), tuning, null)
        val roll = GearStats.averageRoll(tuning) / 100.0
        return Stat.entries.sumOf { stat ->
            val curve = tuning.curve(stat)
            if (curve.per100 == 0.0) 0.0 else stats[stat] / roll / curve.per100 * 100.0 / curve.growth.pow(def.tier - 1)
        }
    }

    @Test
    fun `every piece of a slot and tier spends the same points, however it is shaped`() {
        for (tier in 1..10) {
            val shapes = listOf(
                def(GearSlot.WEAPON, tier, Stat.DAMAGE to 0.6, Stat.STRENGTH to 0.4),
                def(GearSlot.WEAPON, tier, Stat.DAMAGE to 0.5, Stat.CRIT_CHANCE to 0.25, Stat.CRIT_DAMAGE to 0.25),
                def(GearSlot.WEAPON, tier, Stat.MAGIC_FIND to 1.0),
            )
            shapes.forEach { assertEquals(tuning.points(GearSlot.WEAPON), spent(it), 1e-6) }
        }
    }

    @Test
    fun `rarity and stars scale a piece as tuned`() {
        val sword = def(GearSlot.WEAPON, 3, Stat.DAMAGE to 1.0)
        val common = GearStats.of(sword, data(), tuning, null)[Stat.DAMAGE]
        val legendary = GearStats.of(sword, data(Rarity.LEGENDARY), tuning, null)[Stat.DAMAGE]
        val starred = GearStats.of(sword, data(stars = 3), tuning, null)[Stat.DAMAGE]
        val overStarred = GearStats.of(sword, data(stars = 99), tuning, null)[Stat.DAMAGE]

        assertEquals(tuning.rarityMultiplier(Rarity.LEGENDARY), legendary / common, 1e-9)
        assertEquals(1.0 + 3 * tuning.starBonus / 100.0, starred / common, 1e-9)
        assertEquals(tuning.starMultiplier(tuning.maxStars), overStarred / common, 1e-9)
    }

    @Test
    fun `each stat is scaled by its own roll`() {
        val sword = def(GearSlot.WEAPON, 1, Stat.DAMAGE to 0.5, Stat.STRENGTH to 0.5)
        val rolled = GearData("test", Rarity.COMMON, 0, mapOf(Stat.DAMAGE to 100, Stat.STRENGTH to 90), null, 0)
        val stats = GearStats.of(sword, rolled, tuning, null)
        val average = GearStats.of(sword, data(), tuning, null)
        val averageRoll = GearStats.averageRoll(tuning).toDouble()

        assertEquals(100.0 / averageRoll, stats[Stat.DAMAGE] / average[Stat.DAMAGE], 1e-9)
        assertEquals(90.0 / averageRoll, stats[Stat.STRENGTH] / average[Stat.STRENGTH], 1e-9)
    }

    @Test
    fun `a reforge adds its share, and only on a slot it fits`() {
        val sharp = ReforgeDef("sharp", setOf(GearSlot.WEAPON), mapOf(Stat.CRIT_DAMAGE to 1.0))
        val sword = def(GearSlot.WEAPON, 2, Stat.DAMAGE to 1.0)
        val helmet = def(GearSlot.HELMET, 2, Stat.HEALTH to 1.0)

        val reforged = GearStats.of(sword, data(reforge = "sharp"), tuning, sharp)[Stat.CRIT_DAMAGE]
        val expected =
            tuning.curve(Stat.CRIT_DAMAGE).value(tuning.points(GearSlot.WEAPON) * tuning.reforgeShare / 100.0, 2)
        assertEquals(expected, reforged, 1e-9)
        assertEquals(0.0, GearStats.of(helmet, data(reforge = "sharp"), tuning, sharp)[Stat.CRIT_DAMAGE])
    }

    /** The tier is the progression and rarity the chase: the rarest piece of a tier is weaker than a rare one of the next. */
    @Test
    fun `rarity never beats a tier on the stats that grow`() {
        listOf(Stat.DAMAGE, Stat.STRENGTH, Stat.HEALTH, Stat.DEFENSE).forEach { stat ->
            for (tier in 1..9) {
                val piece = { t: Int -> def(GearSlot.WEAPON, t, stat to 1.0) }
                val mythic = GearStats.of(piece(tier), data(Rarity.MYTHIC, stars = 3), tuning, null)[stat]
                val nextRare = GearStats.of(piece(tier + 1), data(Rarity.RARE, stars = 3), tuning, null)[stat]
                assertTrue(mythic < nextRare, "a mythic tier-$tier piece's $stat beats a rare tier-${tier + 1} one")
            }
        }
    }

    @Test
    fun `rolls stay in range, one per stat`() {
        val sword = def(GearSlot.WEAPON, 1, Stat.DAMAGE to 0.5, Stat.STRENGTH to 0.5)
        val random = Random(1)
        repeat(1_000) {
            val rolls = GearStats.roll(sword, tuning, random)
            assertEquals(setOf(Stat.DAMAGE, Stat.STRENGTH), rolls.keys)
            rolls.values.forEach { assertTrue(it in tuning.rollMin..tuning.rollMax) }
        }
    }

    @Test
    fun `rarities are picked as weighted`() {
        val random = Random(2)
        val rolls = 100_000
        val picks = List(rolls) { GearStats.pick(tuning.craftOdds, random) }.groupingBy { it }.eachCount()
        val total = tuning.craftOdds.values.sum()
        tuning.craftOdds.forEach { (rarity, weight) ->
            val expected = weight / total
            val actual = (picks[rarity] ?: 0).toDouble() / rolls
            assertTrue(abs(actual - expected) <= expected * 0.1, "$rarity picked $actual of the time, not $expected")
        }
        assertEquals(null, picks[Rarity.MYTHIC], "the Forge never crafts a mythic")
    }
}

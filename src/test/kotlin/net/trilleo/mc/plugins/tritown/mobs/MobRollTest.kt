package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.content.Balance
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobRollTest {

    private val balance = Balance.DEFAULT
    private val rolls = 200_000

    @Test
    fun `nothing ranks below the elite level`() {
        val random = Random(1)
        repeat(10_000) {
            assertEquals(MobRank.NORMAL, MobRoll.rank(balance.ranks.elite.minLevel - 1, balance, random))
        }
    }

    @Test
    fun `elites appear as often as tuned, and champions not before their level`() {
        val random = Random(2)
        val ranks = List(rolls) { MobRoll.rank(balance.ranks.champion.minLevel - 1, balance, random) }

        assertEquals(0, ranks.count { it == MobRank.CHAMPION })
        assertClose(balance.ranks.elite.chance, ranks.count { it == MobRank.ELITE })
    }

    @Test
    fun `champions are rolled first, and elites from what is left`() {
        val random = Random(3)
        val ranks = List(rolls) { MobRoll.rank(balance.ranks.champion.minLevel, balance, random) }

        assertClose(balance.ranks.champion.chance, ranks.count { it == MobRank.CHAMPION })
        val eliteShare = balance.ranks.elite.chance * (1.0 - balance.ranks.champion.chance / 100.0)
        assertClose(eliteShare, ranks.count { it == MobRank.ELITE })
    }

    @Test
    fun `a rank's affixes are distinct and as many as it allows`() {
        val random = Random(4)
        listOf(MobRank.ELITE, MobRank.CHAMPION).forEach { rank ->
            val tuning = rank.tuning(balance)!!
            repeat(1_000) {
                val affixes = MobRoll.affixes(rank, balance, random)
                assertEquals(affixes.size, affixes.toSet().size, "$rank rolled a duplicate: $affixes")
                assertTrue(affixes.size in tuning.minAffixes..tuning.maxAffixes, "$rank rolled ${affixes.size}")
            }
        }
    }

    @Test
    fun `a normal mob has no affixes`() {
        assertEquals(emptyList(), MobRoll.affixes(MobRank.NORMAL, balance, Random(5)))
    }

    /** [count] out of [rolls] is [percent] percent, within a tenth of it. */
    private fun assertClose(percent: Double, count: Int) {
        val actual = count * 100.0 / rolls
        assertTrue(abs(actual - percent) <= percent * 0.1, "expected about $percent%, rolled $actual%")
    }
}

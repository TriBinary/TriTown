package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.content.LootEntry
import net.trilleo.mc.plugins.tritown.content.LootTable
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LootRollerTest {

    private val table = LootTable(
        families = mapOf("ZOMBIE" to LootTable.Family("zombie", "grave-dust")),
        rules = LootTable.Rules(minLevel = 3, playerShare = 50.0, magicFindCap = 100.0),
        normal = LootTable.Drop(
            chance = 4.0,
            minAmount = 1,
            maxAmount = 1,
            minEssence = 0,
            maxEssence = 0,
            gearChance = 0.0
        ),
        elite = LootTable.Drop(
            chance = 100.0,
            minAmount = 1,
            maxAmount = 3,
            minEssence = 1,
            maxEssence = 2,
            gearChance = 0.0
        ),
        champion = LootTable.Drop(
            chance = 100.0,
            minAmount = 3,
            maxAmount = 6,
            minEssence = 3,
            maxEssence = 5,
            gearChance = 1.0
        ),
        essence = listOf(
            LootTable.EssenceGrade("dim-essence", 15),
            LootTable.EssenceGrade("glowing-essence", 30),
        ),
    )

    @Test
    fun `nothing drops below the minimum level`() {
        repeat(1_000) {
            assertEquals(emptyList(), LootRoller.roll(table, "ZOMBIE", MobRank.CHAMPION, 2, 0.0, Random(it)))
        }
    }

    @Test
    fun `a normal mob drops its material as often as tuned`() {
        assertClose(4.0, materialRate(magicFind = 0.0))
    }

    @Test
    fun `magic find multiplies the chance, up to its cap`() {
        assertClose(8.0, materialRate(magicFind = 100.0))
        assertClose(8.0, materialRate(magicFind = 500.0))
    }

    @Test
    fun `an elite always drops its material and essence in range`() {
        repeat(1_000) {
            val drops = LootRoller.roll(table, "ZOMBIE", MobRank.ELITE, 10, 0.0, Random(it))
                .associate { d -> d.item to d.amount }
            assertTrue(drops.getValue("grave-dust") in 1..3)
            assertTrue(drops.getValue("dim-essence") in 1..2)
        }
    }

    @Test
    fun `essence follows the level, and the last grade covers everything above`() {
        val at20 = LootRoller.roll(table, "ZOMBIE", MobRank.ELITE, 20, 0.0, Random(1)).map { it.item }
        val at99 = LootRoller.roll(table, "ZOMBIE", MobRank.ELITE, 99, 0.0, Random(1)).map { it.item }
        assertTrue("glowing-essence" in at20)
        assertTrue("glowing-essence" in at99)
    }

    @Test
    fun `a mob of no family drops essence only`() {
        val drops = LootRoller.roll(table, "COW", MobRank.CHAMPION, 10, 0.0, Random(3)).map { it.item }
        assertEquals(listOf("dim-essence"), drops)
        assertEquals(emptyList(), LootRoller.roll(table, "COW", MobRank.NORMAL, 10, 0.0, Random(3)))
    }

    @Test
    fun `champions drop gear as often as tuned, and magic find helps`() {
        assertClose(1.0, gearRate(magicFind = 0.0))
        assertClose(2.0, gearRate(magicFind = 100.0))
    }

    @Test
    fun `nothing below a champion drops gear here, and nothing below the minimum level`() {
        repeat(1_000) {
            assertTrue(!LootRoller.dropsGear(table, MobRank.ELITE, 30, 100.0, Random(it)))
            assertTrue(!LootRoller.dropsGear(table, MobRank.CHAMPION, 2, 100.0, Random(it)))
        }
    }

    @Test
    fun `a custom mob's own loot rolls each line on its own, in its amounts`() {
        val entries = listOf(
            LootEntry.Item("grave-dust", chance = 100.0, min = 2, max = 4),
            LootEntry.Gear("gravewalker-blade", chance = 100.0),
        )
        repeat(1_000) {
            val won = LootRoller.rollEntries(table, entries, 10, 0.0, Random(it))
            assertEquals(2, won.size)
            assertTrue(won[0].amount in 2..4)
            assertEquals(1, won[1].amount)
        }
    }

    @Test
    fun `a custom mob's own loot is as likely as tuned, and magic find helps`() {
        val entries = listOf(LootEntry.Item("bone-shard", chance = 1.5, min = 1, max = 1))
        val rate = { magicFind: Double ->
            val random = Random(11)
            val rolls = 200_000
            (1..rolls).count {
                LootRoller.rollEntries(table, entries, 10, magicFind, random).isNotEmpty()
            } * 100.0 / rolls
        }
        assertClose(1.5, rate(0.0))
        assertClose(3.0, rate(100.0))
    }

    @Test
    fun `a custom mob's own loot follows the minimum level too`() {
        val entries = listOf(LootEntry.Item("grave-dust", chance = 100.0, min = 1, max = 1))
        assertEquals(emptyList(), LootRoller.rollEntries(table, entries, 2, 0.0, Random(1)))
    }

    private fun gearRate(magicFind: Double): Double {
        val random = Random(7)
        val rolls = 200_000
        return (1..rolls).count { LootRoller.dropsGear(table, MobRank.CHAMPION, 30, magicFind, random) } * 100.0 / rolls
    }

    private fun materialRate(magicFind: Double): Double {
        val random = Random(42)
        val rolls = 100_000
        val drops =
            (1..rolls).count { LootRoller.roll(table, "ZOMBIE", MobRank.NORMAL, 10, magicFind, random).isNotEmpty() }
        return drops * 100.0 / rolls
    }

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(actual - expected) <= expected * 0.1, "expected about $expected%, rolled $actual%")
}

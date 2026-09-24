package net.trilleo.mc.plugins.tritown.mobs

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
        normal = LootTable.Drop(chance = 4.0, minAmount = 1, maxAmount = 1, minEssence = 0, maxEssence = 0),
        elite = LootTable.Drop(chance = 100.0, minAmount = 1, maxAmount = 3, minEssence = 1, maxEssence = 2),
        champion = LootTable.Drop(chance = 100.0, minAmount = 3, maxAmount = 6, minEssence = 3, maxEssence = 5),
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
            val drops = LootRoller.roll(table, "ZOMBIE", MobRank.ELITE, 10, 0.0, Random(it)).associate { d -> d.item to d.amount }
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

    private fun materialRate(magicFind: Double): Double {
        val random = Random(42)
        val rolls = 100_000
        val drops = (1..rolls).count { LootRoller.roll(table, "ZOMBIE", MobRank.NORMAL, 10, magicFind, random).isNotEmpty() }
        return drops * 100.0 / rolls
    }

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(actual - expected) <= expected * 0.1, "expected about $expected%, rolled $actual%")
}

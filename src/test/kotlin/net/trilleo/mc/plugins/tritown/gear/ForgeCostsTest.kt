package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.content.ForgeTuning
import net.trilleo.mc.plugins.tritown.content.GearDef
import net.trilleo.mc.plugins.tritown.content.GearSlot
import net.trilleo.mc.plugins.tritown.content.GearTuning
import net.trilleo.mc.plugins.tritown.content.LootTable
import net.trilleo.mc.plugins.tritown.content.Rarity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForgeCostsTest {

    private val forge = ForgeTuning.DEFAULT
    private val gear = GearTuning.DEFAULT
    private val loot = LootTable.EMPTY.copy(
        essence = listOf(
            LootTable.EssenceGrade("dim-essence", 15),
            LootTable.EssenceGrade("glowing-essence", 30),
            LootTable.EssenceGrade("radiant-essence", 45),
            LootTable.EssenceGrade("astral-essence", 60),
        ),
    )

    private fun def(tier: Int) = GearDef("test", GearSlot.WEAPON, tier, "IRON_SWORD", null, null, null, mapOf(Stat.DAMAGE to 1.0), null)

    private fun data(rarity: Rarity = Rarity.COMMON, stars: Int = 0) = GearData("test", rarity, stars, emptyMap(), null, 0)

    @Test
    fun `essence is the grade a mob of the piece's tier drops`() {
        assertEquals("dim-essence", ForgeCosts.essence(2, loot))
        assertEquals("glowing-essence", ForgeCosts.essence(3, loot))
        assertEquals("radiant-essence", ForgeCosts.essence(7, loot))
        assertEquals("astral-essence", ForgeCosts.essence(10, loot))
    }

    @Test
    fun `each star costs more than the last, and money grows with the tier`() {
        val first = ForgeCosts.upgrade(def(1), data(stars = 0), gear, forge, loot)!!
        val third = ForgeCosts.upgrade(def(1), data(stars = 2), gear, forge, loot)!!
        val firstAtFive = ForgeCosts.upgrade(def(5), data(stars = 0), gear, forge, loot)!!

        assertEquals(mapOf("dim-essence" to forge.upgradeEssence), first.items)
        assertEquals(mapOf("dim-essence" to forge.upgradeEssence * 3), third.items)
        assertEquals(forge.upgradeMoney * 3, third.money, 1e-9)
        assertEquals(forge.upgradeMoney * forge.moneyGrowth * forge.moneyGrowth * forge.moneyGrowth * forge.moneyGrowth, firstAtFive.money, 1e-6)
    }

    @Test
    fun `a piece with every star cannot be upgraded`() {
        assertNull(ForgeCosts.upgrade(def(1), data(stars = gear.maxStars), gear, forge, loot))
    }

    @Test
    fun `the forge refines no higher than its cap`() {
        assertEquals(Rarity.UNCOMMON, ForgeCosts.nextRarity(Rarity.COMMON))
        assertNull(ForgeCosts.refine(def(1), data(forge.refineCap), forge, loot))
        assertNull(ForgeCosts.refine(def(1), data(Rarity.MYTHIC), forge, loot))
        val toRare = ForgeCosts.refine(def(1), data(Rarity.UNCOMMON), forge, loot)!!
        assertEquals(mapOf("dim-essence" to forge.refineEssence * 2), toRare.items)
    }

    @Test
    fun `salvage gives back essence for rarity and stars`() {
        val returned = ForgeCosts.salvage(def(3), data(Rarity.RARE, stars = 2), forge, loot)
        assertEquals(mapOf("glowing-essence" to forge.salvageEssence * 3 + 2), returned)
    }
}

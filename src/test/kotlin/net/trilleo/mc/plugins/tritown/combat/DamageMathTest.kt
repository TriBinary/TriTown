package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.content.Balance
import kotlin.test.Test
import kotlin.test.assertEquals

class DamageMathTest {

    private val balance = Balance.DEFAULT

    @Test
    fun `defense takes a growing but never complete share`() {
        assertEquals(1.0, DamageMath.defenseMultiplier(0.0))
        assertEquals(0.5, DamageMath.defenseMultiplier(100.0))
        assertEquals(0.25, DamageMath.defenseMultiplier(300.0))
        assertEquals(1.0, DamageMath.defenseMultiplier(-50.0))
    }

    @Test
    fun `a full vanilla swing with a vanilla sword is the lens times vanilla`() {
        val ironSword = StatSheet.of(Stat.DAMAGE to 5.0 * balance.lens)
        val share = DamageMath.vanillaShare(vanillaHit = 6.0, attackDamage = 6.0, vanillaCrit = false)

        assertEquals(1.0, share)
        assertEquals(30.0, DamageMath.meleeHit(ironSword, balance, share, crit = false))
    }

    @Test
    fun `vanilla's jump crit is divided back out of the share`() {
        assertEquals(1.0, DamageMath.vanillaShare(vanillaHit = 9.0, attackDamage = 6.0, vanillaCrit = true), 1e-9)
    }

    @Test
    fun `strength and crits multiply a hit`() {
        val sheet = StatSheet.of(Stat.DAMAGE to 95.0, Stat.STRENGTH to 50.0, Stat.CRIT_DAMAGE to 100.0)

        assertEquals(150.0, DamageMath.meleeHit(sheet, balance, 1.0, crit = false), 1e-9)
        assertEquals(300.0, DamageMath.meleeHit(sheet, balance, 1.0, crit = true), 1e-9)
    }

    @Test
    fun `weakness can take a hit to nothing but not below`() {
        val sheet = StatSheet.of(Stat.STRENGTH to -150.0)
        assertEquals(0.0, DamageMath.strengthMultiplier(sheet))
    }

    @Test
    fun `crit chance adds the jump attack and stays a probability`() {
        val sheet = StatSheet.of(Stat.CRIT_CHANCE to 90.0)

        assertEquals(0.9, DamageMath.critChance(sheet, jumpAttack = false, balance), 1e-9)
        assertEquals(1.0, DamageMath.critChance(sheet, jumpAttack = true, balance))
    }

    @Test
    fun `a level-1 mob is the lens times vanilla, and grows from there`() {
        assertEquals(15.0, DamageMath.mobHit(3.0, balance, level = 1))
        assertEquals(100.0, DamageMath.mobMaxHealth(20.0, balance, level = 1))
        assertEquals(15.0 * 1.1, DamageMath.mobHit(3.0, balance, level = 2), 1e-9)
        assertEquals(100.0 * 1.13 * 1.13, DamageMath.mobMaxHealth(20.0, balance, level = 3), 1e-9)
    }

    @Test
    fun `the environment only ever hurts a mob as it would at level 1`() {
        assertEquals(20.0, DamageMath.environmentHit(4.0, balance))
    }

    @Test
    fun `health boost adds to the pool in proportion`() {
        assertEquals(100.0, DamageMath.playerMaxHealth(100.0, vanillaMax = 20.0))
        assertEquals(120.0, DamageMath.playerMaxHealth(100.0, vanillaMax = 24.0))
    }

    @Test
    fun `converting to vanilla and back is lossless`() {
        val vanilla = DamageMath.toVanilla(rpg = 2_500.0, rpgMax = 50_000.0, vanillaMax = 20.0)

        assertEquals(1.0, vanilla, 1e-9)
        assertEquals(2_500.0, DamageMath.toRpg(vanilla, rpgMax = 50_000.0, vanillaMax = 20.0), 1e-9)
    }

    @Test
    fun `vanilla armor becomes defense by its points`() {
        assertEquals(120.0, DamageMath.armorDefense(armor = 15.0, toughness = 0.0, balance))
        assertEquals(320.0, DamageMath.armorDefense(armor = 20.0, toughness = 8.0, balance))
    }
}

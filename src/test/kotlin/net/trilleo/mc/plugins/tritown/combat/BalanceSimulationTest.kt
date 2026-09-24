package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.combat.BalanceSimulator.Foe
import net.trilleo.mc.plugins.tritown.combat.BalanceSimulator.Kit
import net.trilleo.mc.plugins.tritown.content.Balance
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the bundled balance to the targets the combat layer is designed
 * around. A change to `balance.yml` or to [DamageMath] that breaks one of these
 * changes how the game feels, and has to be a decision rather than an
 * accident.
 */
class BalanceSimulationTest {

    private val balance = Balance.DEFAULT
    private val kits = mapOf("iron" to Kit.IRON, "diamond" to Kit.DIAMOND, "netherite" to Kit.NETHERITE)

    /** The anchor: at level 1 with vanilla gear, a fight is the vanilla fight. */
    @Test
    fun `a level-1 zombie dies in as many swings as in vanilla`() {
        kits.forEach { (name, kit) ->
            val vanilla = ceil(Foe.ZOMBIE.health / (1.0 + kit.attackDamage)).toInt()
            assertEquals(vanilla, BalanceSimulator.row(balance, 1, kit).hitsToKill, "$name sword")
        }
    }

    @Test
    fun `a level-1 zombie's hit takes what it does in vanilla, give or take 15 percent`() {
        kits.forEach { (name, kit) ->
            val vanilla = vanillaShareTaken(Foe.ZOMBIE.hit, kit)
            val ours = BalanceSimulator.row(balance, 1, kit).shareTaken
            assertTrue(abs(ours - vanilla) / vanilla <= 0.15, "$name armor: $ours of health against vanilla's $vanilla")
        }
    }

    @Test
    fun `the default crit chance adds at most 15 percent to an average swing`() {
        val sheet = StatSheet.of(
            Stat.CRIT_CHANCE to balance.player.critChance,
            Stat.CRIT_DAMAGE to balance.player.critDamage,
        )
        val average = 1.0 + DamageMath.critChance(sheet, jumpAttack = false, balance) * sheet[Stat.CRIT_DAMAGE] / 100.0
        assertTrue(average <= 1.15, "an average swing is $average of a plain one")
    }

    @Test
    fun `every level up is harder than the last with the same gear`() {
        val rows = (1..60).map { BalanceSimulator.row(balance, it, Kit.DIAMOND) }
        rows.zipWithNext().forEach { (lower, higher) ->
            assertTrue(higher.hitsToKill >= lower.hitsToKill, "level ${higher.level} dies faster than ${lower.level}")
            assertTrue(higher.shareTaken > lower.shareTaken, "level ${higher.level} hits softer than ${lower.level}")
        }
    }

    /**
     * Vanilla gear is where the world starts, not where it ends: by the
     * Overworld's cap it should be hopeless, which is what makes the gear
     * above it worth having.
     */
    @Test
    fun `vanilla diamond is outclassed by the Overworld's cap`() {
        val row = BalanceSimulator.row(balance, 30, Kit.DIAMOND)
        assertTrue(row.hitsToKill >= 50, "a level-30 zombie dies in ${row.hitsToKill} diamond swings")
        assertTrue(row.shareTaken >= 0.5, "a level-30 zombie takes only ${row.shareTaken} a hit through diamond")
    }

    /** Vanilla's own armor formula, for a hit of [damage] on a player wearing [kit]. */
    private fun vanillaShareTaken(damage: Double, kit: Kit): Double {
        val effective = maxOf(kit.armor / 5.0, kit.armor - 4.0 * damage / (kit.toughness + 8.0)).coerceAtMost(20.0)
        return damage * (1.0 - effective / 25.0) / DamageMath.VANILLA_PLAYER_HEALTH
    }
}

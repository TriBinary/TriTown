package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.content.Balance
import kotlin.math.ceil

/**
 * What a fight at a given level looks like, worked out with [DamageMath]
 * alone.
 *
 * `/tritown mob balance` prints it, so an owner can see what an edit to
 * `balance.yml` does before anyone fights, and `BalanceSimulationTest` holds
 * the bundled tuning to the design's targets with the same numbers.
 */
object BalanceSimulator {

    /** A full set of vanilla gear, by what its items add: a sword's attack damage, the set's armor and toughness. */
    data class Kit(val attackDamage: Double, val armor: Double, val toughness: Double) {

        companion object {
            val IRON = Kit(attackDamage = 5.0, armor = 15.0, toughness = 0.0)
            val DIAMOND = Kit(attackDamage = 6.0, armor = 20.0, toughness = 8.0)
            val NETHERITE = Kit(attackDamage = 7.0, armor = 20.0, toughness = 12.0)
        }
    }

    /** A vanilla mob, by its health and the hit it lands on normal difficulty. */
    data class Foe(val health: Double, val hit: Double) {

        companion object {
            val ZOMBIE = Foe(health = 20.0, hit = 3.0)
        }
    }

    /**
     * One level of the table.
     *
     * @param hitsToKill uncritical swings the kit's sword needs to kill the foe
     * @param shareTaken the part of a player's base health one of the foe's hits takes through the kit's armor
     */
    data class Row(val level: Int, val foeHealth: Double, val foeHit: Double, val hitsToKill: Int, val shareTaken: Double)

    fun row(balance: Balance, level: Int, kit: Kit, foe: Foe = Foe.ZOMBIE): Row {
        val sheet = StatSheet.of(
            Stat.HEALTH to balance.player.health,
            Stat.DAMAGE to kit.attackDamage * balance.lens,
            Stat.DEFENSE to kit.armor * balance.vanilla.armorPoint + kit.toughness * balance.vanilla.toughnessPoint,
        )
        val foeHealth = DamageMath.mobMaxHealth(foe.health, balance, level)
        val foeHit = DamageMath.mobHit(foe.hit, balance, level)
        val swing = DamageMath.meleeHit(sheet, balance, vanillaShare = 1.0, crit = false)
        val taken = DamageMath.afterDefense(foeHit, sheet[Stat.DEFENSE])

        return Row(
            level = level,
            foeHealth = foeHealth,
            foeHit = foeHit,
            hitsToKill = ceil(foeHealth / swing - EPSILON).toInt(),
            shareTaken = taken / sheet[Stat.HEALTH],
        )
    }

    /** Guards `ceil` against a hit that divides a pool exactly landing a hair over. */
    private const val EPSILON = 1e-9
}

package net.trilleo.mc.plugins.tritown.content

import kotlin.math.pow

/**
 * The numbers the combat layer is tuned with, read from `content/balance.yml`.
 *
 * Everything that decides how hard a hit lands or how much a mob can take is
 * here rather than in Kotlin, so an owner can retune the curve without a
 * rebuild. `BalanceSimulationTest` holds the bundled copy to the targets the
 * design rests on.
 *
 * @param lens RPG health per vanilla health point at level 1. Level-1 play with
 *   vanilla gear stays vanilla whatever it is, because every hit and every pool
 *   is multiplied by it alike.
 * @param jumpCritChance crit chance, in percent, that a vanilla jump attack adds
 *   in place of vanilla's flat bonus
 */
data class Balance(
    val lens: Double,
    val player: PlayerBase,
    val jumpCritChance: Double,
    val effects: EffectBonuses,
    val mobs: MobCurves,
    val vanilla: VanillaMapping,
) {

    /** What every player has before any gear. Crit values are percentages. */
    data class PlayerBase(val health: Double, val critChance: Double, val critDamage: Double)

    /** Strength a level of the Strength or Weakness effect is worth. */
    data class EffectBonuses(val strengthPerLevel: Double, val weaknessPerLevel: Double)

    /** How a mob's health and hits grow with its level; level 1 is always ×1. */
    data class MobCurves(val healthGrowth: Double, val damageGrowth: Double) {

        fun health(level: Int): Double = healthGrowth.pow(level.coerceAtLeast(1) - 1)

        fun damage(level: Int): Double = damageGrowth.pow(level.coerceAtLeast(1) - 1)
    }

    /** Defense a point of vanilla armor or toughness on a worn piece is worth. */
    data class VanillaMapping(val armorPoint: Double, val toughnessPoint: Double)

    companion object {

        /** The bundled tuning, used for anything `balance.yml` leaves out. */
        val DEFAULT = Balance(
            lens = 5.0,
            player = PlayerBase(health = 100.0, critChance = 20.0, critDamage = 50.0),
            jumpCritChance = 25.0,
            effects = EffectBonuses(strengthPerLevel = 40.0, weaknessPerLevel = 40.0),
            mobs = MobCurves(healthGrowth = 1.13, damageGrowth = 1.10),
            vanilla = VanillaMapping(armorPoint = 8.0, toughnessPoint = 20.0),
        )
    }
}

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
    val ranks: Ranks,
    val affixes: AffixTuning,
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

    data class Ranks(val elite: Rank, val champion: Rank)

    /**
     * A rank a wild mob can spawn with.
     *
     * @param chance percent of the wild spawns at [minLevel] or above that take this rank
     * @param health what the rank multiplies the mob's pool by
     * @param damage what the rank multiplies the mob's hits by
     */
    data class Rank(
        val chance: Double,
        val minLevel: Int,
        val health: Double,
        val damage: Double,
        val minAffixes: Int,
        val maxAffixes: Int,
    )

    /**
     * What each affix does. Percentages are percentages, durations seconds
     * unless they say ticks.
     */
    data class AffixTuning(
        val armoredDefense: Double,
        val frenziedSpeed: Double,
        val vampiricHeal: Double,
        val enragedBelow: Double,
        val enragedDamage: Double,
        val moltenSeconds: Int,
        val frostboundSeconds: Int,
        val venomousSeconds: Int,
        val volatilePower: Double,
        val volatileDelayTicks: Int,
        val summonerBelow: Double,
        val summonerMinions: Int,
        val blinkingRange: Double,
        val blinkingCooldownTicks: Int,
    )

    companion object {

        /** The bundled tuning, used for anything `balance.yml` leaves out. */
        val DEFAULT = Balance(
            lens = 5.0,
            player = PlayerBase(health = 100.0, critChance = 20.0, critDamage = 50.0),
            jumpCritChance = 25.0,
            effects = EffectBonuses(strengthPerLevel = 40.0, weaknessPerLevel = 40.0),
            mobs = MobCurves(healthGrowth = 1.13, damageGrowth = 1.10),
            vanilla = VanillaMapping(armorPoint = 8.0, toughnessPoint = 20.0),
            ranks = Ranks(
                elite = Rank(chance = 3.0, minLevel = 5, health = 4.0, damage = 1.5, minAffixes = 1, maxAffixes = 2),
                champion = Rank(chance = 0.4, minLevel = 15, health = 12.0, damage = 2.0, minAffixes = 2, maxAffixes = 3),
            ),
            affixes = AffixTuning(
                armoredDefense = 150.0,
                frenziedSpeed = 30.0,
                vampiricHeal = 25.0,
                enragedBelow = 30.0,
                enragedDamage = 50.0,
                moltenSeconds = 2,
                frostboundSeconds = 3,
                venomousSeconds = 3,
                volatilePower = 2.5,
                volatileDelayTicks = 30,
                summonerBelow = 50.0,
                summonerMinions = 2,
                blinkingRange = 6.0,
                blinkingCooldownTicks = 100,
            ),
        )
    }
}

package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.mobs.Ability
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
    val abilities: AbilityTuning,
    val gear: GearTuning,
    val forge: ForgeTuning,
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

    /**
     * What vanilla gear is worth: Defense for a point of armor or toughness on a
     * worn piece, and Magic Find for a level of Looting on the held weapon.
     *
     * @param bowAttack the attack damage a vanilla bow or crossbow counts as for
     *   what it shoots, as though it were a sword
     * @param arrowReference the vanilla damage of a fully drawn arrow, which a
     *   shot is measured against
     */
    data class VanillaMapping(
        val armorPoint: Double,
        val toughnessPoint: Double,
        val lootingMagicFind: Double,
        val bowAttack: Double,
        val arrowReference: Double,
    )

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

    /**
     * What custom mobs' abilities are worth.
     *
     * @param globalCooldownTicks the least time between any two abilities of one mob
     */
    data class AbilityTuning(val globalCooldownTicks: Int, val stats: Map<Ability, AbilityStats>) {

        operator fun get(ability: Ability): AbilityStats = stats[ability] ?: AbilityStats.NONE
    }

    /**
     * One ability's numbers. Each ability reads only those it needs; the rest
     * stay 0. Damage is in the units of a level-1 mob's vanilla hit, so the
     * mob's level, rank and kind grow it like any other hit of its own.
     *
     * @param cooldownTicks the least time between two of it
     * @param windupTicks how long its warning lasts before it lands
     * @param range how far off its target may be
     * @param radius how far from where it lands it reaches
     * @param seconds how long what it leaves behind lasts
     * @param count how many of it: arrows, fireballs, marked spots, minions
     * @param power what else it needs: a push, a heal, Defense
     */
    data class AbilityStats(
        val cooldownTicks: Int,
        val windupTicks: Int,
        val damage: Double,
        val range: Double,
        val radius: Double,
        val seconds: Double,
        val count: Int,
        val power: Double,
    ) {
        companion object {
            val NONE = AbilityStats(1, 0, 0.0, 0.0, 0.0, 0.0, 0, 0.0)
        }
    }

    companion object {

        /** The bundled tuning, used for anything `balance.yml` leaves out. */
        val DEFAULT = Balance(
            lens = 5.0,
            player = PlayerBase(health = 100.0, critChance = 20.0, critDamage = 50.0),
            jumpCritChance = 25.0,
            effects = EffectBonuses(strengthPerLevel = 40.0, weaknessPerLevel = 40.0),
            mobs = MobCurves(healthGrowth = 1.13, damageGrowth = 1.10),
            vanilla = VanillaMapping(
                armorPoint = 8.0,
                toughnessPoint = 20.0,
                lootingMagicFind = 10.0,
                bowAttack = 5.0,
                arrowReference = 6.0,
            ),
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
            abilities = AbilityTuning(
                globalCooldownTicks = 40,
                stats = mapOf(
                    Ability.LEAP to ability(cooldown = 160, windup = 8, range = 12.0, radius = 2.5, damage = 4.0),
                    Ability.SLAM to ability(cooldown = 200, windup = 20, radius = 5.0, damage = 6.0, power = 0.8),
                    Ability.CHARGE to ability(cooldown = 200, windup = 15, range = 16.0, damage = 6.0, power = 1.6),
                    Ability.VOLLEY to ability(cooldown = 140, windup = 10, range = 20.0, damage = 2.5, count = 5),
                    Ability.FIREBALL to ability(cooldown = 120, windup = 10, range = 24.0, count = 3),
                    Ability.METEOR to ability(cooldown = 240, windup = 30, range = 20.0, radius = 2.5, damage = 6.0, count = 3),
                    Ability.STORM to ability(cooldown = 240, windup = 30, range = 24.0, radius = 2.0, damage = 6.0, count = 3),
                    Ability.ENSNARE to ability(cooldown = 200, windup = 10, range = 14.0, seconds = 2.0),
                    Ability.HOOK to ability(cooldown = 200, windup = 10, range = 16.0, damage = 2.0, power = 1.4),
                    Ability.SUMMON to ability(cooldown = 400, windup = 20, count = 2),
                    Ability.BULWARK to ability(cooldown = 400, seconds = 6.0, power = 200.0),
                    Ability.FROST_NOVA to ability(cooldown = 240, windup = 20, radius = 5.0, damage = 5.0, seconds = 3.0),
                    Ability.MIASMA to ability(cooldown = 260, windup = 10, range = 16.0, radius = 3.0, damage = 1.5, seconds = 6.0),
                    Ability.DRAIN to ability(cooldown = 240, range = 10.0, damage = 1.5, seconds = 3.0, power = 50.0),
                ),
            ),
            gear = GearTuning.DEFAULT,
            forge = ForgeTuning.DEFAULT,
        )

        private fun ability(
            cooldown: Int,
            windup: Int = 0,
            damage: Double = 0.0,
            range: Double = 0.0,
            radius: Double = 0.0,
            seconds: Double = 0.0,
            count: Int = 0,
            power: Double = 0.0,
        ) = AbilityStats(cooldown, windup, damage, range, radius, seconds, count, power)
    }
}

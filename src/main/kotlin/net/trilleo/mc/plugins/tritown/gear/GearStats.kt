package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.combat.StatSheet
import net.trilleo.mc.plugins.tritown.content.GearDef
import net.trilleo.mc.plugins.tritown.content.GearTuning
import net.trilleo.mc.plugins.tritown.content.Rarity
import net.trilleo.mc.plugins.tritown.content.ReforgeDef
import kotlin.random.Random

/**
 * What a piece of gear is worth, and the only place that is worked out.
 *
 * A piece's slot and tier give it points (`balance.yml`), its definition's
 * weights share them between stats, and each stat's curve turns its share into
 * a number for the tier. Rarity, stars and the quality each stat rolled then
 * scale that; a reforge adds a small share of its own on top. Plain Kotlin, so
 * `GearStatsTest` and `BalanceSimulationTest` run it as the server does.
 */
object GearStats {

    /** What [data], a piece of [def], gives whoever wears or holds it. */
    fun of(def: GearDef, data: GearData, tuning: GearTuning, reforge: ReforgeDef?): StatSheet =
        base(def, data, tuning) + reforged(def, data.rarity, tuning, reforge)

    /** The piece's own stats: its weights, at its rarity, stars and rolls. */
    private fun base(def: GearDef, data: GearData, tuning: GearTuning): StatSheet {
        val points = tuning.points(def.slot)
        val scale = tuning.rarityMultiplier(data.rarity) * tuning.starMultiplier(data.stars)
        return StatSheet.of(*def.weights.map { (stat, weight) ->
            val roll = data.rolls[stat] ?: averageRoll(tuning)
            stat to tuning.curve(stat).value(points * weight, def.tier) * scale * roll / 100.0
        }.toTypedArray())
    }

    /** What [reforge] adds to a piece of [def] at [rarity]: a share of its points, spent by the reforge's weights. */
    private fun reforged(def: GearDef, rarity: Rarity, tuning: GearTuning, reforge: ReforgeDef?): StatSheet {
        if (reforge == null || def.slot !in reforge.slots) return StatSheet.EMPTY
        val points = tuning.points(def.slot) * tuning.reforgeShare / 100.0
        val scale = tuning.rarityMultiplier(rarity)
        return StatSheet.of(*reforge.weights.map { (stat, weight) ->
            stat to tuning.curve(stat).value(points * weight, def.tier) * scale
        }.toTypedArray())
    }

    /** A fresh roll for every stat [def] has. */
    fun roll(def: GearDef, tuning: GearTuning, random: Random): Map<Stat, Int> =
        def.weights.keys.associateWith { random.nextInt(tuning.rollMin, tuning.rollMax + 1) }

    /** A rarity drawn from [odds], weighted; the lowest listed if the odds are all zero. */
    fun pick(odds: Map<Rarity, Double>, random: Random): Rarity {
        val total = odds.values.sum()
        if (total <= 0.0) return odds.keys.minOrNull() ?: Rarity.COMMON
        var roll = random.nextDouble() * total
        for ((rarity, weight) in odds.entries.sortedBy { it.key.ordinal }) {
            roll -= weight
            if (roll < 0.0) return rarity
        }
        return odds.keys.max()
    }

    fun averageRoll(tuning: GearTuning): Int = (tuning.rollMin + tuning.rollMax) / 2
}

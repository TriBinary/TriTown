package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.content.*

/**
 * What each thing the Forge does costs, worked out from `balance.yml` and the
 * essence grades in `mobs.yml`. Plain Kotlin, so `ForgeCostsTest` checks it.
 *
 * Essence is the grade a mob of the piece's tier drops — a mob of level
 * `6 × tier` — so upgrading gear means fighting at its tier.
 */
object ForgeCosts {

    /** Content items by id, and money. */
    data class Cost(val items: Map<String, Int>, val money: Double)

    /** The levels one tier of gear is made for. */
    const val LEVELS_PER_TIER = 6

    /** The essence a piece of [tier] is upgraded with, or `null` when `mobs.yml` has none. */
    fun essence(tier: Int, loot: LootTable): String? = loot.essenceFor(tier * LEVELS_PER_TIER)

    /** What the next star costs, or `null` when [data] already has every star. */
    fun upgrade(def: GearDef, data: GearData, gear: GearTuning, forge: ForgeTuning, loot: LootTable): Cost? {
        if (data.stars >= gear.maxStars) return null
        val step = data.stars + 1
        return cost(def, loot, forge.upgradeEssence * step, forge.money(forge.upgradeMoney * step, def.tier))
    }

    /** What the next step of rarity costs, or `null` when [data] is already as rare as the Forge refines. */
    fun refine(def: GearDef, data: GearData, forge: ForgeTuning, loot: LootTable): Cost? {
        if (data.rarity >= forge.refineCap) return null
        val step = data.rarity.ordinal + 1
        return cost(def, loot, forge.refineEssence * step, forge.money(forge.refineMoney * step, def.tier))
    }

    fun reforge(def: GearDef, forge: ForgeTuning, loot: LootTable): Cost =
        cost(def, loot, forge.reforgeEssence, forge.money(forge.reforgeMoney, def.tier))

    /** What salvaging [data] gives back: essence for each step of rarity, and one more for each star. */
    fun salvage(def: GearDef, data: GearData, forge: ForgeTuning, loot: LootTable): Map<String, Int> {
        val essence = essence(def.tier, loot) ?: return emptyMap()
        val amount = forge.salvageEssence * (data.rarity.ordinal + 1) + data.stars
        return if (amount > 0) mapOf(essence to amount) else emptyMap()
    }

    /** The rarity after [rarity] that the Forge refines to next. */
    fun nextRarity(rarity: Rarity): Rarity = Rarity.entries[(rarity.ordinal + 1).coerceAtMost(Rarity.entries.size - 1)]

    private fun cost(def: GearDef, loot: LootTable, essence: Int, money: Double): Cost {
        val grade = essence(def.tier, loot)
        val items = if (grade != null && essence > 0) mapOf(grade to essence) else emptyMap()
        return Cost(items, money)
    }
}

package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.combat.Stat

/**
 * A piece of gear defined in `gear.yml`.
 *
 * It never lists its stats: it lists [weights], and its tier's budget in
 * `balance.yml` turns them into numbers. Its name and flavour line are
 * `gear.item.<id>.name` and `gear.item.<id>.lore` in the language files.
 *
 * @param base the vanilla item it is made of, by its Minecraft name. Between players the piece is exactly that item
 * @param model a vanilla item's id whose look it takes instead of its base's, if any
 * @param weights how its points are shared between stats, summing to 1
 */
data class GearDef(
    val id: String,
    val slot: GearSlot,
    val tier: Int,
    val base: String,
    val model: String?,
    val trim: Trim?,
    val dye: Int?,
    val weights: Map<Stat, Double>,
    val recipe: Recipe?,
) {

    /** An armor trim, by the ids of its pattern and material, such as `coast` and `emerald`. */
    data class Trim(val pattern: String, val material: String)

    /** What the Forge takes to craft the piece: content items by id, and money. */
    data class Recipe(val items: Map<String, Int>, val money: Double)

    val nameKey: String
        get() = "$KEY_PREFIX$id.name"

    val loreKey: String
        get() = "$KEY_PREFIX$id.lore"

    companion object {
        /** Where gear's names live in the language files, which the language tests know is built at runtime. */
        const val KEY_PREFIX = "gear.item."
    }
}

/**
 * A reforge: a small, named extra a piece can be given at the Forge, worth
 * `reforge-share` of its points and spent by its own [weights]. Its name is
 * `gear.reforge.<id>` and goes before the piece's.
 */
data class ReforgeDef(val id: String, val slots: Set<GearSlot>, val weights: Map<Stat, Double>) {

    val key: String
        get() = "$KEY_PREFIX$id"

    companion object {
        const val KEY_PREFIX = "gear.reforge."
    }
}

/** Everything `gear.yml` defines. */
data class GearCatalog(val gear: Map<String, GearDef>, val reforges: Map<String, ReforgeDef>) {

    fun reforgesFor(slot: GearSlot): List<ReforgeDef> = reforges.values.filter { slot in it.slots }

    /**
     * A stamp of everything a piece's tooltip is drawn from, so a piece drawn
     * before an edit can tell it is stale. Built from text, which is the same
     * from one start to the next, where an enum's hash is not.
     */
    fun revision(id: String, tuning: GearTuning): Int =
        listOf(gear[id], reforges, tuning).joinToString().hashCode()

    companion object {
        val EMPTY = GearCatalog(emptyMap(), emptyMap())
    }
}

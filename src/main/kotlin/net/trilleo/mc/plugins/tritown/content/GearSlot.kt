package net.trilleo.mc.plugins.tritown.content

/** Where a piece of gear goes, which decides how many points it has to spend on stats. */
enum class GearSlot {
    /** Held in the main hand and swung, or thrown if it is a trident. */
    WEAPON,

    /** A bow or crossbow in the main hand. Its Damage only counts for what it shoots. */
    BOW,

    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    val isArmor: Boolean
        get() = this != WEAPON && this != BOW

    /** The translation key of the slot's label on a tooltip, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            WEAPON -> "gear.slot.weapon"
            BOW -> "gear.slot.bow"
            HELMET -> "gear.slot.helmet"
            CHESTPLATE -> "gear.slot.chestplate"
            LEGGINGS -> "gear.slot.leggings"
            BOOTS -> "gear.slot.boots"
        }

    companion object {
        fun of(name: String): GearSlot? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

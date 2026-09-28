package net.trilleo.mc.plugins.tritown.content

import net.kyori.adventure.text.format.NamedTextColor

/**
 * How rare a content item or a piece of gear is.
 *
 * An item's name takes its rarity's colour, so the name keys in the language
 * files carry no colour of their own and every item of a rarity reads alike.
 */
enum class Rarity(val color: NamedTextColor) {
    COMMON(NamedTextColor.WHITE),
    UNCOMMON(NamedTextColor.GREEN),
    RARE(NamedTextColor.BLUE),
    EPIC(NamedTextColor.DARK_PURPLE),
    LEGENDARY(NamedTextColor.GOLD),
    MYTHIC(NamedTextColor.LIGHT_PURPLE);

    /** The translation key of the rarity's label, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            COMMON -> "rarity.common"
            UNCOMMON -> "rarity.uncommon"
            RARE -> "rarity.rare"
            EPIC -> "rarity.epic"
            LEGENDARY -> "rarity.legendary"
            MYTHIC -> "rarity.mythic"
        }

    companion object {
        fun of(name: String): Rarity? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

package net.trilleo.mc.plugins.tritown.enums

/**
 * What a player's standing in Towny has to be before a shop or one of its
 * entries is open to them.
 *
 * Checked against [com.palmergames.bukkit.towny.TownyAPI] at the moment of the
 * click, never cached — Towny stays the source of truth.
 */
enum class TownyRequirement {

    /** No requirement at all. */
    NONE,

    /** The player must belong to a town. */
    HAS_TOWN,

    /** The player must not belong to a town, for a newcomers' shop. */
    NO_TOWN,

    /** The player's town must belong to a nation. */
    HAS_NATION,

    /** The player must be the mayor of their town. */
    IS_MAYOR,

    /** The player must be the king of their nation. */
    IS_KING,
}

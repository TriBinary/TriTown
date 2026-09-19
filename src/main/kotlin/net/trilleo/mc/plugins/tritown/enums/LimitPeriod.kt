package net.trilleo.mc.plugins.tritown.enums

/**
 * How often a per-player purchase limit starts over.
 *
 * Windows are counted from the epoch rather than from the first purchase, so
 * every player's limit rolls over at the same moment.
 */
enum class LimitPeriod {

    /** The limit is a lifetime total and never resets. */
    NONE,

    /** The limit resets every 24 hours. */
    DAILY,

    /** The limit resets every 7 days. */
    WEEKLY,
}

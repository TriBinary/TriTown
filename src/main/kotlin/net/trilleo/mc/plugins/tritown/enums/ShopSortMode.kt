package net.trilleo.mc.plugins.tritown.enums

/**
 * The orders the shop editor can arrange a shop's entries into.
 *
 * A sort is an alternative to arranging entries by hand, not a property of the
 * shop: it is applied once, on request, and the result is an ordinary order
 * that can be rearranged afterwards.
 */
enum class ShopSortMode {

    /** By item name, A to Z. */
    NAME,

    /** By item name, Z to A. */
    NAME_REVERSED,

    /** By what the entry is sold for, cheapest first. */
    PRICE,

    /** By what the entry is sold for, dearest first. */
    PRICE_REVERSED,
}

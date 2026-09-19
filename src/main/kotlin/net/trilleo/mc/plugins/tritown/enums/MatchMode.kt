package net.trilleo.mc.plugins.tritown.enums

/**
 * How closely a player's stack has to resemble a shop entry before the shop
 * will buy it back, or accept it as part of a price.
 */
enum class MatchMode {

    /**
     * Every property must match: name, lore, enchantments, custom model data
     * and any custom data a plugin attached. This is the default, so a shop
     * that sells a custom item does not buy back a plain one of the same
     * material.
     */
    EXACT,

    /** Only the material must match, so any plain stack of it is accepted. */
    MATERIAL,
}

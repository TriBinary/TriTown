package net.trilleo.mc.plugins.tritown.combat

/**
 * A number that shapes how a player fights mobs.
 *
 * None of them apply between players: a hit from one player to another is left
 * to vanilla, so what a player has here only ever changes how they fare
 * against mobs.
 *
 * @property percent whether the value is read as a percentage
 */
enum class Stat(val percent: Boolean) {

    /** RPG health, drawn over the player's vanilla hearts. */
    HEALTH(false),

    /** Takes a share off every mob's hit: `100 / (100 + defense)` of it gets through. */
    DEFENSE(false),

    /** What the held weapon adds to a hit, on top of the lens's worth for the bare hand. */
    DAMAGE(false),

    /** Adds its value in percent to every hit on a mob. */
    STRENGTH(false),

    CRIT_CHANCE(true),

    CRIT_DAMAGE(true);

    /** The translation key naming the stat, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            HEALTH -> "combat.stat.health"
            DEFENSE -> "combat.stat.defense"
            DAMAGE -> "combat.stat.damage"
            STRENGTH -> "combat.stat.strength"
            CRIT_CHANCE -> "combat.stat.crit-chance"
            CRIT_DAMAGE -> "combat.stat.crit-damage"
        }
}

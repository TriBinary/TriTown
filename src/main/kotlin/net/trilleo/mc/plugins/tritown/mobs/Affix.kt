package net.trilleo.mc.plugins.tritown.mobs

/**
 * Something that makes a ranked mob fight differently. What each one does is
 * in `AffixEffects`, and how strongly in the `affixes` block of `balance.yml`.
 */
enum class Affix {
    /** Has Defense, like a player's armor. */
    ARMORED,

    /** Moves faster. */
    FRENZIED,

    /** Heals a share of the damage it deals. */
    VAMPIRIC,

    /** Hits harder once badly hurt. */
    ENRAGED,

    /** Sets whoever it hits on fire. */
    MOLTEN,

    /** Slows whoever it hits. */
    FROSTBOUND,

    /** Poisons whoever it hits. */
    VENOMOUS,

    /** Explodes a moment after it dies. */
    VOLATILE,

    /** Calls more of its kind once badly hurt. */
    SUMMONER,

    /** Steps in behind a target that keeps its distance. */
    BLINKING,

    /** Shrugs off projectiles until something strikes it in melee. */
    WARDED;

    /** The translation key naming the affix, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            ARMORED -> "mob.affix.armored"
            FRENZIED -> "mob.affix.frenzied"
            VAMPIRIC -> "mob.affix.vampiric"
            ENRAGED -> "mob.affix.enraged"
            MOLTEN -> "mob.affix.molten"
            FROSTBOUND -> "mob.affix.frostbound"
            VENOMOUS -> "mob.affix.venomous"
            VOLATILE -> "mob.affix.volatile"
            SUMMONER -> "mob.affix.summoner"
            BLINKING -> "mob.affix.blinking"
            WARDED -> "mob.affix.warded"
        }
}

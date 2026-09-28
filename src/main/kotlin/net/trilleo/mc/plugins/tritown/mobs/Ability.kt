package net.trilleo.mc.plugins.tritown.mobs

/**
 * Something a custom mob does in a fight, on a cooldown, with a warning first.
 * What each one does is in `AbilityEffects`, and how strongly in the
 * `abilities` block of `balance.yml`.
 */
enum class Ability {
    /** Pounces on a target keeping its distance, and lands hard. */
    LEAP,

    /** Rears up, then strikes the ground: a shockwave that throws players up. */
    SLAM,

    /** Lowers its head, then dashes at its target, bowling over whoever is in the way. */
    CHARGE,

    /** Looses a fan of arrows. */
    VOLLEY,

    /** Hurls fireballs that set no blocks alight. */
    FIREBALL,

    /** Marks the ground around its target, then brings fire down on it. */
    METEOR,

    /** Marks the ground around its target, then brings lightning down on it. */
    STORM,

    /** Spits a web that all but roots its target. */
    ENSNARE,

    /** Drags its target in. */
    HOOK,

    /** Calls minions to its side. */
    SUMMON,

    /** Braces itself: far more Defense for a few seconds. */
    BULWARK,

    /** A burst of frost that hurts and slows everyone close by. */
    FROST_NOVA,

    /** Leaves a poisonous cloud where its target stands. */
    MIASMA,

    /** Latches on to its target and drinks its health back. */
    DRAIN;

    /** The translation key naming the ability, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            LEAP -> "mob.ability.leap"
            SLAM -> "mob.ability.slam"
            CHARGE -> "mob.ability.charge"
            VOLLEY -> "mob.ability.volley"
            FIREBALL -> "mob.ability.fireball"
            METEOR -> "mob.ability.meteor"
            STORM -> "mob.ability.storm"
            ENSNARE -> "mob.ability.ensnare"
            HOOK -> "mob.ability.hook"
            SUMMON -> "mob.ability.summon"
            BULWARK -> "mob.ability.bulwark"
            FROST_NOVA -> "mob.ability.frost-nova"
            MIASMA -> "mob.ability.miasma"
            DRAIN -> "mob.ability.drain"
        }

    /** Its name in the content files: `frost-nova`. */
    val configName: String
        get() = name.lowercase().replace('_', '-')

    companion object {
        fun of(name: String): Ability? = entries.firstOrNull { it.configName == name.lowercase().replace('_', '-') }
    }
}

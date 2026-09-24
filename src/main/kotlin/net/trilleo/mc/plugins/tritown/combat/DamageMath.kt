package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.content.Balance

/**
 * Every formula of the combat layer, and the only place one lives.
 *
 * Plain Kotlin with no server behind it, so the whole balance model can be
 * run in a test. The listeners only turn events into these calls and the
 * results back into vanilla damage.
 *
 * Two units meet here. **RPG** numbers are the ones players see: health pools,
 * hits, Defense. **Vanilla** numbers are what the server actually applies to an
 * entity's health. [toVanilla] and [toRpg] convert between them through the
 * entity's own pool, which is what keeps vanilla health the single truth.
 */
object DamageMath {

    /** The damage multiplier of vanilla's own critical hit, which the jump attack's crit chance replaces. */
    const val VANILLA_CRIT = 1.5

    /** A player's vanilla maximum health, which their RPG pool is measured against. */
    const val VANILLA_PLAYER_HEALTH = 20.0

    /** How much of a mob's hit gets through [defense]: 100 Defense halves it, 300 leaves a quarter. */
    fun defenseMultiplier(defense: Double): Double = 100.0 / (100.0 + defense.coerceAtLeast(0.0))

    fun strengthMultiplier(sheet: StatSheet): Double = (1.0 + sheet[Stat.STRENGTH] / 100.0).coerceAtLeast(0.0)

    fun critMultiplier(sheet: StatSheet, crit: Boolean): Double =
        if (crit) 1.0 + sheet[Stat.CRIT_DAMAGE] / 100.0 else 1.0

    /** The chance, from 0 to 1, that a player's hit on a mob is critical. */
    fun critChance(sheet: StatSheet, jumpAttack: Boolean, balance: Balance): Double {
        val percent = sheet[Stat.CRIT_CHANCE] + if (jumpAttack) balance.jumpCritChance else 0.0
        return (percent / 100.0).coerceIn(0.0, 1.0)
    }

    /**
     * How much of a full swing vanilla says a melee hit was worth: the attack
     * cooldown, a sweep, Sharpness and its kin, a mace's fall.
     *
     * Vanilla's hit is measured against the player's attack damage, so what is
     * left is everything *but* the weapon — which the RPG hit supplies instead.
     * Vanilla's own crit is divided back out, since the jump attack's crit
     * chance stands in for it.
     */
    fun vanillaShare(vanillaHit: Double, attackDamage: Double, vanillaCrit: Boolean): Double {
        val share = if (attackDamage > 0.0) vanillaHit / attackDamage else vanillaHit
        return if (vanillaCrit) share / VANILLA_CRIT else share
    }

    /** A player's melee hit on a mob, before the mob's Defense. */
    fun meleeHit(sheet: StatSheet, balance: Balance, vanillaShare: Double, crit: Boolean): Double =
        (balance.lens + sheet[Stat.DAMAGE]) * strengthMultiplier(sheet) * vanillaShare * critMultiplier(sheet, crit)

    /**
     * A player's arrow or trident hitting a mob. Vanilla's hit already carries
     * the draw, the arrow's speed and Power, so the lens scales it as it is.
     */
    fun shotHit(vanillaHit: Double, shooter: StatSheet, balance: Balance, crit: Boolean): Double =
        vanillaHit * balance.lens * strengthMultiplier(shooter) * critMultiplier(shooter, crit)

    /**
     * A mob's hit on a player or another mob, before the target's Defense.
     * [multiplier] is what the mob's rank and affixes make of it.
     */
    fun mobHit(vanillaHit: Double, balance: Balance, level: Int, multiplier: Double = 1.0): Double =
        vanillaHit * balance.lens * balance.mobs.damage(level) * multiplier

    /**
     * Whatever else hurts a mob — fire, lava, falling, cactus, a player's
     * potion — in level-1 units, so a trap is only ever as strong as it is
     * against a level-1 mob.
     */
    fun environmentHit(vanillaHit: Double, balance: Balance): Double = vanillaHit * balance.lens

    fun afterDefense(hit: Double, defense: Double): Double = hit * defenseMultiplier(defense)

    /** A mob's RPG pool. [multiplier] is what its rank makes of it. */
    fun mobMaxHealth(vanillaMax: Double, balance: Balance, level: Int, multiplier: Double = 1.0): Double =
        vanillaMax * balance.lens * balance.mobs.health(level) * multiplier

    /**
     * A player's RPG pool. The Health stat is measured against vanilla's 20, so
     * Health Boost, or anything else that adds vanilla hearts, adds to it in the
     * same proportion.
     */
    fun playerMaxHealth(health: Double, vanillaMax: Double): Double = health * vanillaMax / VANILLA_PLAYER_HEALTH

    fun toVanilla(rpg: Double, rpgMax: Double, vanillaMax: Double): Double =
        if (rpgMax > 0.0) rpg / rpgMax * vanillaMax else rpg

    fun toRpg(vanilla: Double, rpgMax: Double, vanillaMax: Double): Double =
        if (vanillaMax > 0.0) vanilla / vanillaMax * rpgMax else vanilla

    /** The Defense a worn piece of vanilla armor is worth. */
    fun armorDefense(armor: Double, toughness: Double, balance: Balance): Double =
        armor * balance.vanilla.armorPoint + toughness * balance.vanilla.toughnessPoint
}

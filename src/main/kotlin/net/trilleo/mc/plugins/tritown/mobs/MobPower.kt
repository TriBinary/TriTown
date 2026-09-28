package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.content.Balance
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import org.bukkit.entity.LivingEntity

/**
 * What a mob's profile makes of it: its rank, its kind and its affixes, put
 * together in one place. `CombatHealth` asks for its pool and `DamageListener`
 * for its hits and its Defense, and each is `DamageMath`'s `multiplier` or
 * Defense — this only says how large they are.
 */
object MobPower {

    /** What [profile]'s mob multiplies its pool by: its rank's, and its kind's. */
    fun health(profile: MobProfile, balance: Balance): Double =
        profile.rank.health(balance) * (MobKinds.def(profile)?.health ?: 1.0)

    /** What [mob]'s hits are multiplied by: its rank, its kind, and its rage once it is badly hurt. */
    fun damage(mob: LivingEntity, profile: MobProfile, balance: Balance): Double {
        val enraged = profile.has(Affix.ENRAGED) && CombatHealth.fraction(mob) * 100.0 < balance.affixes.enragedBelow
        val rage = if (enraged) 1.0 + balance.affixes.enragedDamage / 100.0 else 1.0
        return profile.rank.damage(balance) * (MobKinds.def(profile)?.damage ?: 1.0) * rage
    }

    /** The Defense [profile]'s mob has against every hit: its kind's, and Armored's. */
    fun defense(profile: MobProfile): Double {
        val armored = if (profile.has(Affix.ARMORED)) ContentRegistry.balance.affixes.armoredDefense else 0.0
        return armored + (MobKinds.def(profile)?.defense ?: 0.0)
    }
}

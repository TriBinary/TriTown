package net.trilleo.mc.plugins.tritown.listeners.combat

import net.trilleo.mc.plugins.tritown.combat.*
import net.trilleo.mc.plugins.tritown.content.Balance
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.mobs.MobNameplate
import net.trilleo.mc.plugins.tritown.mobs.MobProfiles
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Works out every hit between a player and a mob, or between two mobs, in RPG
 * numbers, and hands vanilla the result.
 *
 * | Hit                        | Worked out as                                              |
 * |:---------------------------|:-----------------------------------------------------------|
 * | player → player            | **left alone**, and so is a player's pet biting a player    |
 * | anything the world does to a player | left alone                                         |
 * | player → mob (melee)       | the player's stats, scaled by vanilla's share of a full swing |
 * | player → mob (arrow, trident) | vanilla's hit, scaled by the stats the shot was fired with |
 * | mob → player or mob        | vanilla's hit, grown by the attacker's level, through Defense |
 * | anything else → mob        | level-1 units, so no trap or fire outgrows level 1          |
 *
 * The result is converted to vanilla damage through the target's own pool
 * ([DamageMath.toVanilla]). A mob keeps vanilla's reductions as they are. A
 * player's worn armor is already their Defense, so vanilla's armor reduction
 * is taken out and the rest carried over ([VanillaReductions]).
 */
class DamageListener : Listener {

    /** Whether each hit a player landed on a mob was critical, until its indicator is drawn. */
    private val crits = WeakHashMap<EntityDamageEvent, Boolean>()

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onDamage(event: EntityDamageEvent) {
        val victim = event.entity as? LivingEntity ?: return
        if (victim is ArmorStand || !Combat.isActive(victim.world)) return

        val balance = ContentRegistry.balance
        val attacker = event.damageSource.causingEntity as? LivingEntity
        if (victim is Player) hurtPlayer(event, victim, attacker, balance) else hurtMob(event, victim, attacker, balance)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun afterDamage(event: EntityDamageEvent) {
        val victim = event.entity as? LivingEntity ?: return
        if (victim is Player) return
        MobNameplate.updateLater(victim)

        val crit = crits.remove(event) ?: return
        val dealt = DamageMath.toRpg(event.finalDamage, CombatHealth.max(victim), CombatHealth.vanillaMax(victim))
        if (dealt > 0.0) DamageIndicators.show(victim, dealt, crit)
    }

    private fun hurtPlayer(event: EntityDamageEvent, victim: Player, attacker: LivingEntity?, balance: Balance) {
        if (attacker == null || attacker is Player || (attacker as? Tameable)?.isTamed == true) return

        val hit = DamageMath.afterDefense(
            DamageMath.mobHit(event.damage, balance, MobProfiles.level(attacker)),
            PlayerStats.sheet(victim)[Stat.DEFENSE],
        )
        applyWithoutArmor(event, victim, DamageMath.toVanilla(hit, CombatHealth.max(victim), CombatHealth.vanillaMax(victim)))
    }

    private fun hurtMob(event: EntityDamageEvent, victim: LivingEntity, attacker: LivingEntity?, balance: Balance) {
        val direct = event.damageSource.directEntity
        var crit: Boolean? = null

        val hit = when {
            attacker is Player && direct === attacker && event.cause in MELEE -> {
                val sheet = PlayerStats.sheet(attacker)
                val jumpAttack = (event as? EntityDamageByEntityEvent)?.isCritical == true
                crit = roll(DamageMath.critChance(sheet, jumpAttack, balance))
                val share = DamageMath.vanillaShare(event.damage, attackDamage(attacker), jumpAttack)
                DamageMath.meleeHit(sheet, balance, share, crit)
            }

            attacker is Player && direct is AbstractArrow -> {
                val sheet = ShotStats.read(direct) ?: PlayerStats.sheet(attacker)
                crit = roll(DamageMath.critChance(sheet, false, balance))
                DamageMath.shotHit(event.damage, sheet, balance, crit)
            }

            attacker != null && attacker !is Player -> DamageMath.mobHit(event.damage, balance, MobProfiles.level(attacker))
            else -> DamageMath.environmentHit(event.damage, balance)
        }

        event.damage = DamageMath.toVanilla(hit, CombatHealth.max(victim), CombatHealth.vanillaMax(victim))
        crit?.let { crits[event] = it }
    }

    /**
     * Sets the hit to [base] and carries vanilla's reductions over, all but
     * armor. The modifier API is deprecated without a replacement, and it is
     * the only way to take one reduction out and keep the rest.
     */
    @Suppress("DEPRECATION")
    private fun applyWithoutArmor(event: EntityDamageEvent, victim: Player, base: Double) {
        val modifiers = REDUCTIONS.filter(event::isApplicable)
        val rescaled = VanillaReductions.rescale(
            originalBase = event.getDamage(EntityDamageEvent.DamageModifier.BASE),
            reductions = modifiers.map { VanillaReductions.Reduction(kind(it), event.getDamage(it)) },
            base = base,
            absorption = victim.absorptionAmount,
        )
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, base)
        modifiers.zip(rescaled).forEach { (modifier, value) -> event.setDamage(modifier, value) }
    }

    @Suppress("DEPRECATION")
    private fun kind(modifier: EntityDamageEvent.DamageModifier): VanillaReductions.Kind = when (modifier) {
        EntityDamageEvent.DamageModifier.ARMOR -> VanillaReductions.Kind.ARMOR
        EntityDamageEvent.DamageModifier.ABSORPTION -> VanillaReductions.Kind.ABSORPTION
        else -> VanillaReductions.Kind.SHARE
    }

    private fun attackDamage(player: Player): Double = player.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 1.0

    private fun roll(chance: Double): Boolean = ThreadLocalRandom.current().nextDouble() < chance

    private companion object {
        val MELEE = setOf(DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK)

        /** Every reduction, in the order the server applies them: the enum is kept in that order. */
        @Suppress("DEPRECATION")
        val REDUCTIONS = EntityDamageEvent.DamageModifier.entries.filter { it != EntityDamageEvent.DamageModifier.BASE }
    }
}

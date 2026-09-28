package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.combat.DamageMath
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*

/**
 * What every [Affix] does, and the only place any of it is done.
 *
 * Numbers come from the `affixes` block of `balance.yml`; what Armored and
 * Enraged add to a mob's Defense and hits is `MobPower`'s. What an affix
 * remembers about one mob — a ward already broken, minions already called, the
 * last blink — is kept in memory: a mob that unloads mid-fight gets its ward
 * back, which only ever favours the mob.
 *
 * **Nothing here touches a hit between players.** Every effect is triggered by
 * a mob, and lands on a player only through a mob's hit.
 */
object AffixEffects {

    private val FRENZY = NamespacedKey("tritown", "frenzied")

    private val brokenWards = HashSet<UUID>()
    private val summoned = HashSet<UUID>()
    private val lastBlink = HashMap<UUID, Int>()

    /** Sets up what an affix changes about the mob itself, as it spawns or gains one. Safe to run again. */
    fun onSpawn(mob: LivingEntity, profile: MobProfile) {
        val tuning = ContentRegistry.balance.affixes
        if (profile.has(Affix.FRENZIED)) {
            mob.getAttribute(Attribute.MOVEMENT_SPEED)?.let { speed ->
                speed.removeModifier(FRENZY)
                speed.addModifier(
                    AttributeModifier(
                        FRENZY,
                        tuning.frenziedSpeed / 100.0,
                        AttributeModifier.Operation.ADD_SCALAR
                    )
                )
            }
        }
        if (profile.rank == MobRank.CHAMPION) mob.isGlowing = true
    }

    /**
     * Whether a warded [mob] shrugs this hit off. A projectile is shrugged off
     * until anything strikes the mob in melee, which breaks the ward for good.
     */
    fun wards(mob: LivingEntity, profile: MobProfile, projectile: Boolean, melee: Boolean): Boolean {
        if (!profile.has(Affix.WARDED) || mob.uniqueId in brokenWards) return false
        if (melee) {
            brokenWards += mob.uniqueId
            mob.world.playSound(mob.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f)
            return false
        }
        if (!projectile) return false
        mob.world.playSound(mob.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1f)
        mob.world.spawnParticle(Particle.ENCHANT, mob.location.add(0.0, mob.height / 2, 0.0), 20, 0.4, 0.5, 0.4, 0.5)
        return true
    }

    /** What happens to [player] once [mob]'s hit has landed for [dealt] RPG damage. */
    fun afterHittingPlayer(mob: LivingEntity, profile: MobProfile, player: Player, dealt: Double) {
        val tuning = ContentRegistry.balance.affixes
        if (profile.has(Affix.VAMPIRIC) && !mob.isDead) {
            val heal = DamageMath.toVanilla(
                dealt * tuning.vampiricHeal / 100.0,
                CombatHealth.max(mob),
                CombatHealth.vanillaMax(mob)
            )
            mob.heal(heal.coerceAtMost(CombatHealth.vanillaMax(mob) - mob.health).coerceAtLeast(0.0))
        }
        if (profile.has(Affix.MOLTEN)) player.fireTicks = maxOf(player.fireTicks, tuning.moltenSeconds * TICKS)
        if (profile.has(Affix.FROSTBOUND)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, tuning.frostboundSeconds * TICKS, 1))
        }
        if (profile.has(Affix.VENOMOUS)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.POISON, tuning.venomousSeconds * TICKS, 0))
        }
    }

    /**
     * What happens once [mob] has been hit down to [left] of its health, from
     * 0 to 1: a summoner that has fallen far enough calls for help, once.
     */
    fun afterHurt(mob: LivingEntity, profile: MobProfile, left: Double) {
        val tuning = ContentRegistry.balance.affixes
        if (!profile.has(Affix.SUMMONER) || left <= 0.0 || mob.uniqueId in summoned) return
        if (left * 100.0 >= tuning.summonerBelow) return

        summoned += mob.uniqueId
        mob.world.playSound(mob.location, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f)
        MobSetup.minions(mob, profile, tuning.summonerMinions, (mob as? Mob)?.target)
    }

    /**
     * A volatile mob's parting shot: after a hiss, a blast that hurts every
     * player near where it died, as an explosion of the mob's, so its level
     * scales it and Blast Protection guards against it. It is not a real
     * explosion, so it breaks no blocks and leaves the mob's drops alone.
     */
    fun onDeath(mob: LivingEntity, profile: MobProfile) {
        forget(mob.uniqueId)
        if (!profile.has(Affix.VOLATILE)) return

        val tuning = ContentRegistry.balance.affixes
        val center = mob.location.add(0.0, mob.height / 2, 0.0)
        mob.world.playSound(center, Sound.ENTITY_CREEPER_PRIMED, 1f, 0.8f)
        mob.world.spawnParticle(Particle.SMOKE, center, 30, 0.4, 0.4, 0.4, 0.02)

        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
            val world = center.world ?: return@Runnable
            val radius = tuning.volatilePower * 2.0
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1)
            world.getNearbyPlayers(center, radius).forEach { player ->
                val falloff = 1.0 - player.location.distance(center) / radius
                if (falloff <= 0.0) return@forEach
                val blast =
                    DamageSource.builder(DamageType.EXPLOSION).withCausingEntity(mob).withDirectEntity(mob).build()
                player.damage(tuning.volatilePower * VOLATILE_HIT * falloff, blast)
                player.velocity =
                    player.location.toVector().subtract(center.toVector()).normalizeOrZero().multiply(falloff)
            }
        }, tuning.volatileDelayTicks.toLong())
    }

    /** A ranked mob's twice-a-second turn: what it shows, and a blinker closing the distance. */
    fun tick(mob: LivingEntity, profile: MobProfile) {
        val center = mob.location.add(0.0, mob.height / 2, 0.0)
        profile.affixes.forEach { affix ->
            particle(affix, mob)?.let {
                mob.world.spawnParticle(
                    it,
                    center,
                    1,
                    0.3,
                    0.4,
                    0.3,
                    0.0
                )
            }
        }
        if (profile.has(Affix.BLINKING)) blink(mob)
    }

    fun forget(mob: UUID) {
        brokenWards -= mob
        summoned -= mob
        lastBlink -= mob
    }

    private fun blink(mob: LivingEntity) {
        val tuning = ContentRegistry.balance.affixes
        val target = (mob as? Mob)?.target as? Player ?: return
        if (target.world != mob.world || target.location.distance(mob.location) <= tuning.blinkingRange) return
        val now = Bukkit.getCurrentTick()
        if (now - (lastBlink[mob.uniqueId] ?: Int.MIN_VALUE / 2) < tuning.blinkingCooldownTicks) return

        val behind =
            target.location.clone().subtract(target.location.direction.setY(0).normalizeOrZero().multiply(BLINK_BEHIND))
        if (!MobSetup.standable(behind)) return
        lastBlink[mob.uniqueId] = now
        mob.world.spawnParticle(Particle.PORTAL, mob.location.add(0.0, 1.0, 0.0), 30, 0.3, 0.6, 0.3, 0.5)
        MobSetup.teleport(mob, behind.setDirection(target.location.toVector().subtract(behind.toVector())))
        mob.world.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
    }

    private fun particle(affix: Affix, mob: LivingEntity): Particle? = when (affix) {
        Affix.ARMORED -> Particle.CRIT
        Affix.FRENZIED -> Particle.CLOUD
        Affix.VAMPIRIC -> Particle.DAMAGE_INDICATOR
        Affix.ENRAGED -> Particle.ANGRY_VILLAGER.takeIf {
            CombatHealth.fraction(mob) * 100.0 < ContentRegistry.balance.affixes.enragedBelow
        }

        Affix.MOLTEN -> Particle.FLAME
        Affix.FROSTBOUND -> Particle.SNOWFLAKE
        Affix.VENOMOUS -> Particle.ITEM_SLIME
        Affix.VOLATILE -> Particle.SMOKE
        Affix.SUMMONER -> Particle.SOUL
        Affix.BLINKING -> Particle.PORTAL
        Affix.WARDED -> Particle.ENCHANT.takeIf { mob.uniqueId !in brokenWards }
    }

    private fun Vector.normalizeOrZero(): Vector = if (lengthSquared() > 0.0) normalize() else this

    private const val TICKS = 20
    private const val BLINK_BEHIND = 1.5

    /** Vanilla damage a Volatile blast of power 1 lands point-blank, before the mob's level scales it. */
    private const val VOLATILE_HIT = 4.0
}

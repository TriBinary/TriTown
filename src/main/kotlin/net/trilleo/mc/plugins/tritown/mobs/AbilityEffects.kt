package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.combat.CombatHealth
import net.trilleo.mc.plugins.tritown.combat.DamageMath
import net.trilleo.mc.plugins.tritown.content.Balance
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import org.bukkit.*
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.*
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * What every [Ability] does, and the only place any of it is done.
 *
 * A custom mob's turn in the mob task may start one: its target must be a
 * player the ability can reach, the ability off its cooldown, and the mob off
 * the global one. Every ability warns first — particles and sound for its
 * `windup-ticks` — so it can be dodged, and a mob winding one up starts no
 * other. Numbers come from the `abilities` block of `balance.yml`.
 *
 * **An ability only ever hurts players, and only as the mob.** Every hit is a
 * [DamageSource] the mob caused, so `DamageListener` grows it by the mob's
 * level, rank and kind and takes the player's Defense off it like any other of
 * its hits, and no hit between players is ever touched. **No ability changes a
 * block**: fire and lightning are only what they look like.
 *
 * What it remembers about one mob — cooldowns, a Bulwark, its minions — is kept
 * in memory and forgotten when the mob unloads, which only ever favours the
 * players.
 */
object AbilityEffects {

    private val readyAt = HashMap<UUID, HashMap<Ability, Int>>()
    private val nextCast = HashMap<UUID, Int>()
    private val busy = HashSet<UUID>()
    private val bracedUntil = HashMap<UUID, Int>()
    private val minions = HashMap<UUID, MutableSet<UUID>>()

    /**
     * What [mob] can do now: its kind's abilities, and a boss's from every
     * phase its health has fallen into.
     */
    fun abilities(mob: LivingEntity, profile: MobProfile): List<Ability> {
        val def = MobKinds.def(profile) ?: return emptyList()
        val phases = def.boss?.phases.orEmpty().filter { CombatHealth.fraction(mob) * 100.0 < it.below }
        return (def.abilities + phases.flatMap { it.abilities }).distinct()
    }

    /** [mob]'s twice-a-second turn: showing a Bulwark it has up, and perhaps starting an ability. */
    fun tick(mob: LivingEntity, profile: MobProfile) {
        if (braced(mob)) showBulwark(mob)
        val abilities = abilities(mob, profile)
        if (abilities.isEmpty() || mob.uniqueId in busy) return
        val target = (mob as? Mob)?.target as? Player ?: return
        if (!hittable(target) || target.world != mob.world) return

        val now = Bukkit.getCurrentTick()
        val tuning = ContentRegistry.balance.abilities
        if (now < nextCast.getOrPut(mob.uniqueId) { now + tuning.globalCooldownTicks }) return

        val ready = readyAt.getOrPut(mob.uniqueId, ::HashMap)
        val distance = target.location.distance(mob.location)
        val ability = abilities
            .filter { now >= (ready[it] ?: 0) && usable(it, mob, target, distance, tuning[it]) }
            .randomOrNull() ?: return

        ready[ability] = now + tuning[ability].cooldownTicks
        nextCast[mob.uniqueId] = now + tuning.globalCooldownTicks
        cast(ability, mob, profile, target, tuning[ability])
    }

    /** The Defense a Bulwark [mob] has up adds, on top of what its profile gives it. */
    fun defense(mob: LivingEntity): Double =
        if (braced(mob)) ContentRegistry.balance.abilities[Ability.BULWARK].power else 0.0

    fun forget(mob: UUID) {
        readyAt -= mob
        nextCast -= mob
        busy -= mob
        bracedUntil -= mob
        minions -= mob
    }

    private fun usable(
        ability: Ability,
        mob: LivingEntity,
        target: Player,
        distance: Double,
        stats: Balance.AbilityStats
    ) =
        when (ability) {
            Ability.LEAP -> mob.isOnGround && distance in LEAP_MIN..stats.range
            Ability.SLAM -> mob.isOnGround && distance <= stats.radius * CLOSE_ENOUGH
            Ability.CHARGE -> mob.isOnGround && distance in CHARGE_MIN..stats.range && mob.hasLineOfSight(target)
            Ability.VOLLEY, Ability.FIREBALL, Ability.ENSNARE, Ability.DRAIN ->
                distance <= stats.range && mob.hasLineOfSight(target)

            Ability.HOOK -> distance in HOOK_MIN..stats.range && mob.hasLineOfSight(target)
            Ability.METEOR, Ability.STORM, Ability.MIASMA -> distance <= stats.range
            Ability.SUMMON -> livingMinions(mob) < stats.count
            Ability.BULWARK -> !braced(mob) && CombatHealth.fraction(mob) < BULWARK_BELOW
            Ability.FROST_NOVA -> distance <= stats.radius * CLOSE_ENOUGH
        }

    private fun cast(
        ability: Ability,
        mob: LivingEntity,
        profile: MobProfile,
        target: Player,
        stats: Balance.AbilityStats
    ) =
        when (ability) {
            Ability.LEAP -> leap(mob, target, stats)
            Ability.SLAM -> slam(mob, stats)
            Ability.CHARGE -> charge(mob, target, stats)
            Ability.VOLLEY -> volley(mob, target, stats)
            Ability.FIREBALL -> fireball(mob, target, stats)
            Ability.METEOR -> strike(mob, target, stats, lightning = false)
            Ability.STORM -> strike(mob, target, stats, lightning = true)
            Ability.ENSNARE -> ensnare(mob, target, stats)
            Ability.HOOK -> hook(mob, target, stats)
            Ability.SUMMON -> summon(mob, profile, target, stats)
            Ability.BULWARK -> bulwark(mob, stats)
            Ability.FROST_NOVA -> frostNova(mob, stats)
            Ability.MIASMA -> miasma(mob, target, stats)
            Ability.DRAIN -> drain(mob, target, stats)
        }

    // ── The abilities ───────────────────────────────────────────────────

    private fun leap(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = true, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_RAVAGER_STEP, 0.6f)
            mob.world.spawnParticle(Particle.CLOUD, mob.location, 2, 0.3, 0.05, 0.3, 0.01)
        }) {
            val flat = target.location.toVector().subtract(mob.location.toVector()).setY(0.0)
            val reach = flat.length().coerceAtMost(stats.range)
            mob.velocity = flat.normalizeOrZero().multiply(reach * LEAP_REACH).setY(LEAP_LIFT)
            sound(mob.location, Sound.ENTITY_GOAT_LONG_JUMP, 0.8f)

            repeatFor(mob, LEAP_AIRTIME) { tick ->
                if (tick < LEAP_TAKEOFF || !mob.isOnGround) return@repeatFor true
                val at = mob.location
                mob.world.spawnParticle(Particle.EXPLOSION, at, 1)
                mob.world.spawnParticle(Particle.CLOUD, at, 20, stats.radius / 2, 0.1, stats.radius / 2, 0.05)
                sound(at, Sound.BLOCK_ANVIL_LAND, 0.6f)
                playersNear(at, stats.radius).forEach { hurt(it, mob, stats.damage, DamageType.MOB_ATTACK) }
                false
            }
        }
    }

    private fun slam(mob: LivingEntity, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = true, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_RAVAGER_ROAR, 0.7f)
            ring(mob.location, stats.radius * (tick + 1) / stats.windupTicks.coerceAtLeast(1), Particle.CRIT)
        }) {
            val at = mob.location
            sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f)
            mob.world.spawnParticle(Particle.EXPLOSION, at, 3, stats.radius / 3, 0.2, stats.radius / 3, 0.0)
            ring(at, stats.radius, Particle.SWEEP_ATTACK)
            playersNear(at, stats.radius).forEach { player ->
                hurt(player, mob, stats.damage, DamageType.MOB_ATTACK)
                player.velocity = away(at, player.location, 0.4).setY(stats.power)
            }
        }
    }

    private fun charge(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = true, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_RAVAGER_ROAR, 1.2f)
            face(mob, target.location)
            mob.world.spawnParticle(Particle.LARGE_SMOKE, mob.location, 2, 0.3, 0.1, 0.3, 0.01)
        }) {
            val direction = target.location.toVector().subtract(mob.location.toVector()).setY(0.0).normalizeOrZero()
            mob.velocity = direction.clone().multiply(stats.power).setY(CHARGE_LIFT)
            sound(mob.location, Sound.ENTITY_BREEZE_CHARGE, 0.8f)
            val struck = HashSet<UUID>()

            repeatFor(mob, CHARGE_TICKS) {
                mob.world.spawnParticle(Particle.CLOUD, mob.location, 3, 0.2, 0.05, 0.2, 0.01)
                playersNear(mob.location, CHARGE_REACH).filter { struck.add(it.uniqueId) }.forEach { player ->
                    hurt(player, mob, stats.damage, DamageType.MOB_ATTACK)
                    player.velocity = direction.clone().multiply(CHARGE_PUSH).setY(CHARGE_PUSH / 2)
                }
                true
            }
        }
    }

    private fun volley(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = false, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ITEM_CROSSBOW_LOADING_START, 1f)
            face(mob, target.location)
        }) {
            val aim = aimAt(mob, target)
            val spread = VOLLEY_SPREAD_DEGREES * PI / 180.0
            repeat(stats.count) { index ->
                val angle = (index - (stats.count - 1) / 2.0) * spread
                val arrow =
                    mob.launchProjectile(Arrow::class.java, aim.clone().rotateAroundY(angle).multiply(VOLLEY_SPEED))
                arrow.damage = stats.damage / VOLLEY_SPEED
                arrow.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
            }
            sound(mob.location, Sound.ENTITY_WITHER_SHOOT, 1.4f)
        }
    }

    private fun fireball(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = false, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_BLAZE_BURN, 1f)
            mob.world.spawnParticle(Particle.FLAME, mob.eyeLocation, 3, 0.3, 0.3, 0.3, 0.01)
        }) {
            repeatFor(mob, stats.count * FIREBALL_GAP, FIREBALL_GAP.toLong()) {
                if (!target.isValid || target.world != mob.world) return@repeatFor false
                val fireball =
                    mob.launchProjectile(SmallFireball::class.java, aimAt(mob, target).multiply(FIREBALL_SPEED))
                fireball.setIsIncendiary(false)
                sound(mob.location, Sound.ENTITY_BLAZE_SHOOT, 1f)
                true
            }
        }
    }

    /** Meteor and Storm: spots marked on and around the target, and then fire or lightning on each. */
    private fun strike(mob: LivingEntity, target: Player, stats: Balance.AbilityStats, lightning: Boolean) {
        val spots = List(stats.count.coerceAtLeast(1)) { index ->
            if (index == 0) target.location else target.location.add(
                Random.nextDouble(-STRIKE_SCATTER, STRIKE_SCATTER),
                0.0,
                Random.nextDouble(-STRIKE_SCATTER, STRIKE_SCATTER),
            )
        }
        val mark = if (lightning) Particle.ELECTRIC_SPARK else Particle.FLAME
        windUp(mob, stats.windupTicks, root = false, show = { tick ->
            if (tick == 0) sound(
                mob.location,
                if (lightning) Sound.ENTITY_EVOKER_CAST_SPELL else Sound.ITEM_FIRECHARGE_USE,
                0.8f
            )
            if (tick % 2 == 0) spots.forEach { ring(it, stats.radius, mark) }
        }) {
            spots.forEach { spot ->
                if (lightning) {
                    spot.world.strikeLightningEffect(spot)
                } else {
                    spot.world.spawnParticle(Particle.EXPLOSION, spot, 2, 0.5, 0.2, 0.5, 0.0)
                    spot.world.spawnParticle(Particle.LAVA, spot, 12, 0.6, 0.2, 0.6, 0.0)
                    sound(spot, Sound.ENTITY_GENERIC_EXPLODE, 1.2f)
                }
                playersNear(spot, stats.radius).forEach { player ->
                    hurt(player, mob, stats.damage, if (lightning) DamageType.LIGHTNING_BOLT else DamageType.EXPLOSION)
                }
            }
        }
    }

    private fun ensnare(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = false, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_SPIDER_AMBIENT, 0.6f)
            line(mob.eyeLocation, target.location.add(0.0, 1.0, 0.0), Particle.ITEM_SNOWBALL)
        }) {
            if (!reaches(mob, target, stats.range + REACH_GRACE)) return@windUp
            target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, ticks(stats.seconds), ENSNARED))
            target.world.spawnParticle(
                Particle.BLOCK,
                target.location.add(0.0, 0.5, 0.0),
                30,
                0.4,
                0.5,
                0.4,
                0.0,
                Material.COBWEB.createBlockData(),
            )
            sound(target.location, Sound.BLOCK_POWDER_SNOW_STEP, 0.6f)
        }
    }

    private fun hook(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = true, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_FISHING_BOBBER_THROW, 0.6f)
            line(mob.eyeLocation, target.location.add(0.0, 1.0, 0.0), Particle.CRIT)
        }) {
            if (!reaches(mob, target, stats.range + REACH_GRACE)) return@windUp
            val pull = mob.location.toVector().subtract(target.location.toVector()).setY(0.0)
            val speed = (pull.length() * LEAP_REACH).coerceAtMost(stats.power)
            hurt(target, mob, stats.damage, DamageType.MOB_ATTACK)
            target.velocity = pull.normalizeOrZero().multiply(speed).setY(HOOK_LIFT)
            sound(target.location, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f)
        }
    }

    private fun summon(mob: LivingEntity, profile: MobProfile, target: Player, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = true, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f)
            mob.world.spawnParticle(Particle.SOUL, mob.location.add(0.0, 1.0, 0.0), 3, 0.6, 0.6, 0.6, 0.02)
        }) {
            val called = MobSetup.minions(mob, profile, stats.count - livingMinions(mob), target)
            minions.getOrPut(mob.uniqueId, ::HashSet) += called.map { it.uniqueId }
        }
    }

    private fun bulwark(mob: LivingEntity, stats: Balance.AbilityStats) {
        bracedUntil[mob.uniqueId] = Bukkit.getCurrentTick() + ticks(stats.seconds)
        sound(mob.location, Sound.ITEM_SHIELD_BLOCK, 0.7f)
        sound(mob.location, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8f)
        showBulwark(mob)
    }

    private fun frostNova(mob: LivingEntity, stats: Balance.AbilityStats) {
        windUp(mob, stats.windupTicks, root = true, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.BLOCK_POWDER_SNOW_STEP, 0.5f)
            val left = 1.0 - tick.toDouble() / stats.windupTicks.coerceAtLeast(1)
            ring(mob.location.add(0.0, 0.5, 0.0), stats.radius * left, Particle.SNOWFLAKE)
        }) {
            val at = mob.location
            sound(at, Sound.BLOCK_GLASS_BREAK, 0.6f)
            sound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f)
            mob.world.spawnParticle(
                Particle.SNOWFLAKE,
                at.clone().add(0.0, 1.0, 0.0),
                80,
                stats.radius / 2,
                0.5,
                stats.radius / 2,
                0.1
            )
            playersNear(at, stats.radius).forEach { player ->
                hurt(player, mob, stats.damage, DamageType.FREEZE)
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, ticks(stats.seconds), CHILLED))
                player.freezeTicks = maxOf(player.freezeTicks, player.maxFreezeTicks - FROST_MARGIN)
            }
        }
    }

    private fun miasma(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        val center = target.location
        windUp(mob, stats.windupTicks, root = false, show = { tick ->
            if (tick == 0) sound(mob.location, Sound.BLOCK_BREWING_STAND_BREW, 0.7f)
            ring(center, stats.radius, Particle.WITCH)
        }) {
            repeatFor(mob, ticks(stats.seconds), PULSE.toLong()) {
                center.world.spawnParticle(
                    Particle.DUST, center.clone().add(0.0, 0.6, 0.0), 30, stats.radius / 2, 0.4, stats.radius / 2, 0.0,
                    Particle.DustOptions(MIASMA_COLOR, 1.6f),
                )
                playersNear(center, stats.radius).forEach { hurt(it, mob, stats.damage, DamageType.MAGIC) }
                true
            }
        }
    }

    private fun drain(mob: LivingEntity, target: Player, stats: Balance.AbilityStats) {
        sound(mob.location, Sound.ENTITY_PHANTOM_BITE, 0.6f)
        repeatFor(mob, ticks(stats.seconds), PULSE.toLong()) {
            if (!reaches(mob, target, stats.range + REACH_GRACE) || !mob.hasLineOfSight(target)) return@repeatFor false
            line(
                target.location.add(0.0, 1.0, 0.0),
                mob.eyeLocation,
                Particle.DUST,
                Particle.DustOptions(DRAIN_COLOR, 1.2f)
            )
            val before = target.health
            hurt(target, mob, stats.damage, DamageType.MAGIC)
            val taken =
                DamageMath.toRpg(before - target.health, CombatHealth.max(target), CombatHealth.vanillaMax(target))
            if (taken > 0.0) heal(mob, taken * stats.power / 100.0)
            true
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * [mob]'s warning: [show] every tick for [ticks] ticks, held in place if
     * [root], and then [land] — unless it died or unloaded first. It starts
     * nothing else while it winds up.
     */
    private fun windUp(mob: LivingEntity, ticks: Int, root: Boolean, show: (Int) -> Unit, land: () -> Unit) {
        busy += mob.uniqueId
        if (root && ticks > 0) mob.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, ticks, ROOTED, false, false))
        object : BukkitRunnable() {
            var elapsed = 0

            override fun run() {
                if (!mob.isValid || elapsed >= ticks) {
                    busy -= mob.uniqueId
                    cancel()
                    if (mob.isValid) land()
                    return
                }
                show(elapsed++)
            }
        }.runTaskTimer(Main.instance, 0L, 1L)
    }

    /** Runs [step] every [period] ticks for up to [ticks] ticks while [mob] lives, until it answers `false`. */
    private fun repeatFor(mob: LivingEntity, ticks: Int, period: Long = 1L, step: (Int) -> Boolean) {
        object : BukkitRunnable() {
            var elapsed = 0

            override fun run() {
                if (!mob.isValid || elapsed >= ticks || !step(elapsed)) {
                    cancel()
                    return
                }
                elapsed += period.toInt()
            }
        }.runTaskTimer(Main.instance, 0L, period)
    }

    private fun hurt(player: Player, mob: LivingEntity, amount: Double, type: DamageType) {
        if (amount <= 0.0 || !hittable(player)) return
        player.damage(amount, DamageSource.builder(type).withCausingEntity(mob).withDirectEntity(mob).build())
    }

    /** Heals [mob] by [rpg] of its own health, no further than full. */
    private fun heal(mob: LivingEntity, rpg: Double) {
        val vanilla = DamageMath.toVanilla(rpg, CombatHealth.max(mob), CombatHealth.vanillaMax(mob))
        mob.heal(vanilla.coerceAtMost(CombatHealth.vanillaMax(mob) - mob.health).coerceAtLeast(0.0))
    }

    private fun hittable(player: Player): Boolean =
        player.isValid && !player.isDead && (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE)

    private fun playersNear(center: Location, radius: Double): List<Player> =
        center.world.getNearbyPlayers(center, radius)
            .filter { hittable(it) && it.location.distanceSquared(center) <= radius * radius }

    private fun reaches(mob: LivingEntity, target: Player, range: Double): Boolean =
        hittable(target) && target.world == mob.world && target.location.distance(mob.location) <= range

    private fun braced(mob: LivingEntity): Boolean = Bukkit.getCurrentTick() < (bracedUntil[mob.uniqueId] ?: 0)

    private fun showBulwark(mob: LivingEntity) =
        ring(mob.location.add(0.0, mob.height / 2, 0.0), mob.width + 0.4, Particle.ENCHANTED_HIT)

    private fun livingMinions(mob: LivingEntity): Int {
        val called = minions[mob.uniqueId] ?: return 0
        called.removeIf { Bukkit.getEntity(it)?.isValid != true }
        return called.size
    }

    /** A unit vector from [mob]'s eyes to [target]'s chest, aimed a little high for the drop over distance. */
    private fun aimAt(mob: LivingEntity, target: Player): Vector {
        val to = target.location.add(0.0, target.height * 0.6, 0.0).toVector()
        val from = mob.eyeLocation.toVector()
        val aim = to.subtract(from)
        return aim.setY(aim.y + aim.length() * DROP_ALLOWANCE).normalizeOrZero()
    }

    private fun away(from: Location, to: Location, strength: Double): Vector =
        to.toVector().subtract(from.toVector()).setY(0.0).normalizeOrZero().multiply(strength)

    private fun face(mob: LivingEntity, at: Location) {
        mob.setRotation(
            mob.location.setDirection(at.toVector().subtract(mob.location.toVector())).yaw,
            mob.location.pitch
        )
    }

    private fun ring(center: Location, radius: Double, particle: Particle) {
        val points = (radius * RING_DENSITY).toInt().coerceAtLeast(8)
        repeat(points) { index ->
            val angle = 2 * PI * index / points
            center.world.spawnParticle(
                particle,
                center.clone().add(radius * cos(angle), 0.1, radius * sin(angle)),
                1,
                0.0,
                0.0,
                0.0,
                0.0
            )
        }
    }

    private fun line(from: Location, to: Location, particle: Particle, data: Any? = null) {
        val step = to.toVector().subtract(from.toVector())
        val points = (step.length() * LINE_DENSITY).toInt().coerceAtLeast(2)
        step.multiply(1.0 / points)
        val at = from.clone()
        repeat(points) {
            at.add(step)
            at.world.spawnParticle(particle, at, 1, 0.0, 0.0, 0.0, 0.0, data)
        }
    }

    private fun sound(at: Location, sound: Sound, pitch: Float) = at.world.playSound(at, sound, 1f, pitch)

    private fun ticks(seconds: Double): Int = (seconds * 20).toInt()

    private fun Vector.normalizeOrZero(): Vector = if (lengthSquared() > 0.0) normalize() else this

    private const val CLOSE_ENOUGH = 0.8
    private const val REACH_GRACE = 2.0
    private const val BULWARK_BELOW = 0.8
    private const val PULSE = 10

    private const val LEAP_MIN = 4.0
    private const val LEAP_REACH = 0.08
    private const val LEAP_LIFT = 0.6
    private const val LEAP_TAKEOFF = 4
    private const val LEAP_AIRTIME = 40

    private const val CHARGE_MIN = 5.0
    private const val CHARGE_TICKS = 15
    private const val CHARGE_LIFT = 0.15
    private const val CHARGE_REACH = 1.8
    private const val CHARGE_PUSH = 0.9

    private const val HOOK_MIN = 5.0
    private const val HOOK_LIFT = 0.45

    private const val VOLLEY_SPEED = 1.6
    private const val VOLLEY_SPREAD_DEGREES = 8.0
    private const val FIREBALL_SPEED = 1.2
    private const val FIREBALL_GAP = 5
    private const val DROP_ALLOWANCE = 0.02
    private const val STRIKE_SCATTER = 3.0

    private const val FROST_MARGIN = 10
    private const val RING_DENSITY = 6.0
    private const val LINE_DENSITY = 3.0

    /** Slowness amplifiers: rooted in place, all but rooted, and chilled. */
    private const val ROOTED = 9
    private const val ENSNARED = 4
    private const val CHILLED = 1

    private val MIASMA_COLOR = Color.fromRGB(0x6a8f2a)
    private val DRAIN_COLOR = Color.fromRGB(0x8a0f1f)
}

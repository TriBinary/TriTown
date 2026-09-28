package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.config.MobSettings
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import java.util.*
import kotlin.random.Random

/** Setting a mob up once TriTown has decided what it is. */
object MobSetup {

    private val moving = HashSet<UUID>()

    /** Everything a mob with a fresh [profile] needs besides the profile itself. */
    fun settle(mob: LivingEntity, profile: MobProfile) {
        MobKinds.def(profile)?.let { MobKinds.dress(mob, it) }
        if (profile.isActive) {
            AffixEffects.onSpawn(mob, profile)
            ActiveMobs.track(mob)
        }
        if (profile.nameplate) MobNameplate.updateLater(mob)
    }

    /**
     * Spawns a mob of [type] at [level] that TriTown calls up itself — a
     * summoner's minion, or one an administrator asks for — as [kind] if one is
     * given, with the affixes the kind always has. It is unranked, and never
     * eligible: it drops nothing beyond vanilla's loot.
     */
    fun spawn(at: Location, type: EntityType, level: Int, kind: MobKindDef?): LivingEntity? {
        val profile = MobProfile(
            level = level,
            rank = MobRank.NORMAL,
            affixes = kind?.affixes.orEmpty(),
            nameplate = MobSettings.snapshot.nameplates,
            eligible = false,
            kind = kind?.id,
        )
        val entity = at.world.spawnEntity(at, type, SpawnReason.CUSTOM) { spawned ->
            (spawned as? LivingEntity)?.let { MobProfiles.assign(it, profile) }
        }
        val mob = entity as? LivingEntity ?: run {
            entity.remove()
            return null
        }
        settle(mob, profile)
        return mob
    }

    /**
     * Calls [count] minions to [summoner]'s side, at its level, aimed at
     * [target]: the kind its `minions` names, or its own — but never a copy of
     * a boss, which calls its base kind instead. How many turn up depends on
     * the room around it.
     */
    fun minions(summoner: LivingEntity, profile: MobProfile, count: Int, target: LivingEntity?): List<LivingEntity> {
        val own = MobKinds.def(profile)
        val kind = own?.minions?.let { ContentRegistry.bestiary.kinds[it] } ?: own?.takeIf { it.boss == null }
        val type = kind?.base?.let { base -> EntityType.entries.firstOrNull { it.name == base } } ?: summoner.type
        return (0 until count).mapNotNull {
            val spot = besideOf(summoner.location) ?: return@mapNotNull null
            spawn(spot, type, profile.level, kind)?.also { minion ->
                if (target != null) (minion as? Mob)?.target = target
                summoner.world.spawnParticle(Particle.SOUL, spot.clone().add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.02)
            }
        }
    }

    /**
     * Moves [mob] to [to] on TriTown's own account — a blink, a boss taken
     * back to its arena — which the teleports a boss is otherwise refused
     * (see [isMovingOnPurpose]) must let through.
     */
    fun teleport(mob: LivingEntity, to: Location) {
        moving += mob.uniqueId
        try {
            mob.teleport(to)
        } finally {
            moving -= mob.uniqueId
        }
    }

    fun isMovingOnPurpose(mob: UUID): Boolean = mob in moving

    /** A spot within a couple of blocks of [origin] that a mob can stand in, if one turns up in a few tries. */
    fun besideOf(origin: Location): Location? {
        repeat(SPOT_TRIES) {
            val spot = origin.clone().add(Random.nextDouble(-2.0, 2.0), 0.0, Random.nextDouble(-2.0, 2.0))
            if (standable(spot)) return spot
        }
        return null
    }

    /** Whether a mob can stand at [spot]: room for its feet and head, and ground beneath. */
    fun standable(spot: Location): Boolean {
        val feet = spot.block
        return feet.isPassable && feet.getRelative(0, 1, 0).isPassable && !feet.getRelative(0, -1, 0).isPassable
    }

    private const val SPOT_TRIES = 6
}

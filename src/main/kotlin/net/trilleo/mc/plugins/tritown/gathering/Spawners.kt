package net.trilleo.mc.plugins.tritown.gathering

import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import net.trilleo.mc.plugins.tritown.mobs.MobSetup
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Enemy
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The animals and monsters a region's spawners keep about.
 *
 * Each mob carries the region and spawner that called it up, so it is still
 * recognised after a restart; [adopt] counts it back in when its chunk loads,
 * and gets rid of one whose spawner is gone. Which mobs are alive is kept
 * only in memory, re-learned the same way.
 *
 * A spawner only works while a player is in range, and keeps its mobs within
 * reach of it, walking any that stray back.
 */
object Spawners {

    private val KEY = NamespacedKey("tritown", "gather-spawner")
    private const val SPOT_TRIES = 10
    private const val STRAY_MARGIN = 4

    private val alive = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val nextSpawn = ConcurrentHashMap<String, Long>()

    /** Set while TriTown is calling a mob up, so Towny's spawn rules can be told to let it be. */
    @Volatile
    var isSpawning: Boolean = false
        private set

    fun isSpawned(entity: Entity): Boolean = entity.persistentDataContainer.has(KEY, PersistentDataType.STRING)

    /** The region and spawner [entity] belongs to, or `null` when it is not a spawner's or the spawner is gone. */
    fun ownerOf(entity: Entity): Pair<ResourceRegion, ResourceSpawner>? {
        val tag = entity.persistentDataContainer.get(KEY, PersistentDataType.STRING) ?: return null
        val region = GatherManager.get(tag.substringBefore('/')) ?: return null
        val spawner = region.spawner(tag.substringAfter('/')) ?: return null
        return region to spawner
    }

    /** Counts a loaded spawner mob back in, or removes it when its spawner no longer exists. */
    fun adopt(entity: Entity) {
        if (!isSpawned(entity)) return
        val (_, spawner) = ownerOf(entity) ?: run {
            entity.remove()
            return
        }
        alive.getOrPut(spawner.id) { ConcurrentHashMap.newKeySet() } += entity.uniqueId
    }

    fun forget(entity: Entity) {
        val (_, spawner) = ownerOf(entity) ?: return
        alive[spawner.id]?.remove(entity.uniqueId)
    }

    /** Removes every loaded mob [spawner] called up, for a spawner that is being deleted or moved. */
    fun clear(spawner: ResourceSpawner) {
        alive.remove(spawner.id)?.forEach { Bukkit.getEntity(it)?.remove() }
        nextSpawn.remove(spawner.id)
    }

    fun clear(region: ResourceRegion) = region.spawners.forEach(::clear)

    /** How many of [spawner]'s mobs are loaded and alive. */
    fun count(spawner: ResourceSpawner): Int = living(spawner).size

    fun tick() {
        val now = System.currentTimeMillis()
        val range = GatheringSettings.snapshot.spawnerRange
        for (region in GatherManager.all()) {
            if (!region.enabled) continue
            for (spawner in region.spawners) tick(region, spawner, now, range)
        }
    }

    private fun tick(region: ResourceRegion, spawner: ResourceSpawner, now: Long, range: Double) {
        val home = spawner.location(region) ?: return
        if (!home.isChunkLoaded) return

        val mobs = living(spawner)
        mobs.forEach { keepNear(it, region, spawner, home) }
        if (home.getNearbyPlayers(range).isEmpty()) return

        if (mobs.size >= spawner.maxAlive) {
            nextSpawn[spawner.id] = now + spawner.respawnSeconds * 1000L
            return
        }
        if (now < (nextSpawn[spawner.id] ?: 0L)) return

        spot(region, spawner, home)?.let { spawn(region, spawner, it) }
        nextSpawn[spawner.id] = now + spawner.respawnSeconds * 1000L
    }

    private fun living(spawner: ResourceSpawner): List<LivingEntity> {
        val ids = alive[spawner.id] ?: return emptyList()
        val mobs = ids.mapNotNull { Bukkit.getEntity(it) as? LivingEntity }.filter { it.isValid && !it.isDead }
        ids.retainAll(mobs.map { it.uniqueId }.toSet())
        return mobs
    }

    private fun spawn(region: ResourceRegion, spawner: ResourceSpawner, at: Location) {
        val prepare: (Entity) -> Unit = { mob ->
            mob.persistentDataContainer.set(KEY, PersistentDataType.STRING, "${region.id}/${spawner.id}")
            (mob as? LivingEntity)?.removeWhenFarAway = false
        }

        isSpawning = true
        val mob = try {
            if (spawner.category == GatherCategory.COMBAT && isHostile(spawner)) {
                MobSetup.spawn(at, spawner.type, spawner.level, null, prepare)
            } else {
                at.world.spawnEntity(at, spawner.type, SpawnReason.CUSTOM) { prepare(it) } as? LivingEntity
            }
        } finally {
            isSpawning = false
        }

        mob ?: return
        alive.getOrPut(spawner.id) { ConcurrentHashMap.newKeySet() } += mob.uniqueId
        if (GatheringSettings.snapshot.effects) {
            at.world.spawnParticle(Particle.POOF, at.clone().add(0.0, 0.5, 0.0), 8, 0.3, 0.3, 0.3, 0.02)
        }
    }

    /** Walks a mob that has wandered off, or left the region, back to its spawner. */
    private fun keepNear(mob: LivingEntity, region: ResourceRegion, spawner: ResourceSpawner, home: Location) {
        val far = mob.world != home.world ||
                mob.location.distanceSquared(home) > (spawner.radius + STRAY_MARGIN).let { it * it }.toDouble()
        if (!far && region.area.contains(mob.location)) return
        spot(region, spawner, home)?.let { MobSetup.teleport(mob, it) }
    }

    /** A spot within [ResourceSpawner.radius] of the spawner, inside the region, that a mob can stand on. */
    private fun spot(region: ResourceRegion, spawner: ResourceSpawner, home: Location): Location? {
        repeat(SPOT_TRIES) {
            val dx = Random.nextInt(-spawner.radius, spawner.radius + 1)
            val dz = Random.nextInt(-spawner.radius, spawner.radius + 1)
            for (dy in intArrayOf(0, 1, -1, 2, -2)) {
                val spot = home.clone().add(dx.toDouble(), dy.toDouble(), dz.toDouble()).toBlockLocation()
                    .add(0.5, 0.0, 0.5)
                if (region.area.contains(spot) && MobSetup.standable(spot)) return spot
            }
        }
        return null
    }

    private fun isHostile(spawner: ResourceSpawner): Boolean =
        spawner.type.entityClass?.let { Enemy::class.java.isAssignableFrom(it) } == true
}

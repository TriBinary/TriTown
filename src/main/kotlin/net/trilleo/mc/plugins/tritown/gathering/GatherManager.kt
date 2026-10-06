package net.trilleo.mc.plugins.tritown.gathering

import net.trilleo.mc.plugins.tritown.gathering.storage.*
import net.trilleo.mc.plugins.tritown.shops.ItemCodec
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Every resource region, every block harvested in one, and the only thing that
 * reads or writes either.
 *
 * A region is written out the moment it is edited, like a shop. Harvested
 * blocks change on every swing, so they only mark the store dirty and are
 * flushed by [net.trilleo.mc.plugins.tritown.tasks.gathering.GatherSaveTask] —
 * a crash costs at most one interval of them, and they are written once more
 * at shutdown so a restart picks every one back up where it left off.
 */
object GatherManager {

    private val regions = ConcurrentHashMap<String, ResourceRegion>()
    private val depleted = ConcurrentHashMap<BlockKey, DepletedNode>()
    private val dirty = AtomicBoolean(false)

    private lateinit var storage: JsonGatherStorage
    private lateinit var logger: Logger

    @Volatile
    var isReady: Boolean = false
        private set

    fun start(store: JsonGatherStorage, pluginLogger: Logger) {
        storage = store
        logger = pluginLogger

        val loaded = try {
            store.loadRegions()
        } catch (e: GatherStorageException) {
            logger.severe("Resource regions are unavailable: ${e.message}")
            isReady = false
            return
        }

        regions.clear()
        loaded.mapNotNull(::toRegion).forEach { regions[it.id] = it }

        depleted.clear()
        store.loadDepleted().forEach { stored ->
            if (regions.containsKey(stored.region)) {
                val node = toDepleted(stored)
                depleted[node.key] = node
            }
        }

        dirty.set(false)
        isReady = true
        logger.info("Loaded ${regions.size} resource region(s) with ${depleted.size} block(s) growing back")
    }

    fun shutdown() {
        if (!isReady) return
        dirty.set(true)
        flush()
        isReady = false
        regions.clear()
        depleted.clear()
    }

    // ── Regions ─────────────────────────────────────────────────────────

    fun get(id: String): ResourceRegion? = regions[id.lowercase()]

    fun all(): List<ResourceRegion> = regions.values.sortedBy { it.id }

    fun ids(): List<String> = regions.keys.sorted()

    /** The regions of the town with [townId]. */
    fun ofTown(townId: UUID): List<ResourceRegion> = all().filter { it.townId == townId }

    /** The region [block] is in, if any. */
    fun at(block: Block): ResourceRegion? =
        if (!isReady) null else regions.values.firstOrNull { it.area.contains(block) }

    fun at(location: Location): ResourceRegion? =
        if (!isReady) null else regions.values.firstOrNull { it.area.contains(location) }

    /** The region that would collide with [area], other than [except]. */
    fun overlapping(area: Cuboid, except: String? = null): ResourceRegion? =
        regions.values.firstOrNull { it.id != except && it.area.overlaps(area) }

    /** Creates an empty region. Returns `null` when [id] is malformed or taken. */
    fun create(id: String, name: String, area: Cuboid, townId: UUID): ResourceRegion? {
        val key = id.lowercase()
        if (!ResourceRegion.isValidId(key) || regions.containsKey(key)) return null

        val region = ResourceRegion(key, name, area, townId)
        regions[key] = region
        save()
        return region
    }

    /** Takes [id] out, having grown back everything it harvested so nothing is left as bedrock. */
    fun delete(id: String): ResourceRegion? {
        val region = regions[id.lowercase()] ?: return null
        Regrowth.regrowAll(region)
        regions.remove(region.id)
        save()
        return region
    }

    /** Writes every region out now, for an edit that must not be lost. */
    fun save() {
        if (!isReady) return
        storage.saveRegions(regions.values.sortedBy { it.id }.map(::toStored))
    }

    // ── Harvested blocks ────────────────────────────────────────────────

    fun depletedAt(key: BlockKey): DepletedNode? = depleted[key]

    fun depletedAt(block: Block): DepletedNode? = depleted[BlockKey.of(block)]

    fun depletedIn(region: ResourceRegion): List<DepletedNode> = depleted.values.filter { it.regionId == region.id }

    fun allDepleted(): Collection<DepletedNode> = depleted.values

    fun markDepleted(node: DepletedNode) {
        depleted[node.key] = node
        dirty.set(true)
    }

    fun clearDepleted(key: BlockKey) {
        if (depleted.remove(key) != null) dirty.set(true)
    }

    /** Writes the harvested blocks out when any changed since the last write. */
    fun flush() {
        if (!isReady) return
        if (!dirty.compareAndSet(true, false)) return
        storage.saveDepleted(depleted.values.map(::toStored))
    }

    // ── Conversion ──────────────────────────────────────────────────────

    private fun toRegion(stored: StoredRegion): ResourceRegion? {
        val town = runCatching { UUID.fromString(stored.town) }.getOrNull() ?: run {
            logger.warning("Skipped the resource region ${stored.id}: it names no town")
            return null
        }

        return ResourceRegion(
            id = stored.id.lowercase(),
            name = stored.name.ifBlank { stored.id },
            area = Cuboid(stored.world, stored.minX, stored.minY, stored.minZ, stored.maxX, stored.maxY, stored.maxZ),
            townId = town,
            enabled = stored.enabled,
            nodes = stored.nodes.mapNotNull { toNode(stored.id, it) }.toMutableList(),
            spawners = stored.spawners.mapNotNull { toSpawner(stored.id, it) }.toMutableList(),
        )
    }

    private fun toNode(regionId: String, stored: StoredNode): ResourceNode? {
        val block = Material.matchMaterial(stored.block)?.takeIf { it.isBlock } ?: run {
            logger.warning("Dropped a resource of an unknown block (${stored.block}) from $regionId")
            return null
        }

        return ResourceNode(
            id = stored.id.ifBlank { UUID.randomUUID().toString() },
            block = block,
            category = enumOrDefault(stored.category, GatherCategory.of(block)),
            regrowSeconds = stored.regrowSeconds.coerceAtLeast(1),
            depleted = stored.depleted?.let(Material::matchMaterial)?.takeIf { it.isBlock },
            vanillaDrops = stored.vanillaDrops,
            drops = stored.drops.mapNotNull { drop ->
                ItemCodec.decode(drop.item)?.let { ResourceDrop(it, drop.chance) }
            }.toMutableList(),
            pool = stored.pool.mapNotNull { entry ->
                Material.matchMaterial(entry.block)?.takeIf { it.isBlock }?.let { PoolEntry(it, entry.weight) }
            }.toMutableList(),
        )
    }

    private fun toSpawner(regionId: String, stored: StoredSpawner): ResourceSpawner? {
        val type = EntityType.entries.firstOrNull { it.name.equals(stored.entity, ignoreCase = true) } ?: run {
            logger.warning("Dropped a spawner of an unknown mob (${stored.entity}) from $regionId")
            return null
        }

        return ResourceSpawner(
            id = stored.id.ifBlank { UUID.randomUUID().toString() },
            type = type,
            category = enumOrDefault(stored.category, GatherCategory.of(type)),
            x = stored.x,
            y = stored.y,
            z = stored.z,
            maxAlive = stored.maxAlive.coerceAtLeast(1),
            respawnSeconds = stored.respawnSeconds.coerceAtLeast(1),
            radius = stored.radius.coerceAtLeast(1),
            level = stored.level.coerceAtLeast(1),
        )
    }

    private fun toStored(region: ResourceRegion): StoredRegion = StoredRegion(
        id = region.id,
        name = region.name,
        world = region.area.world,
        minX = region.area.minX,
        minY = region.area.minY,
        minZ = region.area.minZ,
        maxX = region.area.maxX,
        maxY = region.area.maxY,
        maxZ = region.area.maxZ,
        town = region.townId.toString(),
        enabled = region.enabled,
        nodes = region.nodes.map { node ->
            StoredNode(
                id = node.id,
                block = node.block.name,
                category = node.category.name,
                regrowSeconds = node.regrowSeconds,
                depleted = node.depleted?.name,
                vanillaDrops = node.vanillaDrops,
                drops = node.drops.map { StoredDrop(ItemCodec.encode(it.item), it.chance) },
                pool = node.pool.map { StoredPoolEntry(it.block.name, it.weight) },
            )
        },
        spawners = region.spawners.map { spawner ->
            StoredSpawner(
                id = spawner.id,
                entity = spawner.type.name,
                category = spawner.category.name,
                x = spawner.x,
                y = spawner.y,
                z = spawner.z,
                maxAlive = spawner.maxAlive,
                respawnSeconds = spawner.respawnSeconds,
                radius = spawner.radius,
                level = spawner.level,
            )
        },
    )

    private fun toDepleted(stored: StoredDepleted) = DepletedNode(
        key = BlockKey(stored.world, stored.x, stored.y, stored.z),
        regionId = stored.region,
        nodeId = stored.node,
        original = stored.original,
        placed = stored.placed,
        depletedAt = stored.depletedAt,
        regrowAt = stored.regrowAt,
    )

    private fun toStored(node: DepletedNode) = StoredDepleted(
        world = node.key.world,
        x = node.key.x,
        y = node.key.y,
        z = node.key.z,
        region = node.regionId,
        node = node.nodeId,
        original = node.original,
        placed = node.placed,
        depletedAt = node.depletedAt,
        regrowAt = node.regrowAt,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
}

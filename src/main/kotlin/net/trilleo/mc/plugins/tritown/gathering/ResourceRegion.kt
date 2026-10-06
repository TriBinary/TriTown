package net.trilleo.mc.plugins.tritown.gathering

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * A part of a town whose resources grow back, and that only the town's
 * residents may gather.
 *
 * Everything inside [area] is protected: only a ripe [ResourceNode] may be
 * broken, nothing may be placed, and nothing grows, spreads or decays except
 * through TriTown. The town is remembered by UUID, and Towny is asked again on
 * every harvest whether the ground is still the town's.
 *
 * [name] is administrator-written MiniMessage, shown as written.
 */
class ResourceRegion(
    val id: String,
    var name: String,
    var area: Cuboid,
    var townId: UUID,
    var enabled: Boolean = true,
    val nodes: MutableList<ResourceNode> = mutableListOf(),
    val spawners: MutableList<ResourceSpawner> = mutableListOf(),
) {

    /** The rule for blocks of [material], or `null` when they are not a resource here. */
    fun nodeFor(material: Material): ResourceNode? = nodes.firstOrNull { it.block == material }

    fun node(id: String): ResourceNode? = nodes.firstOrNull { it.id == id }

    fun spawner(id: String): ResourceSpawner? = spawners.firstOrNull { it.id == id }

    /** Every category the region offers, in the enum's order. */
    fun categories(): List<GatherCategory> =
        (nodes.map { it.category } + spawners.map { it.category }).distinct().sortedBy { it.ordinal }

    companion object {
        private val VALID_ID = Regex("[a-z0-9_-]{1,32}")

        fun isValidId(id: String): Boolean = VALID_ID.matches(id)
    }
}

/**
 * One kind of block in a region that can be harvested, and how it grows back.
 *
 * Every block of [block] inside the region is a node, so an administrator
 * builds a mine out of ore and adds one rule, rather than registering each
 * block. A crop is ripe only at its last stage, and grows back through its
 * stages; anything else is replaced by its [depleted] block until it returns.
 *
 * @param depleted     what stands in for a harvested node, or `null` for the category's default
 * @param vanillaDrops whether the block drops what it would in vanilla, before [drops] are added
 * @param pool         what the node may grow back as, by weight; empty to grow back as itself
 */
class ResourceNode(
    val id: String = UUID.randomUUID().toString(),
    var block: Material,
    var category: GatherCategory,
    var regrowSeconds: Int,
    var depleted: Material? = null,
    var vanillaDrops: Boolean = true,
    val drops: MutableList<ResourceDrop> = mutableListOf(),
    val pool: MutableList<PoolEntry> = mutableListOf(),
) {

    /** Whether the node is a crop that ripens in stages, and grows back through them. */
    val growsInStages: Boolean
        get() = depleted == null && isStaged(block)

    /** Whether breaking the node takes the same plant stacked on top of it as well. */
    val isColumn: Boolean
        get() = block in COLUMNS

    /** Whether [block] is ready to be harvested. */
    fun isRipe(block: Block): Boolean {
        if (block.type != this.block) return false
        val data = block.blockData as? Ageable ?: return true
        return !growsInStages || data.age >= data.maximumAge
    }

    /** One draw from [pool], or `null` to grow back as whatever was there. */
    fun rollRegrowth(random: Random): Material? {
        val total = pool.sumOf { it.weight.coerceAtLeast(0) }
        if (total <= 0) return null
        var pick = random.nextInt(total)
        for (entry in pool) {
            pick -= entry.weight.coerceAtLeast(0)
            if (pick < 0) return entry.block
        }
        return null
    }

    companion object {
        private val STAGED = setOf(Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH)
        private val COLUMNS = setOf(Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO)

        fun isStaged(material: Material): Boolean =
            (Tag.CROPS.isTagged(material) || material in STAGED) && material.createBlockData() is Ageable
    }
}

/** An extra item a node or its harvest may give, [chance] percent of the time. */
class ResourceDrop(val item: ItemStack, var chance: Double)

/** One block a node may grow back as, and how likely it is against the others. */
class PoolEntry(val block: Material, var weight: Int)

/**
 * A spot in a region that keeps up to [maxAlive] mobs of [type] about, calling
 * up another [respawnSeconds] after one is lost.
 *
 * Its mobs stay within [radius] blocks of it. A hostile one is [level]; an
 * animal has no level.
 */
class ResourceSpawner(
    val id: String = UUID.randomUUID().toString(),
    var type: EntityType,
    var category: GatherCategory,
    var x: Double,
    var y: Double,
    var z: Double,
    var maxAlive: Int,
    var respawnSeconds: Int,
    var radius: Int,
    var level: Int = 1,
) {
    fun location(region: ResourceRegion): Location? = region.area.bukkitWorld()?.let { Location(it, x, y, z) }
}

/**
 * A harvested block waiting to grow back.
 *
 * [original] is the block as it was, as Bukkit's block data string; [placed]
 * is what TriTown put in its place, and if anything else is there by the time
 * it is due, somebody has rebuilt the spot and it is left alone.
 */
data class DepletedNode(
    val key: BlockKey,
    val regionId: String,
    val nodeId: String,
    val original: String,
    val placed: String,
    val depletedAt: Long,
    val regrowAt: Long,
) {
    /** How far through growing back the node is, from 0 to 1. */
    fun progress(now: Long): Double =
        if (regrowAt <= depletedAt) 1.0 else ((now - depletedAt).toDouble() / (regrowAt - depletedAt)).coerceIn(0.0, 1.0)
}

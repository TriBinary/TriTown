package net.trilleo.mc.plugins.tritown.gathering

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block

/** A box of whole blocks in one world, both corners included. */
data class Cuboid(
    val world: String,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {

    val volume: Long
        get() = (maxX - minX + 1).toLong() * (maxY - minY + 1) * (maxZ - minZ + 1)

    fun bukkitWorld(): World? = Bukkit.getWorld(world)

    fun contains(world: String, x: Int, y: Int, z: Int): Boolean =
        world == this.world && x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    fun contains(block: Block): Boolean = contains(block.world.name, block.x, block.y, block.z)

    fun contains(location: Location): Boolean =
        contains(location.world.name, location.blockX, location.blockY, location.blockZ)

    fun overlaps(other: Cuboid): Boolean =
        world == other.world &&
                minX <= other.maxX && maxX >= other.minX &&
                minY <= other.maxY && maxY >= other.minY &&
                minZ <= other.maxZ && maxZ >= other.minZ

    /** The middle of the box at its floor, which is where a region is said to be. */
    fun center(world: World): Location =
        Location(world, (minX + maxX) / 2.0 + 0.5, minY.toDouble(), (minZ + maxZ) / 2.0 + 0.5)

    /** The four corner columns and the middle, at the floor: the spots a town has to own. */
    fun footprint(world: World): List<Location> = listOf(
        Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
        Location(world, maxX.toDouble(), minY.toDouble(), minZ.toDouble()),
        Location(world, minX.toDouble(), minY.toDouble(), maxZ.toDouble()),
        Location(world, maxX.toDouble(), minY.toDouble(), maxZ.toDouble()),
        center(world),
    )

    companion object {
        fun of(a: Location, b: Location): Cuboid = Cuboid(
            world = a.world.name,
            minX = minOf(a.blockX, b.blockX), minY = minOf(a.blockY, b.blockY), minZ = minOf(a.blockZ, b.blockZ),
            maxX = maxOf(a.blockX, b.blockX), maxY = maxOf(a.blockY, b.blockY), maxZ = maxOf(a.blockZ, b.blockZ),
        )
    }
}

/** One block's address, for keying what has been harvested. */
data class BlockKey(val world: String, val x: Int, val y: Int, val z: Int) {

    fun block(): Block? = Bukkit.getWorld(world)?.getBlockAt(x, y, z)

    fun isLoaded(): Boolean = Bukkit.getWorld(world)?.isChunkLoaded(x shr 4, z shr 4) == true

    companion object {
        fun of(block: Block) = BlockKey(block.world.name, block.x, block.y, block.z)
        fun of(location: Location) = BlockKey(location.world.name, location.blockX, location.blockY, location.blockZ)
    }
}

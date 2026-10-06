package net.trilleo.mc.plugins.tritown.gathering.storage

/**
 * A resource region as it sits on disk.
 *
 * Kept apart from the live model for the same reason a shop is: items are
 * Base64 here, so the storage layer never names a Bukkit type. Gson builds these
 * by reflection, so every field needs a default.
 */
data class StoredRegion(
    val id: String = "",
    val name: String = "",
    val world: String = "",
    val minX: Int = 0,
    val minY: Int = 0,
    val minZ: Int = 0,
    val maxX: Int = 0,
    val maxY: Int = 0,
    val maxZ: Int = 0,
    val town: String = "",
    val enabled: Boolean = true,
    val nodes: List<StoredNode> = emptyList(),
    val spawners: List<StoredSpawner> = emptyList(),
)

data class StoredNode(
    val id: String = "",
    val block: String = "",
    val category: String = "MINING",
    val regrowSeconds: Int = 60,
    val depleted: String? = null,
    val vanillaDrops: Boolean = true,
    val drops: List<StoredDrop> = emptyList(),
    val pool: List<StoredPoolEntry> = emptyList(),
)

data class StoredDrop(val item: String = "", val chance: Double = 100.0)

data class StoredPoolEntry(val block: String = "", val weight: Int = 1)

data class StoredSpawner(
    val id: String = "",
    val entity: String = "",
    val category: String = "HUSBANDRY",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val maxAlive: Int = 3,
    val respawnSeconds: Int = 30,
    val radius: Int = 6,
    val level: Int = 1,
)

/** A harvested block on disk; see [net.trilleo.mc.plugins.tritown.gathering.DepletedNode]. */
data class StoredDepleted(
    val world: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val z: Int = 0,
    val region: String = "",
    val node: String = "",
    val original: String = "",
    val placed: String = "",
    val depletedAt: Long = 0L,
    val regrowAt: Long = 0L,
)
